package com.github.lany192.fivechess.domain.engine

import com.github.lany192.fivechess.domain.model.GameEvent
import com.github.lany192.fivechess.domain.model.GameMode
import com.github.lany192.fivechess.domain.model.GameState
import com.github.lany192.fivechess.domain.model.Move
import com.github.lany192.fivechess.domain.model.Point
import com.github.lany192.fivechess.domain.model.Side

/**
 * 五子棋规则引擎，纯 Kotlin 无 Android 依赖
 *
 * 引擎内部持有可变棋盘，对外一律通过 [snapshot] 导出不可变快照；
 * 操作结果以事件列表形式返回，不再通过 Handler/Message 通知。
 */
class GameEngine(val width: Int = 15, val height: Int = 15) {

    private val dirs = arrayOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)

    private var mode = GameMode.LOCAL_TWO
    private var mySide: Side? = null
    private var first = Side.BLACK
    private var board: Array<Array<Side?>> = emptyBoard()
    private val moves = mutableListOf<Move>()
    private var active = Side.BLACK
    private var winner: Side? = null
    private var over = false

    fun start(mode: GameMode, mySide: Side? = null, first: Side = Side.BLACK): EngineResult {
        this.mode = mode
        this.mySide = mySide
        this.first = first
        board = emptyBoard()
        moves.clear()
        active = first
        winner = null
        over = false
        return EngineResult(snapshot(), emptyList())
    }

    /** 本端落子（LOCAL_TWO 落给当前行棋方，AI/LAN 校验轮次） */
    fun applyMove(x: Int, y: Int): EngineResult {
        val reason = turnCheck(x, y)
        if (reason != null) {
            return EngineResult(snapshot(), listOf(GameEvent.IllegalMove(x, y, reason)))
        }
        return place(x, y, active)
    }

    /** 远端落子（AI / 网络对手），指定落子方，不校验轮次 */
    fun applyRemoteMove(x: Int, y: Int, side: Side): EngineResult {
        val reason = placementCheck(x, y)
        if (reason != null) {
            return EngineResult(snapshot(), listOf(GameEvent.IllegalMove(x, y, reason)))
        }
        return place(x, y, side)
    }

    /** 回退 steps 手 */
    fun rollback(steps: Int = 1): EngineResult {
        val removed = mutableListOf<Move>()
        repeat(steps) {
            val last = moves.removeLastOrNull() ?: return@repeat
            board[last.x][last.y] = null
            removed.add(last)
        }
        if (removed.isNotEmpty()) {
            winner = null
            over = false
        }
        active = moves.lastOrNull()?.side?.opposite ?: first
        return EngineResult(snapshot(), listOf(GameEvent.RollbackApplied(removed.toList(), active)))
    }

    /** 清盘重开，黑先 */
    fun restart(): EngineResult {
        board = emptyBoard()
        moves.clear()
        active = first
        winner = null
        over = false
        return EngineResult(snapshot(), listOf(GameEvent.Restarted))
    }

    fun snapshot(): GameState = GameState(
        mode = mode,
        width = width,
        height = height,
        cells = List(width) { x -> List(height) { y -> board[x][y] } },
        moves = moves.toList(),
        active = active,
        winner = winner,
        over = over,
        mySide = mySide,
    )

    private fun turnCheck(x: Int, y: Int): GameEvent.IllegalMove.Reason? {
        placementCheck(x, y)?.let { return it }
        if (mySide != null && active != mySide) {
            return GameEvent.IllegalMove.Reason.NOT_YOUR_TURN
        }
        return null
    }

    private fun placementCheck(x: Int, y: Int): GameEvent.IllegalMove.Reason? {
        if (x !in 0 until width || y !in 0 until height) {
            return GameEvent.IllegalMove.Reason.OUT_OF_BOUNDS
        }
        if (over) return GameEvent.IllegalMove.Reason.GAME_OVER
        if (board[x][y] != null) return GameEvent.IllegalMove.Reason.OCCUPIED
        return null
    }

    private fun place(x: Int, y: Int, side: Side): EngineResult {
        board[x][y] = side
        val move = Move(x, y, side)
        moves.add(move)
        val line = winLineFrom(x, y, side)
        return if (line != null) {
            winner = side
            over = true
            EngineResult(snapshot(), listOf(GameEvent.GameOver(side, line)))
        } else {
            active = side.opposite
            EngineResult(snapshot(), listOf(GameEvent.MoveApplied(move, active)))
        }
    }

    private fun winLineFrom(x: Int, y: Int, side: Side): List<Point>? {
        for ((dx, dy) in dirs) {
            val line = mutableListOf(Point(x, y))
            var i = 1
            while (inBoard(x + dx * i, y + dy * i) && board[x + dx * i][y + dy * i] == side) {
                line.add(Point(x + dx * i, y + dy * i))
                i++
            }
            i = 1
            while (inBoard(x - dx * i, y - dy * i) && board[x - dx * i][y - dy * i] == side) {
                line.add(0, Point(x - dx * i, y - dy * i))
                i++
            }
            if (line.size >= 5) return line
        }
        return null
    }

    private fun inBoard(x: Int, y: Int) = x in 0 until width && y in 0 until height

    private fun emptyBoard(): Array<Array<Side?>> = Array(width) { arrayOfNulls(height) }
}
