package com.github.lany192.fivechess.ui.robot

import androidx.lifecycle.viewModelScope
import com.github.lany192.fivechess.core.mvi.MviViewModel
import com.github.lany192.fivechess.data.settings.AiLevelStore
import com.github.lany192.fivechess.domain.ai.Difficulty
import com.github.lany192.fivechess.domain.ai.RobotAI
import com.github.lany192.fivechess.domain.engine.EngineResult
import com.github.lany192.fivechess.domain.engine.GameEngine
import com.github.lany192.fivechess.domain.model.GameEvent
import com.github.lany192.fivechess.domain.model.GameMode
import com.github.lany192.fivechess.domain.model.Point
import com.github.lany192.fivechess.domain.model.Side
import com.github.lany192.fivechess.ui.common.BoardRenderState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 人机对战：本端执黑，AI 执白
 *
 * - AI 在 Dispatchers.Default 计算，结果切回主线程按序入局，与 Intent 串行不冲突
 * - AI 思考期间请求悔棋，先记 [pendingRollback]，等 AI 落子后一并回退两子（对齐旧版 isRollback 语义）
 * - 思考期间重开棋局时，过期的 AI 落子按轮次校验丢弃
 */
class RobotGameViewModel(
    private val engine: GameEngine = GameEngine(),
    private val ai: RobotAI = RobotAI(BOARD_SIZE, BOARD_SIZE),
    private val levelStore: AiLevelStore? = null,
) : MviViewModel<RobotGameIntent, RobotGameState, RobotGameEffect>(
    RobotGameState(BoardRenderState.empty())
) {

    private var blackWins = 0
    private var whiteWins = 0
    private var winLine: List<Point> = emptyList()
    private var aiThinking = false
    private var pendingRollback = false

    init {
        engine.start(GameMode.AI, mySide = Side.BLACK)
        val level = levelStore?.read()
            ?.let { Difficulty.entries.getOrNull(it) }
            ?: Difficulty.MEDIUM
        ai.level = level
        updateState { it.copy(board = BoardRenderState.from(engine.snapshot()), aiLevel = level) }
    }

    override fun onIntent(intent: RobotGameIntent) {
        when (intent) {
            is RobotGameIntent.BoardTap -> {
                consume(engine.applyMove(intent.x, intent.y))
                if (isAiTurn()) scheduleAiMove()
            }
            RobotGameIntent.RestartClicked -> {
                pendingRollback = false
                consume(engine.restart())
            }
            RobotGameIntent.RollbackClicked -> {
                if (aiThinking) {
                    pendingRollback = true
                } else {
                    consume(engine.rollback(ROLLBACK_STEPS))
                }
            }
            RobotGameIntent.DifficultyClicked -> {
                val level = state.value.aiLevel
                viewModelScope.launch { emitEffect(RobotGameEffect.ShowDifficulty(level)) }
            }
            is RobotGameIntent.LevelSelected -> {
                ai.level = intent.level
                levelStore?.write(intent.level.ordinal)
                updateState { it.copy(aiLevel = intent.level) }
            }
        }
    }

    private fun isAiTurn(): Boolean {
        val snapshot = engine.snapshot()
        return !snapshot.over && snapshot.active == AI_SIDE
    }

    private fun scheduleAiMove() {
        if (aiThinking) return
        aiThinking = true
        viewModelScope.launch(Dispatchers.Default) {
            val point = ai.getPosition(aiMap())
            withContext(Dispatchers.Main.immediate) {
                aiThinking = false
                if (isAiTurn()) {
                    consume(engine.applyRemoteMove(point.x, point.y, AI_SIDE))
                }
                if (pendingRollback) {
                    pendingRollback = false
                    consume(engine.rollback(ROLLBACK_STEPS))
                }
            }
        }
    }

    private fun aiMap(): Array<IntArray> {
        val snapshot = engine.snapshot()
        return Array(snapshot.width) { x ->
            IntArray(snapshot.height) { y ->
                snapshot.cells[x][y]?.toCode() ?: EMPTY_CODE
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
            viewModelScope.launch { emitEffect(RobotGameEffect.ShowGameOver(event.winner)) }
        }
    }

    private companion object {
        const val BOARD_SIZE = 15
        const val ROLLBACK_STEPS = 2
        const val EMPTY_CODE = 0
        val AI_SIDE = Side.WHITE
    }
}
