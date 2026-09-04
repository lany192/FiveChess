package com.github.lany192.fivechess.ui.common

import com.github.lany192.fivechess.domain.model.GameState
import com.github.lany192.fivechess.domain.model.Point
import com.github.lany192.fivechess.domain.model.Side

/**
 * GameBoardView 的渲染模型，由 ViewModel 从 GameState 映射而来
 */
data class BoardRenderState(
    val width: Int,
    val height: Int,
    /** [x][y] 棋子矩阵 */
    val cells: List<List<Side?>>,
    val lastMove: Point? = null,
    /** 获胜五连坐标，终局时高亮 */
    val winLine: List<Point> = emptyList(),
) {
    companion object {
        fun from(state: GameState, winLine: List<Point> = emptyList()) = BoardRenderState(
            width = state.width,
            height = state.height,
            cells = state.cells,
            lastMove = state.lastMove,
            winLine = winLine,
        )

        fun empty(width: Int = 15, height: Int = 15): BoardRenderState = BoardRenderState(
            width = width,
            height = height,
            cells = List(width) { List<Side?>(height) { null } },
        )
    }
}
