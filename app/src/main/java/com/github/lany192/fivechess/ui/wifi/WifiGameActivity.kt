package com.github.lany192.fivechess.ui.wifi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.lany192.fivechess.R
import com.github.lany192.fivechess.databinding.GameNetBinding
import com.github.lany192.fivechess.domain.model.Side
import kotlinx.coroutines.launch

class WifiGameActivity : AppCompatActivity() {
    private lateinit var binding: GameNetBinding
    private var waitDialog: AlertDialog? = null
    private val viewModel: WifiGameViewModel by viewModels {
        viewModelFactory {
            initializer {
                val extras = intent.extras
                WifiGameViewModel(
                    isServer = extras?.getBoolean(EXTRA_IS_SERVER) ?: false,
                    remoteIp = extras?.getString(EXTRA_IP).orEmpty(),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.extras
        if (extras == null || !extras.containsKey(EXTRA_IS_SERVER)) {
            Toast.makeText(this, "建立网络失败,请重试", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding = GameNetBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val isServer = extras.getBoolean(EXTRA_IS_SERVER)
        binding.blackName.setText(if (isServer) R.string.myself else R.string.challenger)
        binding.whiteName.setText(if (isServer) R.string.challenger else R.string.myself)
        showWaitDialog()
        binding.gameView.configure(BOARD_SIZE, BOARD_SIZE)
        binding.gameView.onCellTapped = { x, y ->
            viewModel.dispatch(WifiGameIntent.BoardTap(x, y))
        }
        binding.restart.setOnClickListener {
            viewModel.dispatch(WifiGameIntent.RestartClicked)
        }
        binding.rollback.setOnClickListener {
            viewModel.dispatch(WifiGameIntent.RollbackClicked)
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
                            WifiGameEffect.DismissConnecting -> waitDialog?.dismiss()
                            is WifiGameEffect.ShowGameResult -> showGameResultDialog(effect.iWon)
                            WifiGameEffect.ShowRollbackRequest -> showRollbackDialog()
                            is WifiGameEffect.ShowMessage -> Toast.makeText(
                                this@WifiGameActivity, effect.text, Toast.LENGTH_SHORT,
                            ).show()
                            WifiGameEffect.Exit -> finish()
                        }
                    }
                }
            }
        }
    }

    private fun render(state: WifiGameState) {
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

    private fun showWaitDialog() {
        if (waitDialog == null) {
            waitDialog = AlertDialog.Builder(this)
                .setMessage("建立连接中，请稍后")
                .setCancelable(true)
                .create()
        }
        waitDialog?.show()
    }

    private fun showGameResultDialog(iWon: Boolean) {
        val message = if (iWon) "恭喜你！你赢了！" else "很遗憾！你输了！"
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(R.string.Continue) { _, _ ->
                viewModel.dispatch(WifiGameIntent.RestartClicked)
            }
            .setNegativeButton(R.string.exit) { _, _ -> finish() }
            .show()
    }

    private fun showRollbackDialog() {
        AlertDialog.Builder(this)
            .setMessage("是否同意对方悔棋")
            .setCancelable(false)
            .setPositiveButton(R.string.agree) { _, _ ->
                viewModel.dispatch(WifiGameIntent.RollbackAgreed)
            }
            .setNegativeButton(R.string.reject) { _, _ ->
                viewModel.dispatch(WifiGameIntent.RollbackRejected)
            }
            .show()
    }

    companion object {
        fun start(context: Context, server: Boolean, dstIp: String) {
            val intent = Intent(context, WifiGameActivity::class.java).apply {
                putExtra(EXTRA_IS_SERVER, server)
                putExtra(EXTRA_IP, dstIp)
            }
            context.startActivity(intent)
        }

        private const val EXTRA_IS_SERVER = "isServer"
        private const val EXTRA_IP = "ip"
        private const val BOARD_SIZE = 15
    }
}
