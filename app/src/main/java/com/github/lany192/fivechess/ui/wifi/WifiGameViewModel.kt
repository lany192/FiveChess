package com.github.lany192.fivechess.ui.wifi

import androidx.lifecycle.viewModelScope
import com.github.lany192.fivechess.core.mvi.MviViewModel
import com.github.lany192.fivechess.data.net.LanGameClient
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
 * 局域网对战：被请求方（server）执黑先手，发起方（client）执白
 *
 * - 悔棋为协商制：双方各自从落子历史中移除"请求方最后一手及其之后所有棋子"，
 *   两端移除数量由同一份历史推导，修复旧版两端各删一手导致的棋盘失同步
 * - 重开棋局通过 RESTART 消息同步两端，修复旧版仅本地清盘的失同步
 */
class WifiGameViewModel(
    private val isServer: Boolean,
    private val remoteIp: String,
    private val engine: GameEngine = GameEngine(),
    private val client: LanGameClient = LanGameClient(isServer, remoteIp),
) : MviViewModel<WifiGameIntent, WifiGameState, WifiGameEffect>(
    WifiGameState(
        board = BoardRenderState.empty(),
        mySide = if (isServer) Side.BLACK else Side.WHITE,
    )
) {

    private val mySide: Side = if (isServer) Side.BLACK else Side.WHITE
    private var blackWins = 0
    private var whiteWins = 0
    private var winLine: List<Point> = emptyList()
    private var connected = false
    private var awaitingRollbackResponse = false
    private var rollbackDialogShowing = false

    init {
        engine.start(GameMode.LAN, mySide = mySide)
        updateState { it.copy(board = BoardRenderState.from(engine.snapshot())) }
        viewModelScope.launch {
            client.events.collect(::onNetEvent)
        }
        client.start()
    }

    override fun onIntent(intent: WifiGameIntent) {
        when (intent) {
            is WifiGameIntent.BoardTap -> tryLocalMove(intent.x, intent.y)
            WifiGameIntent.RestartClicked -> {
                consume(engine.restart())
                client.requestRestart()
            }
            WifiGameIntent.RollbackClicked -> {
                if (!connected || awaitingRollbackResponse || rollbackDialogShowing) return
                val hasMyStone = engine.snapshot().moves.any { it.side == mySide }
                if (!hasMyStone) return
                awaitingRollbackResponse = true
                client.askRollback()
            }
            WifiGameIntent.RollbackAgreed -> {
                rollbackDialogShowing = false
                client.agreeRollback()
                applyRollback(mySide.opposite)
            }
            WifiGameIntent.RollbackRejected -> {
                rollbackDialogShowing = false
                client.rejectRollback()
            }
        }
    }

    private fun tryLocalMove(x: Int, y: Int) {
        if (!connected || awaitingRollbackResponse || rollbackDialogShowing) return
        val snapshot = engine.snapshot()
        if (snapshot.over || snapshot.active != mySide) return
        val result = engine.applyMove(x, y)
        consume(result)
        result.events.filterIsInstance<GameEvent.MoveApplied>().firstOrNull()?.let {
            client.sendMove(it.move.x, it.move.y)
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
                viewModelScope.launch { emitEffect(WifiGameEffect.DismissConnecting) }
            }
            NetEvent.ConnectFailed -> {
                viewModelScope.launch {
                    emitEffect(WifiGameEffect.DismissConnecting)
                    emitEffect(WifiGameEffect.ShowMessage("建立网络失败,请重试"))
                    emitEffect(WifiGameEffect.Exit)
                }
            }
            NetEvent.Disconnected -> {
                viewModelScope.launch {
                    emitEffect(WifiGameEffect.DismissConnecting)
                    emitEffect(WifiGameEffect.ShowMessage("对方已断开连接"))
                    emitEffect(WifiGameEffect.Exit)
                }
            }
            is NetEvent.ChessMove -> consume(engine.applyRemoteMove(event.x, event.y, mySide.opposite))
            NetEvent.RollbackAsked -> {
                if (rollbackDialogShowing || awaitingRollbackResponse) return
                rollbackDialogShowing = true
                viewModelScope.launch { emitEffect(WifiGameEffect.ShowRollbackRequest) }
            }
            NetEvent.RollbackAgreed -> {
                awaitingRollbackResponse = false
                viewModelScope.launch { emitEffect(WifiGameEffect.ShowMessage("对方同意悔棋")) }
                applyRollback(mySide)
            }
            NetEvent.RollbackRejected -> {
                awaitingRollbackResponse = false
                viewModelScope.launch { emitEffect(WifiGameEffect.ShowMessage("对方拒绝了你的请求")) }
            }
            NetEvent.RestartRequested -> {
                consume(engine.restart())
                viewModelScope.launch { emitEffect(WifiGameEffect.ShowMessage("对方已重新开始")) }
            }
        }
    }

    private fun consume(result: EngineResult) {
        var board = BoardRenderState.from(result.state)
        result.events.forEach { event ->
            when (event) {
                is GameEvent.GameOver -> {
                    winLine = event.line
                    board = board.copy(winLine = event.line)
                    when (event.winner) {
                        Side.BLACK -> blackWins++
                        Side.WHITE -> whiteWins++
                    }
                }
                is GameEvent.RollbackApplied -> winLine = emptyList()
                GameEvent.Restarted -> winLine = emptyList()
                else -> Unit
            }
        }
        updateState { current ->
            current.copy(
                board = board,
                active = result.state.active,
                blackWins = blackWins,
                whiteWins = whiteWins,
            )
        }
        result.events.filterIsInstance<GameEvent.GameOver>().forEach { event ->
            viewModelScope.launch { emitEffect(WifiGameEffect.ShowGameResult(event.winner == mySide)) }
        }
    }

    override fun onCleared() {
        client.stop()
        super.onCleared()
    }
}
