package com.github.lany192.fivechess.ui.net

import com.github.lany192.fivechess.domain.model.Side
import com.github.lany192.fivechess.ui.common.BoardRenderState

/**
 * 联机对战契约（局域网 / 蓝牙共用）
 *
 * 两种模式只在建连层不同，对局玩法（落子、悔棋、重开、求和、认输、胜场）完全一致，
 * 因此共用同一套 Intent/State/Effect 与 ViewModel。
 */
sealed interface NetGameIntent {
    data class BoardTap(val x: Int, val y: Int) : NetGameIntent
    data object RestartClicked : NetGameIntent
    data object RollbackClicked : NetGameIntent
    data object RollbackAgreed : NetGameIntent
    data object RollbackRejected : NetGameIntent
    data object DrawClicked : NetGameIntent
    data object DrawAgreed : NetGameIntent
    data object DrawRejected : NetGameIntent
    data object ResignClicked : NetGameIntent
    data object ResignConfirmed : NetGameIntent
}

/** 终局结果（含引擎五连胜与求和/认输宣告，横幅随状态幂等渲染，不用弹窗） */
sealed interface NetGameEnd {
    data class Win(val winner: Side) : NetGameEnd
    data object Draw : NetGameEnd
}

data class NetGameState(
    val board: BoardRenderState,
    val mySide: Side,
    val active: Side = Side.BLACK,
    val connected: Boolean = false,
    val blackWins: Int = 0,
    val whiteWins: Int = 0,
    val end: NetGameEnd? = null,
)

sealed interface NetGameEffect {
    data object DismissConnecting : NetGameEffect
    data object ShowRollbackRequest : NetGameEffect
    data object ShowDrawRequest : NetGameEffect
    data object ShowResignConfirm : NetGameEffect
    data class ShowMessage(val text: String) : NetGameEffect
    data object Exit : NetGameEffect
}
