package com.github.lany192.gomoku.domain.ai.threat

import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.AiTunings
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.ai.EngineTuning
import com.github.lany192.gomoku.domain.ai.core.AbstractAiEngine
import com.github.lany192.gomoku.domain.ai.core.BoardScanner
import com.github.lany192.gomoku.domain.ai.core.Candidate
import com.github.lany192.gomoku.domain.ai.core.Stone
import com.github.lany192.gomoku.domain.ai.search.SearchEngine
import com.github.lany192.gomoku.domain.model.Point
import kotlin.random.Random

/**
 * 威胁搜索：只算"逼着"（VCF 冲四 / VCT 再加活三）连击出的杀棋。
 *
 * 防守方永远是**被迫应手**：先看自己的连五点（反杀）——不查就会算出送人头的"必胜"，
 * 再看攻击方有几个连五点（≥2 即活四/双四，堵不过来）；都没有才逐点堵。
 * 无杀时回退普通 α-β 搜索，保证棋力不塌。
 */
class ThreatEngine(
    private val algorithm: AiAlgorithm,
    width: Int,
    height: Int,
    level: Difficulty,
    random: Random,
    clock: () -> Long = System::nanoTime,
) : AbstractAiEngine(width, height, level, random, clock) {

    private val fallback = SearchEngine(AiAlgorithm.ALPHA_BETA, width, height, level, random, clock)

    override fun algorithm(): AiAlgorithm = algorithm

    override fun decide(board: Array<IntArray>, tuning: EngineTuning, candidates: List<Candidate>): Point {
        findKill(board)?.let { return it }
        return fallback.searchOn(board, level)
            ?: candidates.firstOrNull()?.let { Point(it.x, it.y) }
            ?: Point(width / 2, height / 2)
    }

    /** 只做杀棋搜索（不走兜底），无杀返回 null */
    internal fun findKill(board: Array<IntArray>, level: Difficulty = this.level): Point? {
        val options = AiTunings.threat(algorithm, level)
        return when (options.mode) {
            ThreatMode.VCF -> kill(board, options, ThreatMode.VCF)
            ThreatMode.VCT -> kill(board, options, ThreatMode.VCT)
            ThreatMode.KILL -> kill(board, options, ThreatMode.VCF)
                ?: kill(board, options, ThreatMode.VCT)
        }
    }

    private fun kill(board: Array<IntArray>, options: ThreatOptions, mode: ThreatMode): Point? =
        ThreatSession(board, options.copy(mode = mode)).kill()

    private inner class ThreatSession(
        private val board: Array<IntArray>,
        private val options: ThreatOptions,
    ) {
        private val attacker = Stone.WHITE
        private val defender = Stone.BLACK
        private var nodes = 0
        private var aborted = false
        private val startNanos = clock()
        private val seen = BooleanArray(width * height)

        /** 迭代加深：第一次找到的杀就是最短杀（同层内候选按棋型分降序，取分最高） */
        fun kill(): Point? {
            for (depth in 1..options.depth) {
                val move = attackMove(depth)
                if (move != null) return move
                if (aborted) return null
            }
            return null
        }

        /** 攻击方视角：depth 手内能否杀，返回首着 */
        private fun attackMove(depth: Int): Point? {
            if (aborted) return null
            if (++nodes % FUSE_CHECK_INTERVAL == 0) checkFuses()

            // 自己成五
            fivePoints(attacker).firstOrNull()?.let { return it }

            // 对手有连五点（反杀）：必须堵，且堵的同时要形成四，否则这条线断
            val rivalFives = fivePoints(defender)
            if (rivalFives.isNotEmpty()) {
                if (depth <= 0 || rivalFives.size > 1) return null
                val block = rivalFives[0]
                place(block, attacker)
                val win = fivePoints(attacker).isNotEmpty() && reply(depth - 1)
                unplace(block)
                return if (win) block else null
            }

            if (depth <= 0) return null
            for (c in attackMoves()) {
                val move = Point(c.x, c.y)
                place(move, attacker)
                val win = scanner.isFiveAt(board, c.x, c.y, attacker) || reply(depth - 1)
                unplace(move)
                if (aborted) return null
                if (win) return move
            }
            return null
        }

        /** 防守方视角：攻击方刚形成威胁，返回攻击方是否仍能取胜 */
        private fun reply(depth: Int): Boolean {
            // 防守方能自己连五就先反杀
            if (fivePoints(defender).isNotEmpty()) return false

            val fives = fivePoints(attacker)
            if (fives.size >= 2) return true // 活四/双四，堵不过来
            if (fives.size == 1) {
                // 防守方的应手是被迫的，不消耗攻击方的手数预算
                val block = fives[0]
                place(block, defender)
                val win = attackMove(depth) != null
                unplace(block)
                return win
            }

            if (depth <= 0) return false
            // VCT 的活三威胁：防守点在"破我活四的点 + 自己的反四"里选，全部防住才算攻击方失败
            val defenses = defensePoints()
            if (defenses.isEmpty()) return true
            for (p in defenses) {
                place(p, defender)
                val win = attackMove(depth) != null
                unplace(p)
                if (aborted) return false
                if (!win) return false
            }
            return true
        }

        /** 攻击着法：逼着（落子成四/成五），VCT/KILL 额外允许活三 */
        private fun attackMoves(): List<Candidate> {
            val moves = ArrayList<Candidate>()
            // 去重数组跨调用复用，必须先清空，否则上一层的候选会污染这一层
            java.util.Arrays.fill(seen, false)
            fill(moves, generator.forcingMoves(board, attacker, options.radius, options.breadth))
            if (options.mode != ThreatMode.VCF) {
                fill(moves, generator.liveThreeMoves(board, attacker, options.radius, options.breadth))
            }
            return generator.rank(moves, options.breadth)
        }

        private fun fill(target: MutableList<Candidate>, source: List<Candidate>) {
            for (c in source) {
                val index = c.x * height + c.y
                if (seen[index]) continue
                seen[index] = true
                target.add(c)
            }
        }

        /** 防守方的候选应手：攻击方能成活四的点 + 防守方自己的反四点 */
        private fun defensePoints(): List<Point> {
            val points = ArrayList<Point>()
            java.util.Arrays.fill(seen, false)
            for (p in liveFourPoints(attacker)) {
                seen[p.x * height + p.y] = true
                points.add(p)
            }
            for (c in generator.forcingMoves(board, defender, options.radius, options.breadth)) {
                val index = c.x * height + c.y
                if (seen[index]) continue
                seen[index] = true
                points.add(Point(c.x, c.y))
            }
            return points
        }

        /** color 落子即连五的空点（连五点必定与某颗子距离 1，用半径 1 过滤即可） */
        private fun fivePoints(color: Int): List<Point> {
            val points = ArrayList<Point>(2)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    if (board[x][y] != Stone.EMPTY || !generator.hasNeighbor(board, x, y, 1)) continue
                    board[x][y] = color
                    val five = scanner.isFiveAt(board, x, y, color)
                    board[x][y] = Stone.EMPTY
                    if (five) points.add(Point(x, y))
                }
            }
            return points
        }

        /** color 落子后形成活四（两端可成五）的点 */
        private fun liveFourPoints(color: Int): List<Point> {
            val points = ArrayList<Point>(2)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    if (board[x][y] != Stone.EMPTY || !generator.hasNeighbor(board, x, y, options.radius)) continue
                    board[x][y] = color
                    var live = false
                    for (d in BoardScanner.DIRS) {
                        scanner.lineInfo(board, x, y, color, d)
                        if (scanner.count == 4 && scanner.opens >= 2) {
                            live = true
                            break
                        }
                    }
                    board[x][y] = Stone.EMPTY
                    if (live) points.add(Point(x, y))
                }
            }
            return points
        }

        private fun place(p: Point, color: Int) {
            board[p.x][p.y] = color
        }

        private fun unplace(p: Point) {
            board[p.x][p.y] = Stone.EMPTY
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
        const val FUSE_CHECK_INTERVAL = 256
    }
}
