package com.github.lany192.fivechess.domain.model

/**
 * 引擎事件：一次引擎操作可能产生的事件列表
 */
sealed interface GameEvent {
    data class MoveApplied(val move: Move, val nextActive: Side) : GameEvent

    data class GameOver(val winner: Side, val line: List<Point>) : GameEvent

    /** 超时判负：无五连，故 [GameOver] 的 line 语义不适用，单列一类事件 */
    data class Timeout(val loser: Side, val winner: Side) : GameEvent

    data class IllegalMove(val x: Int, val y: Int, val reason: Reason) : GameEvent {
        enum class Reason { OUT_OF_BOUNDS, OCCUPIED, NOT_YOUR_TURN, GAME_OVER }
    }

    data class RollbackApplied(val removed: List<Move>, val nextActive: Side) : GameEvent

    data object Restarted : GameEvent
}
