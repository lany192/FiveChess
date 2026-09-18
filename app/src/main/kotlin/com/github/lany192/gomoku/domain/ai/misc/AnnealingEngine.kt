package com.github.lany192.gomoku.domain.ai.misc

import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.AiTunings
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.ai.EngineTuning
import com.github.lany192.gomoku.domain.ai.core.AbstractAiEngine
import com.github.lany192.gomoku.domain.ai.core.Candidate
import com.github.lany192.gomoku.domain.ai.core.Stone
import com.github.lany192.gomoku.domain.model.Point
import kotlin.math.exp
import kotlin.random.Random

/**
 * 模拟退火：解空间是候选着法，能量 = 我的落点收益 − 1.2 × 对手最佳应答威胁。
 *
 * 邻域取候选排序表里的相邻位置（相邻分数的点互换），按 Metropolis 准则接受劣解，
 * 温度 T_k = T0 · 0.92^k 逐渐固化，最终返回历史最优解。
 * 退火可以接受暂时变差的一步，因此不会像贪心那样卡在"分数最高的看起来好、实际送先手"的局部最优。
 */
class AnnealingEngine(
    private val algorithm: AiAlgorithm,
    width: Int,
    height: Int,
    level: Difficulty,
    random: Random,
    clock: () -> Long = System::nanoTime,
) : AbstractAiEngine(width, height, level, random, clock) {

    override fun algorithm(): AiAlgorithm = algorithm

    override fun decide(board: Array<IntArray>, tuning: EngineTuning, candidates: List<Candidate>): Point {
        val options = AiTunings.misc(algorithm, level)
        val limit = minOf(candidates.size, options.breadth)
        if (limit <= 0) return Point(width / 2, height / 2)
        if (limit == 1) return Point(candidates[0].x, candidates[0].y)

        var current = 0
        var currentEnergy = energy(board, candidates[current], options.radius)
        var best = current
        var bestEnergy = currentEnergy
        var temperature = options.initialTemperature
        val startNanos = clock()

        for (k in 0 until options.iterations) {
            if ((k and FUSE_MASK) == 0 && options.timeBudgetMillis > 0 &&
                (clock() - startNanos) / 1_000_000 >= options.timeBudgetMillis
            ) {
                break
            }
            // 邻域：候选排序表里相邻跳动（确定性抽向 ±1，用 random 决定方向）
            val step = if (random.nextDouble() < 0.5) 1 else -1
            var next = current + step
            if (next < 0) next = 1
            if (next >= limit) next = limit - 2
            val nextEnergy = energy(board, candidates[next], options.radius)
            val delta = nextEnergy - currentEnergy
            if (delta >= 0 || random.nextDouble() < exp(delta / temperature)) {
                current = next
                currentEnergy = nextEnergy
                if (currentEnergy > bestEnergy) {
                    best = current
                    bestEnergy = currentEnergy
                }
            }
            temperature *= options.cooling
        }
        return Point(candidates[best].x, candidates[best].y)
    }

    /** 能量：我在 c 落子，对手用最贪的一手应答（不消耗我方的子），收益减去对手威胁的 1.2 倍 */
    private fun energy(board: Array<IntArray>, c: Candidate, radius: Int): Double {
        val attack = generator.pointScore(board, c.x, c.y, self)
        board[c.x][c.y] = self
        val replies = generator.generate(board, REPLY_BREADTH, radius, rival, self)
        var counter = 0L
        for (r in replies) {
            if (board[r.x][r.y] != Stone.EMPTY) continue
            val threat = generator.pointScore(board, r.x, r.y, rival)
            if (threat > counter) counter = threat
        }
        board[c.x][c.y] = Stone.EMPTY
        return attack - counter * COUNTER_WEIGHT / 10.0
    }

    private companion object {
        const val REPLY_BREADTH = 4
        const val COUNTER_WEIGHT = 12L

        /** 每 32 步查一次时间预算 */
        const val FUSE_MASK = 31
    }
}
