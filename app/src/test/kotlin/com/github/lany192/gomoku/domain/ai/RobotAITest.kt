package com.github.lany192.gomoku.domain.ai

import com.github.lany192.gomoku.domain.model.Point
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
    fun `大师难度把活三走成活四`() {
        val board = emptyBoard()
        for (x in 5..7) board[x][7] = 2
        board[10][10] = 1
        val point = RobotAI(15, 15, Difficulty.MASTER).getPosition(board)
        assertTrue("实际落点 ($point)", point == Point(4, 7) || point == Point(8, 7))
    }

    @Test
    fun `中等以上难度必堵对手连五`() {
        val board = emptyBoard()
        for (x in 5..8) board[x][7] = 1
        for (level in listOf(Difficulty.MEDIUM, Difficulty.HARD, Difficulty.MASTER)) {
            val point = RobotAI(15, 15, level).getPosition(board)
            assertTrue("$level 实际落点 ($point)", point == Point(4, 7) || point == Point(9, 7))
        }
    }

    @Test
    fun `低难度会漏堵连五且入门比简单更常漏`() {
        val board = emptyBoard()
        for (x in 5..8) board[x][7] = 1
        val novice = blockTimes(board, Difficulty.NOVICE, 100)
        val easy = blockTimes(board, Difficulty.EASY, 100)
        assertTrue("入门封堵 $novice/100 次，未体现难度", novice in 1..99)
        assertTrue("简单封堵 $easy/100 次，未体现难度", easy in 1..99)
        assertTrue("入门($novice) 应比简单($easy) 更常漏堵", novice < easy)
    }

    @Test
    fun `入门难度会放过自己的连五`() {
        val board = emptyBoard()
        for (x in 5..8) board[x][7] = 2
        var missed = 0
        repeat(100) { i ->
            val point = RobotAI(15, 15, Difficulty.NOVICE, Random(2000 + i)).getPosition(board)
            if (point != Point(4, 7) && point != Point(9, 7)) missed++
        }
        assertTrue("100 次全部取胜，未体现入门难度", missed > 0)
    }

    /** 同一局面重复 n 次，统计封堵对方连五的次数 */
    private fun blockTimes(board: Array<IntArray>, level: Difficulty, n: Int): Int {
        var blocks = 0
        repeat(n) { i ->
            val point = RobotAI(15, 15, level, Random(100 + i)).getPosition(board)
            if (point == Point(4, 7) || point == Point(9, 7)) blocks++
        }
        return blocks
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

    /** 约12子的中局局面 */
    private fun midGameBoard(): Array<IntArray> {
        val board = emptyBoard()
        val stones = listOf(
            7 to 7 to 1, 8 to 8 to 2, 7 to 8 to 1, 6 to 8 to 2, 8 to 7 to 1, 9 to 7 to 2,
            6 to 6 to 1, 5 to 5 to 2, 8 to 6 to 1, 9 to 5 to 2, 5 to 8 to 1, 4 to 9 to 2,
        )
        stones.forEach { (pos, side) -> board[pos.first][pos.second] = side }
        return board
    }

    @Test
    fun `困难深度4中局冒烟测试`() {
        val board = midGameBoard()

        val start = System.nanoTime()
        val point = RobotAI(15, 15, Difficulty.HARD).getPosition(board)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue("实际落点 ($point)", board[point.x][point.y] == 0)
        assertTrue("耗时 ${elapsedMs}ms 超出预期", elapsedMs < 10_000)
    }

    @Test
    fun `大师深度6中局冒烟测试`() {
        val board = midGameBoard()

        val start = System.nanoTime()
        val point = RobotAI(15, 15, Difficulty.MASTER).getPosition(board)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue("实际落点 ($point)", board[point.x][point.y] == 0)
        assertTrue("耗时 ${elapsedMs}ms 超出预期", elapsedMs < 5_000)
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
