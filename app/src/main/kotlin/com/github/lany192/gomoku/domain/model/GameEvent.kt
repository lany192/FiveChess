package com.github.lany192.gomoku.domain.model

/**
 * 引擎事件：一次引擎操作可能产生的事件列表
 */
sealed interface GameEvent {
    data class MoveApplied(val move: Move, val nextActive: Side) : GameEvent

    data class GameOver(val winner: Side, val line: List<Point>) : GameEvent

    /** 超时判负：无五连，故 [GameOver] 的 line 语义不适用，单列一类事件 */
    data class Timeout(val loser: Side, val winner: Side) : GameEvent

    /** 满盘和棋：最后一手落满棋盘且未成五连（五连优先：若同时成五，上方 [GameOver] 分支先返回） */
    data object Draw : GameEvent

    data class IllegalMove(val x: Int, val y: Int, val reason: Reason) : GameEvent {
        enum class Reason { OUT_OF_BOUNDS, OCCUPIED, NOT_YOUR_TURN, GAME_OVER }
    }

    data class RollbackApplied(val removed: List<Move>, val nextActive: Side) : GameEvent

    data object Restarted : GameEvent
}
