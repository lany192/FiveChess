package com.github.lany192.fivechess.domain.model

/**
 * 棋局不可变快照，对外一律深拷贝导出
 */
data class GameState(
    val mode: GameMode = GameMode.LOCAL_TWO,
    val width: Int = 15,
    val height: Int = 15,
    /** [x][y] 的棋子矩阵，不可变 */
    val cells: List<List<Side?>> = emptyList(),
    /** 落子历史，按时间顺序 */
    val moves: List<Move> = emptyList(),
    /** 当前轮到的落子方 */
    val active: Side = Side.BLACK,
    val winner: Side? = null,
    val over: Boolean = false,
    /** 本端操控的一方；null 表示本地双人（双方都可操作） */
    val mySide: Side? = null,
) {
    val lastMove: Point?
        get() = moves.lastOrNull()?.let { Point(it.x, it.y) }

    fun sideAt(x: Int, y: Int): Side? = cells.getOrNull(x)?.getOrNull(y)

    /** side 现在是否被允许落子 */
    fun canMove(side: Side): Boolean =
        !over && active == side && (mySide == null || mySide == side)
}
