package com.github.lany192.gomoku.domain.ai.learn

import kotlin.math.tanh
import kotlin.random.Random

/** 一次训练样本：根局面的逐点特征（扁平 n×37）、访问分布 π、终局结果 z（白方视角 ±1/0） */
internal class AzSample(
    val count: Int,
    val features: DoubleArray,
    val pi: DoubleArray,
    var z: Double = 0.0,
)

/**
 * AlphaZero 的手写策略/价值网络：37 维逐点特征 → 24 个 tanh 隐层 → 策略 logit + 价值。
 *
 * 参数布局：W1(37×24=888) + B1(24) + WP(24) + BP(1) + WV(24) + BV(1) = 962。
 * 价值头吃"逐点隐层的平均向量"，因此一次前向同时给出整手候选的先验与叶子价值（无 rollout）。
 * 训练用 SGD 直接反传 CE(π,p) + (z−v)²，样本量小（一局 ≤64 条），不需要额外框架。
 */
internal class AzNetwork(random: Random) {

    var weights = DoubleArray(SIZE) { (random.nextDouble() - 0.5) * INIT_SCALE }
        private set

    fun applyWeights(stored: DoubleArray) {
        if (stored.size == SIZE) weights = stored
    }

    fun snapshot(): DoubleArray = weights.copyOf()

    /**
     * 前向：把 n 个候选点各过一遍网络，写出隐层矩阵与逐点 logit，并返回价值 v ∈ (−1,1)。
     * 价值视角 = 传入特征时的走子方。
     */
    fun policyAndValue(features: DoubleArray, n: Int, hidden: DoubleArray, logits: DoubleArray): Double {
        for (j in 0 until n) {
            val fOff = j * FEATURES
            val hOff = j * HIDDEN
            for (k in 0 until HIDDEN) {
                var z = weights[B1 + k]
                val wOff = W1 + k * FEATURES
                for (i in 0 until FEATURES) {
                    z += weights[wOff + i] * features[fOff + i]
                }
                hidden[hOff + k] = tanh(z)
            }
            var logit = weights[BP]
            for (k in 0 until HIDDEN) {
                logit += weights[WP + k] * hidden[hOff + k]
            }
            logits[j] = logit
        }
        var valuePre = weights[BV]
        for (k in 0 until HIDDEN) {
            var mean = 0.0
            for (j in 0 until n) {
                mean += hidden[j * HIDDEN + k]
            }
            valuePre += weights[WV + k] * (mean / n)
        }
        return tanh(valuePre)
    }

    /** 一步 SGD：policy 交叉熵 + value 均方误差，直接在手写计算图上反传 */
    fun train(sample: AzSample, learningRate: Double) {
        val n = sample.count
        if (n <= 0) return
        val hidden = DoubleArray(n * HIDDEN)
        val logits = DoubleArray(n)
        val value = policyAndValue(sample.features, n, hidden, logits)

        // softmax(logits)
        var maxLogit = logits[0]
        for (j in 1 until n) if (logits[j] > maxLogit) maxLogit = logits[j]
        var sum = 0.0
        val probs = DoubleArray(n)
        for (j in 0 until n) {
            probs[j] = kotlin.math.exp(logits[j] - maxLogit)
            sum += probs[j]
        }
        for (j in 0 until n) probs[j] /= sum

        // 头部梯度
        val gradLogit = DoubleArray(n) { probs[it] - sample.pi[it] }
        val gradValuePre = 2.0 * (value - sample.z) * (1.0 - value * value)

        val gradWp = DoubleArray(HIDDEN)
        var gradBp = 0.0
        for (j in 0 until n) {
            for (k in 0 until HIDDEN) {
                gradWp[k] += gradLogit[j] * hidden[j * HIDDEN + k]
            }
            gradBp += gradLogit[j]
        }
        val gradWv = DoubleArray(HIDDEN)
        for (k in 0 until HIDDEN) {
            var mean = 0.0
            for (j in 0 until n) mean += hidden[j * HIDDEN + k]
            gradWv[k] = gradValuePre * (mean / n)
        }

        // 反传到隐层与输入层
        val gradW1 = DoubleArray(HIDDEN * FEATURES)
        val gradB1 = DoubleArray(HIDDEN)
        for (j in 0 until n) {
            val fOff = j * FEATURES
            val hOff = j * HIDDEN
            for (k in 0 until HIDDEN) {
                val h = hidden[hOff + k]
                val dPre = (gradLogit[j] * weights[WP + k] + gradValuePre * weights[WV + k] / n) * (1.0 - h * h)
                gradB1[k] += dPre
                val wOff = W1 + k * FEATURES
                val gOff = k * FEATURES
                for (i in 0 until FEATURES) {
                    gradW1[gOff + i] += dPre * sample.features[fOff + i]
                }
            }
        }

        // 梯度下降
        for (i in 0 until HIDDEN * FEATURES) weights[W1 + i] -= learningRate * gradW1[i]
        for (k in 0 until HIDDEN) weights[B1 + k] -= learningRate * gradB1[k]
        for (k in 0 until HIDDEN) weights[WP + k] -= learningRate * gradWp[k]
        weights[BP] -= learningRate * gradBp
        for (k in 0 until HIDDEN) weights[WV + k] -= learningRate * gradWv[k]
        weights[BV] -= learningRate * gradValuePre
    }

    companion object {
        const val FEATURES = 37
        const val HIDDEN = 24

        private const val W1 = 0
        private const val B1 = W1 + HIDDEN * FEATURES
        private const val WP = B1 + HIDDEN
        private const val BP = WP + HIDDEN
        private const val WV = BP + 1
        private const val BV = WV + HIDDEN

        /** 37×24 + 24 + 24 + 1 + 24 + 1 = 962 */
        const val SIZE = BV + 1

        private const val INIT_SCALE = 0.2
    }
}
