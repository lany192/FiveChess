package com.github.lany192.gomoku.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 算法清单与档位参数的回归网：
 * 枚举顺序（UI 家族分组依赖）、工厂覆盖、时间预算与各家族规模参数的档位上限。
 */
class AiAlgorithmTest {

    @Test
    fun `25 种算法且同家族连续排列`() {
        assertEquals(25, AiAlgorithm.entries.size)

        // UI 按家族分组展示：同一家族一旦离开就不许再出现
        val families = AiAlgorithm.entries.map { it.family }
        val runs = families.fold(emptyList<AiFamily>()) { acc, family ->
            if (acc.lastOrNull() == family) acc else acc + family
        }
        assertEquals(AiFamily.entries.toList(), runs)

        assertEquals(AiAlgorithm.SHAPE_SCORE, AiAlgorithm.DEFAULT)
    }

    @Test
    fun `工厂覆盖全部算法与档位`() {
        for (algorithm in AiAlgorithm.entries) {
            for (level in Difficulty.entries) {
                val engine = AiEngineFactory.create(algorithm, level, 15, 15, Random(1), { 0L })
                assertNotNull("$algorithm / $level 建不出引擎", engine)
            }
        }
    }

    @Test
    fun `时间预算随档位递增且不超硬上限`() {
        for (algorithm in AiAlgorithm.entries) {
            val budgets = Difficulty.entries.map { timeBudget(algorithm, it) }
            assertNonDecreasing("$algorithm 时间预算", budgets)
            assertTrue(
                "$algorithm 超硬上限：${budgets.max()}",
                budgets.max() <= AiTunings.MAX_TIME_BUDGET_MILLIS,
            )
        }
    }

    @Test
    fun `搜索与威胁深度随档位不减且不超 MAX_PLY`() {
        val searching = AiAlgorithm.entries.filter {
            it.family == AiFamily.SEARCH ||
                (it.family == AiFamily.EVALUATION && it != AiAlgorithm.SHAPE_SCORE)
        }
        for (algorithm in searching) {
            val depths = Difficulty.entries.map { AiTunings.search(algorithm, it).maxDepth }
            assertNonDecreasing("$algorithm 搜索深度", depths)
            assertTrue("$algorithm 深度超 MAX_PLY", depths.max() <= AiTunings.MAX_PLY)
        }
        for (algorithm in listOf(AiAlgorithm.VCF, AiAlgorithm.VCT, AiAlgorithm.KILL_SEARCH)) {
            val depths = Difficulty.entries.map { AiTunings.threat(algorithm, it).depth }
            assertNonDecreasing("$algorithm 威胁深度", depths)
            assertTrue("$algorithm 深度超 MAX_PLY", depths.max() <= AiTunings.MAX_PLY)
        }
    }

    @Test
    fun `模拟次数与家族规模参数随档位不减`() {
        for (algorithm in listOf(AiAlgorithm.MCTS, AiAlgorithm.UCT, AiAlgorithm.RAVE)) {
            val simulations = Difficulty.entries.map { AiTunings.simulation(algorithm, it).simulations }
            assertNonDecreasing("$algorithm 模拟数", simulations)
        }
        for (algorithm in listOf(AiAlgorithm.TD_LEARNING, AiAlgorithm.Q_LEARNING)) {
            val depths = Difficulty.entries.map { AiTunings.learning(algorithm, it).depth }
            assertNonDecreasing("$algorithm 浅搜深度", depths)
        }
        assertNonDecreasing(
            "AlphaZero 模拟数",
            Difficulty.entries.map { AiTunings.learning(AiAlgorithm.ALPHA_ZERO, it).simulations },
        )
        // 探索率反过来：档位越高越少乱走
        assertNonIncreasing(
            "Q 学习探索率",
            Difficulty.entries.map { AiTunings.learning(AiAlgorithm.Q_LEARNING, it).epsilonPercent },
        )
        assertNonDecreasing(
            "蚁群蚂蚁数",
            Difficulty.entries.map { AiTunings.misc(AiAlgorithm.ANT_COLONY, it).ants },
        )
        assertNonDecreasing(
            "退火迭代数",
            Difficulty.entries.map { AiTunings.misc(AiAlgorithm.SIMULATED_ANNEALING, it).iterations },
        )
        assertNonDecreasing(
            "专家系统前瞻宽度",
            Difficulty.entries.map { AiTunings.misc(AiAlgorithm.EXPERT_SYSTEM, it).lookahead },
        )
    }

    /** 经典棋型评分走 RobotAI、不经过 AiTunings，预算取通用档位值 */
    private fun timeBudget(algorithm: AiAlgorithm, level: Difficulty): Long = when {
        algorithm == AiAlgorithm.SHAPE_SCORE -> AiTunings.of(algorithm, level).timeBudgetMillis
        else -> when (algorithm.family) {
            AiFamily.SEARCH, AiFamily.EVALUATION -> AiTunings.search(algorithm, level).timeBudgetMillis
            AiFamily.THREAT -> AiTunings.threat(algorithm, level).timeBudgetMillis
            AiFamily.SIMULATION -> AiTunings.simulation(algorithm, level).timeBudgetMillis
            AiFamily.LEARNING -> AiTunings.learning(algorithm, level).timeBudgetMillis
            AiFamily.MISC -> AiTunings.misc(algorithm, level).timeBudgetMillis
        }
    }

    private fun <T : Comparable<T>> assertNonDecreasing(label: String, values: List<T>) {
        values.zipWithNext().forEach { (low, high) ->
            assertTrue("$label 随档位下降：$values", high >= low)
        }
    }

    private fun assertNonIncreasing(label: String, values: List<Int>) {
        values.zipWithNext().forEach { (high, low) ->
            assertTrue("$label 随档位上升：$values", low <= high)
        }
    }
}
