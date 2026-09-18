package com.github.lany192.gomoku.domain.ai

import com.github.lany192.gomoku.domain.model.Point
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SearchCoreTest {

    private val presets = listOf(
        AiAlgorithm.MINIMAX,
        AiAlgorithm.ALPHA_BETA,
        AiAlgorithm.PVS,
        AiAlgorithm.MTD_F,
        AiAlgorithm.ITERATIVE_DEEPENING,
        AiAlgorithm.TRANSPOSITION_TABLE,
        AiAlgorithm.KILLER_HISTORY,
    )

    /** clock 固定为 0：关掉时间预算，保证结果可复现 */
    private fun engine(algorithm: AiAlgorithm, level: Difficulty = Difficulty.MEDIUM): GomokuAI =
        AiEngineFactory.create(algorithm, level, 15, 15, Random(7), clock = { 0L })

    @Test
    fun `七个预设都返回合法落点`() {
        val board = AiTestBoards.midGame()
        for (algorithm in presets) {
            val point = engine(algorithm).getPosition(board)
            assertTrue("$algorithm 落点越界 ($point)", point.x in 0 until 15 && point.y in 0 until 15)
            assertEquals("$algorithm 落在已有棋子上 ($point)", 0, board[point.x][point.y])
        }
    }

    @Test
    fun `中等难度七个预设都抓住自己的连五点`() {
        val board = AiTestBoards.aiFour()
        for (algorithm in presets) {
            val point = engine(algorithm).getPosition(board)
            assertTrue("$algorithm 实际落点 ($point)", point == Point(4, 7) || point == Point(9, 7))
        }
    }

    @Test
    fun `中等难度七个预设都封堵对手的连五点`() {
        val board = AiTestBoards.rivalFour()
        for (algorithm in presets) {
            val point = engine(algorithm).getPosition(board)
            assertTrue("$algorithm 实际落点 ($point)", point == Point(4, 7) || point == Point(9, 7))
        }
    }

    @Test
    fun `中等难度七个预设都把活三走成活四`() {
        val board = AiTestBoards.aiLiveThree()
        for (algorithm in presets) {
            val point = engine(algorithm).getPosition(board)
            assertTrue("$algorithm 实际落点 ($point)", point == Point(4, 7) || point == Point(8, 7))
        }
    }

    @Test
    fun `同种子同局面结果确定`() {
        val board = AiTestBoards.midGame()
        for (algorithm in presets) {
            val first = engine(algorithm).getPosition(board)
            val second = engine(algorithm).getPosition(board)
            assertEquals("$algorithm 结果不确定", first, second)
        }
    }

    @Test
    fun `不修改传入棋盘`() {
        val board = AiTestBoards.midGame()
        val before = board.map { it.copyOf() }.toTypedArray()
        for (algorithm in presets) {
            engine(algorithm).getPosition(board)
            for (x in 0 until 15) {
                assertArrayEquals("$algorithm 改动了棋盘第 $x 列", before[x], board[x])
            }
        }
    }

    @Test
    fun `大师难度置换表预设中局冒烟测试`() {
        val board = AiTestBoards.midGame()
        val start = System.nanoTime()
        val point = AiEngineFactory.create(
            AiAlgorithm.TRANSPOSITION_TABLE, Difficulty.MASTER, 15, 15, Random(3),
        ).getPosition(board)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("实际落点 ($point)", board[point.x][point.y] == 0)
        assertTrue("耗时 ${elapsedMs}ms 超出预期", elapsedMs < 10_000)
    }

    @Test
    fun `大师难度七个预设都遵守时间预算`() {
        val board = AiTestBoards.midGame()
        for (algorithm in presets) {
            val start = System.nanoTime()
            val point = AiEngineFactory.create(
                algorithm, Difficulty.MASTER, 15, 15, Random(5),
            ).getPosition(board)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertTrue("$algorithm 落点不合法 ($point)", board[point.x][point.y] == 0)
            assertTrue("$algorithm 耗时 ${elapsedMs}ms 超出预期", elapsedMs < 4_000)
        }
    }

    @Test
    fun `迷你棋盘也返回合法落点`() {
        val board = Array(5) { IntArray(5) }
        board[2][2] = 1
        for (algorithm in presets) {
            val point = AiEngineFactory.create(
                algorithm, Difficulty.MEDIUM, 5, 5, Random(7), clock = { 0L },
            ).getPosition(board)
            assertTrue("$algorithm 落点越界 ($point)", point.x in 0 until 5 && point.y in 0 until 5)
            assertEquals("$algorithm 落在已有棋子上 ($point)", 0, board[point.x][point.y])
        }
    }
}
