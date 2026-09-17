package com.github.lany192.fivechess.ui.net

import androidx.lifecycle.viewModelScope
import com.github.lany192.fivechess.core.mvi.MviViewModel
import com.github.lany192.fivechess.data.net.GameTransport
import com.github.lany192.fivechess.data.net.NetEvent
import com.github.lany192.fivechess.domain.engine.EngineResult
import com.github.lany192.fivechess.domain.engine.GameEngine
import com.github.lany192.fivechess.domain.model.GameEvent
import com.github.lany192.fivechess.domain.model.GameMode
import com.github.lany192.fivechess.domain.model.Move
import com.github.lany192.fivechess.domain.model.Point
import com.github.lany192.fivechess.domain.model.Side
import com.github.lany192.fivechess.ui.common.BoardRenderState
import kotlinx.coroutines.launch

/**
 * 联机对战：被请求方（server）执黑先手，发起方（client）执白
 *
 * 传输层经 [GameTransport] 注入，局域网与蓝牙共用本 ViewModel。
 *
 * - 悔棋为协商制：双方各自从落子历史中移除"请求方最后一手及其之后所有棋子"，
 *   两端移除数量由同一份历史推导，修复旧版两端各删一手导致的棋盘失同步
 * - 重开棋局通过 RESTART 消息同步两端，修复旧版仅本地清盘的失同步
 */
