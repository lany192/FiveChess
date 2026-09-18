package com.github.lany192.gomoku.data.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {

    // ---------- 字节值冻结回归 ----------

    @Test
    fun `协议字节值与旧版一致`() {
        assertEquals(0.toByte(), BroadcastType.JOIN.b)
        assertEquals(1.toByte(), BroadcastType.EXIT.b)

        assertEquals(0.toByte(), UdpType.UDP_JOIN.b)
        assertEquals(11.toByte(), UdpType.ASK.b)
        assertEquals(12.toByte(), UdpType.AGREE.b)
        assertEquals(13.toByte(), UdpType.REJECT.b)
        assertEquals(14.toByte(), UdpType.CHAT.b)

        assertEquals(0.toByte(), TcpType.ADD_CHESS.b)
        assertEquals(1.toByte(), TcpType.CONNECTED.b)
        assertEquals(2.toByte(), TcpType.ROLLBACK_ASK.b)
        assertEquals(3.toByte(), TcpType.ROLLBACK_AGREE.b)
        assertEquals(4.toByte(), TcpType.ROLLBACK_REJECT.b)
        assertEquals(5.toByte(), TcpType.RESTART.b)
    }

    // ---------- 广播 ----------

    @Test
    fun `广播编解码对称`() {
        val encoded = Protocol.encodeBroadcast(BroadcastType.JOIN, "小米-Xiaomi13", "192.168.1.23")
        val (item, type) = Protocol.decodeBroadcast(encoded)
        assertEquals("小米-Xiaomi13", item.name)
        assertEquals("192.168.1.23", item.ip)
        assertEquals(BroadcastType.JOIN, type)
    }

    @Test
    fun `广播编码与旧版字节布局一致`() {
        val name = "HUAWEI-P40"
        val ip = "192.168.0.7"
        val expected = byteArrayOf(
            name.length.toByte(),
            *name.toByteArray(Charsets.UTF_8),
            ip.length.toByte(),
            *ip.toByteArray(Charsets.UTF_8),
            0,
        )
        assertArrayEquals(expected, Protocol.encodeBroadcast(BroadcastType.JOIN, name, ip))
    }

    @Test
    fun `旧版广播字节可解析`() {
        val name = "OPPO-Reno"
        val ip = "10.0.0.3"
        val legacy = byteArrayOf(
            name.length.toByte(),
            *name.toByteArray(Charsets.UTF_8),
            ip.length.toByte(),
            *ip.toByteArray(Charsets.UTF_8),
            1,
        )
        val (item, type) = Protocol.decodeBroadcast(legacy)
        assertEquals(name, item.name)
        assertEquals(ip, item.ip)
        assertEquals(BroadcastType.EXIT, type)
    }

    // ---------- 带类型 UDP 消息 ----------

    @Test
    fun `握手消息编解码对称`() {
        val encoded = Protocol.encodeTyped(UdpType.ASK, "vivo-X90", "192.168.1.9")
        assertEquals(UdpType.ASK.b, encoded[0])
        val item = Protocol.decodeTypedBody(encoded.copyOfRange(1, encoded.size))
        assertEquals("vivo-X90", item.name)
        assertEquals("192.168.1.9", item.ip)
    }

    @Test
    fun `旧版握手字节布局一致`() {
        val name = "OnePlus-11"
        val ip = "192.168.1.66"

        fun expectedBytes(type: Byte) = byteArrayOf(
            type,
            name.length.toByte(),
            *name.toByteArray(Charsets.UTF_8),
            ip.length.toByte(),
            *ip.toByteArray(Charsets.UTF_8),
        )

        assertArrayEquals(expectedBytes(11), Protocol.encodeTyped(UdpType.ASK, name, ip))
        assertArrayEquals(expectedBytes(12), Protocol.encodeTyped(UdpType.AGREE, name, ip))
        assertArrayEquals(expectedBytes(13), Protocol.encodeTyped(UdpType.REJECT, name, ip))
    }

    // ---------- 聊天 ----------

    @Test
    fun `聊天编解码对称`() {
        val encoded = Protocol.encodeChat("Xiaomi-14", "192.168.1.5", "你好，来一局？")
        assertEquals(UdpType.CHAT.b, encoded[0])
        val payload = Protocol.decodeChatBody(encoded.copyOfRange(1, encoded.size))
        assertEquals("Xiaomi-14", payload.from.name)
        assertEquals("192.168.1.5", payload.from.ip)
        assertEquals("你好，来一局？", payload.content)
    }

    @Test
    fun `旧版聊天字节可解析`() {
        val name = "LMY"
        val ip = "192.168.3.4"
        val chat = "hi"
        val legacy = byteArrayOf(
            14,
            name.length.toByte(),
            *name.toByteArray(Charsets.UTF_8),
            ip.length.toByte(),
            *ip.toByteArray(Charsets.UTF_8),
            chat.length.toByte(),
            *chat.toByteArray(Charsets.UTF_8),
        )
        val payload = Protocol.decodeChatBody(legacy.copyOfRange(1, legacy.size))
        assertEquals(name, payload.from.name)
        assertEquals(ip, payload.from.ip)
        assertEquals(chat, payload.content)
    }

    // ---------- 畸形输入防御 ----------

    @Test(expected = ProtocolException::class)
    fun `广播长度不足抛协议异常`() {
        Protocol.decodeBroadcast(byteArrayOf(10, 1, 2))
    }

    @Test(expected = ProtocolException::class)
    fun `未知广播类型抛协议异常`() {
        val data = Protocol.encodeBroadcast(BroadcastType.JOIN, "a", "1.1.1.1")
        data[data.size - 1] = 9
        Protocol.decodeBroadcast(data)
    }

    @Test(expected = ProtocolException::class)
    fun `消息体长度不符抛协议异常`() {
        val body = Protocol.encodeTyped(UdpType.ASK, "abc", "1.2.3.4").copyOfRange(1, 5)
        Protocol.decodeTypedBody(body)
    }

    // ---------- TCP 帧 ----------

    @Test
    fun `落子帧与旧版字节布局一致`() {
        val expected = byteArrayOf(4, 0, 7, 8)
        assertArrayEquals(expected, Protocol.encodeTcp(TcpType.ADD_CHESS, byteArrayOf(7, 8)))
        assertArrayEquals(byteArrayOf(2, 2), Protocol.encodeTcp(TcpType.ROLLBACK_ASK))
    }

    @Test
    fun `完整帧一次解析`() {
        val reader = Protocol.TcpFrameReader()
        val frames = reader.feed(Protocol.encodeTcp(TcpType.ADD_CHESS, byteArrayOf(3, 4)))
        assertEquals(1, frames.size)
        assertEquals(TcpType.ADD_CHESS, frames[0].typeEnum())
        assertArrayEquals(byteArrayOf(3, 4), frames[0].payload)
    }

    @Test
    fun `半帧分两次到达`() {
        val reader = Protocol.TcpFrameReader()
        val frame = Protocol.encodeTcp(TcpType.ROLLBACK_ASK)
        assertTrue(reader.feed(frame.copyOfRange(0, 1)).isEmpty())
        val frames = reader.feed(frame.copyOfRange(1, frame.size))
        assertEquals(1, frames.size)
        assertEquals(TcpType.ROLLBACK_ASK, frames[0].typeEnum())
    }

    @Test
    fun `粘包一次解析多帧`() {
        val reader = Protocol.TcpFrameReader()
        val data = Protocol.encodeTcp(TcpType.ADD_CHESS, byteArrayOf(1, 2)) +
                Protocol.encodeTcp(TcpType.RESTART) +
                Protocol.encodeTcp(TcpType.ROLLBACK_REJECT)
        val frames = reader.feed(data)
        assertEquals(3, frames.size)
        assertEquals(TcpType.ADD_CHESS, frames[0].typeEnum())
        assertEquals(TcpType.RESTART, frames[1].typeEnum())
        assertEquals(TcpType.ROLLBACK_REJECT, frames[2].typeEnum())
    }

    @Test
    fun `畸形长度帧不阻塞后续解析`() {
        val reader = Protocol.TcpFrameReader()
        val frames = reader.feed(byteArrayOf(0, 1) + Protocol.encodeTcp(TcpType.CONNECTED))
        assertEquals(1, frames.size)
        assertEquals(TcpType.CONNECTED, frames[0].typeEnum())
    }

    @Test
    fun `空帧类型未知时返回null`() {
        val frames = Protocol.TcpFrameReader().feed(byteArrayOf(3, 99, 1))
        assertEquals(1, frames.size)
        assertNull(frames[0].typeEnum())
    }

    // ---------- 求和/认输（新增消息类型） ----------

    @Test
    fun `超时判负字节值冻结`() {
        assertEquals(10.toByte(), TcpType.TIMEOUT.b)
        assertArrayEquals(byteArrayOf(2, 10), Protocol.encodeTcp(TcpType.TIMEOUT))
    }

    @Test
    fun `求和认输字节值冻结`() {
        assertEquals(6.toByte(), TcpType.DRAW_ASK.b)
        assertEquals(7.toByte(), TcpType.DRAW_AGREE.b)
        assertEquals(8.toByte(), TcpType.DRAW_REJECT.b)
        assertEquals(9.toByte(), TcpType.RESIGN.b)
    }

    @Test
    fun `求和认输帧字节布局`() {
        assertArrayEquals(byteArrayOf(2, 6), Protocol.encodeTcp(TcpType.DRAW_ASK))
        assertArrayEquals(byteArrayOf(2, 7), Protocol.encodeTcp(TcpType.DRAW_AGREE))
        assertArrayEquals(byteArrayOf(2, 8), Protocol.encodeTcp(TcpType.DRAW_REJECT))
        assertArrayEquals(byteArrayOf(2, 9), Protocol.encodeTcp(TcpType.RESIGN))
    }

    @Test
    fun `新类型粘包解析`() {
        val reader = Protocol.TcpFrameReader()
        val data = Protocol.encodeTcp(TcpType.DRAW_ASK) +
                Protocol.encodeTcp(TcpType.RESIGN) +
                Protocol.encodeTcp(TcpType.DRAW_AGREE)
        val frames = reader.feed(data)
        assertEquals(3, frames.size)
        assertEquals(TcpType.DRAW_ASK, frames[0].typeEnum())
        assertEquals(TcpType.RESIGN, frames[1].typeEnum())
        assertEquals(TcpType.DRAW_AGREE, frames[2].typeEnum())
    }

    @Test
    fun `新类型typeEnum映射`() {
        assertEquals(TcpType.DRAW_ASK, TcpFrame(6, ByteArray(0)).typeEnum())
        assertEquals(TcpType.DRAW_AGREE, TcpFrame(7, ByteArray(0)).typeEnum())
        assertEquals(TcpType.DRAW_REJECT, TcpFrame(8, ByteArray(0)).typeEnum())
        assertEquals(TcpType.RESIGN, TcpFrame(9, ByteArray(0)).typeEnum())
    }

    // ---------- 蓝牙控制通道 ----------

    @Test
    fun `蓝牙握手信号字节冻结`() {
        assertEquals(20.toByte(), BtSignal.AGREE.b)
        assertEquals(21.toByte(), BtSignal.REJECT.b)
    }

    @Test
    fun `蓝牙服务UUID双端一致`() {
        // 双端必须是同一个 UUID 才能互相发现；改动即破坏蓝牙互通
        assertEquals("00001101-0000-1000-8000-00805F9B34FB", Protocol.BT_UUID)
    }
}
