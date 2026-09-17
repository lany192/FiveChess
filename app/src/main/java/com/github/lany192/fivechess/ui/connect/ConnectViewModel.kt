package com.github.lany192.fivechess.ui.connect

import androidx.lifecycle.viewModelScope
import com.github.lany192.fivechess.core.mvi.MviViewModel
import com.github.lany192.fivechess.data.net.ChatContent
import com.github.lany192.fivechess.data.net.ConnectionItem
import com.github.lany192.fivechess.data.net.DiscoveryEvent
import com.github.lany192.fivechess.data.net.LanDiscoveryManager
import com.github.lany192.fivechess.ui.common.describe
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ConnectViewModel(
    private val localIp: String,
    private val discovery: LanDiscoveryManager = LanDiscoveryManager(localIp),
) : MviViewModel<ConnectIntent, ConnectState, ConnectEffect>(ConnectState()) {

    private val chats = mutableListOf<ChatContent>()
    private var awaitingAgree = false
    private var pendingIp: String? = null
    private var agreeTimeoutJob: Job? = null
    private var handshakeDialogShowing = false
    private var incomingIp: String? = null

    init {
        viewModelScope.launch {
            discovery.events.collect(::onDiscoveryEvent)
        }
        discovery.start()
        discovery.sendScanBroadcast()
    }

    override fun onIntent(intent: ConnectIntent) {
        when (intent) {
            ConnectIntent.ScanClicked -> {
                // 清掉可能已离线的残留 peer，依赖广播应答重建列表
                updateState { it.copy(peers = emptyList()) }
                discovery.sendScanBroadcast()
                viewModelScope.launch { emitEffect(ConnectEffect.ShowMessage(SCANNING_TEXT)) }
            }
            is ConnectIntent.PeerClicked -> {
                if (awaitingAgree || handshakeDialogShowing) return
                awaitingAgree = true
                pendingIp = intent.ip
                discovery.askConnect(intent.ip)
                viewModelScope.launch { emitEffect(ConnectEffect.ShowConnecting(intent.ip)) }
                agreeTimeoutJob = viewModelScope.launch {
                    delay(AGREE_TIMEOUT_MS)
                    if (awaitingAgree && pendingIp == intent.ip) {
                        resetPending()
                        emitEffect(ConnectEffect.DismissConnecting)
                        emitEffect(ConnectEffect.ShowMessage("对方无响应，请稍后再试"))
                    }
                }
            }
            ConnectIntent.ConnectCancelled -> {
                if (!awaitingAgree) return
                resetPending()
            }
            is ConnectIntent.HandshakeAgreed -> {
                handshakeDialogShowing = false
                incomingIp = null
                discovery.accept(intent.ip)
                viewModelScope.launch {
                    emitEffect(ConnectEffect.NavigateToGame(isServer = true, ip = intent.ip))
                }
            }
            is ConnectIntent.HandshakeRejected -> {
                handshakeDialogShowing = false
                incomingIp = null
                discovery.reject(intent.ip)
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
                if (!awaitingAgree || pendingIp != event.ip) return
                resetPending()
                viewModelScope.launch {
                    emitEffect(ConnectEffect.DismissConnecting)
                    emitEffect(ConnectEffect.NavigateToGame(isServer = false, ip = event.ip))
                }
            }
            is DiscoveryEvent.HandshakeRejected -> {
                if (!awaitingAgree || pendingIp != event.ip) return
                resetPending()
                viewModelScope.launch {
                    emitEffect(ConnectEffect.DismissConnecting)
                    emitEffect(ConnectEffect.ShowMessage("对方拒绝了你的请求"))
                }
            }
            is DiscoveryEvent.ChatReceived -> {
                chats.add(ChatContent(event.from.name + "(" + event.from.ip + ")", event.content))
                viewModelScope.launch { emitEffect(ConnectEffect.ShowChat(chats.toList())) }
            }
            is DiscoveryEvent.Error -> viewModelScope.launch {
                emitEffect(ConnectEffect.ShowMessage(event.kind.describe()))
            }
        }
    }

    private fun onHandshakeRequested(event: DiscoveryEvent.HandshakeRequested) {
        if (awaitingAgree && pendingIp == event.ip) {
            // 互戳：双方各自比较 IP，小的一方自动接受对方请求当 server，裁定结果必然唯一
            if (localIp < event.ip) {
                resetPending()
                discovery.accept(event.ip)
                viewModelScope.launch {
                    emitEffect(ConnectEffect.DismissConnecting)
                    emitEffect(ConnectEffect.NavigateToGame(isServer = true, ip = event.ip))
                }
            }
            return
        }
        if (awaitingAgree || handshakeDialogShowing) {
            // 忙：立即拒绝，避免请求方空等
            discovery.reject(event.ip)
            return
        }
        handshakeDialogShowing = true
        incomingIp = event.ip
        viewModelScope.launch {
            emitEffect(ConnectEffect.ShowHandshakeRequest(event.name, event.ip))
        }
    }

    private fun addPeer(item: ConnectionItem) {
        updateState { state ->
            state.copy(peers = state.peers.filterNot { it.ip == item.ip } + item)
        }
    }

    private fun removePeer(ip: String) {
        updateState { state ->
            state.copy(peers = state.peers.filterNot { it.ip == ip })
        }
        if (awaitingAgree && pendingIp == ip) {
            resetPending()
            viewModelScope.launch {
                emitEffect(ConnectEffect.DismissConnecting)
                emitEffect(ConnectEffect.ShowMessage("对方已退出"))
            }
        }
        if (handshakeDialogShowing && incomingIp == ip) {
            handshakeDialogShowing = false
            incomingIp = null
            viewModelScope.launch {
                emitEffect(ConnectEffect.DismissHandshake)
                emitEffect(ConnectEffect.ShowMessage("对方已退出"))
            }
        }
    }

    private fun resetPending() {
        awaitingAgree = false
        pendingIp = null
        agreeTimeoutJob?.cancel()
        agreeTimeoutJob = null
    }

    override fun onCleared() {
        discovery.sendExitBroadcast()
        discovery.stop()
        super.onCleared()
    }

    private companion object {
        const val SCANNING_TEXT = "扫描中，请稍后......"
        const val AGREE_TIMEOUT_MS = 10_000L
    }
}
