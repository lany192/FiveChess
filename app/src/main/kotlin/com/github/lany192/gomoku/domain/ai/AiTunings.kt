package com.github.lany192.gomoku.domain.ai

import com.github.lany192.gomoku.domain.ai.learn.LearningOptions
import com.github.lany192.gomoku.domain.ai.misc.MiscOptions
import com.github.lany192.gomoku.domain.ai.search.Pruning
import com.github.lany192.gomoku.domain.ai.search.SearchOptions
import com.github.lany192.gomoku.domain.ai.sim.McPolicy
import com.github.lany192.gomoku.domain.ai.sim.MonteCarloOptions
import com.github.lany192.gomoku.domain.ai.sim.RolloutPolicy
import com.github.lany192.gomoku.domain.ai.threat.ThreatMode
import com.github.lany192.gomoku.domain.ai.threat.ThreatOptions

/** 各家族共用的档位参数（alertPercent 复用 Difficulty 的"战术留意概率"语义） */
data class EngineTuning(
    /** 留意战术（连五取胜/封堵/活四）的概率，剩余概率会看漏 */
    val alertPercent: Int,
    /** 选中劣着的概率（只在入门/简单档非 0） */
    val noisePercent: Int,
    /** 本手的时间预算 */
    val timeBudgetMillis: Long,
    /** 候选点宽度上限 */
    val breadth: Int,
    /** 邻域半径 */
    val radius: Int,
)

/**
 * 档位 → 各家族参数的唯一映射点。
 *
 * 硬上限集中在这里：任何引擎都不许超出预算，否则主线程外的搜索会拖垮对局节奏。
 * 经典 SHAPE_SCORE 走 RobotAI，不经过本对象（行为冻结）。
 */
object AiTunings {
    const val MAX_TIME_BUDGET_MILLIS = 1200L
    const val MAX_PLY = 32

    private val TIME_BUDGETS = longArrayOf(80L, 150L, 300L, 600L, 1200L)

    /** 极小极大无剪枝，宽度与深度都必须压住 */
    private val MINIMAX_DEPTHS = intArrayOf(1, 1, 2, 3, 4)
    private const val MINIMAX_BREADTH = 8
    private const val MINIMAX_NODE_LIMIT = 60_000

    /** 算杀比同档普通搜索多吃两层 */
    private const val THREAT_DEPTH_BONUS = 2
    private const val THREAT_BREADTH = 12

    /** MCTS 平铺模拟次数（没有启发，多撒几次）；UCT/RAVE 靠启发式 rollout，次数减半 */
    private val MCTS_SIMULATIONS = intArrayOf(120, 240, 500, 900, 1400)
    private val HEURISTIC_SIMULATIONS = intArrayOf(60, 120, 260, 450, 700)

    fun of(algorithm: AiAlgorithm, level: Difficulty): EngineTuning = EngineTuning(
        alertPercent = level.alertPercent,
        noisePercent = level.noise,
        timeBudgetMillis = TIME_BUDGETS[level.ordinal],
        breadth = level.breadth,
        radius = level.radius,
    )

