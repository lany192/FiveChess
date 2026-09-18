package com.github.lany192.gomoku.domain.ai

import com.github.lany192.gomoku.domain.ai.core.BoardScanner
import com.github.lany192.gomoku.domain.ai.core.LinearEvaluator
import com.github.lany192.gomoku.domain.ai.core.Stone
import com.github.lany192.gomoku.domain.ai.learn.AlphaZeroEngine
import com.github.lany192.gomoku.domain.ai.learn.AzNetwork
import com.github.lany192.gomoku.domain.ai.learn.GeneticEngine
import com.github.lany192.gomoku.domain.ai.learn.QLearningEngine
import com.github.lany192.gomoku.domain.ai.learn.TdEngine
import com.github.lany192.gomoku.domain.ai.learn.WeightsCodec
import com.github.lany192.gomoku.domain.model.Point
import com.github.lany192.gomoku.domain.model.Side
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class LearningEngineTest {

    private val scanner = BoardScanner(15, 15)

    private val algorithms = listOf(
        AiAlgorithm.TD_LEARNING,
        AiAlgorithm.Q_LEARNING,
        AiAlgorithm.ALPHA_ZERO,
        AiAlgorithm.GENETIC,
    )

    /** clock 固定为 0：关掉时间预算，结果可复现 */
    private fun engine(algorithm: AiAlgorithm, seed: Int = 7): GomokuAI =
        AiEngineFactory.create(algorithm, Difficulty.MEDIUM, 15, 15, Random(seed), clock = { 0L })

    private fun td(store: AiWeightStore? = null, seed: Int = 7): TdEngine = AiEngineFactory.create(
        AiAlgorithm.TD_LEARNING, Difficulty.MEDIUM, 15, 15, Random(seed), clock = { 0L }, weightStore = store,
    ) as TdEngine

    private fun q(seed: Int = 7): QLearningEngine = AiEngineFactory.create(
        AiAlgorithm.Q_LEARNING, Difficulty.MEDIUM, 15, 15, Random(seed), clock = { 0L },
    ) as QLearningEngine

    private fun az(seed: Int = 7): AlphaZeroEngine = AiEngineFactory.create(
        AiAlgorithm.ALPHA_ZERO, Difficulty.MEDIUM, 15, 15, Random(seed), clock = { 0L },
    ) as AlphaZeroEngine

    private fun genetic(seed: Int = 7): GeneticEngine = AiEngineFactory.create(
        AiAlgorithm.GENETIC, Difficulty.MEDIUM, 15, 15, Random(seed), clock = { 0L },
    ) as GeneticEngine

    @Test
    fun `四个学习类算法都返回合法落点且不改入参`() {
        val board = AiTestBoards.midGame()
        val before = board.map { it.copyOf() }.toTypedArray()
        for (algorithm in algorithms) {
            val point = engine(algorithm).getPosition(board)
            assertTrue("$algorithm 落点越界 ($point)", point.x in 0 until 15 && point.y in 0 until 15)
            assertEquals("$algorithm 落在已有棋子上 ($point)", Stone.EMPTY, board[point.x][point.y])
            assertTrue("$algorithm 修改了传入棋盘", board.contentDeepEquals(before))
        }
    }

    @Test
    fun `学习类引擎都抓住自己的连五点`() {
        val board = AiTestBoards.aiFour()
        for (algorithm in algorithms) {
            val point = engine(algorithm).getPosition(board)
            assertTrue("$algorithm 实际落点 ($point)", point == Point(4, 7) || point == Point(9, 7))
        }
    }

    @Test
    fun `TD 估值朝终局目标单调变化`() {
        val board = AiTestBoards.midGame()
        val white = td(seed = 3)
        white.getPosition(board)
        white.getPosition(board)
        val beforeWin = white.valueOf(board)
        white.onGameOver(GameOutcome(Side.WHITE, emptyList()))
        assertTrue("白胜后估值未上升：$beforeWin -> ${white.valueOf(board)}", white.valueOf(board) > beforeWin)

        val black = td(seed = 11)
        black.getPosition(board)
        black.getPosition(board)
        val beforeLoss = black.valueOf(board)
        black.onGameOver(GameOutcome(Side.BLACK, emptyList()))
        assertTrue("黑胜后估值未下降：$beforeLoss -> ${black.valueOf(board)}", black.valueOf(board) < beforeLoss)
    }

    @Test
    fun `Q 学习连续更新后权重有限`() {
        val board = AiTestBoards.midGame()
        val engine = q()
        repeat(6) { engine.getPosition(board) }
        engine.onGameOver(GameOutcome(Side.WHITE, emptyList()))
        engine.getPosition(board)
        engine.onGameOver(GameOutcome(null, emptyList()))
        val weights = engine.weightsSnapshot()
        assertEquals(LinearEvaluator.FEATURE_SIZE, weights.size)
        for ((i, w) in weights.withIndex()) {
            assertTrue("第 $i 维权重异常：$w", w.isFinite())
        }
        assertTrue("估值异常：${engine.valueOf(board)}", engine.valueOf(board).toDouble().isFinite())
    }

    @Test
    fun `AlphaZero 对局有界且网络权重有限`() {
        val board = AiTestBoards.empty()
        val opponent = engine(AiAlgorithm.GREEDY, seed = 21)
        val self = az(seed = 5)
        var toMove = Stone.BLACK
        var winner = Stone.EMPTY
        var plies = 0
        while (plies < MAX_PLIES && winner == Stone.EMPTY) {
            val point = if (toMove == Stone.BLACK) opponent.getPosition(board) else self.getPosition(board)
            board[point.x][point.y] = toMove
            if (scanner.isFiveAt(board, point.x, point.y, toMove)) winner = toMove
            toMove = Stone.opponent(toMove)
            plies++
        }
        self.onGameOver(GameOutcome(Side.fromCode(winner), emptyList()))
        val weights = self.weightsSnapshot()
        assertEquals(AzNetwork.SIZE, weights.size)
        for ((i, w) in weights.withIndex()) {
            assertTrue("第 $i 维网络权重异常：$w", w.isFinite() && abs(w) < 10.0)
        }
        assertTrue("对局未在 $MAX_PLIES 手内收口", plies <= MAX_PLIES)
    }

    @Test
    fun `遗传算法一代有界且精英不减`() {
        val board = AiTestBoards.midGame()
        val engine = genetic(seed = 5)
        val point = engine.getPosition(board)
        assertEquals("$point 不是合法落点", Stone.EMPTY, board[point.x][point.y])

        assertEquals("clock=0 时应恰好完成一代", 1, engine.generationsDone())
        val fitness = engine.lastGenerationFitness()
        assertTrue("适应度数组为空", fitness.isNotEmpty())
        for ((i, f) in fitness.withIndex()) {
            assertTrue("第 $i 个个体适应度越界：$f", f in 0.0..2.0)
        }
        var best = fitness[0]
        for (f in fitness) if (f > best) best = f
        assertTrue("冠军适应度 ${best} 低于精英 ${fitness[0]}", best >= fitness[0])

        val genome = engine.championGenome()
        val bounds = engine.geneBounds()
        assertEquals(GeneticEngine.GENOME_SIZE, genome.size)
        for (i in genome.indices) {
            assertTrue("第 $i 维基因组异常：${genome[i]}", genome[i].isFinite())
            assertTrue("第 $i 维基因组越界：${genome[i]} > ${bounds[i]}", abs(genome[i]) <= bounds[i])
        }

        engine.getPosition(board)
        assertEquals(2, engine.generationsDone())
    }

    @Test
    fun `权重编解码往返`() {
        val weights = doubleArrayOf(0.0, -1.5, 12345.678, -0.125, 1e9)
        val bytes = WeightsCodec.encode(weights)
        assertEquals(weights.size * 8, bytes.size)
        val decoded = WeightsCodec.decode(bytes)
        assertNotNull(decoded)
        assertArrayEquals(weights, decoded!!, 0.0)

        val engineWeights = az().weightsSnapshot()
        assertArrayEquals(engineWeights, WeightsCodec.decode(WeightsCodec.encode(engineWeights))!!, 0.0)

        assertNull("空数组应视为损坏", WeightsCodec.decode(ByteArray(0)))
        assertNull("非 8 字节倍数应视为损坏", WeightsCodec.decode(ByteArray(5)))
    }

    @Test
    fun `权重落库后同一 store 的新引擎能读回`() {
        val store = FakeWeightStore()
        val board = AiTestBoards.midGame()
        val first = td(store)
        first.getPosition(board)
        first.getPosition(board)
        val saved = store.saved[AiAlgorithm.TD_LEARNING.name]
        assertNotNull("权重未落库", saved)
        val decoded = WeightsCodec.decode(saved!!)
        assertNotNull("落库数据无法解码", decoded)
        assertEquals(LinearEvaluator.FEATURE_SIZE, decoded!!.size)

        val second = td(store)
        second.getPosition(board)
        assertEquals("读回的权重与落库时不一致", first.valueOf(board), second.valueOf(board))
    }

    private class FakeWeightStore : AiWeightStore {
        val saved = HashMap<String, ByteArray>()

        override fun load(modelId: String): DoubleArray? = saved[modelId]?.let { WeightsCodec.decode(it) }

        override fun save(modelId: String, weights: DoubleArray) {
            saved[modelId] = WeightsCodec.encode(weights)
        }
    }

    private companion object {
        const val MAX_PLIES = 60
    }
}
