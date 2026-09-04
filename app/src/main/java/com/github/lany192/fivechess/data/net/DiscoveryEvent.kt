package com.github.lany192.fivechess.data.net

/**
 * 局域网发现/握手/聊天事件（LanDiscoveryManager 对外输出）
 */
sealed interface DiscoveryEvent {
    data class PeerJoined(val name: String, val ip: String) : DiscoveryEvent

    data class PeerExited(val name: String, val ip: String) : DiscoveryEvent

    data class HandshakeRequested(val name: String, val ip: String) : DiscoveryEvent

    data class HandshakeAccepted(val name: String, val ip: String) : DiscoveryEvent

    data class HandshakeRejected(val name: String, val ip: String) : DiscoveryEvent

    data class ChatReceived(val from: ConnectionItem, val content: String) : DiscoveryEvent

    data class Error(val kind: DiscoveryError) : DiscoveryEvent
}
