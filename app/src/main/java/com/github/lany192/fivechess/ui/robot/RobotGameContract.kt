package com.github.lany192.fivechess.ui.robot

import com.github.lany192.fivechess.domain.ai.Difficulty
import com.github.lany192.fivechess.domain.model.Side
import com.github.lany192.fivechess.ui.common.BoardRenderState

sealed interface RobotGameIntent {
    data class BoardTap(val x: Int, val y: Int) : RobotGameIntent
    data object RestartClicked : RobotGameIntent
    data object RollbackClicked : RobotGameIntent
    data object DifficultyClicked : RobotGameIntent
    data class LevelSelected(val level: Difficulty) : RobotGameIntent
}

data class RobotGameState(
    val board: BoardRenderState,
    val active: Side = Side.BLACK,
    val blackWins: Int = 0,
    val whiteWins: Int = 0,
    val aiLevel: Difficulty = Difficulty.MEDIUM,
)

sealed interface RobotGameEffect {
    data class ShowGameOver(val winner: Side) : RobotGameEffect
    data class ShowDifficulty(val current: Difficulty) : RobotGameEffect
}
