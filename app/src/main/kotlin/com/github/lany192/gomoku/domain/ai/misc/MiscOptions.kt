package com.github.lany192.gomoku.domain.ai.misc

import com.github.lany192.gomoku.domain.ai.core.ShapeScores

/**
 * 其他类算法的参数：各引擎只用自己的那几个字段（贪心/模糊只看宽度半径，
 * 蚁群看蚂蚁数，退火看迭代数，专家系统看前瞻宽度）。
 */
data class MiscOptions(
    val breadth: Int,
    val radius: Int,
    val timeBudgetMillis: Long,
    /** 蚁群：每步释放的蚂蚁数 */
    val ants: Int = 0,
    /** 蚁群：信息素权重 α */
    val antAlpha: Double = 1.0,
    /** 蚁群：启发权重 β */
    val antBeta: Double = 2.0,
    /** 蚁群：蒸发率 ρ */
    val antEvaporation: Double = 0.3,
    /** 退火：迭代步数 */
    val iterations: Int = 0,
    /** 退火：初始温度（活三量级，足以迈过冲四以下的崎岖） */
    val initialTemperature: Double = 3.0 * ShapeScores.LIVE_THREE,
    /** 退火：降温系数 */
    val cooling: Double = 0.92,
    /** 专家系统：一层前瞻的候选宽度 */
    val lookahead: Int = 0,
)
