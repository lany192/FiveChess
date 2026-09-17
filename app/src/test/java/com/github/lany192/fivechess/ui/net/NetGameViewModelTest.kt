package com.github.lany192.fivechess.ui.net

import com.github.lany192.fivechess.data.net.GameTransport
import com.github.lany192.fivechess.data.net.NetEvent
import com.github.lany192.fivechess.data.net.TcpType
import com.github.lany192.fivechess.domain.model.GameMode
import com.github.lany192.fivechess.domain.model.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 联机对局逻辑回归（局域网与蓝牙共用本 ViewModel，这里用过传输桩验证语义）
 *
 * 重点覆盖从旧 WifiGameViewModel 搬过来后不能走样的部分：悔棋按"请求方最后一手"回退、
 * 求和/认输的终局判定、重开同步。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetGameViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `本端落子同步给对端`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)
        vm.dispatch(NetGameIntent.BoardTap(7, 7))
        advanceUntilIdle()

        assertEquals(listOf(7 to 7), transport.moves)
        assertEquals(Side.WHITE, vm.state.value.active)
    }

    @Test
    fun `未连上时落子不生效`() = runTest(dispatcher) {
        val transport = FakeTransport().apply { connected = false }
        val vm = startVm(transport, Side.BLACK)
        vm.dispatch(NetGameIntent.BoardTap(7, 7))
        advanceUntilIdle()

        assertTrue(transport.moves.isEmpty())
        assertNull(vm.state.value.board.cells[7][7])
    }

    @Test
    fun `对端落子落到对端颜色`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)
        transport.emit(NetEvent.ChessMove(3, 4))
        advanceUntilIdle()

        assertEquals(Side.WHITE, vm.state.value.board.cells[3][4])
    }

    @Test
    fun `不是本方回合不能落子`() = runTest(dispatcher) {
        val transport = FakeTransport()
        // 本方执白，黑方先行，白方此时点棋盘应被引擎拒绝
        val vm = startVm(transport, Side.WHITE)
        vm.dispatch(NetGameIntent.BoardTap(7, 7))
        advanceUntilIdle()

        assertTrue(transport.moves.isEmpty())
    }

    @Test
    fun `悔棋由请求方最后一手推导，双方各自回退相同手数`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)

        // 黑(我) 白(对端) 黑(我) —— 我请求悔棋，应移除我最后一手及其之后所有棋子
        vm.dispatch(NetGameIntent.BoardTap(0, 0))
        advanceUntilIdle()
        transport.emit(NetEvent.ChessMove(1, 0))
        advanceUntilIdle()
        vm.dispatch(NetGameIntent.BoardTap(2, 0))
        advanceUntilIdle()

        vm.dispatch(NetGameIntent.RollbackClicked)
        advanceUntilIdle()
        assertEquals(TcpType.ROLLBACK_ASK, transport.lastSent)

        // 对端同意：以"请求方(黑)最后一手"为界，本地移除 (2,0) 一手
        transport.emit(NetEvent.RollbackAgreed)
        advanceUntilIdle()

        assertNull(vm.state.value.board.cells[2][0])
        assertEquals(Side.BLACK, vm.state.value.active)
    }

    @Test
    fun `和棋终局双方均不加胜场`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)

        transport.emit(NetEvent.DrawAsked)
        advanceUntilIdle()
        vm.dispatch(NetGameIntent.DrawAgreed)
        advanceUntilIdle()

        assertEquals(NetGameEnd.Draw, vm.state.value.end)
        assertEquals(0, vm.state.value.blackWins)
        assertEquals(0, vm.state.value.whiteWins)
        assertEquals(TcpType.DRAW_AGREE, transport.lastSent)
    }

    @Test
    fun `认输单方宣告且对方收到后判本方胜`() = runTest(dispatcher) {
        val transport = FakeTransport()
        // 我方执黑并认输 —— 对端(白)应被判胜
        val vm = startVm(transport, Side.BLACK)
        vm.dispatch(NetGameIntent.ResignConfirmed)
        advanceUntilIdle()

        assertEquals(TcpType.RESIGN, transport.lastSent)
        assertEquals(NetGameEnd.Win(Side.WHITE), vm.state.value.end)
        assertEquals(1, vm.state.value.whiteWins)
    }

    @Test
    fun `对端认输则本方获胜`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)
        transport.emit(NetEvent.Resigned)
        advanceUntilIdle()

        assertEquals(NetGameEnd.Win(Side.BLACK), vm.state.value.end)
        assertEquals(1, vm.state.value.blackWins)
    }

    @Test
    fun `重开请求双方同步清盘`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)
        vm.dispatch(NetGameIntent.BoardTap(5, 5))
        advanceUntilIdle()

        transport.emit(NetEvent.RestartRequested)
        advanceUntilIdle()

        assertNull(vm.state.value.board.cells[5][5])
        assertNull(vm.state.value.end)
        assertEquals(Side.BLACK, vm.state.value.active)
    }

    @Test
    fun `五连胜计胜场并高亮五连`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)

        // 黑方连下 0..4 列，白方在另一行应付
        repeat(4) { i ->
            vm.dispatch(NetGameIntent.BoardTap(i, 0))
            advanceUntilIdle()
            transport.emit(NetEvent.ChessMove(i, 1))
            advanceUntilIdle()
        }
        vm.dispatch(NetGameIntent.BoardTap(4, 0))
        advanceUntilIdle()

        assertEquals(NetGameEnd.Win(Side.BLACK), vm.state.value.end)
        assertEquals(1, vm.state.value.blackWins)
        assertEquals(5, vm.state.value.board.winLine.size)
    }

    /**
     * 建 VM 并等它订阅上事件流后再投递 Connected
     *
     * 真实传输层是在自己的 IO 协程里异步上报 Connected 的，这里也必须在订阅建立之后发，
     * 否则 replay=0 的 SharedFlow 会把事件直接丢掉。
     */
    private suspend fun TestScope.startVm(
        transport: FakeTransport,
        mySide: Side,
    ): NetGameViewModel {
        val vm = NetGameViewModel(
            mode = GameMode.BLUETOOTH,
            mySide = mySide,
            transport = transport,
        )
        advanceUntilIdle()
        if (transport.connected) {
            transport.emit(NetEvent.Connected)
            advanceUntilIdle()
        }
        return vm
    }

    /** 传输桩：记录发出的指令，并允许测试从"对端"注入事件 */
    private class FakeTransport : GameTransport {
        private val _events = MutableSharedFlow<NetEvent>(extraBufferCapacity = 64)
        override val events: SharedFlow<NetEvent> = _events.asSharedFlow()

        val moves = mutableListOf<Pair<Int, Int>>()
        var lastSent: TcpType? = null
        var connected = true

        override fun start() = Unit

        override fun stop() = Unit

        override fun sendMove(x: Int, y: Int) {
            moves += x to y
            lastSent = TcpType.ADD_CHESS
        }

        override fun askRollback() {
            lastSent = TcpType.ROLLBACK_ASK
        }

        override fun agreeRollback() {
            lastSent = TcpType.ROLLBACK_AGREE
        }

        override fun rejectRollback() {
            lastSent = TcpType.ROLLBACK_REJECT
        }

        override fun requestRestart() {
            lastSent = TcpType.RESTART
        }

        override fun askDraw() {
            lastSent = TcpType.DRAW_ASK
        }

        override fun agreeDraw() {
            lastSent = TcpType.DRAW_AGREE
        }

        override fun rejectDraw() {
            lastSent = TcpType.DRAW_REJECT
        }

        override fun sendResign() {
            lastSent = TcpType.RESIGN
        }

        fun emit(event: NetEvent) {
            _events.tryEmit(event)
        }
    }
}
