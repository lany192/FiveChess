package com.github.lany192.fivechess.data.net

import android.util.Log
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
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * 局域网对局传输：TCP 8899 收发落子/悔棋/重开指令（取代旧 ConnectedService）
 *
 * 读侧按 [len][type][payload] 精确分帧（粘包/半帧安全），写侧字节与旧版完全一致。
 */
class LanGameClient(private val isServer: Boolean, private val remoteIp: String) : GameTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<NetEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<NetEvent> = _events.asSharedFlow()

    private var serverSocket: ServerSocket? = null

    @Volatile
    private var socket: Socket? = null

    private val sendMutex = Mutex()
    private val frameReader = Protocol.TcpFrameReader()

    @Volatile
    private var running = false

    /** 服务端监听 accept / 客户端重试建连（8 次 × 200ms） */
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

    /** 请求双方同步重开（新增消息类型） */
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

    /** 单方宣告认输，无需对方确认 */
    override fun sendResign() {
        send(Protocol.encodeTcp(TcpType.RESIGN))
    }

    /** 单方宣告本方限时归零判负（新增消息类型） */
    override fun sendTimeout() {
        send(Protocol.encodeTcp(TcpType.TIMEOUT))
    }

    private suspend fun connectLoop() {
        var connected: Socket? = null
        try {
            connected = if (isServer) {
                val server = ServerSocket(Protocol.TCP_PORT)
                serverSocket = server
                // 对方同意后迟迟不来连接（闪退/杀进程）时不能永久阻塞
                server.soTimeout = ACCEPT_TIMEOUT_MS
                Log.d(TAG, "server waiting accept")
                server.accept()
            } else {
                connectWithRetry()
            }
        } catch (e: IOException) {
            Log.d(TAG, "connect fail: ${e.message}")
            _events.tryEmit(NetEvent.ConnectFailed)
            closeAll()
            return
        }
        if (connected == null) {
            // 客户端重试耗尽
            _events.tryEmit(NetEvent.ConnectFailed)
            closeAll()
            return
        }
        socket = connected
        Log.d(TAG, "net connected")
        _events.tryEmit(NetEvent.Connected)
        readLoop(connected)
    }

    private suspend fun connectWithRetry(): Socket? {
        var lastError: IOException? = null
        repeat(RETRY_TIMES) { attempt ->
            try {
                val s = Socket()
                s.connect(InetSocketAddress(remoteIp, Protocol.TCP_PORT))
                Log.d(TAG, "client connected after ${attempt + 1} attempts")
                return s
            } catch (e: IOException) {
                lastError = e
                delay(RETRY_INTERVAL_MS)
            }
        }
        Log.d(TAG, "connect retry exhausted: ${lastError?.message}")
        return null
    }

    private fun readLoop(socket: Socket) {
        try {
            val input = socket.getInputStream()
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
                    val output = target.getOutputStream()
                    output.write(frame)
                    output.flush()
                } catch (e: IOException) {
                    // 发送失败通常伴随断线，读循环负责上报 Disconnected
                    Log.d(TAG, "tcp send fail: ${e.message}")
                } catch (e: SocketException) {
                    Log.d(TAG, "tcp send fail: ${e.message}")
                }
            }
        }
    }

    private fun closeAll() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        socket = null
        serverSocket = null
    }

    private companion object {
        const val TAG = "LanGameClient"
        const val BUFFER_SIZE = 2048
        const val RETRY_TIMES = 10
        const val RETRY_INTERVAL_MS = 250L
        const val ACCEPT_TIMEOUT_MS = 30_000
    }
}
