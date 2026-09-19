package com.github.lany192.gomoku.ui.net

import com.github.lany192.gomoku.data.net.GameTransport
import com.github.lany192.gomoku.data.net.NetEvent
import com.github.lany192.gomoku.data.net.TcpType
import com.github.lany192.gomoku.domain.engine.GameEngine
import com.github.lany192.gomoku.domain.model.GameMode
import com.github.lany192.gomoku.domain.model.Side
import com.github.lany192.gomoku.ui.common.TurnCountdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

        playBlackWin(vm, transport)

        assertEquals(NetGameEnd.Win(Side.BLACK), vm.state.value.end)
        assertEquals(1, vm.state.value.blackWins)
        assertEquals(5, vm.state.value.board.winLine.size)
    }

    @Test(timeout = 10_000)
    fun `本方回合超时判负并同步给对端`() = runTest(dispatcher) {
        val transport = FakeTransport()
        // 本方执黑先手，5 秒时限：startVm 末尾的 advanceUntilIdle 会把表推到归零
        val vm = startVm(transport, Side.BLACK, turnMillis = 5_000)

        assertEquals(TcpType.TIMEOUT, transport.lastSent)
        assertEquals(NetGameEnd.Timeout(Side.WHITE), vm.state.value.end)
        assertEquals(1, vm.state.value.whiteWins)
        assertEquals(0, vm.state.value.blackWins)
    }

    @Test(timeout = 10_000)
    fun `对端超时则本方获胜且不回发`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)
        transport.emit(NetEvent.TimedOut)
        advanceUntilIdle()

        assertEquals(NetGameEnd.Timeout(Side.BLACK), vm.state.value.end)
        assertEquals(1, vm.state.value.blackWins)
        assertTrue(transport.sent.none { it == TcpType.TIMEOUT })
    }

    @Test(timeout = 10_000)
    fun `不是本方回合时归零保持沉默`() = runTest(dispatcher) {
        val transport = FakeTransport()
        // 本方执白，黑方先行：白方一侧归零不得宣告，须等对端自己宣告
        val vm = startVm(transport, Side.WHITE, turnMillis = 3_000)

        // 先确认表确实跑到了归零，否则"保持沉默"可能是压根没起表而白白通过
        assertEquals(0L, vm.state.value.remainingMillis)
        assertTrue(transport.sent.isEmpty())
        assertNull(vm.state.value.end)
    }

    @Test(timeout = 10_000)
    fun `非法落子不重置倒计时`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = NetGameViewModel(
            mode = GameMode.BLUETOOTH,
            mySide = Side.BLACK,
            transport = transport,
            turnDurationMillis = 10_000,
        )
        advanceUntilIdle()
        transport.emit(NetEvent.Connected)
        runCurrent()

        advanceTimeBy(5_000)
        runCurrent()
        // 越界落子被引擎拒绝：若它重置了表，归零会推迟到 15 秒，下面的断言就会失败
        vm.dispatch(NetGameIntent.BoardTap(20, 20))
        runCurrent()

        advanceTimeBy(6_000)
        runCurrent()

        assertEquals(TcpType.TIMEOUT, transport.lastSent)
    }

    @Test(timeout = 10_000)
    fun `超时终局期间到达的悔棋同意不会复活棋局`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)
        vm.dispatch(NetGameIntent.BoardTap(0, 0))
        advanceUntilIdle()

        // 对端请求悔棋，对话框开着；此时本方收到对方超时宣告
        transport.emit(NetEvent.RollbackAsked)
        advanceUntilIdle()
        transport.emit(NetEvent.TimedOut)
        advanceUntilIdle()

        vm.dispatch(NetGameIntent.RollbackAgreed)
        advanceUntilIdle()

        assertEquals(NetGameEnd.Timeout(Side.BLACK), vm.state.value.end)
        assertTrue(transport.sent.none { it == TcpType.ROLLBACK_AGREE })
        // 棋子仍在，未曾回退
        assertEquals(Side.BLACK, vm.state.value.board.cells[0][0])
    }

    // ---------- 满盘和棋 ----------

    @Test
    fun `满盘和棋由对端最后一手判定`() = runTest(dispatcher) {
        val transport = FakeTransport()
        // 2×2 棋盘：黑(我) 白 黑 白，铺满即和棋
        val vm = startVm(transport, Side.BLACK, engine = GameEngine(2, 2))

        vm.dispatch(NetGameIntent.BoardTap(0, 0))
        advanceUntilIdle()
        transport.emit(NetEvent.ChessMove(1, 0))
        advanceUntilIdle()
        vm.dispatch(NetGameIntent.BoardTap(0, 1))
        advanceUntilIdle()
        transport.emit(NetEvent.ChessMove(1, 1))
        advanceUntilIdle()

        assertEquals(NetGameEnd.Draw, vm.state.value.end)
        assertEquals(0, vm.state.value.blackWins)
        assertEquals(0, vm.state.value.whiteWins)
        // 第 225 手照常走 ADD_CHESS，不引入新的消息类型
        assertEquals(2, transport.sent.size)
        assertTrue(transport.sent.all { it == TcpType.ADD_CHESS })
    }

    @Test
    fun `满盘和棋由本方最后一手判定`() = runTest(dispatcher) {
        val transport = FakeTransport()
        // 3×1 棋盘：黑(我) 白 黑，本方落最后一手
        val vm = startVm(transport, Side.BLACK, engine = GameEngine(3, 1))

        vm.dispatch(NetGameIntent.BoardTap(0, 0))
        advanceUntilIdle()
        transport.emit(NetEvent.ChessMove(1, 0))
        advanceUntilIdle()
        vm.dispatch(NetGameIntent.BoardTap(2, 0))
        advanceUntilIdle()

        assertEquals(NetGameEnd.Draw, vm.state.value.end)
        assertEquals(2, transport.moves.size)
        assertEquals(TcpType.ADD_CHESS, transport.lastSent)
    }

    @Test
    fun `满盘和棋后落子与协商一律被拦`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK, engine = GameEngine(2, 2))
        vm.dispatch(NetGameIntent.BoardTap(0, 0))
        advanceUntilIdle()
        transport.emit(NetEvent.ChessMove(1, 0))
        advanceUntilIdle()
        vm.dispatch(NetGameIntent.BoardTap(0, 1))
        advanceUntilIdle()
        transport.emit(NetEvent.ChessMove(1, 1))
        advanceUntilIdle()
        val sentBefore = transport.sent.size

        vm.dispatch(NetGameIntent.BoardTap(1, 1))
        vm.dispatch(NetGameIntent.DrawClicked)
        advanceUntilIdle()

        assertEquals(sentBefore, transport.sent.size)
        assertEquals(NetGameEnd.Draw, vm.state.value.end)
    }

    // ---------- 终局回执（D3） ----------

    @Test
    fun `五连终局后不再发起悔棋`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)
        playBlackWin(vm, transport)
        val sentBefore = transport.sent.size

        vm.dispatch(NetGameIntent.RollbackClicked)
        advanceUntilIdle()

        assertEquals(sentBefore, transport.sent.size)
        assertEquals(NetGameEnd.Win(Side.BLACK), vm.state.value.end)
    }

    @Test
    fun `五连终局后收到悔棋请求回绝而非静默丢弃`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)
        playBlackWin(vm, transport)

        transport.emit(NetEvent.RollbackAsked)
        advanceUntilIdle()

        assertEquals(TcpType.ROLLBACK_REJECT, transport.lastSent)
        assertEquals(NetGameEnd.Win(Side.BLACK), vm.state.value.end)
    }

    @Test
    fun `五连终局后收到求和请求回绝`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)
        playBlackWin(vm, transport)

        transport.emit(NetEvent.DrawAsked)
        advanceUntilIdle()

        assertEquals(TcpType.DRAW_REJECT, transport.lastSent)
        assertEquals(NetGameEnd.Win(Side.BLACK), vm.state.value.end)
    }

    @Test
    fun `本方等待悔棋回应时对方请求立即回绝`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)
        vm.dispatch(NetGameIntent.BoardTap(0, 0))
        advanceUntilIdle()

        // 本方发起悔棋后等待回应；此时对端也发来悔棋请求（此前会被静默丢弃，请求方永久悬挂）
        vm.dispatch(NetGameIntent.RollbackClicked)
        advanceUntilIdle()
        assertEquals(TcpType.ROLLBACK_ASK, transport.lastSent)

        transport.emit(NetEvent.RollbackAsked)
        advanceUntilIdle()

        assertEquals(TcpType.ROLLBACK_REJECT, transport.lastSent)
    }

    @Test
    fun `求和弹窗挂着时对方再求和立即回绝`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)
        vm.dispatch(NetGameIntent.BoardTap(0, 0))
        advanceUntilIdle()

        transport.emit(NetEvent.DrawAsked)
        advanceUntilIdle()
        transport.emit(NetEvent.DrawAsked)
        advanceUntilIdle()

        assertEquals(TcpType.DRAW_REJECT, transport.lastSent)
    }

    @Test
    fun `五连终局后重开可继续落子`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)
        playBlackWin(vm, transport)
        assertEquals(NetGameEnd.Win(Side.BLACK), vm.state.value.end)

        transport.emit(NetEvent.RestartRequested)
        advanceUntilIdle()

        assertNull(vm.state.value.end)
        vm.dispatch(NetGameIntent.BoardTap(7, 7))
        advanceUntilIdle()
        assertEquals(Side.BLACK, vm.state.value.board.cells[7][7])
        assertEquals(TcpType.ADD_CHESS, transport.lastSent)
    }

    @Test
    fun `在途悔棋同意到达后终局标志复位`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val vm = startVm(transport, Side.BLACK)
        vm.dispatch(NetGameIntent.BoardTap(0, 0))
        advanceUntilIdle()
        transport.emit(NetEvent.ChessMove(1, 0))
        advanceUntilIdle()
        vm.dispatch(NetGameIntent.BoardTap(2, 0))
        advanceUntilIdle()

        vm.dispatch(NetGameIntent.RollbackClicked)
        advanceUntilIdle()
        assertEquals(TcpType.ROLLBACK_ASK, transport.lastSent)

        // 对端宣告超时（本方获胜），随后它在上一步之前发出的"同意悔棋"才到达
        transport.emit(NetEvent.TimedOut)
        advanceUntilIdle()
        assertEquals(NetGameEnd.Timeout(Side.BLACK), vm.state.value.end)

        transport.emit(NetEvent.RollbackAgreed)
        advanceUntilIdle()

        // 对端已按同一份落子历史回退，本方必须跟随；棋局复活后可继续落子
        assertNull(vm.state.value.board.cells[2][0])
        assertNull(vm.state.value.end)
        assertEquals(Side.WHITE, vm.state.value.board.cells[1][0])
        vm.dispatch(NetGameIntent.BoardTap(3, 0))
        advanceUntilIdle()
        assertEquals(Side.BLACK, vm.state.value.board.cells[3][0])
        assertEquals(TcpType.ADD_CHESS, transport.lastSent)
        // 比分不回收：终局结算过的一胜仍然留在记分牌上（见 DEMAND §9.4）
        assertEquals(1, vm.state.value.blackWins)
    }

    /**
     * 建 VM 并等它订阅上事件流后再投递 Connected
     *
     * 真实传输层是在自己的 IO 协程里异步上报 Connected 的，这里也必须在订阅建立之后发，
     * 否则 replay=0 的 SharedFlow 会把事件直接丢掉。
     *
     * [turnMillis] 默认关掉倒计时：若开着，末尾的 `advanceUntilIdle()` 会把表一路推到归零，
     * 那些用例就会在断言前先被超时判负（详见 [TurnCountdown] 关于测试的两条约定）。
     */
    private suspend fun TestScope.startVm(
        transport: FakeTransport,
        mySide: Side,
        turnMillis: Long = 0,
        engine: GameEngine = GameEngine(),
    ): NetGameViewModel {
        val vm = NetGameViewModel(
            mode = GameMode.BLUETOOTH,
            mySide = mySide,
            transport = transport,
            engine = engine,
            turnDurationMillis = turnMillis,
        )
        advanceUntilIdle()
        if (transport.connected) {
            transport.emit(NetEvent.Connected)
            advanceUntilIdle()
        }
        return vm
    }

    /** 黑方（本方）连下 0..4 列成五连，白方在另一行应付 */
    private suspend fun TestScope.playBlackWin(vm: NetGameViewModel, transport: FakeTransport) {
        repeat(4) { i ->
            vm.dispatch(NetGameIntent.BoardTap(i, 0))
            advanceUntilIdle()
            transport.emit(NetEvent.ChessMove(i, 1))
            advanceUntilIdle()
        }
        vm.dispatch(NetGameIntent.BoardTap(4, 0))
        advanceUntilIdle()
    }

    /** 传输桩：记录发出的指令，并允许测试从"对端"注入事件 */
    private class FakeTransport : GameTransport {
        private val _events = MutableSharedFlow<NetEvent>(extraBufferCapacity = 64)
        override val events: SharedFlow<NetEvent> = _events.asSharedFlow()

        val moves = mutableListOf<Pair<Int, Int>>()

        /** 依次发出的全部指令，用于断言"保持沉默" */
        val sent = mutableListOf<TcpType>()
        val lastSent: TcpType? get() = sent.lastOrNull()
        var connected = true

        override fun start() = Unit

        override fun stop() = Unit

        override fun sendMove(x: Int, y: Int) {
            moves += x to y
            sent += TcpType.ADD_CHESS
        }

        override fun askRollback() {
            sent += TcpType.ROLLBACK_ASK
        }

        override fun agreeRollback() {
            sent += TcpType.ROLLBACK_AGREE
        }

        override fun rejectRollback() {
            sent += TcpType.ROLLBACK_REJECT
        }

        override fun requestRestart() {
            sent += TcpType.RESTART
        }

        override fun askDraw() {
            sent += TcpType.DRAW_ASK
        }

        override fun agreeDraw() {
            sent += TcpType.DRAW_AGREE
        }

        override fun rejectDraw() {
            sent += TcpType.DRAW_REJECT
        }

        override fun sendResign() {
            sent += TcpType.RESIGN
        }

        override fun sendTimeout() {
            sent += TcpType.TIMEOUT
        }

        fun emit(event: NetEvent) {
            _events.tryEmit(event)
        }
    }
}
