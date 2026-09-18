package com.github.lany192.gomoku.domain.ai

import kotlin.random.Random

/**
 * 会话级引擎缓存。
 *
 * 只缓存学习类引擎：它们的权重在内存里持续更新，切换算法再切回不应丢；
 * 非学习类每次新建（构造是 O(1)，大表一律 lazy），顺带丢掉置换表等搜索残留。
 */
class AiEnginePool(
    private val width: Int,
    private val height: Int,
    private val seed: Long = Random.Default.nextLong(),
    private val clock: () -> Long = System::nanoTime,
    private val weightStore: AiWeightStore? = null,
    /** 引擎构建口，测试注入假引擎用 */
    internal val factory: (AiAlgorithm, Difficulty, Random) -> GomokuAI = { algorithm, level, random ->
        AiEngineFactory.create(
            algorithm = algorithm,
            level = level,
            width = width,
            height = height,
            random = random,
            clock = clock,
            weightStore = weightStore,
        )
    },
) {
    private val learned = HashMap<AiAlgorithm, GomokuAI>()

    /** 取引擎（已同步档位）；学习类复用缓存实例，其余每次新建 */
    fun obtain(algorithm: AiAlgorithm, level: Difficulty): GomokuAI {
        val engine = if (algorithm.family == AiFamily.LEARNING) {
            learned.getOrPut(algorithm) { newEngine(algorithm, level) }
        } else {
            newEngine(algorithm, level)
        }
        engine.level = level
        return engine
    }

    /** 丢弃已学权重（引擎下次重建时从存储重新加载） */
    fun resetLearning() {
        learned.clear()
    }

    private fun newEngine(algorithm: AiAlgorithm, level: Difficulty): GomokuAI =
        factory(algorithm, level, Random(seed xor algorithm.ordinal.toLong()))
}