class NetGameViewModel(
    private val mode: GameMode,
    private val mySide: Side,
    private val transport: GameTransport,
    private val engine: GameEngine = GameEngine(),
) : MviViewModel<NetGameIntent, NetGameState, NetGameEffect>(
    NetGameState(
        board = BoardRenderState.empty(),
        mySide = mySide,
    )
) {

    private var blackWins = 0
    private var whiteWins = 0
    private var winLine: List<Point> = emptyList()
    private var end: NetGameEnd? = null
    private var connected = false
    private var awaitingRollbackResponse = false
    private var rollbackDialogShowing = false
    private var awaitingDrawResponse = false
    private var drawDialogShowing = false

    /** 和棋/认输宣告的终局（引擎规则之外的 VM 层终局，落子与协商一律拦截） */
    private var declaredOver = false

    init {
        engine.start(mode, mySide = mySide)
        updateState { it.copy(board = BoardRenderState.from(engine.snapshot())) }
        viewModelScope.launch {
            transport.events.collect(::onNetEvent)
        }
        transport.start()
    }

    override fun onIntent(intent: NetGameIntent) {
        when (intent) {
            is NetGameIntent.BoardTap -> tryLocalMove(intent.x, intent.y)
            NetGameIntent.RestartClicked -> {
                resetRoundFlags()
                consume(engine.restart())
                transport.requestRestart()
            }
            NetGameIntent.RollbackClicked -> {
                if (!connected || declaredOver || awaitingRollbackResponse || rollbackDialogShowing) return
                val hasMyStone = engine.snapshot().moves.any { it.side == mySide }
                if (!hasMyStone) return
                awaitingRollbackResponse = true
                transport.askRollback()
            }
            NetGameIntent.RollbackAgreed -> {
                rollbackDialogShowing = false
                transport.agreeRollback()
                applyRollback(mySide.opposite)
            }
            NetGameIntent.RollbackRejected -> {
                rollbackDialogShowing = false
                transport.rejectRollback()
            }
            NetGameIntent.DrawClicked -> {
                if (!connected || declaredOver || awaitingDrawResponse || drawDialogShowing) return
                if (engine.snapshot().over) return
                awaitingDrawResponse = true
                transport.askDraw()
                viewModelScope.launch { emitEffect(NetGameEffect.ShowMessage("已发送求和请求")) }
            }
            NetGameIntent.DrawAgreed -> {
                drawDialogShowing = false
                if (declaredOver || engine.snapshot().over) {
                    transport.rejectDraw()
                    return
                }
                transport.agreeDraw()
                declareDraw()
            }
            NetGameIntent.DrawRejected -> {
                drawDialogShowing = false
                transport.rejectDraw()
            }
            NetGameIntent.ResignClicked -> {
                if (!connected || declaredOver || engine.snapshot().over) return
                viewModelScope.launch { emitEffect(NetGameEffect.ShowResignConfirm) }
            }
            NetGameIntent.ResignConfirmed -> {
                if (declaredOver || engine.snapshot().over) return
                transport.sendResign()
                declareWinner(mySide.opposite)
            }
        }
    }

    private fun tryLocalMove(x: Int, y: Int) {
        if (!connected || declaredOver || awaitingRollbackResponse || rollbackDialogShowing) return
        val snapshot = engine.snapshot()
        if (snapshot.over || snapshot.active != mySide) return
        val result = engine.applyMove(x, y)
        consume(result)
        // 制胜一手只产生 GameOver 事件，按 MoveApplied 过滤会漏发，对端永远收不到终局
        if (result.events.none { it is GameEvent.IllegalMove }) {
            // 局面已变，此前发出的求和请求失效
            awaitingDrawResponse = false
            transport.sendMove(x, y)
        }
    }

    private fun applyRollback(requesterSide: Side) {
        val moves: List<Move> = engine.snapshot().moves
        val requesterLast = moves.indexOfLast { it.side == requesterSide }
        if (requesterLast < 0) return
        consume(engine.rollback(moves.size - requesterLast))
    }

    private fun onNetEvent(event: NetEvent) {
        when (event) {
            NetEvent.Connected -> {
                connected = true
                updateState { it.copy(connected = true) }
                viewModelScope.launch { emitEffect(NetGameEffect.DismissConnecting) }
            }
            NetEvent.ConnectFailed -> {
                viewModelScope.launch {
                    emitEffect(NetGameEffect.DismissConnecting)
                    emitEffect(NetGameEffect.ShowMessage("建立连接失败,请重试"))
                    emitEffect(NetGameEffect.Exit)
                }
            }
            NetEvent.Disconnected -> {
                viewModelScope.launch {
                    emitEffect(NetGameEffect.DismissConnecting)
                    emitEffect(NetGameEffect.ShowMessage("对方已断开连接"))
                    emitEffect(NetGameEffect.Exit)
                }
            }
            is NetEvent.ChessMove -> {
                awaitingDrawResponse = false
                consume(engine.applyRemoteMove(event.x, event.y, mySide.opposite))
            }
            NetEvent.RollbackAsked -> {
                if (declaredOver) {
                    transport.rejectRollback()
                    return
                }
                if (rollbackDialogShowing || awaitingRollbackResponse) return
                rollbackDialogShowing = true
                viewModelScope.launch { emitEffect(NetGameEffect.ShowRollbackRequest) }
            }
            NetEvent.RollbackAgreed -> {
                awaitingRollbackResponse = false
                viewModelScope.launch { emitEffect(NetGameEffect.ShowMessage("对方同意悔棋")) }
                applyRollback(mySide)
            }
            NetEvent.RollbackRejected -> {
                awaitingRollbackResponse = false
                viewModelScope.launch { emitEffect(NetGameEffect.ShowMessage("对方拒绝了你的请求")) }
            }
            NetEvent.RestartRequested -> {
                resetRoundFlags()
                consume(engine.restart())
                viewModelScope.launch { emitEffect(NetGameEffect.ShowMessage("对方已重新开始")) }
            }
            NetEvent.DrawAsked -> {
                if (declaredOver || engine.snapshot().over) {
                    // 终局期间的求和直接拒绝，避免请求方悬挂
                    transport.rejectDraw()
                    return
                }
                if (drawDialogShowing || awaitingDrawResponse) return
                drawDialogShowing = true
                viewModelScope.launch { emitEffect(NetGameEffect.ShowDrawRequest) }
            }
            NetEvent.DrawAgreed -> {
                awaitingDrawResponse = false
                if (declaredOver || engine.snapshot().over) return
                declareDraw()
            }
            NetEvent.DrawRejected -> {
                awaitingDrawResponse = false
                viewModelScope.launch { emitEffect(NetGameEffect.ShowMessage("对方拒绝求和")) }
            }
            NetEvent.Resigned -> {
                if (declaredOver || engine.snapshot().over) return
                declareWinner(mySide)
            }
        }
    }

    /** 和棋终局：双方胜场均不加，两端对称 */
    private fun declareDraw() {
        declaredOver = true
        end = NetGameEnd.Draw
        updateState { it.copy(end = end) }
    }

    /** 宣告终局并结算胜场：两端从同一事件各自推导 winner，胜场计数保持一致 */
    private fun declareWinner(winner: Side) {
        declaredOver = true
        when (winner) {
            Side.BLACK -> blackWins++
            Side.WHITE -> whiteWins++
        }
        end = NetGameEnd.Win(winner)
        updateState { it.copy(blackWins = blackWins, whiteWins = whiteWins, end = end) }
    }

    private fun resetRoundFlags() {
        declaredOver = false
        awaitingDrawResponse = false
        drawDialogShowing = false
    }

    private fun consume(result: EngineResult) {
        var board = BoardRenderState.from(result.state)
        result.events.forEach { event ->
            when (event) {
                is GameEvent.GameOver -> {
                    winLine = event.line
                    board = board.copy(winLine = event.line)
                    end = NetGameEnd.Win(event.winner)
                    when (event.winner) {
                        Side.BLACK -> blackWins++
                        Side.WHITE -> whiteWins++
                    }
                }
                is GameEvent.RollbackApplied -> {
                    winLine = emptyList()
                    end = null
                }
                GameEvent.Restarted -> {
                    winLine = emptyList()
                    end = null
                }
                else -> Unit
            }
        }
        updateState { current ->
            current.copy(
                board = board,
                active = result.state.active,
                blackWins = blackWins,
                whiteWins = whiteWins,
                end = end,
            )
        }
    }

    override fun onCleared() {
        transport.stop()
        super.onCleared()
    }
}
