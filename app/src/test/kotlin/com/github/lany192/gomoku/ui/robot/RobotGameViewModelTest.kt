package com.github.lany192.gomoku.ui.robot

import com.github.lany192.gomoku.data.settings.AiAlgorithmStore
import com.github.lany192.gomoku.data.settings.AiLevelStore
import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.AiEnginePool
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.ai.GameOutcome
import com.github.lany192.gomoku.domain.ai.GomokuAI
import com.github.lany192.gomoku.domain.model.Point
import com.github.lany192.gomoku.domain.model.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch

/**
 * 人机对局的算法切换与学习钩子回归。
 *
 * AI 的实际计算跑在注入的真实调度器上（与生产一致），因此断言状态统一走 [awaitTrue] 轮询：
 * 测试调度器只负责 Intent 串行处理与主线程回调，后台线程的结果会异步入队。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RobotGameViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test(timeout = 10_000)
    fun `启动时读取已存的算法与档位`() = runTest(dispatcher) {
        val factory = FakeFactory()
        val vm = RobotGameViewModel(
            enginePool = factory.pool,
            levelStore = FakeLevelStore(Difficulty.HARD),
            algorithmStore = FakeAlgorithmStore(AiAlgorithm.MCTS),
            aiDispatcher = Dispatchers.Default,
        )

        assertEquals(AiAlgorithm.MCTS, vm.state.value.aiAlgorithm)
        assertEquals(Difficulty.HARD, vm.state.value.aiLevel)
        assertEquals(listOf(AiAlgorithm.MCTS), factory.created)
    }

    @Test(timeout = 10_000)
    fun `切换算法落库并由新引擎接手落子`() = runTest(dispatcher) {
        val factory = FakeFactory()
        val algorithmStore = FakeAlgorithmStore()
        val vm = RobotGameViewModel(
            enginePool = factory.pool,
            levelStore = FakeLevelStore(),
            algorithmStore = algorithmStore,
            aiDispatcher = Dispatchers.Default,
        )

        vm.dispatch(RobotGameIntent.AlgorithmSelected(AiAlgorithm.EXPERT_SYSTEM))
        advanceUntilIdle()

        assertEquals(AiAlgorithm.EXPERT_SYSTEM, vm.state.value.aiAlgorithm)
        assertEquals(AiAlgorithm.EXPERT_SYSTEM, algorithmStore.read())

        vm.dispatch(RobotGameIntent.BoardTap(7, 7))
        awaitTrue { vm.state.value.board.cells[0][0] == Side.WHITE }

        assertEquals(Side.BLACK, vm.state.value.board.cells[7][7])
        assertTrue("新引擎未参与落子", factory[AiAlgorithm.EXPERT_SYSTEM].moves >= 1)
    }

    @Test(timeout = 10_000)
    fun `切换难度落库并传给当前引擎`() = runTest(dispatcher) {
        val factory = FakeFactory()
        val levelStore = FakeLevelStore()
        val vm = RobotGameViewModel(
            enginePool = factory.pool,
            levelStore = levelStore,
            algorithmStore = FakeAlgorithmStore(),
            aiDispatcher = Dispatchers.Default,
        )

        vm.dispatch(RobotGameIntent.LevelSelected(Difficulty.MASTER))
        advanceUntilIdle()

        assertEquals(Difficulty.MASTER, levelStore.read())
        assertEquals(Difficulty.MASTER, vm.state.value.aiLevel)
        assertEquals(Difficulty.MASTER, factory[AiAlgorithm.DEFAULT].level)
    }

    @Test(timeout = 10_000)
    fun `思考中切换算法延后到落子后再换引擎`() = runTest(dispatcher) {
        val gate = CountDownLatch(1)
        val factory = FakeFactory(gateAlgorithm = AiAlgorithm.DEFAULT, gate = gate)
        val vm = RobotGameViewModel(
            enginePool = factory.pool,
            levelStore = FakeLevelStore(),
            algorithmStore = FakeAlgorithmStore(),
            aiDispatcher = Dispatchers.Default,
        )

        // 人类落子 → 旧引擎进入思考（卡在闸门上）
        vm.dispatch(RobotGameIntent.BoardTap(7, 7))
        awaitTrue { factory[AiAlgorithm.DEFAULT].moves == 1 }

        // 思考中悔棋 + 换算法：状态立刻改，但引擎要等这一次落子落地
        vm.dispatch(RobotGameIntent.RollbackClicked)
        vm.dispatch(RobotGameIntent.AlgorithmSelected(AiAlgorithm.NEURAL_EVAL))
        advanceUntilIdle()

        assertEquals(AiAlgorithm.NEURAL_EVAL, vm.state.value.aiAlgorithm)
        assertFalse("思考中不应换引擎", factory.created.contains(AiAlgorithm.NEURAL_EVAL))

        gate.countDown()
        awaitTrue { factory.created.contains(AiAlgorithm.NEURAL_EVAL) }

        // 旧引擎这一子落地后，pendingRollback 一并回退两子：棋盘清空且只清一次
        awaitTrue {
            vm.state.value.board.cells[7][7] == null && vm.state.value.board.cells[0][0] == null
        }
        assertEquals(1, factory.fakeResets())

        // 下一手由新引擎接手
        vm.dispatch(RobotGameIntent.BoardTap(7, 7))
        awaitTrue { factory[AiAlgorithm.NEURAL_EVAL].moves == 1 }
    }

    @Test(timeout = 10_000)
    fun `终局与悔棋的学习钩子各触发一次`() = runTest(dispatcher) {
        val factory = FakeFactory()
        val vm = RobotGameViewModel(
            enginePool = factory.pool,
            levelStore = FakeLevelStore(),
            algorithmStore = FakeAlgorithmStore(),
            aiDispatcher = Dispatchers.Default,
        )

        // 黑方连五：AI 只填 y=0 行，不干扰 y=7 的人工连珠
        for (x in 0..4) {
            vm.dispatch(RobotGameIntent.BoardTap(x, 7))
            awaitTrue {
                val state = vm.state.value
                state.board.cells[x][7] == Side.BLACK &&
                    (state.winner != null || state.active == Side.BLACK)
            }
        }
        assertEquals(Side.BLACK, vm.state.value.winner)

        val engine = factory[AiAlgorithm.DEFAULT]
        awaitTrue { engine.gameOvers == 1 }
        val outcome = engine.lastOutcome
        assertEquals(Side.BLACK, outcome?.winner)
        assertEquals("五黑四白共 9 手", 9, outcome?.moves?.size)

        vm.dispatch(RobotGameIntent.RollbackClicked)
        awaitTrue { engine.resets == 1 }
        // 悔棋不再触发终局回调，胜负回到进行中
        assertEquals(1, engine.gameOvers)
        assertNull(vm.state.value.winner)
    }

    /** 轮询等待：每次先把测试调度器排空（Intent 与主线程回调都在里面），再让出给真实线程 */
    private suspend fun TestScope.awaitTrue(timeoutMillis: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            advanceUntilIdle()
            if (predicate()) return
            withContext(Dispatchers.Default) { delay(5) }
        }
        advanceUntilIdle()
        assertTrue("等待条件超时", predicate())
    }

    private class FakeLevelStore(private var level: Difficulty? = null) : AiLevelStore {
        override fun read(): Difficulty? = level

        override fun write(level: Difficulty) {
            this.level = level
        }
    }

    private class FakeAlgorithmStore(private var algorithm: AiAlgorithm? = null) : AiAlgorithmStore {
        override fun read(): AiAlgorithm? = algorithm

        override fun write(algorithm: AiAlgorithm) {
            this.algorithm = algorithm
        }
    }

    /**
     * 假引擎：按 y 优先取第一个空点（与人工固定的 y=7 行不冲突），
     * 可选的闸门用来把"思考中"这个瞬间定格住。
     */
    private class FakeEngine(private val gate: CountDownLatch? = null) : GomokuAI {

        @Volatile
        override var level: Difficulty = Difficulty.MEDIUM

        @Volatile
        var moves = 0

        @Volatile
        var gameOvers = 0

        @Volatile
        var resets = 0

        @Volatile
        var lastOutcome: GameOutcome? = null

        override fun getPosition(board: Array<IntArray>): Point {
            moves++
            gate?.await()
            for (y in board[0].indices) {
                for (x in board.indices) {
                    if (board[x][y] == 0) return Point(x, y)
                }
            }
            return Point(0, 0)
        }

        override fun onGameOver(outcome: GameOutcome) {
            gameOvers++
            lastOutcome = outcome
        }

        override fun onGameReset() {
            resets++
        }
    }

    /** 记录工厂调用与实例：同一算法复用同一实例，断言才追得上 */
    private class FakeFactory(
        gateAlgorithm: AiAlgorithm? = null,
        gate: CountDownLatch? = null,
    ) {
        val created = mutableListOf<AiAlgorithm>()
        private val engines = HashMap<AiAlgorithm, FakeEngine>()
        val pool = AiEnginePool(
            width = 15,
            height = 15,
            factory = { algorithm, _, _ ->
                created += algorithm
                engines.getOrPut(algorithm) {
                    FakeEngine(if (algorithm == gateAlgorithm) gate else null)
                }
            },
        )

        operator fun get(algorithm: AiAlgorithm): FakeEngine = engines.getValue(algorithm)

        fun fakeResets(): Int = engines.values.sumOf { it.resets }
    }
}
