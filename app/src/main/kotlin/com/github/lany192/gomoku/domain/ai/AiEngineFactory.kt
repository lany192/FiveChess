package com.github.lany192.gomoku.domain.ai

import com.github.lany192.gomoku.domain.ai.core.NeuralEvaluator
import com.github.lany192.gomoku.domain.ai.core.PatternTableEvaluator
import com.github.lany192.gomoku.domain.ai.learn.AlphaZeroEngine
import com.github.lany192.gomoku.domain.ai.learn.GeneticEngine
import com.github.lany192.gomoku.domain.ai.learn.QLearningEngine
import com.github.lany192.gomoku.domain.ai.learn.TdEngine
import com.github.lany192.gomoku.domain.ai.misc.AnnealingEngine
import com.github.lany192.gomoku.domain.ai.misc.AntColonyEngine
import com.github.lany192.gomoku.domain.ai.misc.ExpertSystemEngine
import com.github.lany192.gomoku.domain.ai.misc.FuzzyEngine
import com.github.lany192.gomoku.domain.ai.misc.GreedyEngine
import com.github.lany192.gomoku.domain.ai.search.SearchEngine
import com.github.lany192.gomoku.domain.ai.sim.MonteCarloEngine
import com.github.lany192.gomoku.domain.ai.threat.ThreatEngine
import kotlin.random.Random

/**
 * 算法 → 引擎实现的唯一映射点。
 *
 * when 穷尽（没有 else）：新增算法枚举项时漏了实现会编译失败。
 */
object AiEngineFactory {

    fun create(
        algorithm: AiAlgorithm,
        level: Difficulty,
        width: Int,
        height: Int,
        random: Random = Random.Default,
        clock: () -> Long = System::nanoTime,
        weightStore: AiWeightStore? = null,
    ): GomokuAI = when (algorithm) {
        AiAlgorithm.SHAPE_SCORE -> RobotAI(width, height, level, random)
        AiAlgorithm.MINIMAX,
        AiAlgorithm.ALPHA_BETA,
        AiAlgorithm.PVS,
        AiAlgorithm.MTD_F,
        AiAlgorithm.ITERATIVE_DEEPENING,
        AiAlgorithm.TRANSPOSITION_TABLE,
        AiAlgorithm.KILLER_HISTORY,
        -> SearchEngine(algorithm, width, height, level, random, clock)
        AiAlgorithm.PATTERN_TABLE -> SearchEngine(
            algorithm, width, height, level, random, clock, PatternTableEvaluator(width, height),
        )
        AiAlgorithm.NEURAL_EVAL -> SearchEngine(
            algorithm, width, height, level, random, clock, NeuralEvaluator(width, height),
        )
        AiAlgorithm.VCF,
        AiAlgorithm.VCT,
        AiAlgorithm.KILL_SEARCH,
        -> ThreatEngine(algorithm, width, height, level, random, clock)
        AiAlgorithm.MCTS,
        AiAlgorithm.UCT,
        AiAlgorithm.RAVE,
        -> MonteCarloEngine(algorithm, width, height, level, random, clock)
        AiAlgorithm.GREEDY -> GreedyEngine(algorithm, width, height, level, random, clock)
        AiAlgorithm.FUZZY -> FuzzyEngine(algorithm, width, height, level, random, clock)
        AiAlgorithm.EXPERT_SYSTEM -> ExpertSystemEngine(algorithm, width, height, level, random, clock)
        AiAlgorithm.ANT_COLONY -> AntColonyEngine(algorithm, width, height, level, random, clock)
        AiAlgorithm.SIMULATED_ANNEALING -> AnnealingEngine(algorithm, width, height, level, random, clock)
        AiAlgorithm.TD_LEARNING -> TdEngine(algorithm, width, height, level, random, clock, weightStore)
        AiAlgorithm.Q_LEARNING -> QLearningEngine(algorithm, width, height, level, random, clock, weightStore)
        AiAlgorithm.ALPHA_ZERO -> AlphaZeroEngine(algorithm, width, height, level, random, clock, weightStore)
        AiAlgorithm.GENETIC -> GeneticEngine(algorithm, width, height, level, random, clock, weightStore)
    }
}
