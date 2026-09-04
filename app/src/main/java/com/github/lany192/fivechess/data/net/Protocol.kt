package com.github.lany192.fivechess.data.net

/**
 * 线上协议字节定义与编解码
 *
 * 字节值冻结，勿改：与旧版互通。仅允许追加新的消息类型。
 * 帧格式：
 * - UDP 广播：[nameLen][name][ipLen][ip][type]，type 为末字节，发往组播 230.0.2.2:1688
 * - UDP 单播：[type][nameLen][name][ipLen][ip]（聊天多一段 [chatLen][chat]），发往对端 UDP 2599
 * - TCP：[len][type][payload]，len 为含 len/type 的总长，端口 8899
 */
enum class BroadcastType(val b: Byte) { JOIN(0), EXIT(1) }

enum class UdpType(val b: Byte) { UDP_JOIN(0), ASK(11), AGREE(12), REJECT(13), CHAT(14) }

enum class TcpType(val b: Byte) {
    ADD_CHESS(0),
    CONNECTED(1),
    ROLLBACK_ASK(2),
    ROLLBACK_AGREE(3),
    ROLLBACK_REJECT(4),

    /** 新增：双方同步重开（旧版收到未知字节会忽略，互通安全） */
    RESTART(5),
}

/** 旧 onError 的错误码语义 */
enum class DiscoveryError { SOCKET_NULL, IP_NULL, UDP_IP_ERROR, UDP_DATA_ERROR, MULTICAST_ERROR }

/** 协议解析失败（畸形输入只抛异常，不崩溃调用方） */
class ProtocolException(message: String) : IllegalArgumentException(message)

/** 网络层设备条目：同一 IP 视为同一设备（沿用旧 equals 语义，修复恒 0 的 hashCode） */
data class ConnectionItem(val name: String, val ip: String) {
    override fun equals(other: Any?): Boolean = other is ConnectionItem && other.ip == ip
    override fun hashCode(): Int = ip.hashCode()
}

/** 聊天消息 */
data class ChatContent(
    val connector: String,
    val content: String,
    val time: Long = System.currentTimeMillis(),
)

/** 聊天消息解析结果 */
data class ChatPayload(val from: ConnectionItem, val content: String)

/** 解析出的 TCP 帧 */
class TcpFrame(val type: Byte, val payload: ByteArray) {
    fun typeEnum(): TcpType? = TcpType.entries.firstOrNull { it.b == type }
}

object Protocol {
    const val MULTICAST_IP = "230.0.2.2"
    const val MULTICAST_PORT = 1688
    const val UDP_PORT = 2599
    const val TCP_PORT = 8899

    // ---------- UDP 广播 ----------

    fun encodeBroadcast(type: BroadcastType, name: String, ip: String): ByteArray {
        val nameBytes = utf8(name)
        val ipBytes = utf8(ip)
        val data = ByteArray(nameBytes.size + 1 + ipBytes.size + 1 + 1)
        data[0] = nameBytes.size.toByte()
        nameBytes.copyInto(data, 1)
        data[nameBytes.size + 1] = ipBytes.size.toByte()
        ipBytes.copyInto(data, nameBytes.size + 2)
        data[data.size - 1] = type.b
        return data
    }

    /** 解析广播包，返回设备信息与广播类型 */
    fun decodeBroadcast(data: ByteArray): Pair<ConnectionItem, BroadcastType> {
        if (data.size < 3) throw ProtocolException("broadcast too short")
        val nameLen = data[0].toInt() and 0xFF
        val ipLenPos = 1 + nameLen
        if (ipLenPos >= data.size) throw ProtocolException("broadcast missing ipLen")
        val ipLen = data[ipLenPos].toInt() and 0xFF
        val expected = ipLenPos + 1 + ipLen + 1
        if (data.size != expected) throw ProtocolException("broadcast length mismatch")
        val name = String(readSegment(data, 1, nameLen), Charsets.UTF_8)
        val ip = String(readSegment(data, ipLenPos + 1, ipLen), Charsets.UTF_8)
        val typeByte = data[data.size - 1]
        val type = BroadcastType.entries.firstOrNull { it.b == typeByte }
            ?: throw ProtocolException("unknown broadcast type $typeByte")
        return ConnectionItem(name, ip) to type
    }

    // ---------- 带类型 UDP 消息（[type][nameLen][name][ipLen][ip]） ----------

    fun encodeTyped(type: UdpType, name: String, ip: String): ByteArray {
        val typed = encodeBroadcast(BroadcastType.JOIN, name, ip)
        // 广播结构 [nameLen][name][ipLen][ip][t]，改造成 [type][nameLen][name][ipLen][ip]
        val body = typed.copyOfRange(0, typed.size - 1)
        val data = ByteArray(body.size + 1)
        data[0] = type.b
        body.copyInto(data, 1)
        return data
    }

