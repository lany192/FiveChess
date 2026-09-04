package com.github.lany192.fivechess.ui.connect

import com.github.lany192.fivechess.data.net.ChatContent
import com.github.lany192.fivechess.data.net.ConnectionItem

sealed interface ConnectIntent {
    data object ScanClicked : ConnectIntent
    data class PeerClicked(val ip: String) : ConnectIntent
    data class HandshakeAgreed(val ip: String) : ConnectIntent
    data class HandshakeRejected(val ip: String) : ConnectIntent
}

data class ConnectState(
    val peers: List<ConnectionItem> = emptyList(),
)

sealed interface ConnectEffect {
    data class ShowHandshakeRequest(val name: String, val ip: String) : ConnectEffect
    data class ShowConnecting(val ip: String) : ConnectEffect
    data object DismissConnecting : ConnectEffect
    data class NavigateToGame(val isServer: Boolean, val ip: String) : ConnectEffect
    data class ShowMessage(val text: String) : ConnectEffect
    data class ShowChat(val messages: List<ChatContent>) : ConnectEffect
}
