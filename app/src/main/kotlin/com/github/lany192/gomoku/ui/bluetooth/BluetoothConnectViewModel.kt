package com.github.lany192.gomoku.ui.bluetooth

import androidx.lifecycle.viewModelScope
import com.github.lany192.gomoku.R
import com.github.lany192.gomoku.core.mvi.MviViewModel
import com.github.lany192.gomoku.data.bt.BtDiscoveryManager
import com.github.lany192.gomoku.data.net.ConnectionItem
import com.github.lany192.gomoku.data.net.DiscoveryEvent
import com.github.lany192.gomoku.ui.common.messageRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 蓝牙联机页 ViewModel（对齐 ConnectViewModel，差异见 BtDiscoveryManager）
 *
 * 与局域网的不同：
 * - 无互相发起，因此没有"互戳"仲裁：被叫方 accept 成功即由用户裁决
 * - 无聊天通道（蓝牙没有连接外的带外通道）
 * - 导航前必须先释放监听口，把服务 UUID 让给对局页
 */
class BluetoothConnectViewModel(
    private val discovery: BtDiscoveryManager,
) : MviViewModel<BtConnectIntent, BtConnectState, BtConnectEffect>(BtConnectState()) {

    private var awaitingAgree = false
    private var pendingAddress: String? = null
    private var agreeTimeoutJob: Job? = null
    private var handshakeDialogShowing = false
    private var incomingAddress: String? = null

    init {
        viewModelScope.launch {
            discovery.events.collect(::onDiscoveryEvent)
        }
        discovery.start()
    }

    override fun onIntent(intent: BtConnectIntent) {
        when (intent) {
            BtConnectIntent.ScanClicked -> {
                // 清掉可能已离线的残留 peer，已配对设备由扫描重新播报
                updateState { it.copy(peers = emptyList()) }
                discovery.scan()
                viewModelScope.launch { emitEffect(BtConnectEffect.ShowMessage(R.string.msg_scanning)) }
            }
            is BtConnectIntent.PeerClicked -> {
                if (awaitingAgree || handshakeDialogShowing) return
                awaitingAgree = true
                pendingAddress = intent.address
                discovery.askConnect(intent.address)
                viewModelScope.launch { emitEffect(BtConnectEffect.ShowConnecting(intent.address)) }
                agreeTimeoutJob = viewModelScope.launch {
                    delay(AGREE_TIMEOUT_MS)
                    if (awaitingAgree && pendingAddress == intent.address) {
                        resetPending()
                        emitEffect(BtConnectEffect.DismissConnecting)
                        emitEffect(BtConnectEffect.ShowMessage(R.string.msg_bt_peer_no_response))
                    }
                }
            }
            BtConnectIntent.ConnectCancelled -> {
                if (!awaitingAgree) return
                resetPending()
            }
            BtConnectIntent.HandshakeAgreed -> viewModelScope.launch {
                val address = incomingAddress ?: return@launch
                handshakeDialogShowing = false
                incomingAddress = null
                // 顺序不能动：先让出服务 UUID，再回写裁决字节。
                // 反过来的话，主叫可能在收到 AGREE 后立刻连上这个即将关闭的旧监听口。
                discovery.stopListening()
                discovery.accept()
                discovery.stop()
                emitEffect(BtConnectEffect.DismissHandshake)
                emitEffect(BtConnectEffect.NavigateToGame(isServer = true, address = address))
            }
            BtConnectIntent.HandshakeRejected -> viewModelScope.launch {
                handshakeDialogShowing = false
                incomingAddress = null
                discovery.reject()
                emitEffect(BtConnectEffect.DismissHandshake)
            }
        }
    }

    private fun onDiscoveryEvent(event: DiscoveryEvent) {
        when (event) {
            is DiscoveryEvent.PeerJoined -> addPeer(ConnectionItem(event.name, event.ip))
            is DiscoveryEvent.PeerExited -> removePeer(event.ip)
            is DiscoveryEvent.HandshakeRequested -> onHandshakeRequested(event)
            is DiscoveryEvent.HandshakeAccepted -> {
                // 只响应当前等待对象的回应，迟到/错配的忽略
                if (!awaitingAgree || pendingAddress != event.ip) return
                resetPending()
                viewModelScope.launch {
                    discovery.stop()
                    emitEffect(BtConnectEffect.DismissConnecting)
                    emitEffect(BtConnectEffect.NavigateToGame(isServer = false, address = event.ip))
                }
            }
            is DiscoveryEvent.HandshakeRejected -> {
                if (!awaitingAgree || pendingAddress != event.ip) return
                resetPending()
                viewModelScope.launch {
                    emitEffect(BtConnectEffect.DismissConnecting)
                    emitEffect(BtConnectEffect.ShowMessage(R.string.msg_request_rejected))
                }
            }
            // 蓝牙没有连接外的带外通道，不会收到聊天
            is DiscoveryEvent.ChatReceived -> Unit
            is DiscoveryEvent.Error -> viewModelScope.launch {
                // 蓝牙建连失败就是"对端不可达"的常规路径，必须同步收掉等待框，
                // 否则用户只能干等超时，提示还被对话框盖住
                if (awaitingAgree) {
                    resetPending()
                    emitEffect(BtConnectEffect.DismissConnecting)
                }
                emitEffect(BtConnectEffect.ShowMessage(event.kind.messageRes()))
            }
        }
    }

    private fun onHandshakeRequested(event: DiscoveryEvent.HandshakeRequested) {
        if (awaitingAgree || handshakeDialogShowing) {
            // 忙：立即拒绝，避免请求方空等（管理器已保证同一时刻只有一个待裁决连接）
            viewModelScope.launch { discovery.reject() }
            return
        }
        handshakeDialogShowing = true
        incomingAddress = event.ip
        viewModelScope.launch {
            emitEffect(BtConnectEffect.ShowHandshakeRequest(event.name))
        }
    }

    private fun addPeer(item: ConnectionItem) {
        updateState { state ->
            state.copy(peers = state.peers.filterNot { it.ip == item.ip } + item)
        }
    }

    private fun removePeer(address: String) {
        updateState { state ->
            state.copy(peers = state.peers.filterNot { it.ip == address })
        }
        if (awaitingAgree && pendingAddress == address) {
            resetPending()
            viewModelScope.launch {
                emitEffect(BtConnectEffect.DismissConnecting)
                emitEffect(BtConnectEffect.ShowMessage(R.string.msg_peer_exited))
            }
        }
        if (handshakeDialogShowing && incomingAddress == address) {
            handshakeDialogShowing = false
            incomingAddress = null
            viewModelScope.launch {
                emitEffect(BtConnectEffect.DismissHandshake)
                emitEffect(BtConnectEffect.ShowMessage(R.string.msg_peer_exited))
            }
        }
    }

    private fun resetPending() {
        awaitingAgree = false
        pendingAddress = null
        agreeTimeoutJob?.cancel()
        agreeTimeoutJob = null
    }

    override fun onCleared() {
        discovery.stop()
        super.onCleared()
    }

    private companion object {
        /** 蓝牙握手链路较长（connect + 用户裁决），超时给得比局域网宽 */
        const val AGREE_TIMEOUT_MS = 20_000L
    }
}
