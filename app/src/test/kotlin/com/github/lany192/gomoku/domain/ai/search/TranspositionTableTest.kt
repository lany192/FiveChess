package com.github.lany192.gomoku.domain.ai.search

import com.github.lany192.gomoku.domain.ai.core.ShapeScores
import com.github.lany192.gomoku.domain.ai.core.Stone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class TranspositionTableTest {

    private val key = 0x1234_5678_9ABC_DEF0L

    @Test
    fun `精确值按深度命中`() {
        val table = TranspositionTable(8)
        table.newSearch()
        table.store(key, depth = 3, value = 123L, flag = TranspositionTable.EXACT)
        assertEquals(123L, table.probe(key, depth = 3, alpha = 0L, beta = 200L))
        assertEquals(123L, table.probe(key, depth = 1, alpha = 0L, beta = 200L))
    }

    @Test
    fun `深度不足不命中`() {
        val table = TranspositionTable(8)
        table.newSearch()
        table.store(key, depth = 2, value = 123L, flag = TranspositionTable.EXACT)
        assertNull(table.probe(key, depth = 4, alpha = 0L, beta = 200L))
    }

    @Test
    fun `下界只在超出 beta 时命中`() {
        val table = TranspositionTable(8)
        table.newSearch()
        table.store(key, depth = 3, value = 50L, flag = TranspositionTable.LOWER)
        assertEquals(50L, table.probe(key, depth = 3, alpha = 0L, beta = 45L))
        assertNull(table.probe(key, depth = 3, alpha = 0L, beta = 60L))
    }

    @Test
    fun `上界只在低于 alpha 时命中`() {
        val table = TranspositionTable(8)
        table.newSearch()
        table.store(key, depth = 3, value = 30L, flag = TranspositionTable.UPPER)
        assertEquals(30L, table.probe(key, depth = 3, alpha = 35L, beta = 60L))
        assertNull(table.probe(key, depth = 3, alpha = 20L, beta = 60L))
    }

    @Test
    fun `同代内不覆盖更深的条目`() {
        val table = TranspositionTable(8)
        table.newSearch()
        table.store(key, depth = 5, value = 100L, flag = TranspositionTable.EXACT)
        table.store(key, depth = 2, value = 200L, flag = TranspositionTable.EXACT)
        assertEquals(100L, table.probe(key, depth = 5, alpha = 0L, beta = 300L))
    }

    @Test
    fun `新代允许浅层覆盖深层`() {
        val table = TranspositionTable(8)
        table.newSearch()
        table.store(key, depth = 5, value = 100L, flag = TranspositionTable.EXACT)
        table.newSearch()
        table.store(key, depth = 2, value = 200L, flag = TranspositionTable.EXACT)
        assertEquals(200L, table.probe(key, depth = 2, alpha = 0L, beta = 300L))
    }

    /** 杀棋分依赖 ply，入表后跨深度复用会产生偏移，必须被拒绝 */
    @Test
    fun `杀棋分不入表`() {
        val table = TranspositionTable(8)
        table.newSearch()
        table.store(key, depth = 5, value = ShapeScores.WIN - 1, flag = TranspositionTable.EXACT)
        table.store(key, depth = 5, value = -(ShapeScores.WIN - 3), flag = TranspositionTable.EXACT)
        assertNull(table.probe(key, depth = 1, alpha = -1L shl 40, beta = 1L shl 40))
        assertEquals(TranspositionTable.NO_MOVE, table.bestMove(key))
    }

    @Test
    fun `杀棋着法仍可作为排序着法保留`() {
        val table = TranspositionTable(8)
        table.newSearch()
        table.storeMove(key, 42)
        assertEquals(42, table.bestMove(key))
        // 同键的普通分值覆盖时不清掉着法
        table.store(key, depth = 3, value = 10L, flag = TranspositionTable.EXACT)
        assertEquals(42, table.bestMove(key))
    }

    @Test
    fun `未命中的键不返回着法`() {
        val table = TranspositionTable(8)
        assertEquals(TranspositionTable.NO_MOVE, table.bestMove(key))
    }
}

class ZobristTest {

    /** 引擎按"落子 xor stoneKey xor sideKey"增量维护，必须与全量重算一致 */
    @Test
    fun `随机八手后增量哈希与全量重算一致`() {
        val zobrist = Zobrist(Random(11), 15, 15)
        val board = Array(15) { IntArray(15) }
        var toMove = Stone.WHITE
        var hash = zobrist.hash(board, toMove)

        val random = Random(22)
        repeat(8) {
            var x: Int
            var y: Int
            do {
                x = random.nextInt(15)
                y = random.nextInt(15)
            } while (board[x][y] != Stone.EMPTY)

            board[x][y] = toMove
            hash = hash xor zobrist.stoneKey(x, y, toMove) xor zobrist.sideKey
            toMove = Stone.opponent(toMove)
            assertEquals("第 ${it + 1} 手后不一致", zobrist.hash(board, toMove), hash)
        }
    }

    @Test
    fun `空盘哈希随走子方变化`() {
        val zobrist = Zobrist(Random(3), 15, 15)
        val board = Array(15) { IntArray(15) }
        assertEquals(zobrist.sideKey, zobrist.hash(board, Stone.WHITE))
        assertEquals(0L, zobrist.hash(board, Stone.BLACK))
    }
}
