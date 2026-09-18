package com.github.lany192.gomoku.ui.robot

import com.github.lany192.gomoku.R
import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.AiFamily
import com.github.lany192.gomoku.domain.ai.Difficulty

/**
 * 算法文案映射：只在 UI 层持有 `R.string`，`domain` 不依赖资源 id。
 *
 * 三个 when 都穷尽枚举（无 else）：新增算法/家族/档位漏了文案会编译失败。
 */
fun algorithmNameRes(algorithm: AiAlgorithm): Int = when (algorithm) {
    AiAlgorithm.MINIMAX -> R.string.ai_algo_minimax
    AiAlgorithm.ALPHA_BETA -> R.string.ai_algo_alpha_beta
    AiAlgorithm.PVS -> R.string.ai_algo_pvs
    AiAlgorithm.MTD_F -> R.string.ai_algo_mtd_f
    AiAlgorithm.ITERATIVE_DEEPENING -> R.string.ai_algo_iterative_deepening
    AiAlgorithm.TRANSPOSITION_TABLE -> R.string.ai_algo_transposition_table
    AiAlgorithm.KILLER_HISTORY -> R.string.ai_algo_killer_history
    AiAlgorithm.VCF -> R.string.ai_algo_vcf
    AiAlgorithm.VCT -> R.string.ai_algo_vct
    AiAlgorithm.KILL_SEARCH -> R.string.ai_algo_kill_search
    AiAlgorithm.SHAPE_SCORE -> R.string.ai_algo_shape_score
    AiAlgorithm.PATTERN_TABLE -> R.string.ai_algo_pattern_table
    AiAlgorithm.NEURAL_EVAL -> R.string.ai_algo_neural_eval
    AiAlgorithm.MCTS -> R.string.ai_algo_mcts
    AiAlgorithm.UCT -> R.string.ai_algo_uct
    AiAlgorithm.RAVE -> R.string.ai_algo_rave
    AiAlgorithm.TD_LEARNING -> R.string.ai_algo_td_learning
    AiAlgorithm.Q_LEARNING -> R.string.ai_algo_q_learning
    AiAlgorithm.ALPHA_ZERO -> R.string.ai_algo_alpha_zero
    AiAlgorithm.GENETIC -> R.string.ai_algo_genetic
    AiAlgorithm.GREEDY -> R.string.ai_algo_greedy
    AiAlgorithm.FUZZY -> R.string.ai_algo_fuzzy
    AiAlgorithm.EXPERT_SYSTEM -> R.string.ai_algo_expert_system
    AiAlgorithm.ANT_COLONY -> R.string.ai_algo_ant_colony
    AiAlgorithm.SIMULATED_ANNEALING -> R.string.ai_algo_simulated_annealing
}

fun familyNameRes(family: AiFamily): Int = when (family) {
    AiFamily.SEARCH -> R.string.ai_family_search
    AiFamily.THREAT -> R.string.ai_family_threat
    AiFamily.EVALUATION -> R.string.ai_family_evaluation
    AiFamily.SIMULATION -> R.string.ai_family_simulation
    AiFamily.LEARNING -> R.string.ai_family_learning
    AiFamily.MISC -> R.string.ai_family_misc
}

fun levelNameRes(level: Difficulty): Int = when (level) {
    Difficulty.NOVICE -> R.string.ai_level_novice
    Difficulty.EASY -> R.string.ai_level_easy
    Difficulty.MEDIUM -> R.string.ai_level_medium
    Difficulty.HARD -> R.string.ai_level_hard
    Difficulty.MASTER -> R.string.ai_level_master
}
