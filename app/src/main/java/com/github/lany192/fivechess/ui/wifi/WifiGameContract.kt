package com.github.lany192.fivechess.ui.wifi

import com.github.lany192.fivechess.domain.model.Side
import com.github.lany192.fivechess.ui.common.BoardRenderState

sealed interface WifiGameIntent {
    data class BoardTap(val x: Int, val y: Int) : WifiGameIntent
    data object RestartClicked : WifiGameIntent
    data object RollbackClicked : WifiGameIntent
    data object RollbackAgreed : WifiGameIntent
    data object RollbackRejected : WifiGameIntent
    data object DrawClicked : WifiGameIntent
    data object DrawAgreed : WifiGameIntent
    data object DrawRejected : WifiGameIntent
    data object ResignClicked : WifiGameIntent
    data object ResignConfirmed : WifiGameIntent
}

/** 终局结果（含引擎五连胜与求和/认输宣告，横幅随状态幂等渲染，不用弹窗） */
sealed interface WifiGameEnd {
    data class Win(val winner: Side) : WifiGameEnd
    data object Draw : WifiGameEnd
}

data class WifiGameState(
    val board: BoardRenderState,
    val mySide: Side,
    val active: Side = Side.BLACK,
    val connected: Boolean = false,
    val blackWins: Int = 0,
    val whiteWins: Int = 0,
    val end: WifiGameEnd? = null,
)

sealed interface WifiGameEffect {
    data object DismissConnecting : WifiGameEffect
    data object ShowRollbackRequest : WifiGameEffect
    data object ShowDrawRequest : WifiGameEffect
    data object ShowResignConfirm : WifiGameEffect
    data class ShowMessage(val text: String) : WifiGameEffect
    data object Exit : WifiGameEffect
}
