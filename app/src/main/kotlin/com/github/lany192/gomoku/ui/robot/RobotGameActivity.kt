package com.github.lany192.gomoku.ui.robot

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.lany192.gomoku.R
import com.github.lany192.gomoku.data.db.AiWeightsDatabase
import com.github.lany192.gomoku.data.db.RoomAiWeightStore
import com.github.lany192.gomoku.data.settings.SharedPrefsAiAlgorithmStore
import com.github.lany192.gomoku.data.settings.SharedPrefsAiLevelStore
import com.github.lany192.gomoku.databinding.GameSingleBinding
import com.github.lany192.gomoku.domain.ai.AiEnginePool
import com.github.lany192.gomoku.domain.model.Side
import com.github.lany192.gomoku.ui.common.setupEdgeToEdge
import kotlinx.coroutines.launch

/**
 * 人机对战
 */
class RobotGameActivity : AppCompatActivity() {
    private lateinit var binding: GameSingleBinding
    private val viewModel: RobotGameViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                RobotGameViewModel(
                    enginePool = AiEnginePool(
                        width = BOARD_SIZE,
                        height = BOARD_SIZE,
                        weightStore = RoomAiWeightStore(AiWeightsDatabase.get(app).aiWeightsDao()),
                    ),
                    levelStore = SharedPrefsAiLevelStore(app),
                    algorithmStore = SharedPrefsAiAlgorithmStore(app),
                )
            }
        }
    }

    /** 选择页返回即生效：同时带回落库后的难度与算法 */
    private val selectAi = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@registerForActivityResult
        val (algorithm, level) = AiSelectActivity.parseResult(
            data.getStringExtra(AiSelectActivity.EXTRA_ALGORITHM),
            data.getIntExtra(AiSelectActivity.EXTRA_LEVEL, -1),
        ) ?: return@registerForActivityResult
        viewModel.dispatch(RobotGameIntent.AlgorithmSelected(algorithm))
        viewModel.dispatch(RobotGameIntent.LevelSelected(level))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = GameSingleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.gameView.configure(BOARD_SIZE, BOARD_SIZE)
        binding.gameView.onCellTapped = { x, y ->
            viewModel.dispatch(RobotGameIntent.BoardTap(x, y))
        }
        binding.restart.setOnClickListener {
            viewModel.dispatch(RobotGameIntent.RestartClicked)
        }
        binding.rollback.setOnClickListener {
            viewModel.dispatch(RobotGameIntent.RollbackClicked)
        }
        binding.opponentSettings.setOnClickListener {
            val state = viewModel.state.value
            selectAi.launch(AiSelectActivity.intent(this, state.aiAlgorithm, state.aiLevel))
        }
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
            }
        }
    }

    private fun render(state: RobotGameState) {
        binding.gameView.render(state.board)
        if (state.active == Side.BLACK) {
            binding.blackActive.visibility = View.VISIBLE
            binding.whiteActive.visibility = View.INVISIBLE
        } else {
            binding.blackActive.visibility = View.INVISIBLE
            binding.whiteActive.visibility = View.VISIBLE
        }
        binding.blackWin.text = state.blackWins.toString()
        binding.whiteWin.text = state.whiteWins.toString()
        binding.opponentSummary.text = getString(
            R.string.algorithm_difficulty_format,
            getString(algorithmNameRes(state.aiAlgorithm)),
            getString(levelNameRes(state.aiLevel)),
        )
        val winner = state.winner
        binding.resultBanner.visibility = if (winner != null) View.VISIBLE else View.GONE
        if (winner != null) {
            binding.resultText.setText(
                if (winner == Side.BLACK) R.string.msg_black_win else R.string.msg_white_win
            )
        }
    }

    private companion object {
        const val BOARD_SIZE = 15
    }
}
