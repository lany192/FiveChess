package com.github.lany192.gomoku.ui.robot

import androidx.lifecycle.viewModelScope
import com.github.lany192.gomoku.core.mvi.MviViewModel
import com.github.lany192.gomoku.data.settings.AiAlgorithmStore
import com.github.lany192.gomoku.data.settings.AiLevelStore
import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.AiEnginePool
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.ai.GameOutcome
import com.github.lany192.gomoku.domain.ai.GomokuAI
import com.github.lany192.gomoku.domain.engine.EngineResult
import com.github.lany192.gomoku.domain.engine.GameEngine
import com.github.lany192.gomoku.domain.model.GameEvent
import com.github.lany192.gomoku.domain.model.GameMode
import com.github.lany192.gomoku.domain.model.Point
import com.github.lany192.gomoku.domain.model.Side
import com.github.lany192.gomoku.ui.common.BoardRenderState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 人机对战：本端执黑，AI 执白
 *
 * - AI 在 [aiDispatcher] 计算，结果切回主线程按序入局，与 Intent 串行不冲突
 * - 搜索与学习钩子共用同一条单线程通道：满足 GomokuAI「同一实例不可并发调用」的约定，
 *   学习类的权重更新/落库（Room 禁止主线程访问）也因此只在后台线程发生
 * - AI 思考期间请求悔棋，先记 [pendingRollback]，等 AI 落子后一并回退两子（对齐旧版 isRollback 语义）
 * - AI 思考期间改算法，先记 [pendingAlgorithm]，等本次落子落地后再换引擎，任一时刻只有一个引擎在跑
 * - 思考期间重开棋局时，过期的 AI 落子按轮次校验丢弃
 */
class RobotGameViewModel(
    private val engine: GameEngine = GameEngine(),
    private val enginePool: AiEnginePool = AiEnginePool(BOARD_SIZE, BOARD_SIZE),
    private val levelStore: AiLevelStore? = null,
    private val algorithmStore: AiAlgorithmStore? = null,
    private val aiDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : MviViewModel<RobotGameIntent, RobotGameState, RobotGameEffect>(
    RobotGameState(BoardRenderState.empty())
) {

    private var blackWins = 0
    private var whiteWins = 0
    private var winLine: List<Point> = emptyList()
    private var winner: Side? = null
    private var drawn = false
    private var aiThinking = false
    private var pendingRollback = false
    private var pendingAlgorithm: AiAlgorithm? = null
    private var aiLevel: Difficulty = levelStore?.read() ?: Difficulty.MEDIUM
    private var aiAlgorithm: AiAlgorithm = algorithmStore?.read() ?: AiAlgorithm.DEFAULT

    /** 当前引擎实例；换算法时整体替换，不在旧实例上改行为 */
    private var ai: GomokuAI = enginePool.obtain(aiAlgorithm, aiLevel)

    init {
        engine.start(GameMode.AI, mySide = Side.BLACK)
        updateState {
            it.copy(
                board = BoardRenderState.from(engine.snapshot()),
                aiLevel = aiLevel,
                aiAlgorithm = aiAlgorithm,
            )
        }
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
            is RobotGameIntent.LevelSelected -> {
                aiLevel = intent.level
                ai.level = intent.level
                levelStore?.write(intent.level)
                updateState { it.copy(aiLevel = intent.level) }
            }
            is RobotGameIntent.AlgorithmSelected -> {
                algorithmStore?.write(intent.algorithm)
                if (aiThinking) {
                    // 本次搜索结果仍然有效，下一拍起用新引擎
                    pendingAlgorithm = intent.algorithm
                    updateState { it.copy(aiAlgorithm = intent.algorithm) }
                } else {
                    selectAlgorithm(intent.algorithm)
                }
            }
        }
    }

    /** 换引擎：构造 O(1)（大表一律 lazy，学习类复用池中实例），可在主线程直接调用 */
    private fun selectAlgorithm(algorithm: AiAlgorithm) {
        aiAlgorithm = algorithm
        ai = enginePool.obtain(algorithm, aiLevel)
        updateState { it.copy(aiAlgorithm = algorithm) }
    }

    private fun isAiTurn(): Boolean {
        val snapshot = engine.snapshot()
        return !snapshot.over && snapshot.active == AI_SIDE
    }

    private fun scheduleAiMove() {
        if (aiThinking) return
        aiThinking = true
        val instance = ai
        // 棋盘在主线程取好：AI 计算期间主线程可能重开/悔棋，引擎快照不能跨线程读
        val board = aiMap()
        viewModelScope.launch(aiDispatcher) {
            val point = instance.getPosition(board)
            withContext(Dispatchers.Main.immediate) {
                aiThinking = false
                pendingAlgorithm?.let {
                    pendingAlgorithm = null
                    selectAlgorithm(it)
                }
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
                    winner = event.winner
                    drawn = false
                    when (event.winner) {
                        Side.BLACK -> blackWins++
                        Side.WHITE -> whiteWins++
                    }
                    postGameOver(event.winner)
                }
                GameEvent.Draw -> {
                    // 满盘和棋：双方均不加胜场；AI 可能是落最后一手的一方，学习轨迹同样要闭合
                    winLine = emptyList()
                    winner = null
                    drawn = true
                    postGameOver(null)
                }
                is GameEvent.RollbackApplied -> {
                    winLine = emptyList()
                    winner = null
                    drawn = false
                    postGameReset()
                }
                GameEvent.Restarted -> {
                    winLine = emptyList()
                    winner = null
                    drawn = false
                    postGameReset()
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
                winner = winner,
                drawn = drawn,
            )
        }
    }

    /** 终局学习钩子：丢给 AI 单线程通道，与搜索不并发；引擎实例此刻取定，换算法不打断这次回调 */
    private fun postGameOver(winner: Side?) {
        val instance = ai
        val moves = engine.snapshot().moves
        viewModelScope.launch(aiDispatcher) { instance.onGameOver(GameOutcome(winner, moves)) }
    }

    /** 棋局被重开或回退：学习类据此丢弃未闭合的轨迹 */
    private fun postGameReset() {
        val instance = ai
        viewModelScope.launch(aiDispatcher) { instance.onGameReset() }
    }

    private companion object {
        const val BOARD_SIZE = 15
        const val ROLLBACK_STEPS = 2
        const val EMPTY_CODE = 0
        val AI_SIDE = Side.WHITE
    }
}
