package com.github.lany192.gomoku.data.net

import android.os.Build
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
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketException
import java.net.UnknownHostException

/**
 * 局域网发现管理：UDP 组播广播发现 + 单播握手/聊天（取代旧 ConnnectingService）
 *
 * 线程模型：4 条手写线程改为自管 CoroutineScope 上的阻塞 IO 协程；
 * 阻塞的 receive() 通过 close socket 打断（DatagramSocket 不响应协程取消）。
 */
class LanDiscoveryManager(private val localIp: String) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<DiscoveryEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<DiscoveryEvent> = _events.asSharedFlow()

    private var udpSocket: DatagramSocket? = null
    private var multicastSocket: MulticastSocket? = null

    @Volatile
    private var running = false

    init {
        require(localIp.isNotBlank()) { "local ip must not be blank" }
    }

    fun start() {
        if (running) return
        try {
            udpSocket = DatagramSocket(Protocol.UDP_PORT)
            multicastSocket = MulticastSocket(Protocol.MULTICAST_PORT).also { socket ->
                socket.joinGroup(InetAddress.getByName(Protocol.MULTICAST_IP))
                socket.timeToLive = 1
            }
        } catch (e: IOException) {
            Log.d(TAG, "init sockets fail: ${e.message}")
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.SOCKET_NULL))
            closeSockets()
            return
        }
        running = true
        scope.launch { receiveUdpLoop() }
        scope.launch { receiveMulticastLoop() }
    }

    fun stop() {
        running = false
        closeSockets()
        scope.cancel()
    }

    /** 广播上线，寻找局域网内可联机对象 */
    fun sendScanBroadcast() {
        launchIo { sendMulticast(Protocol.encodeBroadcast(BroadcastType.JOIN, fullDeviceName(), localIp)) }
    }

    /**
     * 广播下线。
     *
     * 刻意走独立线程而非 [scope]：调用方（[stop] 之前的 onCleared）紧接着就会取消 scope，
     * 排在同一条协程队列上的发送会被取消抢先、一个字节都发不出去，对端只能等 10 秒超时。
     * 用临时 socket 发送也与 stop() 关闭自持 socket 的时序无关。
     */
    fun sendExitBroadcast() {
        val data = Protocol.encodeBroadcast(BroadcastType.EXIT, fullDeviceName(), localIp)
        Thread({
            try {
                MulticastSocket().use { socket ->
                    socket.timeToLive = 1
                    val packet = DatagramPacket(data, data.size)
                    packet.address = InetAddress.getByName(Protocol.MULTICAST_IP)
                    packet.port = Protocol.MULTICAST_PORT
                    socket.send(packet)
                }
            } catch (e: IOException) {
                Log.d(TAG, "send exit multicast fail: ${e.message}")
            }
        }, EXIT_THREAD_NAME).start()
    }

    /** 请求与对方联机 */
    fun askConnect(dstIp: String) {
        launchIo { sendUdp(dstIp, Protocol.encodeTyped(UdpType.ASK, fullDeviceName(), localIp)) }
    }

    /** 同意对方联机请求 */
    fun accept(dstIp: String) {
        launchIo { sendUdp(dstIp, Protocol.encodeTyped(UdpType.AGREE, Build.BRAND, localIp)) }
    }

    /** 拒绝对方联机请求 */
    fun reject(dstIp: String) {
        launchIo { sendUdp(dstIp, Protocol.encodeTyped(UdpType.REJECT, Build.BRAND, localIp)) }
    }

    /** 发送聊天内容 */
    fun sendChat(content: String, dstIp: String) {
        launchIo { sendUdp(dstIp, Protocol.encodeChat(Build.BRAND, localIp, content)) }
    }

    // ---------- 接收循环 ----------

    private suspend fun receiveUdpLoop() {
        val socket = udpSocket ?: return
        val buf = ByteArray(BUFFER_SIZE)
        val packet = DatagramPacket(buf, buf.size)
        while (running) {
            try {
                // length 是入参（容量）兼出参（实际大小），复用前必须重置，否则后续包被截断
                packet.setLength(buf.size)
                socket.receive(packet)
            } catch (e: SocketException) {
                break // stop() 关闭 socket 打断阻塞
            } catch (e: IOException) {
                // 瞬时错误不能让接收循环永久退出，否则发现/握手静默失效
                Log.d(TAG, "udp receive error: ${e.message}")
                delay(RECEIVE_ERROR_DELAY_MS)
                continue
            }
            val data = packet.data.copyOf(packet.length)
            if (data.isEmpty()) continue
            val type = data[0]
            val body = data.copyOfRange(1, data.size)
            // 包体 IP 是对端自报的（废弃 API，可能陈旧/错误），同网段以传输层源地址为准
            val srcIp = packet.address?.hostAddress
            try {
                when (type) {
                    UdpType.UDP_JOIN.b -> {
                        val item = Protocol.decodeTypedBody(body)
                        _events.tryEmit(DiscoveryEvent.PeerJoined(item.name, srcIp ?: item.ip))
                    }
                    UdpType.ASK.b -> {
                        val item = Protocol.decodeTypedBody(body)
                        _events.tryEmit(DiscoveryEvent.HandshakeRequested(item.name, srcIp ?: item.ip))
                    }
                    UdpType.AGREE.b -> {
                        val item = Protocol.decodeTypedBody(body)
                        _events.tryEmit(DiscoveryEvent.HandshakeAccepted(item.name, srcIp ?: item.ip))
                    }
                    UdpType.REJECT.b -> {
                        val item = Protocol.decodeTypedBody(body)
                        _events.tryEmit(DiscoveryEvent.HandshakeRejected(item.name, srcIp ?: item.ip))
                    }
                    UdpType.CHAT.b -> {
                        val payload = Protocol.decodeChatBody(body)
                        val from = payload.from.copy(ip = srcIp ?: payload.from.ip)
                        _events.tryEmit(DiscoveryEvent.ChatReceived(from, payload.content))
                    }
                }
            } catch (e: ProtocolException) {
                Log.d(TAG, "drop malformed udp packet: ${e.message}")
            }
        }
    }

    private suspend fun receiveMulticastLoop() {
        val socket = multicastSocket ?: return
        val buf = ByteArray(BUFFER_SIZE)
        val packet = DatagramPacket(buf, buf.size)
        while (running) {
            try {
                packet.setLength(buf.size)
                socket.receive(packet)
            } catch (e: SocketException) {
                break
            } catch (e: IOException) {
                Log.d(TAG, "multicast receive error: ${e.message}")
                delay(RECEIVE_ERROR_DELAY_MS)
                continue
            }
            val data = packet.data.copyOf(packet.length)
            val (bodyItem, type) = try {
                Protocol.decodeBroadcast(data)
            } catch (e: ProtocolException) {
                Log.d(TAG, "drop malformed broadcast: ${e.message}")
                continue
            }
            // 包体 IP 是对端自报的（可能陈旧/错误），同网段以传输层源地址为准
            val srcIp = packet.address?.hostAddress
            if (srcIp == localIp || bodyItem.ip == localIp) continue // 自己发的广播
            val item = if (srcIp != null) bodyItem.copy(ip = srcIp) else bodyItem
            when (type) {
                BroadcastType.JOIN -> _events.tryEmit(DiscoveryEvent.PeerJoined(item.name, item.ip))
                BroadcastType.EXIT -> _events.tryEmit(DiscoveryEvent.PeerExited(item.name, item.ip))
            }
            if (type == BroadcastType.JOIN) {
                // 回单播让对方也能看到自己
                sendUdp(item.ip, Protocol.encodeTyped(UdpType.UDP_JOIN, fullDeviceName(), localIp))
            }
        }
    }

    // ---------- 发送 ----------

    private fun sendUdp(dstIp: String, data: ByteArray) {
        val socket = udpSocket ?: run {
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.SOCKET_NULL))
            return
        }
        try {
            val address = InetAddress.getByName(dstIp)
            socket.send(DatagramPacket(data, data.size, address, Protocol.UDP_PORT))
        } catch (e: UnknownHostException) {
            Log.d(TAG, "udp dst ip invalid: $dstIp")
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.UDP_IP_ERROR))
        } catch (e: IOException) {
            Log.d(TAG, "udp send fail: ${e.message}")
        }
    }

    private fun sendMulticast(data: ByteArray) {
        val socket = multicastSocket ?: run {
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.SOCKET_NULL))
            return
        }
        try {
            val packet = DatagramPacket(data, data.size)
            packet.address = InetAddress.getByName(Protocol.MULTICAST_IP)
            packet.port = Protocol.MULTICAST_PORT
            socket.send(packet)
        } catch (e: IOException) {
            Log.d(TAG, "multicast send fail: ${e.message}")
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.MULTICAST_ERROR))
        } catch (e: UnknownHostException) {
            _events.tryEmit(DiscoveryEvent.Error(DiscoveryError.MULTICAST_ERROR))
        }
    }

    private fun closeSockets() {
        try {
            udpSocket?.close()
        } catch (_: Exception) {
        }
        try {
            multicastSocket?.close()
        } catch (_: Exception) {
        }
        udpSocket = null
        multicastSocket = null
    }

    private fun launchIo(block: () -> Unit) {
        scope.launch { block() }
    }

    private fun fullDeviceName() = "${Build.BRAND}-${Build.MODEL}"

    private companion object {
        const val TAG = "LanDiscovery"
        const val BUFFER_SIZE = 1024
        const val RECEIVE_ERROR_DELAY_MS = 200L
        const val EXIT_THREAD_NAME = "lan-exit-broadcast"
    }
}
