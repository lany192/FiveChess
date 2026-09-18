package com.github.lany192.gomoku.domain.ai

import com.github.lany192.gomoku.domain.ai.core.Evaluator
import com.github.lany192.gomoku.domain.ai.core.NeuralEvaluator
import com.github.lany192.gomoku.domain.ai.core.PatternTableEvaluator
import com.github.lany192.gomoku.domain.ai.core.ShapeEvaluator
import com.github.lany192.gomoku.domain.ai.core.ShapeScores
import com.github.lany192.gomoku.domain.ai.core.Stone
import com.github.lany192.gomoku.domain.model.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EvaluatorTest {

    private val evaluators: List<Evaluator> = listOf(
        ShapeEvaluator(15, 15),
        PatternTableEvaluator(15, 15),
        NeuralEvaluator(15, 15),
    )

    private fun name(e: Evaluator) = e::class.simpleName ?: "?"

    /** 白（5,7)-(9,7) 活四：(4,7)/(10,7) 都是活四的开放端 */
    private fun liveFour(): Array<IntArray> = AiTestBoards.board(
        (5 to 7) to 2, (6 to 7) to 2, (7 to 7) to 2, (8 to 7) to 2,
    )

    private fun liveThree(): Array<IntArray> = AiTestBoards.board(
        (5 to 7) to 2, (6 to 7) to 2, (7 to 7) to 2,
    )

    private fun five(): Array<IntArray> = AiTestBoards.board(
        (4 to 7) to 2, (5 to 7) to 2, (6 to 7) to 2, (7 to 7) to 2, (8 to 7) to 2,
    )

    @Test
    fun `三个评估器量级次序都是 五 大于 活四 大于 活三`() {
        for (e in evaluators) {
            val f = e.evaluate(five(), Stone.WHITE)
            val l4 = e.evaluate(liveFour(), Stone.WHITE)
            val l3 = e.evaluate(liveThree(), Stone.WHITE)
            assertTrue("${name(e)}：五($f) 应大于 活四($l4)", f > l4)
            assertTrue("${name(e)}：活四($l4) 应大于 活三($l3)", l4 > l3)
            assertTrue("${name(e)}：活三($l3) 应为正", l3 > 0L)
        }
    }

    @Test
    fun `颜色互换对称——同一局面换个视角得到相同的评估值`() {
        // 同一组形状分别用白/黑摆放，评估值必须一致（评估器只能依赖"己方/对方"角色）
        val white = AiTestBoards.board(
            (5 to 7) to 2, (6 to 7) to 2, (7 to 7) to 2, (9 to 8) to 2, (4 to 9) to 2,
        )
        val black = AiTestBoards.board(
            (5 to 7) to 1, (6 to 7) to 1, (7 to 7) to 1, (9 to 8) to 1, (4 to 9) to 1,
        )
        for (e in evaluators) {
            assertEquals(name(e), e.evaluate(white, Stone.WHITE), e.evaluate(black, Stone.BLACK))
        }
    }

    @Test
    fun `对手棋型按敌方视角为负——只有对手有子时评估为负`() {
        val rivalOnly = AiTestBoards.board(
            (5 to 7) to 1, (6 to 7) to 1, (7 to 7) to 1,
        )
        for (e in evaluators) {
            assertTrue("${name(e)} 应对对手棋型为负", e.evaluate(rivalOnly, Stone.WHITE) < 0L)
        }
    }

    @Test
    fun `神经网络评估前向确定且输出裁剪在安全范围内`() {
        val board = AiTestBoards.midGame()
        val net = NeuralEvaluator(15, 15)
        val first = net.evaluate(board, Stone.WHITE)
        assertEquals("前向应确定", first, NeuralEvaluator(15, 15).evaluate(board, Stone.WHITE))

        // 极端局面（满盘己方/对方）也不得接近终局分，更不能越界
        val limit = ShapeScores.WIN / 2
        val full = AiTestBoards.empty()
        for (x in 0 until 15) {
            for (y in 0 until 15) full[x][y] = if ((x + y) % 2 == 0) Stone.WHITE else Stone.BLACK
        }
        for (board in listOf(full, allOf(Stone.WHITE), allOf(Stone.BLACK))) {
            val value = net.evaluate(board, Stone.WHITE)
            assertTrue("输出 $value 超出 ±$limit", value in (-limit + 1)..(limit - 1))
        }
    }

    private fun allOf(color: Int): Array<IntArray> {
        val board = AiTestBoards.empty()
        for (x in 0 until 15) {
            for (y in 0 until 15) board[x][y] = color
        }
        return board
    }

    @Test
    fun `模式表评估在小棋盘上不越界`() {
        // 5 宽棋盘上任何五格窗都顶到边界，没有开端可言，分值恒为 0——这里只验证索引不越界
        val tiny = Array(5) { IntArray(5) }
        tiny[1][1] = 2
        tiny[2][2] = 2
        assertEquals(0L, PatternTableEvaluator(5, 5).evaluate(tiny, Stone.WHITE))

        // 9×9 起窗口两侧有空格，棋型能正常得分
        val small = Array(9) { IntArray(9) }
        small[3][4] = 2
        small[4][4] = 2
        assertTrue(
            "9×9 棋盘应能给出正分",
            PatternTableEvaluator(9, 9).evaluate(small, Stone.WHITE) > 0L,
        )
    }

    @Test
    fun `两个评估类算法通过工厂也能走战术必走点`() {
        for (algorithm in listOf(AiAlgorithm.PATTERN_TABLE, AiAlgorithm.NEURAL_EVAL)) {
            val engine = AiEngineFactory.create(algorithm, Difficulty.MEDIUM, 15, 15, Random(4), clock = { 0L })
            val board = AiTestBoards.midGame()
            val point = engine.getPosition(board)
            assertTrue("$algorithm 落点越界 ($point)", point.x in 0 until 15 && point.y in 0 until 15)
            assertEquals("$algorithm 落在已有棋子上 ($point)", 0, board[point.x][point.y])

            val four = AiTestBoards.aiFour()
            val win = engine.getPosition(four)
            assertTrue("$algorithm 未抓住连五点 ($win)", win == Point(4, 7) || win == Point(9, 7))
        }
    }
}
