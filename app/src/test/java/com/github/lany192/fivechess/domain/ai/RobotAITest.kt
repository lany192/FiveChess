package com.github.lany192.fivechess.domain.ai

import com.github.lany192.fivechess.domain.model.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RobotAITest {

    private fun emptyBoard(width: Int = 15, height: Int = 15) = Array(width) { IntArray(height) }

    @Test
    fun `AI有成五点时直接取胜`() {
        val board = emptyBoard()
        // AI(白=2) 横向四子，两端 (4,7)/(9,7) 空
        for (x in 5..8) board[x][7] = 2
        val point = RobotAI(15, 15, Difficulty.MEDIUM).getPosition(board)
        assertTrue("实际落点 ($point)", point == Point(4, 7) || point == Point(9, 7))
    }

    @Test
    fun `对手有成五点时必须封堵`() {
        val board = emptyBoard()
        // 黑(1) 横向四子，两端 (4,7)/(9,7) 空
        for (x in 5..8) board[x][7] = 1
        val point = RobotAI(15, 15, Difficulty.MEDIUM).getPosition(board)
        assertTrue("实际落点 ($point)", point == Point(4, 7) || point == Point(9, 7))
    }

    @Test
    fun `中等难度把活三走成活四`() {
        val board = emptyBoard()
        // AI 活三 (5,7)-(7,7)，两端开放
        for (x in 5..7) board[x][7] = 2
        board[10][10] = 1
        val point = RobotAI(15, 15, Difficulty.MEDIUM).getPosition(board)
        assertTrue("实际落点 ($point)", point == Point(4, 7) || point == Point(8, 7))
    }

    @Test
    fun `困难难度把活三走成活四`() {
        val board = emptyBoard()
        for (x in 5..7) board[x][7] = 2
        board[10][10] = 1
        val point = RobotAI(15, 15, Difficulty.HARD).getPosition(board)
        assertTrue("实际落点 ($point)", point == Point(4, 7) || point == Point(8, 7))
    }

    @Test
    fun `简单难度优先防守成五点`() {
        val board = emptyBoard()
        for (x in 5..8) board[x][7] = 1
        val point = RobotAI(15, 15, Difficulty.EASY).getPosition(board)
        assertTrue("实际落点 ($point)", point == Point(4, 7) || point == Point(9, 7))
    }

    @Test
    fun `同种子同局面结果确定`() {
        val board = emptyBoard()
        board[7][7] = 1
        board[8][8] = 2
        board[7][8] = 1
        val first = RobotAI(15, 15, Difficulty.EASY, Random(42)).getPosition(board)
        val second = RobotAI(15, 15, Difficulty.EASY, Random(42)).getPosition(board)
        assertEquals(first, second)
    }

    @Test
    fun `空棋盘返回合法落点`() {
        val point = RobotAI(15, 15, Difficulty.HARD).getPosition(emptyBoard())
        assertTrue(point.x in 0 until 15 && point.y in 0 until 15)
    }

    @Test
    fun `困难深度4中局冒烟测试`() {
        val board = emptyBoard()
        // 构造一个约12子的中局局面
        val stones = listOf(
            7 to 7 to 1, 8 to 8 to 2, 7 to 8 to 1, 6 to 8 to 2, 8 to 7 to 1, 9 to 7 to 2,
            6 to 6 to 1, 5 to 5 to 2, 8 to 6 to 1, 9 to 5 to 2, 5 to 8 to 1, 4 to 9 to 2,
        )
        stones.forEach { (pos, side) -> board[pos.first][pos.second] = side }

        val start = System.nanoTime()
        val point = RobotAI(15, 15, Difficulty.HARD).getPosition(board)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue("实际落点 ($point)", board[point.x][point.y] == 0)
        assertTrue("耗时 ${elapsedMs}ms 超出预期", elapsedMs < 10_000)
    }

    @Test
    fun `不修改传入棋盘`() {
        val board = emptyBoard()
        board[7][7] = 1
        RobotAI(15, 15, Difficulty.HARD).getPosition(board)
        assertEquals(1, board[7][7])
        var occupied = 0
        board.forEach { row -> row.forEach { if (it != 0) occupied++ } }
        assertEquals(1, occupied)
    }
}
