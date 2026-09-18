package com.github.lany192.fivechess.data.bt

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.github.lany192.fivechess.data.net.GameTransport
import com.github.lany192.fivechess.data.net.NetEvent
import com.github.lany192.fivechess.data.net.Protocol
import com.github.lany192.fivechess.data.net.TcpType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID

/**
 * 蓝牙对局传输：RFCOMM 通道收发落子/悔棋/重开指令（对齐 [com.github.lany192.fivechess.data.net.LanGameClient]）
 *
 * 与局域网版的差异仅在建连方式：TCP 端口换成服务 UUID，字节帧格式完全复用 [Protocol]。
 * 因此两种模式的对局层可以共享同一份 ViewModel，只有发现/握手层不同。
 *
 * 线程模型同局域网版：阻塞 IO 协程，靠关闭 socket 打断 accept()/read()。
 */
class BtGameClient(
    private val adapter: BluetoothAdapter?,
    private val isServer: Boolean,
    private val remoteAddress: String,
) : GameTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<NetEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<NetEvent> = _events.asSharedFlow()

    private var serverSocket: BluetoothServerSocket? = null

    @Volatile
    private var socket: BluetoothSocket? = null

    private val sendMutex = Mutex()
    private val frameReader = Protocol.TcpFrameReader()

    @Volatile
    private var running = false

    override fun start() {
        if (running) return
        running = true
        scope.launch { connectLoop() }
    }

    override fun stop() {
        running = false
        closeAll()
        scope.cancel()
    }

    override fun sendMove(x: Int, y: Int) {
        send(Protocol.encodeTcp(TcpType.ADD_CHESS, byteArrayOf(x.toByte(), y.toByte())))
    }

    override fun askRollback() {
        send(Protocol.encodeTcp(TcpType.ROLLBACK_ASK))
    }

    override fun agreeRollback() {
        send(Protocol.encodeTcp(TcpType.ROLLBACK_AGREE))
    }

    override fun rejectRollback() {
        send(Protocol.encodeTcp(TcpType.ROLLBACK_REJECT))
    }

    override fun requestRestart() {
        send(Protocol.encodeTcp(TcpType.RESTART))
    }

    override fun askDraw() {
        send(Protocol.encodeTcp(TcpType.DRAW_ASK))
    }

    override fun agreeDraw() {
        send(Protocol.encodeTcp(TcpType.DRAW_AGREE))
    }

    override fun rejectDraw() {
        send(Protocol.encodeTcp(TcpType.DRAW_REJECT))
    }

    override fun sendResign() {
        send(Protocol.encodeTcp(TcpType.RESIGN))
    }

    override fun sendTimeout() {
        send(Protocol.encodeTcp(TcpType.TIMEOUT))
    }

    // ---------- 建连 ----------

    private suspend fun connectLoop() {
        val adapter = adapter ?: run {
            // 本机不支持蓝牙 / 服务缺失，直接按建连失败上报
            Log.d(TAG, "bluetooth adapter unavailable")
            _events.tryEmit(NetEvent.ConnectFailed)
            return
        }
        val connected = try {
            if (isServer) acceptOnce(adapter) else connectWithRetry(adapter)
        } catch (e: SecurityException) {
            Log.d(TAG, "bt permission denied: ${e.message}")
            null
        } catch (e: IOException) {
            Log.d(TAG, "bt connect fail: ${e.message}")
            null
        }
        if (connected == null || !running) {
            _events.tryEmit(NetEvent.ConnectFailed)
            closeAll()
            return
        }
        socket = connected
        Log.d(TAG, "bt connected")
        _events.tryEmit(NetEvent.Connected)
        readLoop(connected)
    }

    private fun acceptOnce(adapter: BluetoothAdapter): BluetoothSocket? {
        val server = adapter.listenUsingRfcommWithServiceRecord(
            Protocol.BT_SERVICE_NAME,
            UUID.fromString(Protocol.BT_UUID),
        )
        serverSocket = server
        Log.d(TAG, "bt server waiting accept")
        // accept(timeout) 超时返回 null；stop() 关闭 socket 则以 IOException 打断
        return server.accept(ACCEPT_TIMEOUT_MS)
    }

    /**
     * 客户端重试建连
     *
     * 对端在联机页同意后要先关掉握手通道、再打开对局监听口，存在短暂空窗，故必须重试。
     * 重试按**墙钟**封顶而非次数：对端不在范围内时单次 connect 会耗到寻呼超时（数十秒），
     * 固定次数会把这个时间乘以次数，用户要干等几分钟。
     * 发现过程也会显著拖慢甚至阻塞 RFCOMM 建连，先取消。
     */
    private suspend fun connectWithRetry(adapter: BluetoothAdapter): BluetoothSocket? {
        try {
            adapter.cancelDiscovery()
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "cancel discovery fail: ${e.message}")
        }
        val remote = try {
            adapter.getRemoteDevice(remoteAddress)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "bt remote address invalid: $remoteAddress")
            return null
        }
        val deadline = System.currentTimeMillis() + RETRY_BUDGET_MS
        var attempt = 0
        var lastError: IOException? = null
        while (running && System.currentTimeMillis() < deadline) {
            attempt++
            val candidate = try {
                remote.createRfcommSocketToServiceRecord(UUID.fromString(Protocol.BT_UUID))
            } catch (e: IOException) {
                Log.d(TAG, "bt create socket fail: ${e.message}")
                return null
            }
            try {
                candidate.connect()
                Log.d(TAG, "bt client connected after $attempt attempts")
                return candidate
            } catch (e: IOException) {
                lastError = e
                closeQuietly(candidate)
            }
            if (System.currentTimeMillis() >= deadline) break
            delay(RETRY_INTERVAL_MS)
        }
        Log.d(TAG, "bt connect retry exhausted: ${lastError?.message}")
        return null
    }

    // ---------- 收发 ----------

    private fun readLoop(socket: BluetoothSocket) {
        try {
            val input = socket.inputStream
            val buf = ByteArray(BUFFER_SIZE)
            while (running) {
                val n = input.read(buf)
                if (n == -1) break
                frameReader.feed(buf, n).forEach { frame ->
                    when (frame.typeEnum()) {
                        TcpType.ADD_CHESS -> {
                            if (frame.payload.size >= 2) {
                                _events.tryEmit(NetEvent.ChessMove(frame.payload[0].toInt(), frame.payload[1].toInt()))
                            }
                        }
                        TcpType.ROLLBACK_ASK -> _events.tryEmit(NetEvent.RollbackAsked)
                        TcpType.ROLLBACK_AGREE -> _events.tryEmit(NetEvent.RollbackAgreed)
                        TcpType.ROLLBACK_REJECT -> _events.tryEmit(NetEvent.RollbackRejected)
                        TcpType.RESTART -> _events.tryEmit(NetEvent.RestartRequested)
                        TcpType.DRAW_ASK -> _events.tryEmit(NetEvent.DrawAsked)
                        TcpType.DRAW_AGREE -> _events.tryEmit(NetEvent.DrawAgreed)
                        TcpType.DRAW_REJECT -> _events.tryEmit(NetEvent.DrawRejected)
                        TcpType.RESIGN -> _events.tryEmit(NetEvent.Resigned)
                        TcpType.TIMEOUT -> _events.tryEmit(NetEvent.TimedOut)
                        else -> Unit
                    }
                }
            }
            if (running) {
                Log.d(TAG, "peer closed connection")
                _events.tryEmit(NetEvent.Disconnected)
            }
        } catch (e: IOException) {
            if (running) {
                Log.d(TAG, "read loop error: ${e.message}")
                _events.tryEmit(NetEvent.Disconnected)
            }
        }
    }

    private fun send(frame: ByteArray) {
        scope.launch {
            val target = socket ?: return@launch
            sendMutex.withLock {
                try {
                    val output = target.outputStream
                    output.write(frame)
                    output.flush()
                } catch (e: IOException) {
                    // 发送失败通常伴随断线，读循环负责上报 Disconnected
                    Log.d(TAG, "bt send fail: ${e.message}")
                }
            }
        }
    }

    private fun closeAll() {
        closeQuietly(socket)
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        socket = null
        serverSocket = null
    }

    private fun closeQuietly(target: BluetoothSocket?) {
        try {
            target?.close()
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val TAG = "BtGameClient"
        const val BUFFER_SIZE = 2048

        /** 建连重试总时长预算，覆盖对端"关握手口 → 开对局口"的空窗即可 */
        const val RETRY_BUDGET_MS = 15_000L
        const val RETRY_INTERVAL_MS = 300L
        const val ACCEPT_TIMEOUT_MS = 30_000
    }
}
