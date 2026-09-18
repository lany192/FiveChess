package com.github.lany192.gomoku.ui.robot

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.lany192.gomoku.R
import com.github.lany192.gomoku.data.settings.SharedPrefsAiLevelStore
import com.github.lany192.gomoku.databinding.GameSingleBinding
import com.github.lany192.gomoku.domain.ai.Difficulty
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
        setupDifficultySpinner()
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
        if (binding.difficulty.selectedItemPosition != state.aiLevel.ordinal) {
            binding.difficulty.setSelection(state.aiLevel.ordinal)
        }
        val winner = state.winner
        binding.resultBanner.visibility = if (winner != null) View.VISIBLE else View.GONE
        if (winner != null) {
            binding.resultText.setText(
                if (winner == Side.BLACK) R.string.msg_black_win else R.string.msg_white_win
            )
        }
    }

    private fun levelName(level: Difficulty): String = getString(
        when (level) {
            Difficulty.NOVICE -> R.string.ai_level_novice
            Difficulty.EASY -> R.string.ai_level_easy
            Difficulty.MEDIUM -> R.string.ai_level_medium
            Difficulty.HARD -> R.string.ai_level_hard
            Difficulty.MASTER -> R.string.ai_level_master
        }
    )

    private fun setupDifficultySpinner() {
        binding.difficulty.adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            Difficulty.entries.map(::levelName),
        ) {
            // 收起态带"难度"前缀，下拉项只显示难度名
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).apply {
                    (this as TextView).text = getString(R.string.difficulty_format, getItem(position))
                }
        }.apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        // 先对齐已存难度再挂监听，避免适配器初始化触发的首帧回调误发改写
        binding.difficulty.setSelection(viewModel.state.value.aiLevel.ordinal, false)
        binding.difficulty.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val level = Difficulty.entries[position]
                if (level != viewModel.state.value.aiLevel) {
                    viewModel.dispatch(RobotGameIntent.LevelSelected(level))
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private companion object {
        const val BOARD_SIZE = 15
    }
}
