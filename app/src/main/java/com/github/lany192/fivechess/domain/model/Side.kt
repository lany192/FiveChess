package com.github.lany192.fivechess.domain.model

/**
 * 棋子颜色
 */
enum class Side {
    BLACK, WHITE;

    val opposite: Side
        get() = if (this == BLACK) WHITE else BLACK

    /** 棋盘整型编码：1=黑 2=白（与旧 RobotAI 的 Array<IntArray> 约定一致） */
    fun toCode(): Int = if (this == BLACK) 1 else 2

    companion object {
        fun fromCode(code: Int): Side? = when (code) {
            1 -> BLACK
            2 -> WHITE
            else -> null
        }
    }
}
