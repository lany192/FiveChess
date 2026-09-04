package com.github.lany192.fivechess.data.net

/**
 * TCP 对局连接事件（LanGameClient 对外输出）
 */
sealed interface NetEvent {
    /** 连接建立 */
    data object Connected : NetEvent

    /** 建连失败（客户端重试耗尽 / 服务端 accept 失败） */
    data object ConnectFailed : NetEvent

    /** 对局中断线 */
    data object Disconnected : NetEvent

    data class ChessMove(val x: Int, val y: Int) : NetEvent

    data object RollbackAsked : NetEvent

    data object RollbackAgreed : NetEvent

    data object RollbackRejected : NetEvent

    data object RestartRequested : NetEvent
}
