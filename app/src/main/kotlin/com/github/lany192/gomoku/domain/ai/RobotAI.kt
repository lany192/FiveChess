package com.github.lany192.gomoku.domain.ai

import com.github.lany192.gomoku.domain.ai.core.BoardScanner
import com.github.lany192.gomoku.domain.ai.core.Candidate
import com.github.lany192.gomoku.domain.ai.core.CandidateGenerator
import com.github.lany192.gomoku.domain.ai.core.ShapeEvaluator
import com.github.lany192.gomoku.domain.ai.core.ShapeScores
import com.github.lany192.gomoku.domain.ai.core.Stone
import com.github.lany192.gomoku.domain.ai.core.Tactics
import com.github.lany192.gomoku.domain.model.Point
import kotlin.random.Random

/**
 * 电脑AI：棋型评估 + 带Alpha-Beta剪枝的极小极大搜索
 *
 * 也是 25 种可选算法中的「棋型评分」（默认档）。输入棋盘沿用整型编码：0=空 1=黑 2=白。
 * 非线程安全：同一实例不可并发调用（由 ViewModel 保证同一时刻只有一个搜索任务）。
 */
class RobotAI(
    private val width: Int,
    private val height: Int,
    level: Difficulty = Difficulty.MEDIUM,
    private val random: Random = Random.Default,
) : GomokuAI {

    override var level: Difficulty = level

    private val scanner = BoardScanner(width, height)
    private val generator = CandidateGenerator(width, height, scanner)
    private val evaluator = ShapeEvaluator(width, height)

    /**
     * 获取最佳下棋位置
     */
    override fun getPosition(board: Array<IntArray>): Point {
        // 复制棋盘，避免搜索过程修改调用方的棋盘
        val local = Array(width) { x -> board[x].copyOf() }
        // 快照档位：思考途中改档不应让本次搜索读到新的候选宽度/半径
        val config = level

        val candidates = generator.generate(local, config.breadth, config.radius, AI, HUMAN)
        // 低难度按概率看漏战术：没留意时连五点被剔除出候选，避免误打误撞仍封堵/取胜
        val alert = random.nextInt(100) < config.alertPercent
        if (alert) {
            // 1.自己下一手能连五，直接取胜
            Tactics.findFive(local, candidates, AI, scanner)?.let { return it }
            // 2.对手下一手能连五，必须封堵
            Tactics.findFive(local, candidates, HUMAN, scanner)?.let { return it }
        }
        // 3.不搜索的档位只在启发式候选里挑，不做活四检测，留出破绽；
        //   连五点留给上面的战术判定（没留意到就一并剔除，避免误打误撞仍封堵/取胜）
        if (config.depth == 0) {
            val pool = candidates.filterNot { isFivePoint(local, it) }
            return heuristicMove(pool.ifEmpty { candidates }, config)
        }
        // 4.自己能形成活四（对手堵不住），必胜
        Tactics.findLiveFour(local, candidates, AI, scanner)?.let { return it }

        val best = searchRoot(local, candidates, config)
        if (best != null) {
            return Point(best.x, best.y)
        }
        // 兜底：找任意空位
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (local[x][y] == EMPTY) return Point(x, y)
            }
        }
        return Point(width / 2, height / 2)
    }

    /**
     * 根层搜索，带Alpha-Beta窗口传递
     */
    private fun searchRoot(board: Array<IntArray>, candidates: List<Candidate>, level: Difficulty): Candidate? {
        var best: Candidate? = null
        var alpha = -ShapeScores.WIN * 2
        val beta = ShapeScores.WIN * 2
        for (c in candidates) {
            board[c.x][c.y] = AI
            val score = if (scanner.isFiveAt(board, c.x, c.y, AI)) {
                ShapeScores.WIN
            } else {
                search(board, level.depth - 1, alpha, beta, aiTurn = false, ply = 1, level = level)
            }
            board[c.x][c.y] = EMPTY
            if (best == null || score > best.score) {
                best = Candidate(c.x, c.y, score)
            }
            if (score > alpha) {
                alpha = score
            }
        }
        return best
    }

    /**
     * 极小极大搜索，带Alpha-Beta剪枝，评分始终以AI视角为准
     *
     * @param aiTurn 当前是否轮到AI落子
     * @param ply    当前步数（用于优先选择更快的胜利）
     */
    private fun search(
        board: Array<IntArray>,
        depth: Int,
        alpha: Long,
        beta: Long,
        aiTurn: Boolean,
        ply: Int,
        level: Difficulty,
    ): Long {
        if (depth <= 0) {
            return evaluator.evaluate(board, AI)
        }
        val candidates = generator.generate(board, level.breadth, level.radius, AI, HUMAN)
        if (candidates.isEmpty()) {
            return evaluator.evaluate(board, AI)
        }
        val color = if (aiTurn) AI else HUMAN
        var best = if (aiTurn) -ShapeScores.WIN * 2 else ShapeScores.WIN * 2
        var a = alpha
        var b = beta
        for (c in candidates) {
            board[c.x][c.y] = color
            val score = if (scanner.isFiveAt(board, c.x, c.y, color)) {
                // 落子即连五，越早赢分越高
                if (aiTurn) ShapeScores.WIN - ply else -(ShapeScores.WIN - ply)
            } else {
                search(board, depth - 1, a, b, !aiTurn, ply + 1, level)
            }
            board[c.x][c.y] = EMPTY

            if (aiTurn) {
                if (score > best) best = score
                if (best > a) a = best
            } else {
                if (score < best) best = score
                if (best < b) b = best
            }
            if (a >= b) break
        }
        return best
    }

    /**
     * 是否是"任一方落子即连五"的点
     */
    private fun isFivePoint(board: Array<IntArray>, c: Candidate): Boolean {
        board[c.x][c.y] = AI
        val aiFive = scanner.isFiveAt(board, c.x, c.y, AI)
        board[c.x][c.y] = HUMAN
        val humanFive = scanner.isFiveAt(board, c.x, c.y, HUMAN)
        board[c.x][c.y] = EMPTY
        return aiFive || humanFive
    }

    /**
     * 不搜索档位的落子：候选点已按棋型分排序，按档位随机度逐级降级挑选，越往后棋型越差
     */
    private fun heuristicMove(candidates: List<Candidate>, level: Difficulty): Point {
        var index = 0
        while (index < candidates.lastIndex && random.nextInt(100) < level.noise) {
            index++
        }
        val candidate = candidates[index]
        return Point(candidate.x, candidate.y)
    }

    private companion object {
        // 棋盘整型编码：1=黑（人类），2=白（AI）
        const val EMPTY = Stone.EMPTY
        const val HUMAN = Stone.BLACK
        const val AI = Stone.WHITE
    }
}
