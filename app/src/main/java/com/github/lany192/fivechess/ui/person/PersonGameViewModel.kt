package com.github.lany192.fivechess.ui.person

import androidx.lifecycle.viewModelScope
import com.github.lany192.fivechess.core.mvi.MviViewModel
import com.github.lany192.fivechess.domain.engine.EngineResult
import com.github.lany192.fivechess.domain.engine.GameEngine
import com.github.lany192.fivechess.domain.model.GameEvent
import com.github.lany192.fivechess.domain.model.GameMode
import com.github.lany192.fivechess.domain.model.Point
import com.github.lany192.fivechess.domain.model.Side
import com.github.lany192.fivechess.ui.common.BoardRenderState
import kotlinx.coroutines.launch

class PersonGameViewModel(
    private val engine: GameEngine = GameEngine(),
) : MviViewModel<PersonGameIntent, PersonGameState, PersonGameEffect>(
    PersonGameState(BoardRenderState.empty())
) {

    private var blackWins = 0
    private var whiteWins = 0
    private var winLine: List<Point> = emptyList()

    init {
        engine.start(GameMode.LOCAL_TWO)
        updateState { it.copy(board = BoardRenderState.from(engine.snapshot())) }
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
            viewModelScope.launch { emitEffect(PersonGameEffect.ShowGameOver(event.winner)) }
        }
    }
}
