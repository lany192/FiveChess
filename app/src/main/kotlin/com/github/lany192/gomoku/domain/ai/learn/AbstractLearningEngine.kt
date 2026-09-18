package com.github.lany192.gomoku.domain.ai.learn

import com.github.lany192.gomoku.domain.ai.AiWeightStore
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.ai.GameOutcome
import com.github.lany192.gomoku.domain.ai.core.AbstractAiEngine
import kotlin.random.Random

/**
 * 学习类引擎的公共骨架：权重懒加载、脏标记、落库时机。
 *
 * 存储是阻塞的（Room 实现会做磁盘 IO），所有调用都发生在 [getPosition]/[onGameOver] 内部，
 * 由 ViewModel 保证在后台线程执行。
 */
abstract class AbstractLearningEngine(
    width: Int,
    height: Int,
    level: Difficulty,
    random: Random,
    clock: () -> Long,
    private val weightStore: AiWeightStore?,
) : AbstractAiEngine(width, height, level, random, clock) {

    private var loaded = false
    private var dirty = false

    /** 首次使用前懒加载；没有历史权重时保持先验初值 */
    protected fun ensureWeightsLoaded() {
        if (loaded) return
        loaded = true
        val stored = weightStore?.load(modelId()) ?: return
        applyStoredWeights(stored)
    }

    /** 存储里读到的权重；尺寸不符应忽略（视为无历史权重，用先验冷启动） */
    protected abstract fun applyStoredWeights(stored: DoubleArray)

    /** 本引擎当前应落库的权重 */
    protected abstract fun currentWeights(): DoubleArray

    /** 测试与调试用：当前权重快照 */
    internal fun weightsSnapshot(): DoubleArray = currentWeights()

    protected fun markWeightsDirty() {
        dirty = true
    }

    /** 权重有变则落库（阻塞写，只在后台线程调用） */
    protected fun flushWeights() {
        if (!dirty) return
        dirty = false
        weightStore?.save(modelId(), currentWeights())
    }

    final override fun onGameOver(outcome: GameOutcome) {
        ensureWeightsLoaded()
        onTerminal(outcome)
        flushWeights()
    }

    /** 终局学习钩子（在落库之前调用） */
    protected open fun onTerminal(outcome: GameOutcome) {}

    private fun modelId(): String = algorithm().name
}
