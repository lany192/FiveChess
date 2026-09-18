package com.github.lany192.gomoku.domain.ai.search

import com.github.lany192.gomoku.domain.ai.core.ShapeScores

/**
 * 置换表：单槽 + 深度优先替换 + 代际标记（避免每手清空整表）。
 *
 * **杀棋分一律不入表**（|value| 接近 WIN 的值依赖 ply，直接存会引入经典的杀棋分修正 bug），
 * 只记着法用于排序与 MTD(f) 取根着法。
 */
class TranspositionTable(bits: Int) {

    private val mask = (1 shl bits) - 1
    private val keys = LongArray(mask + 1)
    private val values = LongArray(mask + 1)
    private val depths = ByteArray(mask + 1)
    private val flags = ByteArray(mask + 1)
    private val moves = IntArray(mask + 1) { NO_MOVE }
    private val generations = IntArray(mask + 1)
    private var generation = 0

    /** 每次搜索开始时调用：旧代条目只作为替换候选，不再被信任 */
    fun newSearch() {
        generation++
    }

    fun probe(key: Long, depth: Int, alpha: Long, beta: Long): Long? {
        val i = index(key)
        if (keys[i] != key || depths[i].toInt() < depth) return null
        val value = values[i]
        return when (flags[i]) {
            EXACT -> value
            LOWER -> if (value >= beta) value else null
            UPPER -> if (value <= alpha) value else null
            else -> null
        }
    }

    /** 记录的根/节点着法（打包为 x * height + y），没有返回 [NO_MOVE] */
    fun bestMove(key: Long): Int {
        val i = index(key)
        return if (keys[i] == key) moves[i] else NO_MOVE
    }

    /** 只更新着法：命中同键时覆盖，未命中时建一条“只有着法”的条目 */
    fun storeMove(key: Long, move: Int) {
        val i = index(key)
        if (keys[i] != key) {
            keys[i] = key
            values[i] = 0L
            depths[i] = 0
            flags[i] = NONE
            generations[i] = generation
        }
        moves[i] = move
    }

    fun store(key: Long, depth: Int, value: Long, flag: Byte) {
        // 杀棋分依赖 ply，跨深度复用会产生经典的杀棋分偏移 bug，一律不入表
        if (value >= MATE_THRESHOLD || value <= -MATE_THRESHOLD) return
        val i = index(key)
        // 同一次搜索里不覆盖更深的条目；跨代条目允许覆盖
        if (keys[i] == key && generations[i] == generation && depths[i].toInt() > depth) return
        if (keys[i] != key) {
            moves[i] = NO_MOVE
            keys[i] = key
        }
        values[i] = value
        depths[i] = depth.toByte()
        flags[i] = flag
        generations[i] = generation
    }

    private fun index(key: Long): Int = (key xor (key ushr 32)).toInt() and mask

    companion object {
        const val NO_MOVE = -1
        const val NONE: Byte = 0
        const val EXACT: Byte = 1
        const val LOWER: Byte = 2
        const val UPPER: Byte = 3

        /** 达到该阈值即视为杀棋分（连五分含 ply 微调，必然大于它） */
        const val MATE_THRESHOLD = ShapeScores.WIN - ShapeScores.MAX_PLY
    }
}
