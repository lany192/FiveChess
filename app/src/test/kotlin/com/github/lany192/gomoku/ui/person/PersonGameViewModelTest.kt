package com.github.lany192.gomoku.ui.person

import com.github.lany192.gomoku.domain.model.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 本地双人每步限时回归
 *
 * 每个用例都带 `timeout`：倒计时的协程若没跑到归零就结束测试体，`runTest` 收尾排空队列时
 * 会静默挂死整个测试任务（详见 TurnCountdown 的约定），这里让它表现为失败而非卡死。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonGameViewModelTest {

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
    fun `本地双人交替行棋`() = runTest(dispatcher) {
        val vm = PersonGameViewModel(turnDurationMillis = 0)
        vm.dispatch(PersonGameIntent.BoardTap(7, 7))
        advanceUntilIdle()

        assertEquals(Side.BLACK, vm.state.value.board.cells[7][7])
        assertEquals(Side.WHITE, vm.state.value.active)
    }

    @Test(timeout = 10_000)
    fun `归零时轮到走棋的一方判负`() = runTest(dispatcher) {
        // 黑方先手且 5 秒内未落子，advanceUntilIdle 把表推到归零
        val vm = PersonGameViewModel(turnDurationMillis = 5_000)
        advanceUntilIdle()

        assertEquals(Side.WHITE, vm.state.value.winner)
        assertTrue(vm.state.value.timedOut)
        assertEquals(0, vm.state.value.blackWins)
        assertEquals(1, vm.state.value.whiteWins)
        assertEquals(0L, vm.state.value.remainingMillis)
    }

    @Test(timeout = 10_000)
    fun `超时判负后棋盘锁定`() = runTest(dispatcher) {
        val vm = PersonGameViewModel(turnDurationMillis = 5_000)
        advanceUntilIdle()

        vm.dispatch(PersonGameIntent.BoardTap(7, 7))
        advanceUntilIdle()

        assertNull(vm.state.value.board.cells[7][7])
    }

    @Test(timeout = 10_000)
    fun `落子后倒计时重置为完整时限`() = runTest(dispatcher) {
        val vm = PersonGameViewModel(turnDurationMillis = 10_000)
        runCurrent()

        advanceTimeBy(6_000)
        runCurrent()
        vm.dispatch(PersonGameIntent.BoardTap(7, 7))
        runCurrent()

        // 已重置：再过 9 秒仍未归零
        advanceTimeBy(9_000)
        runCurrent()
        assertNull(vm.state.value.winner)

        // 到第 16 秒必然归零，轮到白方故白方判负
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(Side.BLACK, vm.state.value.winner)
        assertTrue(vm.state.value.timedOut)
    }

    @Test(timeout = 10_000)
    fun `悔棋解除超时判负并重置倒计时`() = runTest(dispatcher) {
        val vm = PersonGameViewModel(turnDurationMillis = 5_000)
        vm.dispatch(PersonGameIntent.BoardTap(0, 0))
        advanceUntilIdle()
        // 黑方落子后轮到白方，白方超时判负
        assertEquals(Side.BLACK, vm.state.value.winner)
        assertTrue(vm.state.value.timedOut)

        vm.dispatch(PersonGameIntent.RollbackClicked)
        // 只用 runCurrent：advanceUntilIdle 会把悔棋后重启的表再次推到归零
        runCurrent()

        assertNull(vm.state.value.winner)
        assertFalse(vm.state.value.timedOut)
        // 表已重置回完整时限（悔棋后棋盘清空，轮回到黑方）
        assertEquals(5_000L, vm.state.value.remainingMillis)

        // 让表自然归零终止，否则 runTest 收尾排空队列时会挂死整个测试任务
        advanceUntilIdle()
        assertEquals(Side.WHITE, vm.state.value.winner)
    }
}