    /** 解析剥掉 type 字节后的消息体，返回设备信息 */
    fun decodeTypedBody(body: ByteArray): ConnectionItem {
        if (body.size < 2) throw ProtocolException("typed body too short")
        val nameLen = body[0].toInt() and 0xFF
        val ipLenPos = 1 + nameLen
        if (ipLenPos >= body.size) throw ProtocolException("typed body missing ipLen")
        val ipLen = body[ipLenPos].toInt() and 0xFF
        val expected = ipLenPos + 1 + ipLen
        if (body.size != expected) throw ProtocolException("typed body length mismatch")
        val name = String(readSegment(body, 1, nameLen), Charsets.UTF_8)
        val ip = String(readSegment(body, ipLenPos + 1, ipLen), Charsets.UTF_8)
        return ConnectionItem(name, ip)
    }

    // ---------- 聊天（[type][nameLen][name][ipLen][ip][chatLen][chat]） ----------

    fun encodeChat(name: String, ip: String, content: String): ByteArray {
        val nameBytes = utf8(name)
        val ipBytes = utf8(ip)
        val chatBytes = utf8(content)
        val dataLen = nameBytes.size + ipBytes.size + chatBytes.size + 4
        val data = ByteArray(dataLen)
        data[0] = UdpType.CHAT.b
        data[1] = nameBytes.size.toByte()
        nameBytes.copyInto(data, 2)
        var pos = 2 + nameBytes.size
        data[pos] = ipBytes.size.toByte()
        ipBytes.copyInto(data, pos + 1)
        pos += 1 + ipBytes.size
        data[pos] = chatBytes.size.toByte()
        chatBytes.copyInto(data, pos + 1)
        return data
    }

    /** 解析剥掉 type 字节后的聊天消息体 */
    fun decodeChatBody(body: ByteArray): ChatPayload {
        if (body.size < 3) throw ProtocolException("chat body too short")
        val nameLen = body[0].toInt() and 0xFF
        val ipLenPos = 1 + nameLen
        if (ipLenPos >= body.size) throw ProtocolException("chat body missing ipLen")
        val ipLen = body[ipLenPos].toInt() and 0xFF
        val chatLenPos = ipLenPos + 1 + ipLen
        if (chatLenPos >= body.size) throw ProtocolException("chat body missing chatLen")
        val chatLen = body[chatLenPos].toInt() and 0xFF
        val expected = chatLenPos + 1 + chatLen
        if (body.size != expected) throw ProtocolException("chat body length mismatch")
        val name = String(readSegment(body, 1, nameLen), Charsets.UTF_8)
        val ip = String(readSegment(body, ipLenPos + 1, ipLen), Charsets.UTF_8)
        val chat = String(readSegment(body, chatLenPos + 1, chatLen), Charsets.UTF_8)
        return ChatPayload(ConnectionItem(name, ip), chat)
    }

    // ---------- TCP 帧（[len][type][payload]） ----------

    fun encodeTcp(type: TcpType, payload: ByteArray = ByteArray(0)): ByteArray {
        if (payload.size > 125) throw ProtocolException("tcp payload too large")
        val frame = ByteArray(2 + payload.size)
        frame[0] = frame.size.toByte()
        frame[1] = type.b
        payload.copyInto(frame, 2)
        return frame
    }

    /**
     * TCP 流式分帧器：粘包/半帧安全，兼容旧版写侧帧格式
     */
    class TcpFrameReader {
        private var buffer = ByteArray(0)

        /** 喂入新收到的字节，返回本次解析出的全部完整帧 */
        fun feed(data: ByteArray, length: Int = data.size): List<TcpFrame> {
            if (length !in 0..data.size) throw ProtocolException("invalid feed length")
            val merged = ByteArray(buffer.size + length)
            buffer.copyInto(merged)
            data.copyInto(merged, buffer.size, 0, length)
            buffer = merged

            val frames = mutableListOf<TcpFrame>()
            var offset = 0
            while (offset < buffer.size) {
                val frameLen = buffer[offset].toInt() and 0xFF
                if (frameLen < 2) {
                    // 畸形长度帧，丢弃 1 字节后继续，避免死循环
                    offset++
                    continue
                }
                if (buffer.size - offset < frameLen) break
                val type = buffer[offset + 1]
                val payload = buffer.copyOfRange(offset + 2, offset + frameLen)
                frames.add(TcpFrame(type, payload))
                offset += frameLen
            }
            if (offset > 0) {
                buffer = buffer.copyOfRange(offset, buffer.size)
            }
            return frames
        }
    }

    // ---------- 内部工具 ----------

    private fun utf8(value: String): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > 255) throw ProtocolException("field too long")
        return bytes
    }

    private fun readSegment(data: ByteArray, offset: Int, len: Int): ByteArray {
        if (len < 0 || offset < 0 || offset + len > data.size) {
            throw ProtocolException("segment out of bounds")
        }
        return data.copyOfRange(offset, offset + len)
    }
}
