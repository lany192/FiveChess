package com.github.lany192.fivechess.ui.person

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.lany192.fivechess.R
import com.github.lany192.fivechess.databinding.GameFightBinding
import com.github.lany192.fivechess.domain.model.Side
import kotlinx.coroutines.launch

class PersonGameActivity : AppCompatActivity() {
    private lateinit var binding: GameFightBinding
    private val viewModel: PersonGameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = GameFightBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.gameView.configure(BOARD_SIZE, BOARD_SIZE)
        binding.gameView.onCellTapped = { x, y ->
            viewModel.dispatch(PersonGameIntent.BoardTap(x, y))
        }
        binding.restart.setOnClickListener {
            viewModel.dispatch(PersonGameIntent.RestartClicked)
        }
        binding.rollback.setOnClickListener {
            viewModel.dispatch(PersonGameIntent.RollbackClicked)
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
                            is PersonGameEffect.ShowGameOver -> showGameOverDialog(effect.winner)
                        }
                    }
                }
            }
        }
    }

    private fun render(state: PersonGameState) {
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
    }

    private fun showGameOverDialog(winner: Side) {
        val message = if (winner == Side.BLACK) "黑方胜！" else "白方胜！"
        AlertDialog.Builder(this)
            .setCancelable(false)
            .setMessage(message)
            .setPositiveButton(R.string.Continue) { _, _ ->
                viewModel.dispatch(PersonGameIntent.RestartClicked)
            }
            .setNegativeButton(R.string.exit) { _, _ -> finish() }
            .show()
    }

    private companion object {
        const val BOARD_SIZE = 15
    }
}
