package com.github.lany192.gomoku.data.net

import kotlinx.coroutines.flow.SharedFlow

/**
 * 对局传输通道抽象
 *
 * 局域网 TCP（[LanGameClient]）与蓝牙 RFCOMM（BtGameClient）共用同一套 [TcpType] 帧与事件语义，
 * 对局层（ui/net）只依赖本接口，因此两种联机模式共享全部对局逻辑。
 */
interface GameTransport {

    /** 对局连接事件流 */
    val events: SharedFlow<NetEvent>

    /** 开始建连（服务端监听 accept / 客户端重试建连） */
    fun start()

    /** 停止并释放连接（阻塞读由关闭 socket 打断） */
    fun stop()

    fun sendMove(x: Int, y: Int)

    fun askRollback()

    fun agreeRollback()

    fun rejectRollback()

    /** 请求双方同步重开 */
    fun requestRestart()

    fun askDraw()

    fun agreeDraw()

    fun rejectDraw()

    /** 单方宣告认输，无需对方确认 */
    fun sendResign()

    /** 单方宣告本方每步限时归零判负，无需对方确认 */
    fun sendTimeout()
}
