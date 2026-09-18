package com.github.lany192.gomoku.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 每步限时倒计时：只对当前行棋方计时，落子/悔棋/重开后 [restart] 重置为 [durationMillis]。
 *
 * [durationMillis] <= 0 表示不限时，[restart] 退化为 no-op（人机模式与关掉计时的 JVM 测试用）。
 *
 * 两条必须遵守的约定，否则会有一次「Gradle 测试任务整体静默挂死」的事故：
 * 1. 循环体内**不得做墙钟运算**。`System.currentTimeMillis()` 在虚拟时间里不推进，`remaining`
 *    永远 > 0，循环永不终止。只能靠「每 tick 减一个 tick」的计数式，代价是 3 分钟内约 1s 漂移，
 *    对回合制游戏完全可接受。
 * 2. **开启计时的测试必须让表跑到归零再结束测试体**。`runTest` 在 finally 里用
 *    `advanceUntilIdleOr { false }` 排空队列，且不带前台/后台过滤、也在 `withTimeout(60s)` 之外 ——
 *    测试体结束时只要还有未终止的倒计时协程，就会静默挂死整个测试任务，`runTest` 自带的超时和
 *    `UncompletedCoroutinesError` 都救不了。
 */
class TurnCountdown(
    private val scope: CoroutineScope,
    private val durationMillis: Long,
    private val onTick: (Long) -> Unit,
    private val onExpired: () -> Unit,
    private val tickMillis: Long = TICK_MILLIS,
) {
    private var job: Job? = null

    /** 重新开始倒数；[durationMillis] <= 0 时不起协程 */
    fun restart() {
        job?.cancel()
        job = null
        if (durationMillis <= 0) return
        job = scope.launch {
            var remaining = durationMillis
            onTick(remaining)
            while (remaining > 0) {
                delay(tickMillis)
                remaining = (remaining - tickMillis).coerceAtLeast(0)
                onTick(remaining)
            }
            onExpired()
        }
    }

    /** 停表：终局与断线时调用 */
    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        const val TICK_MILLIS = 1_000L

        /** 每步限时 3 分钟 */
        const val THREE_MINUTES = 3 * 60 * TICK_MILLIS

        /** mm:ss，供渲染层幂等映射（固定 Locale.ROOT，避免本地化数字破坏等宽对齐） */
        fun format(remainingMillis: Long): String {
            val totalSeconds = remainingMillis.coerceAtLeast(0) / TICK_MILLIS
            return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
        }
    }
}
