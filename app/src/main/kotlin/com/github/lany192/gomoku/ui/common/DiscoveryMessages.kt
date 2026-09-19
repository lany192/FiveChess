package com.github.lany192.gomoku.ui.common

import androidx.annotation.StringRes
import com.github.lany192.gomoku.R
import com.github.lany192.gomoku.data.net.DiscoveryError

/**
 * 发现/握手错误码的用户提示文案（局域网与蓝牙联机页共用）
 */
@StringRes
fun DiscoveryError.messageRes(): Int = when (this) {
    DiscoveryError.SOCKET_NULL -> R.string.err_socket_null
    DiscoveryError.IP_NULL -> R.string.err_local_ip_failed
    DiscoveryError.UDP_IP_ERROR -> R.string.err_udp_ip_invalid
    DiscoveryError.UDP_DATA_ERROR -> R.string.err_udp_data_send
    DiscoveryError.MULTICAST_ERROR -> R.string.err_multicast_unavailable
    DiscoveryError.BT_DISABLED -> R.string.err_bt_disabled
    DiscoveryError.BT_PERMISSION_DENIED -> R.string.err_bt_permission
    DiscoveryError.BT_CONNECT_FAILED -> R.string.err_bt_connect_failed
}
