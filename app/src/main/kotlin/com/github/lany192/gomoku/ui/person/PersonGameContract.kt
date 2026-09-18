package com.github.lany192.gomoku.ui.person

import com.github.lany192.gomoku.domain.model.Side
import com.github.lany192.gomoku.ui.common.BoardRenderState

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
    /** 终局由超时判负产生，仅影响横幅文案 */
    val timedOut: Boolean = false,
    /** 当前行棋方剩余毫秒；<= 0 表示不限时（倒计时关闭） */
    val remainingMillis: Long = 0,
)

sealed interface PersonGameEffect
