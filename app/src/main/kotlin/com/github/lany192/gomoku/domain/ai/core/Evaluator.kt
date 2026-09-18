package com.github.lany192.gomoku.domain.ai.core

/**
 * 局面评估：返回"以 [self] 视角"的局分（正值对自己有利）。
 *
 * 数值量级必须与 [ShapeScores] 一致，否则搜索窗口与胜负判定会错。
 */
interface Evaluator {
    /** 全盘评估；实现不得修改 board */
    fun evaluate(board: Array<IntArray>, self: Int): Long
}
