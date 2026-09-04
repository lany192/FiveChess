package com.github.lany192.fivechess.ui.wifi

import com.github.lany192.fivechess.domain.model.Side
import com.github.lany192.fivechess.ui.common.BoardRenderState

sealed interface WifiGameIntent {
    data class BoardTap(val x: Int, val y: Int) : WifiGameIntent
    data object RestartClicked : WifiGameIntent
    data object RollbackClicked : WifiGameIntent
    data object RollbackAgreed : WifiGameIntent
    data object RollbackRejected : WifiGameIntent
}

data class WifiGameState(
    val board: BoardRenderState,
    val mySide: Side,
    val active: Side = Side.BLACK,
    val connected: Boolean = false,
    val blackWins: Int = 0,
    val whiteWins: Int = 0,
)

sealed interface WifiGameEffect {
    data object DismissConnecting : WifiGameEffect
    data class ShowGameResult(val iWon: Boolean) : WifiGameEffect
    data object ShowRollbackRequest : WifiGameEffect
    data class ShowMessage(val text: String) : WifiGameEffect
    data object Exit : WifiGameEffect
}
