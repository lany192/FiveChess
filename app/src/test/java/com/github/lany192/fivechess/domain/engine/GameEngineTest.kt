package com.github.lany192.fivechess.domain.engine

import com.github.lany192.fivechess.domain.model.GameEvent
import com.github.lany192.fivechess.domain.model.GameMode
import com.github.lany192.fivechess.domain.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameEngineTest {

    private lateinit var engine: GameEngine

    @Before
    fun setUp() {
        engine = GameEngine(15, 15)
        engine.start(GameMode.LOCAL_TWO)
    }

    private fun place(vararg moves: Triple<Int, Int, Side>) {
        moves.forEach { (x, y, side) -> engine.applyRemoteMove(x, y, side) }
    }

    private fun gameOverWinner(result: EngineResult): Side? =
        result.events.filterIsInstance<GameEvent.GameOver>().firstOrNull()?.winner

    @Test
    fun `黑方横向五连判胜`() {
        place(
            Triple(3, 7, Side.BLACK), Triple(3, 8, Side.WHITE),
            Triple(4, 7, Side.BLACK), Triple(4, 8, Side.WHITE),
            Triple(5, 7, Side.BLACK), Triple(5, 8, Side.WHITE),
            Triple(6, 7, Side.BLACK), Triple(6, 8, Side.WHITE),
        )
        val result = engine.applyRemoteMove(7, 7, Side.BLACK)
        assertEquals(Side.BLACK, gameOverWinner(result))
        assertEquals(5, result.events.filterIsInstance<GameEvent.GameOver>().first().line.size)
        assertTrue(result.state.over)
    }

    @Test
    fun `白方五连判胜`() {
        place(
            Triple(3, 7, Side.WHITE), Triple(4, 7, Side.WHITE),
            Triple(5, 7, Side.WHITE), Triple(6, 7, Side.WHITE),
        )
        val result = engine.applyRemoteMove(7, 7, Side.WHITE)
        assertEquals(Side.WHITE, gameOverWinner(result))
    }

    @Test
    fun `纵向五连判胜`() {
        place(
            Triple(7, 3, Side.BLACK), Triple(8, 3, Side.WHITE),
            Triple(7, 4, Side.BLACK), Triple(8, 4, Side.WHITE),
            Triple(7, 5, Side.BLACK), Triple(8, 5, Side.WHITE),
            Triple(7, 6, Side.BLACK), Triple(8, 6, Side.WHITE),
        )
        assertEquals(Side.BLACK, gameOverWinner(engine.applyRemoteMove(7, 7, Side.BLACK)))
    }

    @Test
    fun `正对角线五连判胜`() {
        place(
            Triple(3, 3, Side.BLACK), Triple(4, 3, Side.WHITE),
            Triple(4, 4, Side.BLACK), Triple(5, 4, Side.WHITE),
            Triple(5, 5, Side.BLACK), Triple(6, 5, Side.WHITE),
            Triple(6, 6, Side.BLACK), Triple(7, 6, Side.WHITE),
        )
        assertEquals(Side.BLACK, gameOverWinner(engine.applyRemoteMove(7, 7, Side.BLACK)))
    }

    @Test
    fun `反对角线贴角五连判胜`() {
        place(
            Triple(4, 4, Side.BLACK), Triple(5, 4, Side.WHITE),
            Triple(5, 3, Side.BLACK), Triple(6, 3, Side.WHITE),
            Triple(6, 2, Side.BLACK), Triple(7, 2, Side.WHITE),
            Triple(7, 1, Side.BLACK), Triple(8, 1, Side.WHITE),
        )
        assertEquals(Side.BLACK, gameOverWinner(engine.applyRemoteMove(8, 0, Side.BLACK)))
    }

    @Test
    fun `边界横向五连判胜`() {
        place(
            Triple(0, 0, Side.BLACK), Triple(0, 1, Side.WHITE),
            Triple(1, 0, Side.BLACK), Triple(1, 1, Side.WHITE),
            Triple(2, 0, Side.BLACK), Triple(2, 1, Side.WHITE),
            Triple(3, 0, Side.BLACK), Triple(3, 1, Side.WHITE),
        )
        assertEquals(Side.BLACK, gameOverWinner(engine.applyRemoteMove(4, 0, Side.BLACK)))
    }

    @Test
    fun `四子连珠不判胜`() {
        place(
            Triple(3, 7, Side.BLACK), Triple(4, 8, Side.WHITE),
            Triple(4, 7, Side.BLACK), Triple(5, 8, Side.WHITE),
            Triple(5, 7, Side.BLACK), Triple(6, 8, Side.WHITE),
        )
        val result = engine.applyRemoteMove(6, 7, Side.BLACK)
        assertNull(gameOverWinner(result))
        assertFalse(result.state.over)
    }

    @Test
    fun `占据已有位置被拒绝`() {
        engine.applyRemoteMove(7, 7, Side.BLACK)
        val result = engine.applyRemoteMove(7, 7, Side.WHITE)
        val illegal = result.events.filterIsInstance<GameEvent.IllegalMove>().first()
        assertEquals(GameEvent.IllegalMove.Reason.OCCUPIED, illegal.reason)
        assertEquals(Side.BLACK, result.state.sideAt(7, 7))
    }

    @Test
    fun `AI模式非轮到方落子被拒绝`() {
        engine.start(GameMode.AI, mySide = Side.BLACK)
        engine.applyMove(7, 7)
        val result = engine.applyMove(7, 8)
        val illegal = result.events.filterIsInstance<GameEvent.IllegalMove>().first()
        assertEquals(GameEvent.IllegalMove.Reason.NOT_YOUR_TURN, illegal.reason)
    }

    @Test
    fun `本地双人模式交替行棋`() {
        val first = engine.applyMove(7, 7)
        assertEquals(Side.WHITE, first.state.active)
        val second = engine.applyMove(7, 8)
        assertEquals(Side.BLACK, second.state.active)
    }

    @Test
    fun `悔棋一子`() {
        place(Triple(3, 7, Side.BLACK), Triple(4, 7, Side.WHITE))
        val result = engine.rollback(1)
        val rollback = result.events.filterIsInstance<GameEvent.RollbackApplied>().first()
        assertEquals(1, rollback.removed.size)
        assertEquals(1, result.state.moves.size)
        assertEquals(Side.WHITE, result.state.active)
        assertNull(result.state.sideAt(4, 7))
    }

    @Test
    fun `悔棋两子`() {
        place(Triple(3, 7, Side.BLACK), Triple(4, 7, Side.WHITE))
        val result = engine.rollback(2)
        assertEquals(0, result.state.moves.size)
        assertEquals(Side.BLACK, result.state.active)
    }

    @Test
    fun `空历史悔棋安全`() {
        val result = engine.rollback(1)
        val rollback = result.events.filterIsInstance<GameEvent.RollbackApplied>().first()
        assertTrue(rollback.removed.isEmpty())
        assertEquals(0, result.state.moves.size)
    }

    @Test
    fun `悔棋解除终局状态`() {
        place(
            Triple(3, 7, Side.WHITE), Triple(4, 7, Side.WHITE),
            Triple(5, 7, Side.WHITE), Triple(6, 7, Side.WHITE),
            Triple(0, 0, Side.BLACK),
        )
        engine.applyRemoteMove(7, 7, Side.WHITE)
        assertTrue(engine.snapshot().over)
        val result = engine.rollback(1)
        assertFalse(result.state.over)
        assertNull(result.state.winner)
    }

    @Test
    fun `重开清盘黑先`() {
        place(Triple(3, 7, Side.BLACK), Triple(4, 7, Side.WHITE))
        val result = engine.restart()
        assertTrue(result.events.contains(GameEvent.Restarted))
        assertEquals(0, result.state.moves.size)
        assertEquals(Side.BLACK, result.state.active)
        assertFalse(result.state.over)
        assertNull(result.state.winner)
    }

    @Test
    fun `快照与引擎内部状态隔离`() {
        val before = engine.snapshot()
        engine.applyRemoteMove(7, 7, Side.BLACK)
        assertNull(before.sideAt(7, 7))
        assertEquals(0, before.moves.size)

        val after = engine.snapshot()
        assertEquals(1, after.moves.size)
        assertTrue(after.moves !== engine.snapshot().moves)
    }

    @Test
    fun `越界落子被拒绝`() {
        val result = engine.applyRemoteMove(15, 0, Side.BLACK)
        val illegal = result.events.filterIsInstance<GameEvent.IllegalMove>().first()
        assertEquals(GameEvent.IllegalMove.Reason.OUT_OF_BOUNDS, illegal.reason)
    }
}
