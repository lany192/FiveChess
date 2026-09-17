package com.github.lany192.fivechess.ui.person

import com.github.lany192.fivechess.domain.model.Side
import com.github.lany192.fivechess.ui.common.BoardRenderState

sealed interface PersonGameIntent {
    data class BoardTap(val x: Int, val y: Int) : PersonGameIntent
    data object RestartClicked : PersonGameIntent
    data object RollbackClicked : PersonGameIntent
}

data class PersonGameState(
    val board: BoardRenderState,
    val active: Side = Side.BLACK,
    val blackWins: Int = 0,
    val whiteWins: Int = 0,
    /** 终局胜方，null 表示对局进行中（横幅随状态幂等渲染，不用弹窗） */
    val winner: Side? = null,
)

sealed interface PersonGameEffect
