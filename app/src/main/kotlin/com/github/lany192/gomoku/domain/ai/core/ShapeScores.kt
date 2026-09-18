package com.github.lany192.gomoku.domain.ai.core

/**
 * 棋型分值：连五 > 活四 > 冲四 > 活三 > 眠三 > 活二 > 眠二。
 *
 * 数值与旧 RobotAI 的私有常量逐位相同：所有引擎的胜负判定与搜索窗口都依赖这套量级，
 * 改动会同时影响经典引擎行为，须由 ShapeScoresTest 冻结。
 */
object ShapeScores {
    const val FIVE = 100000000L
    const val LIVE_FOUR = 10000000L
    const val RUSH_FOUR = 1000000L
    const val LIVE_THREE = 200000L
    const val SLEEP_THREE = 20000L
    const val LIVE_TWO = 5000L
    const val SLEEP_TWO = 500L
    const val LIVE_ONE = 100L
    const val SLEEP_ONE = 10L

    /** 连五的胜负分：远大于任何棋型分之和 */
    const val WIN = FIVE * 10

    /** 搜索的最大手数：只用于"越早取胜分越高"的微调 */
    const val MAX_PLY = 32

    /** 按连子数和开放端数取分（count 为该点落下后的连子数） */
    fun score(count: Int, opens: Int): Long {
        if (count >= 5) return FIVE
        return when (count) {
            4 -> when {
                opens >= 2 -> LIVE_FOUR
                opens == 1 -> RUSH_FOUR
                else -> 0L
            }
            3 -> when {
                opens >= 2 -> LIVE_THREE
                opens == 1 -> SLEEP_THREE
                else -> 0L
            }
            2 -> when {
                opens >= 2 -> LIVE_TWO
                opens == 1 -> SLEEP_TWO
                else -> 0L
            }
            1 -> when {
                opens >= 2 -> LIVE_ONE
                opens == 1 -> SLEEP_ONE
                else -> 0L
            }
            else -> 0L
        }
    }
}