    /**
     * 7 个搜索预设的完整参数：结构性开关（裁剪/TT/杀手）按算法固定，
     * 档位只调深度与时间预算，保证"同一算法不同难度"的观感一致。
     */
    fun search(algorithm: AiAlgorithm, level: Difficulty): SearchOptions {
        val tier = level.ordinal
        val budget = TIME_BUDGETS[tier]

        fun base(depth: Int, pruning: Pruning): SearchOptions = SearchOptions(
            maxDepth = depth.coerceIn(1, MAX_PLY),
            breadth = level.breadth,
            radius = level.radius,
            pruning = pruning,
            timeBudgetMillis = budget,
        )

        return when (algorithm) {
            AiAlgorithm.MINIMAX -> SearchOptions(
                maxDepth = MINIMAX_DEPTHS[tier],
                breadth = minOf(level.breadth, MINIMAX_BREADTH),
                radius = level.radius,
                pruning = Pruning.NONE,
                timeBudgetMillis = budget,
                nodeLimit = MINIMAX_NODE_LIMIT,
            )
            AiAlgorithm.ALPHA_BETA -> base(maxOf(2, level.depth), Pruning.ALPHA_BETA)
            AiAlgorithm.PVS -> base(maxOf(2, level.depth), Pruning.PVS)
            AiAlgorithm.MTD_F -> base(maxOf(2, level.depth), Pruning.ALPHA_BETA)
                .copy(transposition = true, tableBits = 16, mtdPasses = 4 + tier)
            AiAlgorithm.ITERATIVE_DEEPENING -> base(maxOf(3, level.depth), Pruning.PVS)
                .copy(iterativeDeepening = true, aspiration = true)
            AiAlgorithm.TRANSPOSITION_TABLE -> base(maxOf(3, level.depth), Pruning.PVS)
                .copy(
                    iterativeDeepening = true,
                    aspiration = true,
                    transposition = true,
                    tableBits = 16,
                    killerSlots = 2,
                    historyHeuristic = true,
                )
            AiAlgorithm.KILLER_HISTORY -> base(maxOf(2, level.depth), Pruning.ALPHA_BETA)
                .copy(killerSlots = 2, historyHeuristic = true)
            // 评估类：搜索骨架与"置换表"预设一致，差异在可插拔的 Evaluator
            AiAlgorithm.PATTERN_TABLE,
            AiAlgorithm.NEURAL_EVAL,
            -> base(maxOf(3, level.depth), Pruning.PVS)
                .copy(
                    iterativeDeepening = true,
                    aspiration = true,
                    transposition = true,
                    tableBits = 16,
                    killerSlots = 2,
                    historyHeuristic = true,
                )
            else -> error("$algorithm 不是搜索类算法")
        }
    }

    /**
     * 3 个威胁搜索预设：分支极窄，深度给到"普通搜索 + 2"仍然在预算内；
     * 时间预算取同档一半，给无杀时的回退搜索留出余量。
     */
    fun threat(algorithm: AiAlgorithm, level: Difficulty): ThreatOptions {
        val mode = when (algorithm) {
            AiAlgorithm.VCF -> ThreatMode.VCF
            AiAlgorithm.VCT -> ThreatMode.VCT
            AiAlgorithm.KILL_SEARCH -> ThreatMode.KILL
            else -> error("$algorithm 不是威胁搜索类算法")
        }
        return ThreatOptions(
            mode = mode,
            depth = maxOf(2, level.depth + THREAT_DEPTH_BONUS).coerceAtMost(MAX_PLY),
            breadth = minOf(level.breadth, THREAT_BREADTH),
            radius = maxOf(2, level.radius),
            timeBudgetMillis = TIME_BUDGETS[level.ordinal] / 2,
        )
    }

    /**
     * 3 个随机模拟预设：MCTS 平铺（次数多、rollout 深），UCT/RAVE 启发式 rollout（次数少、rollout 浅）。
     * 次数与时间预算先到者收口，保证最坏档位也不超 [MAX_TIME_BUDGET_MILLIS]。
     */
    fun simulation(algorithm: AiAlgorithm, level: Difficulty): MonteCarloOptions {
        val tier = level.ordinal
        val budget = TIME_BUDGETS[tier]
        return when (algorithm) {
            AiAlgorithm.MCTS -> MonteCarloOptions(
                simulations = MCTS_SIMULATIONS[tier],
                timeBudgetMillis = budget,
                explorationC = 1.4,
                policy = McPolicy.UCB1,
                rollout = RolloutPolicy.UNIFORM,
                maxRolloutDepth = 24,
                radius = maxOf(2, level.radius),
                rootBreadth = level.breadth,
            )
            AiAlgorithm.UCT -> MonteCarloOptions(
                simulations = HEURISTIC_SIMULATIONS[tier],
                timeBudgetMillis = budget,
                explorationC = 1.2,
                policy = McPolicy.UCB1,
                rollout = RolloutPolicy.HEURISTIC,
                maxRolloutDepth = 16,
                radius = maxOf(2, level.radius),
                rootBreadth = level.breadth,
            )
            AiAlgorithm.RAVE -> MonteCarloOptions(
                simulations = HEURISTIC_SIMULATIONS[tier],
                timeBudgetMillis = budget,
                explorationC = 1.2,
                policy = McPolicy.RAVE,
                raveK = 800.0,
                rollout = RolloutPolicy.HEURISTIC,
                maxRolloutDepth = 16,
                radius = maxOf(2, level.radius),
                rootBreadth = level.breadth,
            )
            else -> error("$algorithm 不是随机模拟类算法")
        }
    }

