package com.github.lany192.gomoku.ui.net

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.lany192.gomoku.R
import com.github.lany192.gomoku.data.bt.BtGameClient
import com.github.lany192.gomoku.data.bt.bluetoothAdapterOf
import com.github.lany192.gomoku.data.net.LanGameClient
import com.github.lany192.gomoku.databinding.GameNetBinding
import com.github.lany192.gomoku.domain.model.GameMode
import com.github.lany192.gomoku.domain.model.Side
import com.github.lany192.gomoku.ui.common.TurnCountdown
import com.github.lany192.gomoku.ui.common.setupEdgeToEdge
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * 联机对局页（局域网 / 蓝牙共用）
 *
 * 两种模式只差传输层实现，由 [EXTRA_BLUETOOTH] 决定注入 [LanGameClient] 还是 [BtGameClient]。
 */
class NetGameActivity : AppCompatActivity() {
    private lateinit var binding: GameNetBinding
    private var waitDialog: AlertDialog? = null
    private var rollbackDialog: AlertDialog? = null
    private var drawDialog: AlertDialog? = null

    /** 记分卡下方的倒计时常态色，用于从警告色复位 */
    private var countdownNormalColor = 0
    private val viewModel: NetGameViewModel by viewModels {
        viewModelFactory {
            initializer {
                val extras = intent.extras
                val isServer = extras?.getBoolean(EXTRA_IS_SERVER) ?: false
                val peer = extras?.getString(EXTRA_PEER).orEmpty()
                val bluetooth = extras?.getBoolean(EXTRA_BLUETOOTH) ?: false
                val mySide = if (isServer) Side.BLACK else Side.WHITE
                NetGameViewModel(
                    mode = if (bluetooth) GameMode.BLUETOOTH else GameMode.LAN,
                    mySide = mySide,
                    transport = if (bluetooth) {
                        BtGameClient(bluetoothAdapterOf(this@NetGameActivity), isServer, peer)
                    } else {
                        LanGameClient(isServer, peer)
                    },
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
        countdownNormalColor = binding.countdown.currentTextColor
        binding.toolbar.setNavigationOnClickListener { finish() }
        val isServer = extras.getBoolean(EXTRA_IS_SERVER)
        binding.blackName.setText(if (isServer) R.string.myself else R.string.challenger)
        binding.whiteName.setText(if (isServer) R.string.challenger else R.string.myself)
        showWaitDialog()
        binding.gameView.configure(BOARD_SIZE, BOARD_SIZE)
        binding.gameView.onCellTapped = { x, y ->
            viewModel.dispatch(NetGameIntent.BoardTap(x, y))
        }
        binding.restart.setOnClickListener {
            viewModel.dispatch(NetGameIntent.RestartClicked)
        }
        binding.rollback.setOnClickListener {
            viewModel.dispatch(NetGameIntent.RollbackClicked)
        }
        binding.requestEqual.setOnClickListener {
            viewModel.dispatch(NetGameIntent.DrawClicked)
        }
        binding.fail.setOnClickListener {
            viewModel.dispatch(NetGameIntent.ResignClicked)
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
                            NetGameEffect.DismissConnecting -> waitDialog?.dismiss()
                            NetGameEffect.ShowRollbackRequest -> showRollbackDialog()
                            NetGameEffect.ShowDrawRequest -> showDrawRequestDialog()
                            NetGameEffect.ShowResignConfirm -> showResignConfirmDialog()
                            is NetGameEffect.ShowMessage -> Toast.makeText(
                                this@NetGameActivity, effect.text, Toast.LENGTH_SHORT,
                            ).show()
                            NetGameEffect.Exit -> finish()
                        }
                    }
                }
            }
        }
    }

    private fun render(state: NetGameState) {
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
        renderCountdown(state.connected, state.remainingMillis)
        when (val end = state.end) {
            null -> binding.resultBanner.visibility = View.GONE
            NetGameEnd.Draw -> {
                binding.resultBanner.visibility = View.VISIBLE
                binding.resultText.setText(R.string.msg_draw_end)
            }
            is NetGameEnd.Win -> {
                binding.resultBanner.visibility = View.VISIBLE
                binding.resultText.setText(
                    if (end.winner == state.mySide) R.string.msg_i_won else R.string.msg_i_lost
                )
            }
            is NetGameEnd.Timeout -> {
                binding.resultBanner.visibility = View.VISIBLE
                binding.resultText.setText(
                    if (end.winner == state.mySide) R.string.msg_peer_timeout else R.string.msg_self_timeout
                )
            }
        }
        // 终局期间作废待决协商：对话框可能正开着（超时/认输与协商撞在一起），幂等关闭
        if (state.end != null) {
            rollbackDialog?.dismiss()
            drawDialog?.dismiss()
        }
    }

    private fun renderCountdown(connected: Boolean, remainingMillis: Long) {
        binding.countdown.text =
            if (connected) TurnCountdown.format(remainingMillis) else getString(R.string.countdown_idle)
        val warning = connected && remainingMillis in 1 until WARN_MILLIS
        // 主题的 colorError 就是 @color/error（values 与 values-night 各有一份）
        binding.countdown.setTextColor(
            if (warning) ContextCompat.getColor(this, R.color.error) else countdownNormalColor
        )
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

    private fun showRollbackDialog() {
        rollbackDialog = MaterialAlertDialogBuilder(this)
            .setMessage(R.string.msg_rollback_ask)
            .setCancelable(false)
            .setPositiveButton(R.string.agree) { _, _ ->
                viewModel.dispatch(NetGameIntent.RollbackAgreed)
            }
            .setNegativeButton(R.string.reject) { _, _ ->
                viewModel.dispatch(NetGameIntent.RollbackRejected)
            }
            .show()
    }

    private fun showDrawRequestDialog() {
        drawDialog = MaterialAlertDialogBuilder(this)
            .setMessage(R.string.msg_draw_ask)
            .setCancelable(false)
            .setPositiveButton(R.string.agree) { _, _ ->
                viewModel.dispatch(NetGameIntent.DrawAgreed)
            }
            .setNegativeButton(R.string.reject) { _, _ ->
                viewModel.dispatch(NetGameIntent.DrawRejected)
            }
            .show()
    }

    private fun showResignConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.msg_resign_confirm)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.dispatch(NetGameIntent.ResignConfirmed)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        /** 局域网对局：peer 为对端 IP */
        fun startLan(context: Context, server: Boolean, dstIp: String) {
            context.startActivity(buildIntent(context, server, dstIp, bluetooth = false))
        }

        /** 蓝牙对局：peer 为对端 MAC 地址 */
        fun startBluetooth(context: Context, server: Boolean, address: String) {
            context.startActivity(buildIntent(context, server, address, bluetooth = true))
        }

        private fun buildIntent(context: Context, server: Boolean, peer: String, bluetooth: Boolean) =
            Intent(context, NetGameActivity::class.java).apply {
                putExtra(EXTRA_IS_SERVER, server)
                putExtra(EXTRA_PEER, peer)
                putExtra(EXTRA_BLUETOOTH, bluetooth)
            }

        private const val EXTRA_IS_SERVER = "isServer"
        private const val EXTRA_PEER = "peer"
        private const val EXTRA_BLUETOOTH = "bluetooth"
        private const val BOARD_SIZE = 15
        private const val WARN_MILLIS = 30_000L
    }
}
