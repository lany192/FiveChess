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
import com.github.lany192.gomoku.domain.ai.search.SearchEngine
import com.github.lany192.gomoku.domain.model.Point
import com.github.lany192.gomoku.domain.model.Side
import kotlin.random.Random

/**
 * TD(0) 学习：线性价值函数 V(s) = w·φ(s)，权重初值为棋型分值，落库记忆。
 *
 * 选点用浅层 α-β 搜索 + 线性评估；TD 更新发生在**同相位的两拍之间**
 * （本次 getPosition 用上一拍存下的 V(s) 与当前 V(s') 做归一化 LMS，
 * 途中奖励为 0），终局再由 [onTerminal] 补一次 ±WIN_SCORE 的目标更新。
 */
class TdEngine(
    private val algorithm: AiAlgorithm,
    width: Int,
    height: Int,
    level: Difficulty,
    random: Random,
    clock: () -> Long = System::nanoTime,
    weightStore: AiWeightStore? = null,
) : AbstractLearningEngine(width, height, level, random, clock, weightStore) {

    private val linear = LinearEvaluator(width, height)
    private val search = SearchEngine(AiAlgorithm.ALPHA_BETA, width, height, level, random, clock, linear)

    /** 上一拍的 φ(s) 与 V(s)（s 为白方待走的局面，与本次同相位） */
    private var pendingFeatures: DoubleArray? = null
    private var pendingValue = 0.0

    override fun algorithm(): AiAlgorithm = algorithm

    override fun applyStoredWeights(stored: DoubleArray) {
        if (stored.size == LinearEvaluator.SIZE) linear.weights = stored
    }

    override fun currentWeights(): DoubleArray = linear.weights.copyOf()

    override fun onGameReset() {
        pendingFeatures = null
    }

    /** 测试与调试用：当前权重下某局面的估值 */
    internal fun valueOf(board: Array<IntArray>): Long = linear.evaluate(board, self)

    override fun decide(board: Array<IntArray>, tuning: EngineTuning, candidates: List<Candidate>): Point {
        ensureWeightsLoaded()
        val options = AiTunings.learning(algorithm, level)
        val features = DoubleArray(LinearEvaluator.SIZE)
        linear.fillFeatures(board, self, features)
        val value = dot(linear.weights, features)

        pendingFeatures?.let { prev ->
            learn(prev, value - pendingValue, options.learningRate)
            markWeightsDirty()
        }

        val move = search.searchOn(board, level, options.depth)
            ?: candidates.firstOrNull()?.let { Point(it.x, it.y) }
            ?: Point(width / 2, height / 2)

        // 存本拍：V(s) 用更新后的权重重算，两拍口径一致
        pendingFeatures = features
        pendingValue = dot(linear.weights, features)
        flushWeights()
        return move
    }

    override fun onTerminal(outcome: GameOutcome) {
        val prev = pendingFeatures ?: return
        pendingFeatures = null
        val target = when (outcome.winner) {
            Side.WHITE -> ShapeScores.WIN.toDouble()
            Side.BLACK -> -ShapeScores.WIN.toDouble()
            else -> 0.0
        }
        val options = AiTunings.learning(algorithm, level)
        learn(prev, target - pendingValue, options.learningRate)
        markWeightsDirty()
    }

    /** 归一化 LMS：Δw = α·(target − V(s))·φ(s) / ‖φ(s)‖²，保证不同局面下步长可比 */
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
}
