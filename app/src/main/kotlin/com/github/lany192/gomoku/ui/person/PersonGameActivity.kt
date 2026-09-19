package com.github.lany192.gomoku.ui.person

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import androidx.lifecycle.repeatOnLifecycle
import com.github.lany192.gomoku.R
import com.github.lany192.gomoku.databinding.GameFightBinding
import com.github.lany192.gomoku.domain.model.Side
import com.github.lany192.gomoku.ui.common.TurnCountdown
import com.github.lany192.gomoku.ui.common.setupEdgeToEdge
import kotlinx.coroutines.launch

class PersonGameActivity : AppCompatActivity() {
    private lateinit var binding: GameFightBinding
    private val viewModel: PersonGameViewModel by viewModels()

    /** 记分卡下方的倒计时常态色，用于从警告色复位 */
    private var countdownNormalColor = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = GameFightBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge(binding.root)
        countdownNormalColor = binding.countdown.currentTextColor
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
        renderCountdown(state.remainingMillis)
        val winner = state.winner
        val showBanner = winner != null || state.drawn
        binding.resultBanner.visibility = if (showBanner) View.VISIBLE else View.GONE
        if (showBanner) {
            binding.resultText.setText(
                when {
                    state.drawn -> R.string.msg_draw_end
                    state.timedOut && winner == Side.BLACK -> R.string.msg_black_timeout
                    state.timedOut -> R.string.msg_white_timeout
                    winner == Side.BLACK -> R.string.msg_black_win
                    else -> R.string.msg_white_win
                }
            )
        }
    }

    private fun renderCountdown(remainingMillis: Long) {
        binding.countdown.text = TurnCountdown.format(remainingMillis)
        val warning = remainingMillis in 1 until WARN_MILLIS
        // 主题的 colorError 就是 @color/error（values 与 values-night 各有一份）
        binding.countdown.setTextColor(
            if (warning) ContextCompat.getColor(this, R.color.error) else countdownNormalColor
        )
    }

    private companion object {
        const val BOARD_SIZE = 15
        const val WARN_MILLIS = 30_000L
    }
}
