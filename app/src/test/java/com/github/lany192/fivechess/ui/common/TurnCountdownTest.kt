package com.github.lany192.fivechess.ui.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 倒计时组件自身的回归
 *
 * 每个用例都带 `timeout`：若循环没跑到归零就结束测试体，`runTest` 收尾排空队列时会静默挂死
 * 整个测试任务（详见 [TurnCountdown] 的约定），这里让它表现为失败而非卡死。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TurnCountdownTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `剩余时长格式化为 mm ss`() {
        assertEquals("03:00", TurnCountdown.format(180_000))
        assertEquals("02:59", TurnCountdown.format(179_000))
        assertEquals("01:00", TurnCountdown.format(60_000))
        assertEquals("00:59", TurnCountdown.format(59_000))
        assertEquals("00:01", TurnCountdown.format(1_000))
        assertEquals("00:00", TurnCountdown.format(0))
        // 负数（不该出现，但渲染层不能崩）与非整秒一并兜底
        assertEquals("00:00", TurnCountdown.format(-5_000))
        assertEquals("00:00", TurnCountdown.format(999))
    }

    @Test(timeout = 10_000)
    fun `逐秒递减到零后回调一次`() = runTest(dispatcher) {
        val ticks = mutableListOf<Long>()
        var expired = 0
        val countdown = TurnCountdown(
            scope = this,
            durationMillis = 3_000,
            onTick = { ticks += it },
            onExpired = { expired++ },
        )
        countdown.restart()
        advanceUntilIdle()

        assertEquals(listOf(3_000L, 2_000L, 1_000L, 0L), ticks)
        assertEquals(1, expired)
    }

    @Test(timeout = 10_000)
    fun `时长非正时不起表`() = runTest(dispatcher) {
        var ticks = 0
        var expired = 0
        val countdown = TurnCountdown(
            scope = this,
            durationMillis = 0,
            onTick = { ticks++ },
            onExpired = { expired++ },
        )
        countdown.restart()
        advanceUntilIdle()

        assertEquals(0, ticks)
        assertEquals(0, expired)
    }

    @Test(timeout = 10_000)
    fun `stop 后不再回调`() = runTest(dispatcher) {
        var expired = 0
        val countdown = TurnCountdown(
            scope = this,
            durationMillis = 3_000,
            onTick = {},
            onExpired = { expired++ },
        )
        countdown.restart()
        runCurrent()
        countdown.stop()
        advanceUntilIdle()

        assertEquals(0, expired)
    }

    @Test(timeout = 10_000)
    fun `restart 丢弃上一轮未走完的表`() = runTest(dispatcher) {
        val ticks = mutableListOf<Long>()
        var expired = 0
        val countdown = TurnCountdown(
            scope = this,
            durationMillis = 3_000,
            onTick = { ticks += it },
            onExpired = { expired++ },
        )
        countdown.restart()
        runCurrent()
        // 第一轮走到只剩 1 秒时落子重置：旧表被丢弃，不会在原定第 3 秒触发归零
        advanceTimeBy(2_000)
        runCurrent()
        countdown.restart()
        advanceUntilIdle()

        assertEquals(1, expired)
        assertEquals(listOf(3_000L, 2_000L, 1_000L, 3_000L, 2_000L, 1_000L, 0L), ticks)
    }
}
