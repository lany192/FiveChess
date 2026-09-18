package com.github.lany192.gomoku.domain.ai.misc

import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.ai.EngineTuning
import com.github.lany192.gomoku.domain.ai.core.AbstractAiEngine
import com.github.lany192.gomoku.domain.ai.core.Candidate
import com.github.lany192.gomoku.domain.ai.core.ShapeScores
import com.github.lany192.gomoku.domain.ai.core.Stone
import com.github.lany192.gomoku.domain.model.Point
import kotlin.math.ln
import kotlin.random.Random

/**
 * 模糊推理：输入取"优势度 / 威胁度"（把落点棋型分按对数压到 0..1），
 * 三角隶属度 → 4 条 T-Sugeno 规则 → 加权平均反模糊化，取输出去模糊值最大的点。
 *
 * 全程确定性（无随机分量），弱档的失误率由基类注入；量级不同的棋型
 * 通过对数归一化后共享同一套隶属度，规则表不必为每种棋型单列。
 */
class FuzzyEngine(
    private val algorithm: AiAlgorithm,
    width: Int,
    height: Int,
    level: Difficulty,
    random: Random,
    clock: () -> Long = System::nanoTime,
) : AbstractAiEngine(width, height, level, random, clock) {

    override fun algorithm(): AiAlgorithm = algorithm

    override fun decide(board: Array<IntArray>, tuning: EngineTuning, candidates: List<Candidate>): Point {
        var best: Candidate? = null
        var bestScore = -1.0
        for (c in candidates) {
            if (board[c.x][c.y] != Stone.EMPTY) continue
            val advantage = norm(generator.pointScore(board, c.x, c.y, self))
            val threat = norm(generator.pointScore(board, c.x, c.y, rival))
            val score = defuzzify(advantage, threat)
            if (score > bestScore) {
                best = c
                bestScore = score
            }
        }
        if (best != null) return Point(best.x, best.y)
        val fallback = candidates.firstOrNull()
        return if (fallback != null) Point(fallback.x, fallback.y) else Point(width / 2, height / 2)
    }

    /** 棋型分 → 0..1（五连对应 1.0，活四约 0.87，活三约 0.66） */
    private fun norm(score: Long): Double =
        if (score <= 0L) 0.0 else (ln(1.0 + score) / ln(1.0 + ShapeScores.FIVE)).coerceAtMost(1.0)

    /** T-Sugeno：4 条规则各自的触发强度 μ 与结论常量 c，加权平均 */
    private fun defuzzify(advantage: Double, threat: Double): Double {
        val aLow = triangle(advantage, 0.0, 0.0, 0.5)
        val aMid = triangle(advantage, 0.0, 0.5, 1.0)
        val aHigh = triangle(advantage, 0.5, 1.0, 1.0)
        val tLow = triangle(threat, 0.0, 0.0, 0.5)
        val tMid = triangle(threat, 0.0, 0.5, 1.0)
        val tHigh = triangle(threat, 0.5, 1.0, 1.0)

        val mu = doubleArrayOf(
            aHigh,                          // 规则 1：优势高 → 直接进攻
            minOf(tHigh, aLow),             // 规则 2：威胁高且自己没便宜 → 必堵
            minOf(aMid, tMid),              // 规则 3：攻守均中等 → 攻守兼备
            minOf(aLow, tLow),              // 规则 4：都没价值 → 极少当主选
        )
        val conclusion = doubleArrayOf(1.0, 0.9, 0.6, 0.2)
        var numerator = 0.0
        var denominator = 0.0
        for (i in mu.indices) {
            numerator += mu[i] * conclusion[i]
            denominator += mu[i]
        }
        return if (denominator <= 0.0) 0.0 else numerator / denominator
    }

    /** 三角隶属度：x 在 [left,right] 内线性升到 peak 再降回 0 */
    private fun triangle(x: Double, left: Double, peak: Double, right: Double): Double {
        if (x < left || x > right) return 0.0
        return if (x <= peak) {
            if (peak == left) 1.0 else (x - left) / (peak - left)
        } else {
            if (right == peak) 1.0 else (right - x) / (right - peak)
        }
    }
}
