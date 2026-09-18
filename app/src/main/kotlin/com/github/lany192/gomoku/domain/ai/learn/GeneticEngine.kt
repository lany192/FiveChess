package com.github.lany192.gomoku.domain.ai.learn

import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.AiTunings
import com.github.lany192.gomoku.domain.ai.AiWeightStore
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.ai.EngineTuning
import com.github.lany192.gomoku.domain.ai.core.Candidate
import com.github.lany192.gomoku.domain.ai.core.LinearEvaluator
import com.github.lany192.gomoku.domain.ai.core.ShapeScores
import com.github.lany192.gomoku.domain.ai.core.Stone
import com.github.lany192.gomoku.domain.model.Point
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 遗传算法：基因组 = 20 维线性评估权重 + 攻击/防守两个元缩放，冠军基因组落库。
 *
 * 选点用冠军基因组做 1 层贪心（适应度对局与真实对局同一套着法口径，演化优化的就是真实棋力）；
 * 演化放在**本手选点之后**，每手至多推进一代，预算用尽就留到下一手继续（跨手续跑）。
 */
class GeneticEngine(
    private val algorithm: AiAlgorithm,
    width: Int,
    height: Int,
    level: Difficulty,
    random: Random,
    clock: () -> Long = System::nanoTime,
    weightStore: AiWeightStore? = null,
) : AbstractLearningEngine(width, height, level, random, clock, weightStore) {

    private val linear = LinearEvaluator(width, height)
    private val features = DoubleArray(LinearEvaluator.SIZE)

    private var champion = seed()

    /** 本代被评估者的共同对手：代开始时的冠军快照（代内不变，适应度才可比） */
    private var elite = DoubleArray(GENOME_SIZE)

    private var population: Array<DoubleArray>? = null
    private var fitness = DoubleArray(0)
    private var cursor = 0
    private var generations = 0

    /** 上一代已评估个体：锦标赛从这对数组里选，实现真正的选择压力 */
    private var poolGenomes: Array<DoubleArray> = emptyArray()
    private var poolFitness = DoubleArray(0)

    override fun algorithm(): AiAlgorithm = algorithm

    override fun applyStoredWeights(stored: DoubleArray) {
        if (stored.size == GENOME_SIZE) champion = stored.copyOf()
    }

    override fun currentWeights(): DoubleArray = champion.copyOf()

    /** 测试与调试用：当前冠军基因组 */
    internal fun championGenome(): DoubleArray = champion.copyOf()

    /** 测试与调试用：最近完成一代的适应度（索引 0 是精英） */
    internal fun lastGenerationFitness(): DoubleArray = fitness.copyOf()

    internal fun generationsDone(): Int = generations

    /** 每维基因的取值上限（权重为 4 倍先验量级，元参数上界同 [META_MAX]） */
    internal fun geneBounds(): DoubleArray = DoubleArray(GENOME_SIZE) { i ->
        if (i < WEIGHT_COUNT) BOUNDS[i] else META_MAX
    }

    override fun decide(board: Array<IntArray>, tuning: EngineTuning, candidates: List<Candidate>): Point {
        ensureWeightsLoaded()
        return greedyMove(board, candidates, champion, self)
            ?: candidates.firstOrNull()?.let { Point(it.x, it.y) }
            ?: Point(width / 2, height / 2)
    }

    override fun onMoveDecided(board: Array<IntArray>, tuning: EngineTuning, move: Point) {
        evolveStep()
    }

    /** 推进演化：没有在跑的一代就开新一代，预算内尽量多打适应度对局 */
    private fun evolveStep() {
        val options = AiTunings.learning(algorithm, level)
        val start = clock()
        var pop = population
        if (pop == null) {
            elite = champion.copyOf()
            pop = spawn(options.populationSize.coerceAtLeast(2))
            population = pop
            fitness = DoubleArray(pop.size)
            cursor = 0
        }
        val tasks = pop.size * FITNESS_GAMES
        while (cursor < tasks) {
            val index = cursor / FITNESS_GAMES
            val asWhite = cursor % FITNESS_GAMES == 0
            fitness[index] += playFitnessGame(pop[index], asWhite, options)
            cursor++
            if (clock() - start >= options.evolveBudgetMillis) return
        }
        finishGeneration(pop)
    }

    /** 一代收口：适应度最高者继位（精英也在候选里，冠军只会不劣于精英） */
    private fun finishGeneration(pop: Array<DoubleArray>) {
        var best = 0
        for (i in fitness.indices) {
            if (fitness[i] > fitness[best]) best = i
        }
        champion = pop[best].copyOf()
        poolGenomes = pop
        poolFitness = fitness.copyOf()
        population = null
        cursor = 0
        generations++
        markWeightsDirty()
        flushWeights()
    }

    /** 新种群：精英 + 锦标赛选择出的父代均匀交叉后高斯变异 */
    private fun spawn(size: Int): Array<DoubleArray> {
        val pop = Array(size) { DoubleArray(GENOME_SIZE) }
        pop[0] = elite.copyOf()
        for (i in 1 until size) {
            val child = crossover(tournamentPick(), tournamentPick())
            pop[i] = mutate(child)
        }
        return pop
    }

    /** 锦标赛 2：随机抽两个上一代个体，取适应度高者；首代没有种族池时用精英 */
    private fun tournamentPick(): DoubleArray {
        if (poolGenomes.isEmpty()) return elite
        val a = random.nextInt(poolGenomes.size)
        val b = random.nextInt(poolGenomes.size)
        return if (poolFitness[a] >= poolFitness[b]) poolGenomes[a] else poolGenomes[b]
    }

    private fun crossover(a: DoubleArray, b: DoubleArray): DoubleArray =
        DoubleArray(GENOME_SIZE) { if (random.nextBoolean()) a[it] else b[it] }

    private fun mutate(genome: DoubleArray): DoubleArray {
        for (i in 0 until WEIGHT_COUNT) {
            if (random.nextInt(100) >= MUTATION_PERCENT) continue
            val bound = BOUNDS[i]
            genome[i] = (genome[i] + gaussian() * bound * MUTATION_SIGMA).coerceIn(-bound, bound)
        }
        for (i in WEIGHT_COUNT until GENOME_SIZE) {
            if (random.nextInt(100) >= MUTATION_PERCENT) continue
            genome[i] = (genome[i] + gaussian() * META_SIGMA).coerceIn(META_MIN, META_MAX)
        }
        return genome
    }

    /** Box–Muller：只依赖注入的 Random 流，保证种子可复现 */
    private fun gaussian(): Double {
        var u = random.nextDouble()
        while (u <= 0.0) u = random.nextDouble()
        return sqrt(-2.0 * ln(u)) * cos(2.0 * PI * random.nextDouble())
    }

    /** 个体（执 [individualAsWhite] 对应颜色）与冠军快照一局，胜 1 / 和 0.5 / 负 0 */
    private fun playFitnessGame(individual: DoubleArray, individualAsWhite: Boolean, options: LearningOptions): Double {
        val board = Array(width) { IntArray(height) }
        val individualColor = if (individualAsWhite) Stone.WHITE else Stone.BLACK
        var toMove = Stone.BLACK
        for (ply in 0 until options.fitnessPlies) {
            val genome = if (toMove == individualColor) individual else elite
            val candidates = generator.generate(board, options.breadth, options.radius, toMove, Stone.opponent(toMove))
            val move = greedyMove(board, candidates, genome, toMove) ?: break
            board[move.x][move.y] = toMove
            if (scanner.isFiveAt(board, move.x, move.y, toMove)) {
                return if (toMove == individualColor) WIN_SCORE else LOSS_SCORE
            }
            toMove = Stone.opponent(toMove)
        }
        return DRAW_SCORE
    }

    /** 1 层贪心：逐候选试落并用基因组评分，并列取排序靠前者（确定性） */
    private fun greedyMove(
        board: Array<IntArray>,
        candidates: List<Candidate>,
        genome: DoubleArray,
        color: Int,
    ): Point? {
        var best: Point? = null
        var bestValue = Double.NEGATIVE_INFINITY
        for (c in candidates) {
            if (board[c.x][c.y] != Stone.EMPTY) continue
            board[c.x][c.y] = color
            val value = evaluateWith(genome, board, color)
            board[c.x][c.y] = Stone.EMPTY
            if (value > bestValue) {
                bestValue = value
                best = Point(c.x, c.y)
            }
        }
        return best
    }

    /** 以 [color] 视角按基因组给局面打分：元参数分别缩放己方/对方特征项 */
    private fun evaluateWith(genome: DoubleArray, board: Array<IntArray>, color: Int): Double {
        linear.fillFeatures(board, color, features)
        var mine = 0.0
        var theirs = 0.0
        for (i in 0 until LinearEvaluator.SLOTS) {
            mine += genome[i] * features[i]
            theirs += genome[LinearEvaluator.SLOTS + i] * features[LinearEvaluator.SLOTS + i]
        }
        return genome[META_ATTACK] * mine + genome[META_DEFENSE] * theirs
    }

    private fun seed(): DoubleArray {
        val genome = DoubleArray(GENOME_SIZE)
        System.arraycopy(LinearEvaluator.priorWeights(), 0, genome, 0, WEIGHT_COUNT)
        genome[META_ATTACK] = 1.0
        genome[META_DEFENSE] = 1.0
        return genome
    }

    companion object {
        /** 20 维评估权重 + 攻击/防守两个元缩放 */
        const val GENOME_SIZE = LinearEvaluator.SIZE + 2

        private const val WEIGHT_COUNT = LinearEvaluator.SIZE
        private const val META_ATTACK = WEIGHT_COUNT
        private const val META_DEFENSE = WEIGHT_COUNT + 1

        /** 每个个体黑白各一局 */
        private const val FITNESS_GAMES = 2
        private const val WIN_SCORE = 1.0
        private const val LOSS_SCORE = 0.0
        private const val DRAW_SCORE = 0.5

        private const val MUTATION_PERCENT = 25
        private const val MUTATION_SIGMA = 0.1
        private const val META_SIGMA = 0.12
        private const val META_MIN = 0.25
        private const val META_MAX = 4.0

        /** 权重基因上限 = 4 倍先验量级；先验为 0 的槽位（子数）用最小棋型分兜底 */
        private val BOUNDS: DoubleArray = LinearEvaluator.priorWeights().let { prior ->
            DoubleArray(WEIGHT_COUNT) { maxOf(abs(prior[it]), ShapeScores.LIVE_ONE.toDouble()) * 4.0 }
        }
    }
}
