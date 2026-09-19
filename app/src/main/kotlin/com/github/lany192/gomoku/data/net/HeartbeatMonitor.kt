package com.github.lany192.gomoku.data.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 对局连接心跳与假死检测（纯 Kotlin，JVM 可测）
 *
 * 由传输层（[LanGameClient] / BtGameClient）持有：读循环每收到一帧就调用 [onFrame]，
 * 收到心跳帧则调用 [onPeerHeartbeat]；后台循环每 [intervalMillis] 发一次本方心跳，
 * 并检查"对端是否长时间无帧"（拔网线/杀进程时 TCP 不会立刻报错，只能靠心跳兜底）。
 *
 * **旧版兼容**：只有本方先收到过对端心跳，才启用假死判定 —— 旧版不发心跳，
 * 若一上来就按"无帧"判死，与旧版对局会误报断线；与旧版对局时退化为无检测。
 *
 * 时钟用单调时间（[System.nanoTime]）而非墙钟，避免用户改系统时间导致误报判死。
 */
class HeartbeatMonitor(
    private val scope: CoroutineScope,
    private val sendHeartbeat: () -> Unit,
    private val onPeerDead: () -> Unit,
    private val intervalMillis: Long = HEARTBEAT_INTERVAL_MS,
    private val timeoutMillis: Long = HEARTBEAT_TIMEOUT_MS,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    /** 是否收到过对端心跳（收到后才解锁假死判定） */
    @Volatile
    private var peerHeartbeatSeen = false

    @Volatile
    private var lastFrameAt = 0L

    @Volatile
    private var running = false

    private var job: Job? = null

    fun start() {
        if (running) return
        running = true
        lastFrameAt = nowNanos()
        job = scope.launch {
            while (running) {
                sendHeartbeat()
                delay(intervalMillis)
                if (!running) return@launch
                val silent = nowNanos() - lastFrameAt >= timeoutMillis * NANOS_PER_MILLI
                if (peerHeartbeatSeen && silent) {
                    running = false
                    onPeerDead()
                    return@launch
                }
            }
        }
    }

    fun stop() {
        running = false
        job?.cancel()
        job = null
    }

    /** 收到对端任意非心跳帧 */
    fun onFrame() {
        lastFrameAt = nowNanos()
    }

    /** 收到对端心跳帧：既刷新活性，也解锁此后的假死判定 */
    fun onPeerHeartbeat() {
        peerHeartbeatSeen = true
        lastFrameAt = nowNanos()
    }

    companion object {
        /** 心跳发送间隔：检测窗口 = [HEARTBEAT_TIMEOUT_MS] + 本间隔（对端至多沉默这么久） */
        const val HEARTBEAT_INTERVAL_MS = 5_000L

        /** 判死阈值：期间未收到对端任何帧即视为假死 */
        const val HEARTBEAT_TIMEOUT_MS = 15_000L

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
