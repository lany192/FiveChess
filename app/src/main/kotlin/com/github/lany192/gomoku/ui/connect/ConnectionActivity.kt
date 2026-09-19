package com.github.lany192.gomoku.ui.connect

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.lany192.gomoku.R
import com.github.lany192.gomoku.data.net.ChatContent
import com.github.lany192.gomoku.databinding.ActivityConnectBinding
import com.github.lany192.gomoku.databinding.ChatDialogBinding
import com.github.lany192.gomoku.ui.common.setupEdgeToEdge
import com.github.lany192.gomoku.ui.net.NetGameActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ConnectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConnectBinding
    private val peerAdapter = PeerAdapter(this, idLabel = R.string.label_ip) { item ->
        viewModel.dispatch(ConnectIntent.PeerClicked(item.ip))
    }
    private val chatAdapter = ChatAdapter()
    private var handshakeDialog: AlertDialog? = null
    private var waitDialog: AlertDialog? = null
    private var chatDialog: AlertDialog? = null
    private var chatBinding: ChatDialogBinding? = null

    private val viewModel: ConnectViewModel by viewModels {
        viewModelFactory {
            initializer {
                ConnectViewModel(localIp = localIpAddress())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (localIpAddress().isEmpty()) {
            Toast.makeText(this, R.string.msg_wifi_check, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        binding = ActivityConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.scan.setOnClickListener {
            viewModel.dispatch(ConnectIntent.ScanClicked)
        }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = peerAdapter
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { peerAdapter.submit(it.peers) }
                }
                launch {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is ConnectEffect.ShowHandshakeRequest -> showHandshakeDialog(effect.name, effect.ip)
                            ConnectEffect.DismissHandshake -> handshakeDialog?.dismiss()
                            is ConnectEffect.ShowConnecting -> showConnectingDialog(effect.ip)
                            ConnectEffect.DismissConnecting -> waitDialog?.dismiss()
                            is ConnectEffect.NavigateToGame -> NetGameActivity.startLan(
                                this@ConnectionActivity, effect.isServer, effect.ip,
                            )
                            is ConnectEffect.ShowMessage -> Toast.makeText(
                                this@ConnectionActivity, effect.resId, Toast.LENGTH_LONG,
                            ).show()
                            is ConnectEffect.ShowChat -> showChatDialog(effect.messages)
                        }
                    }
                }
            }
        }
    }

    private fun showHandshakeDialog(name: String, ip: String) {
        val dialog = handshakeDialog ?: MaterialAlertDialogBuilder(this)
            .setCancelable(false)
            .create()
            .also { handshakeDialog = it }
        dialog.setMessage(name + getString(R.string.fight_request))
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getText(R.string.agree)) { _, _ ->
            viewModel.dispatch(ConnectIntent.HandshakeAgreed(ip))
        }
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getText(R.string.reject)) { _, _ ->
            viewModel.dispatch(ConnectIntent.HandshakeRejected(ip))
        }
        if (!dialog.isShowing) dialog.show()
    }

    private fun showConnectingDialog(ip: String) {
        val dialog = waitDialog ?: MaterialAlertDialogBuilder(this)
            .setCancelable(true)
            .create()
            .also {
                it.setOnCancelListener { viewModel.dispatch(ConnectIntent.ConnectCancelled) }
                waitDialog = it
            }
        dialog.setMessage(getString(R.string.msg_wait_peer, ip))
        if (!dialog.isShowing) dialog.show()
    }

    private fun showChatDialog(messages: List<ChatContent>) {
        chatAdapter.submit(messages)
        if (chatDialog == null) {
            val dialogBinding = ChatDialogBinding.inflate(layoutInflater)
            dialogBinding.listChat.layoutManager = LinearLayoutManager(this)
            dialogBinding.listChat.adapter = chatAdapter
            dialogBinding.btnSend.setOnClickListener { sendChatMessage() }
            // 软键盘的回车键也直接发送，省去按键切换
            dialogBinding.editChat.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendChatMessage()
                    true
                } else {
                    false
                }
            }
            chatBinding = dialogBinding
            chatDialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_chat_title)
                .setView(dialogBinding.root)
                .create()
        }
        if (chatDialog?.isShowing != true) chatDialog?.show()
    }

    private fun sendChatMessage() {
        val text = chatBinding?.editChat?.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        viewModel.dispatch(ConnectIntent.SendChat(text))
        chatBinding?.editChat?.text = null
    }

    private fun localIpAddress(): String {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wm.isWifiEnabled) return ""
        @Suppress("DEPRECATION")
        val ip = wm.connectionInfo.ipAddress
        return if (ip != 0) intToIp(ip) else ""
    }

    private fun intToIp(i: Int): String =
        (i and 0xFF).toString() + "." + (i shr 8 and 0xFF) + "." + (i shr 16 and 0xFF) + "." + (i shr 24 and 0xFF)
}
