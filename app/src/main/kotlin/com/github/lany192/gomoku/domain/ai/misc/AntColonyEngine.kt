package com.github.lany192.gomoku.domain.ai.misc

import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.AiTunings
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.ai.EngineTuning
import com.github.lany192.gomoku.domain.ai.core.AbstractAiEngine
import com.github.lany192.gomoku.domain.ai.core.Candidate
import com.github.lany192.gomoku.domain.ai.core.ShapeScores
import com.github.lany192.gomoku.domain.ai.core.Stone
import com.github.lany192.gomoku.domain.model.Point
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * 蚁群：信息素在候选点上累积，每步放出若干蚂蚁按 τ^α·η^β 轮盘选点，
 * 对每只蚂蚁的选点做"对手贪心应答"模拟估质量，蒸发 ρ 后按质量沉积，取 τ×η 最大的点。
 *
 * 信息素是实例状态、跨手保留（同一引擎实例的一局内越走越"记路"）；
 * 棋盘风格的无记忆局面由 η 兜底，新开一局时 [onGameReset] 清空。
 */
class AntColonyEngine(
    private val algorithm: AiAlgorithm,
    width: Int,
    height: Int,
    level: Difficulty,
    random: Random,
    clock: () -> Long = System::nanoTime,
) : AbstractAiEngine(width, height, level, random, clock) {

    /** 全盘信息素，初值 1.0（不偏向任何点） */
    private val pheromone = DoubleArray(width * height) { 1.0 }

    override fun algorithm(): AiAlgorithm = algorithm

    override fun onGameReset() {
        pheromone.fill(1.0)
    }

    override fun decide(board: Array<IntArray>, tuning: EngineTuning, candidates: List<Candidate>): Point {
        val options = AiTunings.misc(algorithm, level)
        val limit = minOf(candidates.size, options.breadth)
        if (limit <= 0) return Point(width / 2, height / 2)

        val eta = DoubleArray(limit) { i ->
            val c = candidates[i]
            1.0 + norm(generator.pointScore(board, c.x, c.y, self)) +
                    norm(generator.pointScore(board, c.x, c.y, rival))
        }
        val weight = DoubleArray(limit) { i ->
            pheromone[candidates[i].x * height + candidates[i].y].pow(options.antAlpha) *
                    eta[i].pow(options.antBeta)
        }
        val deposit = DoubleArray(limit)
        val startNanos = clock()
        var ants = 0
        while (ants < options.ants) {
            if (options.timeBudgetMillis > 0 &&
                (clock() - startNanos) / 1_000_000 >= options.timeBudgetMillis
            ) {
                break
            }
            val pick = roulette(weight, limit)
            if (pick >= 0) deposit[pick] += quality(board, candidates[pick], options.radius)
            ants++
        }

        // 蒸发 + 沉积（沉积用本步蚂蚁质量，未探索的点只受蒸发影响）
        val keep = 1.0 - options.antEvaporation
        for (i in 0 until limit) {
            val index = candidates[i].x * height + candidates[i].y
            pheromone[index] = pheromone[index] * keep + deposit[i]
        }

        var best = 0
        var bestScore = -1.0
        for (i in 0 until limit) {
            val c = candidates[i]
            val score = pheromone[c.x * height + c.y] * eta[i]
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        return Point(candidates[best].x, candidates[best].y)
    }

    /** 轮盘赌：按权重累计抽取，权重全为 0 时退化为均匀随机 */
    private fun roulette(weight: DoubleArray, limit: Int): Int {
        var total = 0.0
        for (i in 0 until limit) total += weight[i]
        if (total <= 0.0) return random.nextInt(limit)
        var target = random.nextDouble() * total
        for (i in 0 until limit) {
            target -= weight[i]
            if (target <= 0.0) return i
        }
        return limit - 1
    }

    /** 质量评估：我落子后对手贪心应答，再看我这点的残余价值（0..1） */
    private fun quality(board: Array<IntArray>, c: Candidate, radius: Int): Double {
        board[c.x][c.y] = self
        val replies = generator.generate(board, REPLY_BREADTH, radius, rival, self)
        var bestReply = 0L
        var replyPoint: Candidate? = null
        for (r in replies) {
            if (board[r.x][r.y] != Stone.EMPTY) continue
            val threat = generator.pointScore(board, r.x, r.y, rival)
            if (threat > bestReply) {
                bestReply = threat
                replyPoint = r
            }
        }
        // 对手真按贪心应答走一手，再看我的收益残余
        replyPoint?.let { board[it.x][it.y] = rival }
        val residual = norm(generator.pointScore(board, c.x, c.y, self))
        replyPoint?.let { board[it.x][it.y] = Stone.EMPTY }
        board[c.x][c.y] = Stone.EMPTY
        return residual
    }

    /** 棋型分 → 0..1（对数压缩，避免个别大分值垄断轮盘） */
    private fun norm(score: Long): Double =
        if (score <= 0L) 0.0 else (ln(1.0 + score) / ln(1.0 + ShapeScores.FIVE)).coerceAtMost(1.0)

    private companion object {
        /** 质量评估里对手的应答宽度 */
        const val REPLY_BREADTH = 4
    }
}
