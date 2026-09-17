package com.github.lany192.fivechess.ui.robot

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.lany192.fivechess.R
import com.github.lany192.fivechess.data.settings.SharedPrefsAiLevelStore
import com.github.lany192.fivechess.databinding.GameSingleBinding
import com.github.lany192.fivechess.domain.ai.Difficulty
import com.github.lany192.fivechess.domain.model.Side
import com.github.lany192.fivechess.ui.common.setupEdgeToEdge
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
                RobotGameViewModel(levelStore = SharedPrefsAiLevelStore(app))
            }
        }
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
        binding.difficulty.setOnClickListener {
            viewModel.dispatch(RobotGameIntent.DifficultyClicked)
        }
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is RobotGameEffect.ShowDifficulty -> showDifficultyDialog(effect.current)
                        }
                    }
                }
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
        val winner = state.winner
        binding.resultBanner.visibility = if (winner != null) View.VISIBLE else View.GONE
        if (winner != null) {
            binding.resultText.setText(
                if (winner == Side.BLACK) R.string.msg_black_win else R.string.msg_white_win
            )
        }
    }

    private fun showDifficultyDialog(current: Difficulty) {
        val names = arrayOf(
            getString(R.string.ai_level_easy),
            getString(R.string.ai_level_medium),
            getString(R.string.ai_level_hard),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.difficulty))
            .setSingleChoiceItems(names, current.ordinal) { dialog, which ->
                dialog.dismiss()
                viewModel.dispatch(RobotGameIntent.LevelSelected(Difficulty.entries[which]))
            }
            .show()
    }

    private companion object {
        const val BOARD_SIZE = 15
    }
}
