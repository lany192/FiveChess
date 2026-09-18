package com.github.lany192.gomoku.domain.ai.learn

import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.AiTunings
import com.github.lany192.gomoku.domain.ai.AiWeightStore
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.ai.EngineTuning
import com.github.lany192.gomoku.domain.ai.GameOutcome
import com.github.lany192.gomoku.domain.ai.core.Candidate
import com.github.lany192.gomoku.domain.ai.core.LinearEvaluator
import com.github.lany192.gomoku.domain.ai.core.ShapeScores
import com.github.lany192.gomoku.domain.ai.core.Stone
import com.github.lany192.gomoku.domain.model.Point
import com.github.lany192.gomoku.domain.model.Side
import kotlin.random.Random

/**
 * 强化学习（Q 学习）：线性 Q(s,a) = w·φ(落子后的局面，走子方视角)，双方共用一套 w。
 *
 * 选点用 ε-greedy 在前 K 个候选里挑（按 Q 值，ε 概率随机探索）；
 * 更新延迟两拍：本次开局先算 max_a' Q(s',a')，再用上一拍存的 (φ, Q) 做
 * `w += α[r + γ·max Q − Q]·φ / ‖φ‖²`，终局由 [onTerminal] 补 r = ±WIN_SCORE。
 */
class QLearningEngine(
    private val algorithm: AiAlgorithm,
    width: Int,
    height: Int,
    level: Difficulty,
    random: Random,
    clock: () -> Long = System::nanoTime,
    weightStore: AiWeightStore? = null,
) : AbstractLearningEngine(width, height, level, random, clock, weightStore) {

    private val linear = LinearEvaluator(width, height)

    /** 上一拍选择的 (φ(s,a), Q(s,a)) */
    private var pendingFeatures: DoubleArray? = null
    private var pendingQ = 0.0

    override fun algorithm(): AiAlgorithm = algorithm

    override fun applyStoredWeights(stored: DoubleArray) {
        if (stored.size == LinearEvaluator.SIZE) linear.weights = stored
    }

    override fun currentWeights(): DoubleArray = linear.weights.copyOf()

    override fun onGameReset() {
        pendingFeatures = null
    }

    /** 测试与调试用：当前权重下某局面（白方视角）的估值 */
    internal fun valueOf(board: Array<IntArray>): Long = linear.evaluate(board, self)

    override fun decide(board: Array<IntArray>, tuning: EngineTuning, candidates: List<Candidate>): Point {
        ensureWeightsLoaded()
        val options = AiTunings.learning(algorithm, level)
        val limit = minOf(candidates.size, options.breadth)
        if (limit <= 0) return Point(width / 2, height / 2)

        val features = DoubleArray(LinearEvaluator.SIZE)
        val qValues = DoubleArray(limit)
        var bestFuture = Double.NEGATIVE_INFINITY
        for (i in 0 until limit) {
            val c = candidates[i]
            if (board[c.x][c.y] != Stone.EMPTY) continue
            board[c.x][c.y] = self
            linear.fillFeatures(board, self, features)
            board[c.x][c.y] = Stone.EMPTY
            qValues[i] = dot(linear.weights, features)
            if (qValues[i] > bestFuture) bestFuture = qValues[i]
        }
        if (bestFuture == Double.NEGATIVE_INFINITY) bestFuture = 0.0

        pendingFeatures?.let { prev ->
            val target = options.gamma * bestFuture
            learn(prev, target - pendingQ, options.learningRate)
            markWeightsDirty()
        }

        val chosen = chooseMove(board, candidates, limit, qValues, options.epsilonPercent)

        // 存本拍：φ/Q 用更新后的权重重算
        board[chosen.x][chosen.y] = self
        linear.fillFeatures(board, self, features)
        board[chosen.x][chosen.y] = Stone.EMPTY
        pendingFeatures = features
        pendingQ = dot(linear.weights, features)
        flushWeights()
        return chosen
    }

    override fun onTerminal(outcome: GameOutcome) {
        val prev = pendingFeatures ?: return
        pendingFeatures = null
        val reward = when (outcome.winner) {
            Side.WHITE -> ShapeScores.WIN.toDouble()
            Side.BLACK -> -ShapeScores.WIN.toDouble()
            else -> 0.0
        }
        val options = AiTunings.learning(algorithm, level)
        learn(prev, reward - pendingQ, options.learningRate)
        markWeightsDirty()
    }

    /** ε-greedy：ε 概率在候选前 K 名里随机探索，否则取 Q 最大的点 */
    private fun chooseMove(
        board: Array<IntArray>,
        candidates: List<Candidate>,
        limit: Int,
        qValues: DoubleArray,
        epsilonPercent: Int,
    ): Point {
        if (epsilonPercent > 0 && random.nextInt(100) < epsilonPercent) {
            val span = minOf(limit, EXPLORE_SPAN)
            for (attempt in 0 until EXPLORE_TRIES) {
                val c = candidates[random.nextInt(span)]
                if (board[c.x][c.y] == Stone.EMPTY) return Point(c.x, c.y)
            }
        }
        var best = -1
        var bestQ = Double.NEGATIVE_INFINITY
        for (i in 0 until limit) {
            val c = candidates[i]
            if (board[c.x][c.y] != Stone.EMPTY) continue
            if (qValues[i] > bestQ) {
                bestQ = qValues[i]
                best = i
            }
        }
        if (best >= 0) return Point(candidates[best].x, candidates[best].y)
        val fallback = candidates.firstOrNull { board[it.x][it.y] == Stone.EMPTY }
        return if (fallback != null) Point(fallback.x, fallback.y) else Point(width / 2, height / 2)
    }

    /** 归一化 LMS：Δw = α·(target − Q)·φ / ‖φ‖² */
    private fun learn(features: DoubleArray, delta: Double, alpha: Double) {
        var norm = 0.0
        for (f in features) norm += f * f
        if (norm <= 0.0 || delta == 0.0) return
        val step = alpha * delta / norm
        val weights = linear.weights
        for (i in weights.indices) {
            weights[i] += step * features[i]
        }
    }

    private fun dot(weights: DoubleArray, features: DoubleArray): Double {
        var sum = 0.0
        for (i in weights.indices) sum += weights[i] * features[i]
        return sum
    }

    private companion object {
        /** 探索时只看候选前几名，避免把弱档的"探索"变成乱走 */
        const val EXPLORE_SPAN = 5
        const val EXPLORE_TRIES = 4
    }
}
