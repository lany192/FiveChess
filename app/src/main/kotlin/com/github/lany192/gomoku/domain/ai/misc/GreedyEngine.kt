package com.github.lany192.gomoku.domain.ai.misc

import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.ai.EngineTuning
import com.github.lany192.gomoku.domain.ai.core.AbstractAiEngine
import com.github.lany192.gomoku.domain.ai.core.Candidate
import com.github.lany192.gomoku.domain.ai.core.Stone
import com.github.lany192.gomoku.domain.model.Point
import kotlin.math.abs
import kotlin.random.Random

/**
 * 贪心：对每个候选点直接算"我落子的收益 + 对手在同点的收益打九折"，取最大。
 *
 * 没有前瞻，容易被双威胁戏耍；并列按到棋盘中心的距离取近者（确定性，不靠随机）。
 * 弱档的失误率由基类按 noise 概率注入，本引擎自身无随机。
 */
class GreedyEngine(
    private val algorithm: AiAlgorithm,
    width: Int,
    height: Int,
    level: Difficulty,
    random: Random,
    clock: () -> Long = System::nanoTime,
) : AbstractAiEngine(width, height, level, random, clock) {

    override fun algorithm(): AiAlgorithm = algorithm

    override fun decide(board: Array<IntArray>, tuning: EngineTuning, candidates: List<Candidate>): Point {
        val centerX = width / 2
        val centerY = height / 2
        var best: Candidate? = null
        var bestScore = Long.MIN_VALUE
        var bestDist = Int.MAX_VALUE
        for (c in candidates) {
            if (board[c.x][c.y] != Stone.EMPTY) continue
            val score = generator.pointScore(board, c.x, c.y, self) +
                    generator.pointScore(board, c.x, c.y, rival) * 9 / 10
            val dist = abs(c.x - centerX) + abs(c.y - centerY)
            if (score > bestScore || (score == bestScore && dist < bestDist)) {
                best = c
                bestScore = score
                bestDist = dist
            }
        }
        if (best != null) return Point(best.x, best.y)
        val fallback = candidates.firstOrNull()
        return if (fallback != null) Point(fallback.x, fallback.y) else Point(centerX, centerY)
    }
}
