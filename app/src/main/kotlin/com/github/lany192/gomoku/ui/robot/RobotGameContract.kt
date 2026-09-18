package com.github.lany192.gomoku.ui.robot

import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.model.Side
import com.github.lany192.gomoku.ui.common.BoardRenderState

sealed interface RobotGameIntent {
    data class BoardTap(val x: Int, val y: Int) : RobotGameIntent
    data object RestartClicked : RobotGameIntent
    data object RollbackClicked : RobotGameIntent
    data class LevelSelected(val level: Difficulty) : RobotGameIntent
}

data class RobotGameState(
    val board: BoardRenderState,
    val active: Side = Side.BLACK,
    val blackWins: Int = 0,
    val whiteWins: Int = 0,
    val aiLevel: Difficulty = Difficulty.MEDIUM,
    /** 终局胜方，null 表示对局进行中（横幅随状态幂等渲染，不用弹窗） */
    val winner: Side? = null,
)

sealed interface RobotGameEffect
