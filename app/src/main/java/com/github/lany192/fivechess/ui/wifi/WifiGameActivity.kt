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
import com.github.lany192.fivechess.ui.common.setupEdgeToEdge
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
            Toast.makeText(this, R.string.msg_connect_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding = GameNetBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
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
        binding.requestEqual.setOnClickListener {
            viewModel.dispatch(WifiGameIntent.DrawClicked)
        }
        binding.fail.setOnClickListener {
            viewModel.dispatch(WifiGameIntent.ResignClicked)
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
                            WifiGameEffect.ShowDrawRequest -> showDrawRequestDialog()
                            WifiGameEffect.ShowResignConfirm -> showResignConfirmDialog()
                            WifiGameEffect.ShowDrawEnd -> showDrawEndDialog()
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
            waitDialog = MaterialAlertDialogBuilder(this)
                .setMessage(R.string.msg_connecting)
                .setCancelable(true)
                .create()
        }
        waitDialog?.show()
    }

    private fun showGameResultDialog(iWon: Boolean) {
        val message = if (iWon) R.string.msg_i_won else R.string.msg_i_lost
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_game_over)
            .setMessage(message)
            .setPositiveButton(R.string.Continue) { _, _ ->
                viewModel.dispatch(WifiGameIntent.RestartClicked)
            }
            .setNegativeButton(R.string.exit) { _, _ -> finish() }
            .show()
    }

    private fun showRollbackDialog() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.msg_rollback_ask)
            .setCancelable(false)
            .setPositiveButton(R.string.agree) { _, _ ->
                viewModel.dispatch(WifiGameIntent.RollbackAgreed)
            }
            .setNegativeButton(R.string.reject) { _, _ ->
                viewModel.dispatch(WifiGameIntent.RollbackRejected)
            }
            .show()
    }

    private fun showDrawRequestDialog() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.msg_draw_ask)
            .setCancelable(false)
            .setPositiveButton(R.string.agree) { _, _ ->
                viewModel.dispatch(WifiGameIntent.DrawAgreed)
            }
            .setNegativeButton(R.string.reject) { _, _ ->
                viewModel.dispatch(WifiGameIntent.DrawRejected)
            }
            .show()
    }

    private fun showResignConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.msg_resign_confirm)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.dispatch(WifiGameIntent.ResignConfirmed)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDrawEndDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_game_over)
            .setMessage(R.string.msg_draw_end)
            .setCancelable(false)
            .setPositiveButton(R.string.Continue) { _, _ ->
                viewModel.dispatch(WifiGameIntent.RestartClicked)
            }
            .setNegativeButton(R.string.exit) { _, _ -> finish() }
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
