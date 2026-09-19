package com.github.lany192.gomoku.ui.robot

import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.model.Side
import com.github.lany192.gomoku.ui.common.BoardRenderState

sealed interface RobotGameIntent {
    data class BoardTap(val x: Int, val y: Int) : RobotGameIntent
    data object RestartClicked : RobotGameIntent
    data object RollbackClicked : RobotGameIntent
    data class LevelSelected(val level: Difficulty) : RobotGameIntent
    data class AlgorithmSelected(val algorithm: AiAlgorithm) : RobotGameIntent
}

data class RobotGameState(
    val board: BoardRenderState,
    val active: Side = Side.BLACK,
    val blackWins: Int = 0,
    val whiteWins: Int = 0,
    val aiLevel: Difficulty = Difficulty.MEDIUM,
    val aiAlgorithm: AiAlgorithm = AiAlgorithm.DEFAULT,
    /** 终局胜方，null 表示对局进行中（横幅随状态幂等渲染，不用弹窗） */
    val winner: Side? = null,
    /** 满盘和棋终局，仅影响横幅文案（胜方为 null，故需单列一位） */
    val drawn: Boolean = false,
)

sealed interface RobotGameEffect
