package com.github.lany192.gomoku.domain.ai.search

import com.github.lany192.gomoku.domain.ai.core.Candidate
import com.github.lany192.gomoku.domain.ai.core.ShapeScores

/**
 * 着法排序：TT/PV 着法 → 杀手着法 → 历史启发 → 静态棋型分。
 *
 * 比较必须是**全序**（末级按 x 升序、y 升序），保证同种子搜索可复现。
 * 索引约定与 [TranspositionTable] 一致：[index] = x * height + y。
 */
class MoveOrdering(private val width: Int, private val height: Int, killerSlots: Int) {

    private val killers = Array(ShapeScores.MAX_PLY) { IntArray(killerSlots) { TranspositionTable.NO_MOVE } }
    private val history = IntArray(width * height)

    /** 就地重排（列表由调用方持有，排序后第一个元素应最先搜索） */
    fun order(moves: MutableList<Candidate>, ply: Int, ttMove: Int) {
        moves.sortWith { a, b -> compare(a, b, ply, ttMove) }
    }

    /** 某个着法触发 β 截断，记入本层杀手槽（槽内去重，新着法挤掉最旧的） */
    fun recordKiller(ply: Int, index: Int) {
        if (ply >= killers.size) return
        val slots = killers[ply]
        if (slots.isEmpty() || slots[0] == index) return
        for (i in slots.lastIndex downTo 1) slots[i] = slots[i - 1]
        slots[0] = index
    }

    /** 历史启发：累加 depth²，超过阈值整体折半（防止旧经验锁死排序） */
    fun recordHistory(index: Int, depth: Int) {
        val value = history[index] + depth * depth
        if (value >= HISTORY_MAX) {
            for (i in history.indices) history[i] = history[i] shr 1
        } else {
            history[index] = value
        }
    }

    /** 杀手着法是"本手搜索内"的经验，每次新搜索都应清掉 */
    fun clearKillers() {
        for (slots in killers) slots.fill(TranspositionTable.NO_MOVE)
    }

    fun clear() {
        for (i in history.indices) history[i] = 0
        clearKillers()
    }

    private fun compare(a: Candidate, b: Candidate, ply: Int, ttMove: Int): Int {
        val ia = index(a)
        val ib = index(b)
        if (ia == ttMove || ib == ttMove) {
            if (ia != ib) return if (ia == ttMove) -1 else 1
        } else if (ply < killers.size) {
            val ka = isKiller(ply, ia)
            val kb = isKiller(ply, ib)
            if (ka != kb) return if (ka) -1 else 1
        }
        if (history[ia] != history[ib]) return history[ib] - history[ia]
        if (a.score != b.score) return b.score.compareTo(a.score)
        if (a.x != b.x) return a.x - b.x
        return a.y - b.y
    }

    private fun isKiller(ply: Int, index: Int): Boolean {
        for (slot in killers[ply]) {
            if (slot == index) return true
        }
        return false
    }

    private fun index(c: Candidate): Int = c.x * height + c.y

    private companion object {
        const val HISTORY_MAX = 1 shl 20
    }
}