    /** 蚁群蚂蚁数 / 退火迭代步数 / 专家系统前瞻宽度，都随档位单调递增 */
    private val MISC_ANTS = intArrayOf(4, 6, 10, 16, 24)
    private val MISC_ITERATIONS = intArrayOf(40, 80, 150, 260, 400)
    private val MISC_LOOKAHEAD = intArrayOf(3, 4, 6, 8, 10)

    /** 学习类的浅搜深度：TD/Q 的选点搜索，只做 1~3 层，强度主要来自学习而非搜索 */
    private val LEARN_DEPTHS = intArrayOf(1, 1, 2, 2, 3)

    /** Q 学习的 ε-greedy 探索概率（%），随档位降低 */
    private val LEARN_EPSILON = intArrayOf(40, 30, 20, 12, 8)

    /** AlphaZero 每手 PUCT 模拟次数 */
    private val AZ_SIMULATIONS = intArrayOf(40, 70, 120, 200, 320)

    /** 遗传算法每手演化预算：跨手续跑，固定不随档位变（预算小才不拖慢对局节奏） */
    private const val GENETIC_EVOLVE_MILLIS = 150L

    /** 4 个学习类算法的参数：浅搜深度/探索率/模拟次数按档位递增，学习率与折扣固定 */
    fun learning(algorithm: AiAlgorithm, level: Difficulty): LearningOptions {
        val tier = level.ordinal
        val base = LearningOptions(
            breadth = level.breadth,
            radius = level.radius,
            timeBudgetMillis = TIME_BUDGETS[tier],
            depth = LEARN_DEPTHS[tier],
        )
        return when (algorithm) {
            AiAlgorithm.TD_LEARNING -> base.copy(learningRate = 0.25)
            AiAlgorithm.Q_LEARNING -> base.copy(learningRate = 0.2, epsilonPercent = LEARN_EPSILON[tier])
            AiAlgorithm.ALPHA_ZERO -> base.copy(simulations = AZ_SIMULATIONS[tier])
            AiAlgorithm.GENETIC -> base.copy(evolveBudgetMillis = GENETIC_EVOLVE_MILLIS)
            else -> error("$algorithm 不是学习类算法")
        }
    }

    /** 其余 5 个小引擎的参数：共用宽度/半径/预算，专属字段各取所需 */
    fun misc(algorithm: AiAlgorithm, level: Difficulty): MiscOptions {
        val tier = level.ordinal
        val base = MiscOptions(
            breadth = level.breadth,
            radius = level.radius,
            timeBudgetMillis = TIME_BUDGETS[tier],
        )
        return when (algorithm) {
            AiAlgorithm.GREEDY -> base
            AiAlgorithm.FUZZY -> base
            AiAlgorithm.EXPERT_SYSTEM -> base.copy(lookahead = MISC_LOOKAHEAD[tier])
            AiAlgorithm.ANT_COLONY -> base.copy(ants = MISC_ANTS[tier])
            AiAlgorithm.SIMULATED_ANNEALING -> base.copy(iterations = MISC_ITERATIONS[tier])
            else -> error("$algorithm 不是其他类算法")
        }
    }
}
