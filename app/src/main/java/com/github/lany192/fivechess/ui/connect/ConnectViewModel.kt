package com.github.lany192.fivechess.ui.connect

import androidx.lifecycle.viewModelScope
import com.github.lany192.fivechess.core.mvi.MviViewModel
import com.github.lany192.fivechess.data.net.ChatContent
import com.github.lany192.fivechess.data.net.ConnectionItem
import com.github.lany192.fivechess.data.net.DiscoveryError
import com.github.lany192.fivechess.data.net.DiscoveryEvent
import com.github.lany192.fivechess.data.net.LanDiscoveryManager
import kotlinx.coroutines.launch

class ConnectViewModel(
    private val localIp: String,
    private val discovery: LanDiscoveryManager = LanDiscoveryManager(localIp),
) : MviViewModel<ConnectIntent, ConnectState, ConnectEffect>(ConnectState()) {

    private val chats = mutableListOf<ChatContent>()
    private var awaitingAgree = false
    private var pendingIp: String? = null
    private var handshakeDialogShowing = false

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
                discovery.sendScanBroadcast()
                viewModelScope.launch { emitEffect(ConnectEffect.ShowMessage(SCANNING_TEXT)) }
            }
            is ConnectIntent.PeerClicked -> {
                if (awaitingAgree || handshakeDialogShowing) return
                awaitingAgree = true
                pendingIp = intent.ip
                discovery.askConnect(intent.ip)
                viewModelScope.launch { emitEffect(ConnectEffect.ShowConnecting(intent.ip)) }
            }
            is ConnectIntent.HandshakeAgreed -> {
                handshakeDialogShowing = false
                discovery.accept(intent.ip)
                viewModelScope.launch {
                    emitEffect(ConnectEffect.NavigateToGame(isServer = true, ip = intent.ip))
                }
            }
            is ConnectIntent.HandshakeRejected -> {
                handshakeDialogShowing = false
                discovery.reject(intent.ip)
            }
        }
    }

    private fun onDiscoveryEvent(event: DiscoveryEvent) {
        when (event) {
            is DiscoveryEvent.PeerJoined -> addPeer(ConnectionItem(event.name, event.ip))
            is DiscoveryEvent.PeerExited -> removePeer(event.ip)
            is DiscoveryEvent.HandshakeRequested -> {
                if (handshakeDialogShowing) return
                handshakeDialogShowing = true
                viewModelScope.launch {
                    emitEffect(ConnectEffect.ShowHandshakeRequest(event.name, event.ip))
                }
            }
            is DiscoveryEvent.HandshakeAccepted -> {
                if (!awaitingAgree) return
                awaitingAgree = false
                viewModelScope.launch {
                    emitEffect(ConnectEffect.DismissConnecting)
                    emitEffect(ConnectEffect.NavigateToGame(isServer = false, ip = event.ip))
                }
            }
            is DiscoveryEvent.HandshakeRejected -> {
                if (!awaitingAgree) return
                awaitingAgree = false
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
                emitEffect(ConnectEffect.ShowMessage(describe(event.kind)))
            }
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
            awaitingAgree = false
            pendingIp = null
            viewModelScope.launch {
                emitEffect(ConnectEffect.DismissConnecting)
                emitEffect(ConnectEffect.ShowMessage("对方已退出"))
            }
        }
    }

    private fun describe(kind: DiscoveryError): String = when (kind) {
        DiscoveryError.SOCKET_NULL -> "网络套接字未就绪"
        DiscoveryError.IP_NULL -> "本机IP获取失败"
        DiscoveryError.UDP_IP_ERROR -> "目标地址无效"
        DiscoveryError.UDP_DATA_ERROR -> "数据发送失败"
        DiscoveryError.MULTICAST_ERROR -> "组播不可用"
    }

    override fun onCleared() {
        discovery.sendExitBroadcast()
        discovery.stop()
        super.onCleared()
    }

    private companion object {
        const val SCANNING_TEXT = "扫描中，请稍后......"
    }
}
