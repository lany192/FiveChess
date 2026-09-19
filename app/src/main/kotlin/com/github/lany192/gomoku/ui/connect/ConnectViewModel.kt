package com.github.lany192.gomoku.ui.connect

import androidx.lifecycle.viewModelScope
import com.github.lany192.gomoku.R
import com.github.lany192.gomoku.core.mvi.MviViewModel
import com.github.lany192.gomoku.data.net.ChatContent
import com.github.lany192.gomoku.data.net.ConnectionItem
import com.github.lany192.gomoku.data.net.DiscoveryEvent
import com.github.lany192.gomoku.data.net.LanDiscoveryManager
import com.github.lany192.gomoku.data.net.Protocol
import com.github.lany192.gomoku.ui.common.messageRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    /** 最近一次收到聊天的对端 IP：发送目标（联机页没有"当前会话"概念，按最近来信回复） */
    private var lastChatIp: String? = null

    /**
     * 各设备最近一次被播报的时间，用于淘汰崩溃后残留的离线设备
     *
     * 时间戳刻意不放进 State：ConnectionItem.equals 只比 IP，而 MutableStateFlow 对等值更新
     * 会「相等即短路、不落值」，放在状态里会被静默吞掉；且它变化时也不该触发列表重刷。
     */
    private val peerSeen = mutableMapOf<String, Long>()

    init {
        viewModelScope.launch {
            discovery.events.collect(::onDiscoveryEvent)
        }
        discovery.start()
        discovery.sendScanBroadcast()
        // 周期重播 JOIN：在线设备会应答刷新，同时让 30 秒未再出现的设备被淘汰
        viewModelScope.launch {
            while (isActive) {
                delay(PEER_REFRESH_INTERVAL_MS)
                discovery.sendScanBroadcast()
                sweepStalePeers()
            }
        }
    }

    override fun onIntent(intent: ConnectIntent) {
        when (intent) {
            ConnectIntent.ScanClicked -> {
                // 清掉可能已离线的残留 peer，依赖广播应答重建列表
                peerSeen.clear()
                updateState { it.copy(peers = emptyList()) }
                discovery.sendScanBroadcast()
                viewModelScope.launch { emitEffect(ConnectEffect.ShowMessage(R.string.msg_scanning)) }
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
                        emitEffect(ConnectEffect.ShowMessage(R.string.msg_peer_no_response))
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
            is ConnectIntent.SendChat -> sendChat(intent.content)
        }
    }

    /** 发送聊天：回复最近来信的对端；本地立即回显（对端不会把消息发回来） */
    private fun sendChat(content: String) {
        val target = lastChatIp ?: return
        val text = Protocol.clampUtf8(content.trim())
        if (text.isEmpty()) return
        discovery.sendChat(text, target)
        chats.add(ChatContent(localIp, text, self = true))
        viewModelScope.launch { emitEffect(ConnectEffect.ShowChat(chats.toList())) }
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
                    emitEffect(ConnectEffect.ShowMessage(R.string.msg_request_rejected))
                }
            }
            is DiscoveryEvent.ChatReceived -> {
                lastChatIp = event.from.ip
                chats.add(ChatContent(event.from.name + "(" + event.from.ip + ")", event.content))
                viewModelScope.launch { emitEffect(ConnectEffect.ShowChat(chats.toList())) }
            }
            is DiscoveryEvent.Error -> viewModelScope.launch {
                emitEffect(ConnectEffect.ShowMessage(event.kind.messageRes()))
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
        peerSeen[item.ip] = System.currentTimeMillis()
        updateState { state -> state.copy(peers = mergePeer(state.peers, item)) }
    }

    private fun removePeer(ip: String) {
        peerSeen.remove(ip)
        updateState { state ->
            state.copy(peers = state.peers.filterNot { it.ip == ip })
        }
        if (awaitingAgree && pendingIp == ip) {
            resetPending()
            viewModelScope.launch {
                emitEffect(ConnectEffect.DismissConnecting)
                emitEffect(ConnectEffect.ShowMessage(R.string.msg_peer_exited))
            }
        }
        if (handshakeDialogShowing && incomingIp == ip) {
            handshakeDialogShowing = false
            incomingIp = null
            viewModelScope.launch {
                emitEffect(ConnectEffect.DismissHandshake)
                emitEffect(ConnectEffect.ShowMessage(R.string.msg_peer_exited))
            }
        }
    }

    /** 淘汰超过 TTL 未再被播报的设备（对端崩溃/掉线不会发 EXIT，只能靠刷新缺失识别） */
    private fun sweepStalePeers() {
        val now = System.currentTimeMillis()
        val stale = stalePeers(peers = state.value.peers, lastSeen = peerSeen, now = now, ttlMillis = PEER_TTL_MS)
        if (stale.isEmpty()) return
        stale.forEach { peerSeen.remove(it) }
        updateState { state -> state.copy(peers = state.peers.filterNot { it.ip in stale }) }
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
        const val AGREE_TIMEOUT_MS = 10_000L

        /** JOIN 重播周期：在线设备借此刷新，离线设备因不再刷新而被淘汰 */
        const val PEER_REFRESH_INTERVAL_MS = 10_000L

        /** 超过该时长未再被播报即视为离线残留（约 3 个重播周期） */
        const val PEER_TTL_MS = 30_000L
    }
}
