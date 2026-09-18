package com.github.lany192.gomoku.domain.ai

/**
 * 算法家族：UI 按家族分组展示（枚举顺序即展示顺序，同家族必须连续排列）
 */
enum class AiFamily {
    SEARCH,
    THREAT,
    EVALUATION,
    SIMULATION,
    LEARNING,
    MISC,
}

/**
 * 可选的对手算法。持久化按枚举名（对齐 [Difficulty] 的写法），改名会让旧值回退到默认档。
 *
 * 新增算法必须同步三处：[AiEngineFactory] 的 when、`ui/robot/RobotAlgorithmText.kt` 的 when、
 * `strings.xml` 的名称；另外三处是编译期穷尽检查，漏一个编译不过。
 */
enum class AiAlgorithm(val family: AiFamily) {
    // 搜索类
    MINIMAX(AiFamily.SEARCH),
    ALPHA_BETA(AiFamily.SEARCH),
    PVS(AiFamily.SEARCH),
    MTD_F(AiFamily.SEARCH),
    ITERATIVE_DEEPENING(AiFamily.SEARCH),
    TRANSPOSITION_TABLE(AiFamily.SEARCH),
    KILLER_HISTORY(AiFamily.SEARCH),

    // 威胁搜索
    VCF(AiFamily.THREAT),
    VCT(AiFamily.THREAT),
    KILL_SEARCH(AiFamily.THREAT),

    // 评估类
    SHAPE_SCORE(AiFamily.EVALUATION),
    PATTERN_TABLE(AiFamily.EVALUATION),
    NEURAL_EVAL(AiFamily.EVALUATION),

    // 随机模拟
    MCTS(AiFamily.SIMULATION),
    UCT(AiFamily.SIMULATION),
    RAVE(AiFamily.SIMULATION),

    // 学习类
    TD_LEARNING(AiFamily.LEARNING),
    Q_LEARNING(AiFamily.LEARNING),
    ALPHA_ZERO(AiFamily.LEARNING),
    GENETIC(AiFamily.LEARNING),

    // 其他
    GREEDY(AiFamily.MISC),
    FUZZY(AiFamily.MISC),
    EXPERT_SYSTEM(AiFamily.MISC),
    ANT_COLONY(AiFamily.MISC),
    SIMULATED_ANNEALING(AiFamily.MISC);

    companion object {
        val DEFAULT = SHAPE_SCORE
    }
}
