package com.github.lany192.fivechess.ui.person

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.lany192.fivechess.R
import com.github.lany192.fivechess.databinding.GameFightBinding
import com.github.lany192.fivechess.domain.model.Side
import com.github.lany192.fivechess.ui.common.setupEdgeToEdge
import kotlinx.coroutines.launch

class PersonGameActivity : AppCompatActivity() {
    private lateinit var binding: GameFightBinding
    private val viewModel: PersonGameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = GameFightBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
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
