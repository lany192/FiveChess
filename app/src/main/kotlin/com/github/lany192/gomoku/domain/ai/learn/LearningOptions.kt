package com.github.lany192.gomoku.domain.ai.learn

/**
 * 学习类算法参数：深度/宽度等共用字段对所有学习引擎生效，
 * 学习率/折扣因子/模拟次数/种群等专属字段各取所需。
 */
data class LearningOptions(
    val breadth: Int,
    val radius: Int,
    val timeBudgetMillis: Long,
    /** 浅搜深度（TD/Q 的选点搜索） */
    val depth: Int = 2,
    /** 归一化 LMS 的学习率（0..1） */
    val learningRate: Double = 0.2,
    /** Q 学习的 ε-greedy 探索概率 */
    val epsilonPercent: Int = 0,
    /** 折扣因子 */
    val gamma: Double = 0.95,
    /** AlphaZero：每手 PUCT 模拟次数 */
    val simulations: Int = 0,
    /** AlphaZero：PUCT 探索常数 */
    val cPuct: Double = 1.5,
    /** 遗传算法：种群规模 */
    val populationSize: Int = 6,
    /** 遗传算法：每手演化的时间预算 */
    val evolveBudgetMillis: Long = 150L,
    /** 遗传算法：适应度对局的步数上限 */
    val fitnessPlies: Int = 30,
)
