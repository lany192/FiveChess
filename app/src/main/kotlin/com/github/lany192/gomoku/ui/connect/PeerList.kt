package com.github.lany192.gomoku.ui.connect

import com.github.lany192.gomoku.data.net.ConnectionItem

/**
 * 设备列表的合并与淘汰（纯函数，便于单测）
 *
 * 联机页每 10 秒重播一次 JOIN，对端会因此反复被播报：同名重复播报必须原地更新而不是
 * 每次移到列表末尾，否则列表会持续抖动。
 */
fun mergePeer(peers: List<ConnectionItem>, item: ConnectionItem): List<ConnectionItem> {
    val index = peers.indexOfFirst { it.ip == item.ip }
    return when {
        index < 0 -> peers + item
        // 名字未变：返回原列表实例，既保持位置也为状态层省掉一次无意义的重发
        peers[index].name == item.name -> peers
        else -> peers.filterIndexed { i, _ -> i != index } + item
    }
}

/** 返回超过 [ttlMillis] 未再被播报的设备 IP；[lastSeen] 里没有记录的条目按"刚见过"处理，不误删 */
fun stalePeers(
    peers: List<ConnectionItem>,
    lastSeen: Map<String, Long>,
    now: Long,
    ttlMillis: Long,
): List<String> = peers
    .filter { now - (lastSeen[it.ip] ?: now) > ttlMillis }
    .map { it.ip }
