package com.github.lany192.gomoku.domain.ai.sim

/** 树内选点策略：UCB1=经典置信上界，RAVE=在上界分上再混入 AMAF 胜率 */
enum class McPolicy { UCB1, RAVE }

/** 模拟落子策略：UNIFORM=均匀随机撒点，HEURISTIC=静态分贪心（前若干步） */
enum class RolloutPolicy { UNIFORM, HEURISTIC }

/**
 * 随机模拟参数：模拟次数与时间预算先到者收口。
 *
 * [raveK] 是 RAVE 的等价性系数（β = √(k/(3N+k))，越大越晚信任 AMAF），
 * [heuristicSteps] 限制贪心 rollout 的步数（之后退回随机，避免每次模拟都走同一条线）。
 */
data class MonteCarloOptions(
    val simulations: Int,
    val timeBudgetMillis: Long,
    val explorationC: Double = 1.4,
    val policy: McPolicy = McPolicy.UCB1,
    val raveK: Double = 800.0,
    val rollout: RolloutPolicy = RolloutPolicy.UNIFORM,
    val maxRolloutDepth: Int = 20,
    val rolloutBreadth: Int = 4,
    val heuristicSteps: Int = 10,
    val rootBreadth: Int = 16,
    val radius: Int = 2,
)
