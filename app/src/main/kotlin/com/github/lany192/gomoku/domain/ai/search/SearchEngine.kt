package com.github.lany192.gomoku.domain.ai.search

import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.AiTunings
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.ai.EngineTuning
import com.github.lany192.gomoku.domain.ai.core.AbstractAiEngine
import com.github.lany192.gomoku.domain.ai.core.Candidate
import com.github.lany192.gomoku.domain.ai.core.Evaluator
import com.github.lany192.gomoku.domain.ai.core.ShapeEvaluator
import com.github.lany192.gomoku.domain.ai.core.ShapeScores
import com.github.lany192.gomoku.domain.ai.core.Stone
import com.github.lany192.gomoku.domain.model.Point
import kotlin.random.Random

/**
 * 搜索类引擎：7 个预设共用这套负极大搜索，差异全部来自 [AiTunings.search]。
 *
 * 控制流按预设真实分化：NONE 不剪枝（α/β 恒为 ±INF）、PVS 零窗重搜、
 * MTD(f) 零窗口迭代收敛、迭代加深只返回已完成深度的着法。
 *
 * 评估类（模式匹配/神经网络）复用本引擎，只替换 [Evaluator]。
 */
class SearchEngine(
    private val algorithm: AiAlgorithm,
    width: Int,
    height: Int,
    level: Difficulty,
    random: Random,
    clock: () -> Long = System::nanoTime,
    evaluator: Evaluator = ShapeEvaluator(width, height),
) : AbstractAiEngine(width, height, level, random, clock) {

    private val evaluator: Evaluator = evaluator
    private val zobrist = Zobrist(random, width, height)

    // 结构参数（裁剪/TT/杀手槽）与档位无关，取 MEDIUM 作为模板即可
    private val shape = AiTunings.search(algorithm, Difficulty.MEDIUM)
    private val table = if (shape.transposition) TranspositionTable(shape.tableBits) else null
    private val ordering = MoveOrdering(width, height, shape.killerSlots)

    override fun algorithm(): AiAlgorithm = algorithm

    override fun decide(board: Array<IntArray>, tuning: EngineTuning, candidates: List<Candidate>): Point {
        return searchOn(board) ?: candidates.firstOrNull()?.let { Point(it.x, it.y) }
            ?: Point(width / 2, height / 2)
    }

    /**
     * 直接对传入棋盘搜索（调用方保证是副本）。
     *
     * 威胁搜索的回退复用这个入口：跳过 [AbstractAiEngine] 的兜底与弱档失误，
     * 避免二层包装把同一手随机化两次；学习类用 [maxDepth] 收紧到浅搜。
     */
    internal fun searchOn(board: Array<IntArray>, level: Difficulty = this.level, maxDepth: Int = 0): Point? {
        val tuning = AiTunings.search(algorithm, level)
        val options = if (maxDepth > 0) tuning.copy(maxDepth = maxDepth) else tuning
        val session = SearchSession(board, options)
        session.prepare()
        val index = session.search()
        if (index < 0) return null
        return Point(index / height, index % height)
    }

    override fun onGameReset() {
        ordering.clear()
        table?.newSearch()
    }

    private inner class SearchSession(
        private val board: Array<IntArray>,
        private val options: SearchOptions,
    ) {
        private var hash = 0L
        private var nodes = 0
        private var aborted = false

        /** 本轮（或本次搜索）已搜索着法中最好的，aborted 时也保留最优的半个结果 */
        private var rootMove = TranspositionTable.NO_MOVE

        private val startNanos = clock()

        fun prepare() {
            hash = zobrist.hash(board, Stone.WHITE)
            ordering.clearKillers()
            table?.newSearch()
        }

        fun search(): Int {
            val depth = options.maxDepth.coerceIn(1, AiTunings.MAX_PLY)
            return when {
                options.mtdPasses > 0 -> mtdf(depth)
                options.iterativeDeepening -> iterativeDeepening(depth)
                else -> {
                    rootMove = TranspositionTable.NO_MOVE
                    negamax(depth, -INF, INF, 0, Stone.WHITE)
                    rootMove()
                }
            }
        }

        /** MTD(f)：零窗口反复收敛，每次都能靠 TT 里的根着法重启，否则退化成普通 α-β */
        private fun mtdf(depth: Int): Int {
            var guess = evaluator.evaluate(board, Stone.WHITE)
            var lower = -INF
            var upper = INF
            var passes = 0
            while (passes < options.mtdPasses && lower < upper && !aborted) {
                val beta = if (guess == lower) guess + 1 else guess
                rootMove = TranspositionTable.NO_MOVE
                guess = negamax(depth, beta - 1, beta, 0, Stone.WHITE)
                if (aborted) break
                if (guess < beta) upper = guess else lower = guess
                passes++
            }
            return rootMove()
        }

        /** 逐层加深：中断的那一层整层丢弃，只采用已完成深度的着法 */
        private fun iterativeDeepening(depth: Int): Int {
            var best = TranspositionTable.NO_MOVE
            var bestScore = 0L
            var d = 1
            while (d <= depth && !aborted) {
                rootMove = TranspositionTable.NO_MOVE
                var alpha = -INF
                var beta = INF
                if (options.aspiration && best != TranspositionTable.NO_MOVE) {
                    alpha = bestScore - ASPIRATION
                    beta = bestScore + ASPIRATION
                }
                var value = negamax(d, alpha, beta, 0, Stone.WHITE)
                if (!aborted && options.aspiration && best != TranspositionTable.NO_MOVE &&
                    (value <= alpha || value >= beta)
                ) {
                    // 渴望窗口落空，全窗口重搜同一层
                    rootMove = TranspositionTable.NO_MOVE
                    value = negamax(d, -INF, INF, 0, Stone.WHITE)
                }
                if (aborted) break
                val found = table?.bestMove(hash) ?: TranspositionTable.NO_MOVE
                val move = if (found != TranspositionTable.NO_MOVE) found else rootMove
                if (move == TranspositionTable.NO_MOVE) break
                best = move
                bestScore = value
                // 已证明胜负，再深也是同一个结果
                if (isMateScore(value)) break
                d++
            }
            return best
        }

        private fun rootMove(): Int {
            if (table != null) {
                val found = table.bestMove(hash)
                if (found != TranspositionTable.NO_MOVE) return found
            }
            return rootMove
        }

        private fun negamax(depth: Int, alpha: Long, beta: Long, ply: Int, side: Int): Long {
            if (aborted) return 0L
            if (++nodes % FUSE_CHECK_INTERVAL == 0) checkFuses()

            val ttKey = if (table != null) hash else 0L
            var ttMove = TranspositionTable.NO_MOVE
            if (table != null) {
                table.probe(ttKey, depth, alpha, beta)?.let { return it }
                ttMove = table.bestMove(ttKey)
            }
            if (depth <= 0) return evaluator.evaluate(board, side)

            val moves = generator.generate(board, options.breadth, options.radius, side, Stone.opponent(side))
            if (moves.isEmpty()) return evaluator.evaluate(board, side)
            ordering.order(moves, ply, ttMove)

            val unpruned = options.pruning == Pruning.NONE
            // 极小极大预设不做任何裁剪：α/β 恒为 ±INF，不早停
            var a = if (unpruned) -INF else alpha
            var b = if (unpruned) INF else beta
            var best = -INF
            var localBest = TranspositionTable.NO_MOVE
            var flag = TranspositionTable.UPPER
            var first = true

            for (c in moves) {
                if (board[c.x][c.y] != Stone.EMPTY) continue
                val index = c.x * height + c.y
                board[c.x][c.y] = side
                hash = hash xor zobrist.stoneKey(c.x, c.y, side) xor zobrist.sideKey
                val value = if (scanner.isFiveAt(board, c.x, c.y, side)) {
                    ShapeScores.WIN - ply
                } else if (options.pruning == Pruning.PVS && !first) {
                    // 零窗口试探；失败再用全窗口重搜
                    val narrow = -negamax(depth - 1, -a - 1, -a, ply + 1, Stone.opponent(side))
                    if (narrow > a && !aborted) {
                        -negamax(depth - 1, -b, -a, ply + 1, Stone.opponent(side))
                    } else {
                        narrow
                    }
                } else {
                    -negamax(depth - 1, -b, -a, ply + 1, Stone.opponent(side))
                }
                board[c.x][c.y] = Stone.EMPTY
                hash = hash xor zobrist.stoneKey(c.x, c.y, side) xor zobrist.sideKey
                first = false
                if (aborted) return 0L

                if (value > best) {
                    best = value
                    localBest = index
                    flag = TranspositionTable.EXACT
                    if (ply == 0) rootMove = index
                }
                if (value > a) {
                    a = value
                    if (!unpruned && value >= b && !isMateScore(value)) {
                        if (options.killerSlots > 0) ordering.recordKiller(ply, index)
                        if (options.historyHeuristic) ordering.recordHistory(index, depth)
                    }
                    if (!unpruned && value >= b) {
                        flag = TranspositionTable.LOWER
                        break
                    }
                }
            }

            if (table != null && !aborted) {
                // store 内部拒绝杀棋分，这里只需补记着法（MTD(f) 与排序都要用）
                table.store(ttKey, depth, best, flag)
                if (localBest != TranspositionTable.NO_MOVE) table.storeMove(ttKey, localBest)
            }
            return best
        }

        private fun checkFuses() {
            if (nodes >= options.nodeLimit) {
                aborted = true
                return
            }
            if (options.timeBudgetMillis > 0 &&
                (clock() - startNanos) / 1_000_000 >= options.timeBudgetMillis
            ) {
                aborted = true
            }
        }
    }

    private companion object {
        const val INF = ShapeScores.WIN * 1000
        const val ASPIRATION = ShapeScores.LIVE_THREE
        const val FUSE_CHECK_INTERVAL = 1024

        fun isMateScore(value: Long): Boolean =
            value >= TranspositionTable.MATE_THRESHOLD || value <= -TranspositionTable.MATE_THRESHOLD
    }
}
