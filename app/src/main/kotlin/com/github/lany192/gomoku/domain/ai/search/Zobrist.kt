package com.github.lany192.gomoku.domain.ai.search

import com.github.lany192.gomoku.domain.ai.core.Stone
import kotlin.random.Random

/**
 * Zobrist 哈希：棋子键 + 走子方键，搜索时增量维护（落子/撤销各 xor 一次）。
 *
 * 键由注入的 [Random] 生成，保证同种子可复现。
 */
class Zobrist(private val random: Random, private val width: Int, private val height: Int) {

    private val keys: Array<Array<LongArray>> = Array(width) { Array(height) { LongArray(2) } }

    /** 轮到白方走时的额外键（黑方不加） */
    val sideKey: Long = random.nextLong()

    init {
        for (x in 0 until width) {
            for (y in 0 until height) {
                keys[x][y][0] = random.nextLong()
                keys[x][y][1] = random.nextLong()
            }
        }
    }

    fun stoneKey(x: Int, y: Int, color: Int): Long = keys[x][y][color - 1]

    /** 全量计算（用于校验增量维护） */
    fun hash(board: Array<IntArray>, sideToMove: Int): Long {
        var h = if (sideToMove == Stone.WHITE) sideKey else 0L
        for (x in 0 until width) {
            for (y in 0 until height) {
                val color = board[x][y]
                if (color != Stone.EMPTY) h = h xor keys[x][y][color - 1]
            }
        }
        return h
    }
}
