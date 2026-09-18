package com.github.lany192.gomoku.domain.ai

import com.github.lany192.gomoku.domain.ai.core.BoardScanner
import com.github.lany192.gomoku.domain.ai.core.CandidateGenerator
import com.github.lany192.gomoku.domain.ai.core.Stone
import com.github.lany192.gomoku.domain.ai.misc.ExpertSystemEngine
import com.github.lany192.gomoku.domain.model.Point
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MiscEngineTest {

    private val presets = listOf(
        AiAlgorithm.GREEDY,
        AiAlgorithm.FUZZY,
        AiAlgorithm.EXPERT_SYSTEM,
        AiAlgorithm.ANT_COLONY,
        AiAlgorithm.SIMULATED_ANNEALING,
    )

    /** clock 固定为 0：关掉时间预算，保证结果可复现 */
    private fun engine(algorithm: AiAlgorithm, level: Difficulty = Difficulty.MEDIUM): GomokuAI =
        AiEngineFactory.create(algorithm, level, 15, 15, Random(9), clock = { 0L })

    @Test
    fun `五个杂项引擎都返回合法落点`() {
        val board = AiTestBoards.midGame()
        for (algorithm in presets) {
            val point = engine(algorithm).getPosition(board)
            assertTrue("$algorithm 落点越界 ($point)", point.x in 0 until 15 && point.y in 0 until 15)
            assertEquals("$algorithm 落在已有棋子上 ($point)", 0, board[point.x][point.y])
        }
    }

    @Test
    fun `中等难度五个杂项引擎都抓住自己的连五点`() {
        val board = AiTestBoards.aiFour()
        for (algorithm in presets) {
            val point = engine(algorithm).getPosition(board)
            assertTrue("$algorithm 实际落点 ($point)", point == Point(4, 7) || point == Point(9, 7))
        }
    }

    @Test
    fun `中等难度五个杂项引擎都封堵对手的连五点`() {
        val board = AiTestBoards.rivalFour()
        for (algorithm in presets) {
            val point = engine(algorithm).getPosition(board)
            assertTrue("$algorithm 实际落点 ($point)", point == Point(4, 7) || point == Point(9, 7))
        }
    }

    @Test
    fun `同种子同局面结果确定`() {
        val board = AiTestBoards.midGame()
        for (algorithm in presets) {
            assertEquals("$algorithm 结果不确定", engine(algorithm).getPosition(board), engine(algorithm).getPosition(board))
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
    fun `迷你棋盘也返回合法落点`() {
        val board = Array(5) { IntArray(5) }
        board[2][2] = 1
        for (algorithm in presets) {
            val point = AiEngineFactory.create(
                algorithm, Difficulty.MEDIUM, 5, 5, Random(3), clock = { 0L },
            ).getPosition(board)
            assertTrue("$algorithm 落点越界 ($point)", point.x in 0 until 5 && point.y in 0 until 5)
            assertEquals("$algorithm 落在已有棋子上 ($point)", 0, board[point.x][point.y])
        }
    }

    @Test
    fun `大师难度五个杂项引擎都遵守时间预算`() {
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
    fun `专家系统规则链按优先级命中`() {
        val expert = AiEngineFactory.create(
            AiAlgorithm.EXPERT_SYSTEM, Difficulty.MEDIUM, 15, 15, Random(1), clock = { 0L },
        ) as ExpertSystemEngine
        val tuning = AiTunings.of(AiAlgorithm.EXPERT_SYSTEM, Difficulty.MEDIUM)
        val options = AiTunings.misc(AiAlgorithm.EXPERT_SYSTEM, Difficulty.MEDIUM)

        fun fire(board: Array<IntArray>): ExpertSystemEngine.RuleHit? {
            val copy = board.map { it.copyOf() }.toTypedArray()
            val generator = CandidateGenerator(15, 15, BoardScanner(15, 15))
            val candidates = generator.generate(copy, tuning.breadth, tuning.radius, Stone.WHITE, Stone.BLACK)
            return expert.select(copy, candidates, options.lookahead, options.radius)
        }

        assertEquals(ExpertSystemEngine.RULE_SELF_FIVE, fire(AiTestBoards.aiFour())?.ruleId)
        assertEquals(ExpertSystemEngine.RULE_BLOCK_FIVE, fire(AiTestBoards.rivalFour())?.ruleId)
        assertEquals(ExpertSystemEngine.RULE_SELF_LIVE_FOUR, fire(AiTestBoards.aiLiveThree())?.ruleId)

        // 白 (5,7)(6,7)+(7,5)(7,6) 在 (7,7) 形成双活三；黑 (1,1)(1,2) 同时有活三点 (1,3)
        val counterBoard = AiTestBoards.board(
            (5 to 7) to 2, (6 to 7) to 2, (7 to 5) to 2, (7 to 6) to 2,
            (1 to 1) to 1, (1 to 2) to 1,
        )
        val counter = fire(counterBoard)
        assertEquals(ExpertSystemEngine.RULE_COUNTER_DOUBLE_THREE, counter?.ruleId)
        assertEquals(Point(7, 7), counter?.point)

        // 角上的黑子凑不出活三：同样的白双三改为主动造
        val makeBoard = AiTestBoards.board(
            (5 to 7) to 2, (6 to 7) to 2, (7 to 5) to 2, (7 to 6) to 2,
            (0 to 0) to 1, (0 to 1) to 1,
        )
        val make = fire(makeBoard)
        assertEquals(ExpertSystemEngine.RULE_MAKE_DOUBLE_THREE, make?.ruleId)
        assertEquals(Point(7, 7), make?.point)

        // 黑 (5,7)(6,7) 有活三点；白只有远端的二连子，堵活三是首选
        val blockBoard = AiTestBoards.board(
            (5 to 7) to 1, (6 to 7) to 1, (5 to 0) to 2, (6 to 0) to 2,
        )
        val block = fire(blockBoard)
        assertEquals(ExpertSystemEngine.RULE_BLOCK_LIVE_THREE, block?.ruleId)
        assertTrue("堵活三点 ($block)", block?.point == Point(4, 7) || block?.point == Point(7, 7))

        // 互不相干的零星局面：退到一层前瞻
        val quiet = AiTestBoards.board((7 to 7) to 2, (3 to 3) to 1)
        assertEquals(ExpertSystemEngine.RULE_ONE_PLY_LOOKAHEAD, fire(quiet)?.ruleId)
    }

    @Test
    fun `蚁群信息素跨手累积后仍能给出合法点`() {
        val board = AiTestBoards.midGame()
        val colony = AiEngineFactory.create(
            AiAlgorithm.ANT_COLONY, Difficulty.HARD, 15, 15, Random(21), clock = { 0L },
        )
        // 同一局面连问多手：信息素蒸发/沉积多轮后选择逻辑不许退化（NaN/越界都会在这里暴露）
        repeat(3) {
            val point = colony.getPosition(board)
            assertTrue("落点越界 ($point)", point.x in 0 until 15 && point.y in 0 until 15)
            assertEquals("落在已有棋子上 ($point)", 0, board[point.x][point.y])
        }
        colony.onGameReset()
        val afterReset = colony.getPosition(board)
        assertTrue("重置后落点越界 ($afterReset)", afterReset.x in 0 until 15 && afterReset.y in 0 until 15)
    }
}
