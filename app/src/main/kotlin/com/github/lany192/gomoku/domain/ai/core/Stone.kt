package com.github.lany192.gomoku.domain.ai.core

/**
 * 棋盘整型编码：0=空 1=黑 2=白（与 domain.model.Side 及旧 RobotAI 约定一致）。
 * 人机对战中 AI 固定执白。
 */
object Stone {
    const val EMPTY = 0
    const val BLACK = 1
    const val WHITE = 2

    /** 对手棋子颜色 */
    fun opponent(color: Int): Int = if (color == BLACK) WHITE else BLACK
}
