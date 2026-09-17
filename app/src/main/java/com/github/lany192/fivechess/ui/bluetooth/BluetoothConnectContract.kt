package com.github.lany192.fivechess.ui.bluetooth

import com.github.lany192.fivechess.data.net.ConnectionItem

sealed interface BtConnectIntent {
    data object ScanClicked : BtConnectIntent
    data class PeerClicked(val address: String) : BtConnectIntent
    data object ConnectCancelled : BtConnectIntent
    data object HandshakeAgreed : BtConnectIntent
    data object HandshakeRejected : BtConnectIntent
}

data class BtConnectState(
    val peers: List<ConnectionItem> = emptyList(),
)

sealed interface BtConnectEffect {
    data class ShowHandshakeRequest(val name: String) : BtConnectEffect
    data object DismissHandshake : BtConnectEffect
    data class ShowConnecting(val address: String) : BtConnectEffect
    data object DismissConnecting : BtConnectEffect
    data class NavigateToGame(val isServer: Boolean, val address: String) : BtConnectEffect
    data class ShowMessage(val text: String) : BtConnectEffect
}
