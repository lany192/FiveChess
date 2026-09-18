package com.github.lany192.gomoku.ui.common

import com.github.lany192.gomoku.data.net.DiscoveryError

/**
 * 发现/握手错误码的用户提示文案（局域网与蓝牙联机页共用）
 */
fun DiscoveryError.describe(): String = when (this) {
    DiscoveryError.SOCKET_NULL -> "网络套接字未就绪"
    DiscoveryError.IP_NULL -> "本机IP获取失败"
    DiscoveryError.UDP_IP_ERROR -> "目标地址无效"
    DiscoveryError.UDP_DATA_ERROR -> "数据发送失败"
    DiscoveryError.MULTICAST_ERROR -> "组播不可用"
    DiscoveryError.BT_DISABLED -> "蓝牙未开启，请先打开蓝牙"
    DiscoveryError.BT_PERMISSION_DENIED -> "缺少蓝牙权限，请授权后重试"
    DiscoveryError.BT_CONNECT_FAILED -> "蓝牙连接失败，请确认双方已配对并都在联机页面"
}
