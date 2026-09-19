package com.github.lany192.gomoku.data.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 心跳/假死检测回归
 *
 * 时钟注入成测试调度器的虚拟时间，循环内的 delay 也走同一时钟，5s/15s 的窗口都可用
 * advanceTimeBy 精确推进。
 *
 * 约定：循环是无限的，只能用 advanceTimeBy/runCurrent 推进，且每个用例末尾必须 stop()
 * —— advanceUntilIdle 会试图排空一个永不结束的队列，测试直接挂死。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HeartbeatMonitorTest {

    private var sent = 0
    private var dead = 0

    @Test(timeout = 10_000)
    fun `未收到对端心跳永不判死`() = runTest {
        // 旧版对端不发心跳：无论沉默多久都不能判死（否则互通时误报断线）
        val monitor = monitor()
        monitor.start()

        advanceTimeBy(60_000)
        runCurrent()

        assertTrue("循环必须真的跑过，否则本用例会因空转白白通过", sent > 0)
        assertEquals(0, dead)
        monitor.stop()
    }

    @Test(timeout = 10_000)
    fun `收到心跳后沉默超时判死恰好一次`() = runTest {
        val monitor = monitor()
        monitor.start()
        monitor.onPeerHeartbeat()

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, dead)

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, dead)

        // 判死后循环已退出：不再回调、也不再发心跳
        val sentAtDeath = sent
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, dead)
        assertEquals(sentAtDeath, sent)
        monitor.stop()
    }

    @Test(timeout = 10_000)
    fun `任意帧都续命`() = runTest {
        val monitor = monitor()
        monitor.start()
        monitor.onPeerHeartbeat()

        // 每 10 秒来一帧（比如落子），持续一分钟都不判死
        repeat(6) {
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(0, dead)
            monitor.onFrame()
        }
        monitor.stop()
    }

    @Test(timeout = 10_000)
    fun `stop后不再发送也不再回调`() = runTest {
        val monitor = monitor()
        monitor.start()
        monitor.onPeerHeartbeat()
        advanceTimeBy(11_000)
        runCurrent()
        val sentBeforeStop = sent

        monitor.stop()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(sentBeforeStop, sent)
        assertEquals(0, dead)
    }

    private fun TestScope.monitor(
        intervalMillis: Long = HeartbeatMonitor.HEARTBEAT_INTERVAL_MS,
        timeoutMillis: Long = HeartbeatMonitor.HEARTBEAT_TIMEOUT_MS,
    ): HeartbeatMonitor = HeartbeatMonitor(
        scope = this,
        sendHeartbeat = { sent++ },
        onPeerDead = { dead++ },
        intervalMillis = intervalMillis,
        timeoutMillis = timeoutMillis,
        nowNanos = { testScheduler.currentTime * 1_000_000 },
    )
}
