package com.github.lany192.gomoku.ui.connect

import com.github.lany192.gomoku.data.net.ConnectionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 联机页设备列表的合并/淘汰回归
 *
 * 列表每 10 秒被在线设备重新播报一次，合并策略直接决定列表是否抖动。
 */
class PeerListTest {

    @Test
    fun `新设备追加到列表末尾`() {
        val peers = listOf(item("A", "1.1.1.1"))
        assertEquals(listOf(item("A", "1.1.1.1"), item("B", "2.2.2.2")), mergePeer(peers, item("B", "2.2.2.2")))
    }

    @Test
    fun `同名重复播报返回原列表实例`() {
        // 同一实例既保证位置不动，也让状态层省掉一次无意义重发（StateFlow 等值短路）
        val peers = listOf(item("A", "1.1.1.1"), item("B", "2.2.2.2"))
        assertSame(peers, mergePeer(peers, item("A", "1.1.1.1")))
    }

    @Test
    fun `改名后移到末尾`() {
        val peers = listOf(item("A", "1.1.1.1"), item("B", "2.2.2.2"))
        assertEquals(listOf(item("B", "2.2.2.2"), item("A-new", "1.1.1.1")), mergePeer(peers, item("A-new", "1.1.1.1")))
    }

    @Test
    fun `超时未刷新的设备被淘汰`() {
        val peers = listOf(item("A", "1.1.1.1"), item("B", "2.2.2.2"))
        val stale = stalePeers(
            peers = peers,
            lastSeen = mapOf("1.1.1.1" to 0L, "2.2.2.2" to 5_000L),
            now = 31_000L,
            ttlMillis = 30_000L,
        )
        // A 沉默了 31 秒（> 30 秒 TTL）；B 只沉默 26 秒，保留
        assertEquals(listOf("1.1.1.1"), stale)
    }

    @Test
    fun `没有时间戳记录的条目不误删`() {
        val peers = listOf(item("A", "1.1.1.1"))
        assertTrue(stalePeers(peers, emptyMap(), now = 100_000_000L, ttlMillis = 30_000L).isEmpty())
    }

    private fun item(name: String, ip: String) = ConnectionItem(name, ip)
}
