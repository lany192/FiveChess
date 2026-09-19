package com.github.lany192.gomoku.ui.person

import androidx.lifecycle.viewModelScope
import com.github.lany192.gomoku.core.mvi.MviViewModel
import com.github.lany192.gomoku.domain.engine.EngineResult
import com.github.lany192.gomoku.domain.engine.GameEngine
import com.github.lany192.gomoku.domain.model.GameEvent
import com.github.lany192.gomoku.domain.model.GameMode
import com.github.lany192.gomoku.domain.model.Point
import com.github.lany192.gomoku.domain.model.Side
import com.github.lany192.gomoku.ui.common.BoardRenderState
import com.github.lany192.gomoku.ui.common.TurnCountdown

/**
 * 本地双人同屏：双方都可操作，每步限时 3 分钟，归零时轮到走棋的一方判负
 *
 * @param turnDurationMillis 每步时限；<= 0 表示不限时（JVM 测试用，见 [TurnCountdown] 的约定）
 */
class PersonGameViewModel(
    private val engine: GameEngine = GameEngine(),
    private val turnDurationMillis: Long = TurnCountdown.THREE_MINUTES,
) : MviViewModel<PersonGameIntent, PersonGameState, PersonGameEffect>(
    PersonGameState(BoardRenderState.empty(), remainingMillis = turnDurationMillis)
) {

    private var blackWins = 0
    private var whiteWins = 0
    private var winLine: List<Point> = emptyList()
    private var winner: Side? = null
    private var timedOut = false
    private var drawn = false

    // 必须在 init 之前声明：属性与 init 按声明顺序执行，init 会用到它
    private val turn = TurnCountdown(
        scope = viewModelScope,
        durationMillis = turnDurationMillis,
        onTick = { remaining -> updateState { it.copy(remainingMillis = remaining) } },
        onExpired = ::onTurnExpired,
    )

    init {
        engine.start(GameMode.LOCAL_TWO)
        updateState { it.copy(board = BoardRenderState.from(engine.snapshot())) }
        turn.restart()
    }

    override fun onIntent(intent: PersonGameIntent) {
        when (intent) {
            is PersonGameIntent.BoardTap -> consume(engine.applyMove(intent.x, intent.y))
            PersonGameIntent.RollbackClicked -> consume(engine.rollback())
            PersonGameIntent.RestartClicked -> consume(engine.restart())
        }
    }

    private fun consume(result: EngineResult) {
        var board = BoardRenderState.from(result.state)
        result.events.forEach { event ->
            when (event) {
                is GameEvent.GameOver -> {
                    winLine = event.line
                    board = board.copy(winLine = event.line)
                    winner = event.winner
                    timedOut = false
                    drawn = false
                    when (event.winner) {
                        Side.BLACK -> blackWins++
                        Side.WHITE -> whiteWins++
                    }
                    turn.stop()
                }
                is GameEvent.Timeout -> {
                    winLine = emptyList()
                    winner = event.winner
                    timedOut = true
                    drawn = false
                    when (event.winner) {
                        Side.BLACK -> blackWins++
                        Side.WHITE -> whiteWins++
                    }
                    turn.stop()
                }
                GameEvent.Draw -> {
                    // 满盘和棋：双方均不加胜场，横幅走「双方和棋」
                    winLine = emptyList()
                    winner = null
                    timedOut = false
                    drawn = true
                    turn.stop()
                }
                is GameEvent.RollbackApplied -> {
                    winLine = emptyList()
                    winner = null
                    timedOut = false
                    drawn = false
                    turn.restart()
                }
                GameEvent.Restarted -> {
                    winLine = emptyList()
                    winner = null
                    timedOut = false
                    drawn = false
                    turn.restart()
                }
                is GameEvent.MoveApplied -> turn.restart()
                else -> Unit
            }
        }
        updateState { current ->
            current.copy(
                board = board,
                active = result.state.active,
                blackWins = blackWins,
                whiteWins = whiteWins,
                winner = winner,
                timedOut = timedOut,
                drawn = drawn,
            )
        }
    }

    /** 归零：轮到走棋的一方判负。非法落子不重置倒计时，故这里不会因乱点而推迟 */
    private fun onTurnExpired() {
        val snapshot = engine.snapshot()
        if (snapshot.over) return
        consume(engine.declareTimeout(snapshot.active))
    }
}
