package com.github.lany192.gomoku.domain.ai.learn

import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.AiTunings
import com.github.lany192.gomoku.domain.ai.AiWeightStore
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.ai.EngineTuning
import com.github.lany192.gomoku.domain.ai.GameOutcome
import com.github.lany192.gomoku.domain.ai.core.BoardScanner
import com.github.lany192.gomoku.domain.ai.core.Candidate
import com.github.lany192.gomoku.domain.ai.core.Stone
import com.github.lany192.gomoku.domain.model.Point
import com.github.lany192.gomoku.domain.model.Side
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/** PUCT 树节点：move 为父方走入本节点的着法（根为 -1） */
private class AzNode(val move: Int, val toMove: Int, val prior: Double) {
    var visits = 0
    var valueSum = 0.0
    var expanded = false
    var terminal = false

    /** 展开时价值头给出的估值（本节点 toMove 视角） */
    var value = 0.0

    /** 展开时的逐点特征与候选点（根节点留作训练样本） */
    var features: DoubleArray? = null
    var points: IntArray? = null
    val children = ArrayList<AzNode>()
}

/**
 * AlphaZero：PUCT MCTS（c_puct=1.5）+ 手写策略/价值网络，叶子价值来自价值头，无 rollout。
 *
 * 根先验 = (1−λ)·softmax(策略头) + λ·静态棋型分布，λ 随累计训练样本数衰减（冷启动先像棋型评估，
 * 练得越多越像自己的策略头）。终局用环形 buffer 做 2 步 SGD：CE(π,p) + (z−v)²。
 * 网络权重（962 维）与其它学习引擎一样懒加载、随手下落库。
 */
class AlphaZeroEngine(
    private val algorithm: AiAlgorithm,
    width: Int,
    height: Int,
    level: Difficulty,
    random: Random,
    clock: () -> Long = System::nanoTime,
    weightStore: AiWeightStore? = null,
) : AbstractLearningEngine(width, height, level, random, clock, weightStore) {

    private val net = AzNetwork(random)
    private val buffer = ArrayDeque<AzSample>()

    /** 累计训练样本数：根先验 λ 的衰减依据 */
    private var trainedSamples = 0

    override fun algorithm(): AiAlgorithm = algorithm

    override fun applyStoredWeights(stored: DoubleArray) {
        net.applyWeights(stored)
    }

    override fun currentWeights(): DoubleArray = net.snapshot()

    override fun onGameReset() {
        // 悔棋/重开会打断本局轨迹：带未闭合 z 的样本作废
        buffer.clear()
    }

    override fun decide(board: Array<IntArray>, tuning: EngineTuning, candidates: List<Candidate>): Point {
        ensureWeightsLoaded()
        val options = AiTunings.learning(algorithm, level)
        val session = AzSession(board, options)
        val move = session.run()
        session.sample()?.let { sample ->
            if (buffer.size >= BUFFER_CAPACITY) buffer.removeFirst()
            buffer.addLast(sample)
        }
        flushWeights()
        return move
    }

    override fun onTerminal(outcome: GameOutcome) {
        if (buffer.isEmpty()) return
        val z = when (outcome.winner) {
            Side.WHITE -> 1.0
            Side.BLACK -> -1.0
            else -> 0.0
        }
        for (sample in buffer) sample.z = z
        repeat(TRAIN_STEPS) {
            var step = 0
            for (sample in buffer) {
                if (step++ >= TRAIN_BATCH) break
                net.train(sample, LEARNING_RATE)
            }
        }
        trainedSamples += buffer.size
        markWeightsDirty()
    }

    /** 根先验里静态棋型分的权重：样本越多越小，最低保留 1/4 */
    private fun rootLambda(): Double = (1.0 - trainedSamples / LAMBDA_DECAY_SAMPLES).coerceAtLeast(LAMBDA_FLOOR)

    private inner class AzSession(
        private val board: Array<IntArray>,
        private val options: LearningOptions,
    ) {
        private val root = AzNode(-1, self, 1.0)
        private val startNanos = clock()

        fun run(): Point {
            expand(root)
            var simulated = 0
            while (simulated < options.simulations) {
                if (options.timeBudgetMillis > 0 &&
                    (clock() - startNanos) / 1_000_000 >= options.timeBudgetMillis
                ) {
                    break
                }
                simulate()
                simulated++
            }
            if (root.children.isEmpty()) return Point(width / 2, height / 2)
            var best = root.children[0]
            for (c in root.children) {
                if (c.visits > best.visits) best = c
            }
            return Point(best.move / height, best.move % height)
        }

        /** 根节点的训练样本：逐点特征 + 访问分布 π（z 由终局回填） */
        fun sample(): AzSample? {
            val features = root.features ?: return null
            val points = root.points ?: return null
            val n = points.size
            if (n == 0) return null
            val visits = DoubleArray(n)
            var total = 0.0
            for ((i, child) in root.children.withIndex()) {
                visits[i] = child.visits.toDouble()
                total += child.visits
            }
            if (total <= 0.0) {
                for (i in 0 until n) visits[i] = 1.0 / n
            } else {
                for (i in 0 until n) visits[i] /= total
            }
            return AzSample(n, features, visits)
        }

        private fun simulate() {
            root.visits++
            val path = ArrayList<AzNode>()
            var node = root
            while (node.expanded && !node.terminal) {
                val child = select(node)
                place(child.move, node.toMove)
                val x = child.move / height
                val y = child.move % height
                if (scanner.isFiveAt(board, x, y, node.toMove)) child.terminal = true
                path.add(child)
                node = child
            }
            val value = if (node.terminal) {
                // 上一手连五，该方必败
                -1.0
            } else {
                expand(node)
                node.value
            }
            var v = value
            for (i in path.indices.reversed()) {
                val n = path[i]
                n.visits++
                n.valueSum += v
                v = -v
            }
            for (i in path.indices.reversed()) {
                val move = path[i].move
                board[move / height][move % height] = Stone.EMPTY
            }
        }

        /** PUCT 选择：Q 取子树视角再翻转（子节点 valueSum 记的是对手视角） */
        private fun select(node: AzNode): AzNode {
            val sqrtVisits = sqrt(node.visits.toDouble())
            var best = node.children[0]
            var bestScore = Double.NEGATIVE_INFINITY
            for (c in node.children) {
                val q = if (c.visits == 0) 0.0 else -(c.valueSum / c.visits)
                val score = q + options.cPuct * c.prior * sqrtVisits / (1 + c.visits)
                if (score > bestScore) {
                    bestScore = score
                    best = c
                }
            }
            return best
        }

        /** 展开：逐点特征 → 网络先验 + 价值；根节点额外混入静态棋型分布 */
        private fun expand(node: AzNode) {
            node.expanded = true
            val color = node.toMove
            val candidates = generator.generate(
                board, minOf(options.breadth, MAX_POINTS), options.radius, color, Stone.opponent(color),
            )
            if (candidates.isEmpty()) {
                node.value = 0.0
                return
            }
            val n = candidates.size
            val stats = boardStats(board, color)
            val features = DoubleArray(n * AzNetwork.FEATURES)
            for (i in 0 until n) {
                fillPointFeatures(board, candidates[i].x, candidates[i].y, color, stats, features, i * AzNetwork.FEATURES)
            }
            val hidden = DoubleArray(n * AzNetwork.HIDDEN)
            val logits = DoubleArray(n)
            val value = net.policyAndValue(features, n, hidden, logits)
            val probs = softmax(logits)
            val lambda = if (node === root) rootLambda() else 0.0
            val static = if (lambda > 0.0) staticDistribution(candidates) else null
            val points = IntArray(n) { candidates[it].x * height + candidates[it].y }
            for (i in 0 until n) {
                val prior = if (static == null) probs[i] else (1.0 - lambda) * probs[i] + lambda * static[i]
                node.children.add(AzNode(points[i], color, prior))
            }
            node.value = value
            node.features = features
            node.points = points
        }

        private fun softmax(logits: DoubleArray): DoubleArray {
            var maxLogit = logits[0]
            for (v in logits) if (v > maxLogit) maxLogit = v
            var sum = 0.0
            val probs = DoubleArray(logits.size)
            for (i in logits.indices) {
                probs[i] = exp(logits[i] - maxLogit)
                sum += probs[i]
            }
            for (i in probs.indices) probs[i] /= sum
            return probs
        }

        /** 静态棋型分布：候选点自身分（含攻防与中心偏置）压缩成概率 */
        private fun staticDistribution(candidates: List<Candidate>): DoubleArray {
            val scores = DoubleArray(candidates.size) { candidates[it].score.toDouble() }
            var max = scores[0]
            var min = scores[0]
            for (s in scores) {
                if (s > max) max = s
                if (s < min) min = s
            }
            val span = (max - min).coerceAtLeast(1.0)
            var sum = 0.0
            val probs = DoubleArray(scores.size)
            for (i in scores.indices) {
                probs[i] = exp((scores[i] - max) / span * STATIC_TEMP)
                sum += probs[i]
            }
            for (i in probs.indices) probs[i] /= sum
            return probs
        }

        private fun place(move: Int, color: Int) {
            board[move / height][move % height] = color
        }
    }

    /** 43 维里与位置无关的全局统计（按 color 视角），每个局面只算一次 */
    private class BoardStats {
        var myFive = 0
        var rivalFive = 0
        var myLiveFour = 0
        var rivalLiveFour = 0
        var myLiveThree = 0
        var rivalLiveThree = 0
        var myStones = 0
        var rivalStones = 0
    }

    private fun boardStats(board: Array<IntArray>, color: Int): BoardStats {
        val rival = Stone.opponent(color)
        val stats = BoardStats()
        for (d in BoardScanner.DIRS) {
            val dx = d[0]
            val dy = d[1]
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val ex = x + 4 * dx
                    val ey = y + 4 * dy
                    if (!scanner.inBoard(ex, ey)) continue
                    var mine = 0
                    var theirs = 0
                    for (i in 0 until 5) {
                        when (board[x + i * dx][y + i * dy]) {
                            color -> mine++
                            rival -> theirs++
                        }
                    }
                    if ((mine > 0) == (theirs > 0)) continue
                    var opens = 0
                    if (scanner.isEmpty(board, x - dx, y - dy)) opens++
                    if (scanner.isEmpty(board, ex + dx, ey + dy)) opens++
                    if (mine >= 5) stats.myFive += 1
                    if (theirs >= 5) stats.rivalFive += 1
                    if (mine == 4 && opens >= 2) stats.myLiveFour += 1
                    if (theirs == 4 && opens >= 2) stats.rivalLiveFour += 1
                    if (mine == 3 && opens >= 2) stats.myLiveThree += 1
                    if (theirs == 3 && opens >= 2) stats.rivalLiveThree += 1
                }
            }
        }
        for (x in 0 until width) {
            for (y in 0 until height) {
                when (board[x][y]) {
                    color -> stats.myStones++
                    rival -> stats.rivalStones++
                }
            }
        }
        return stats
    }

    /** 37 维逐点特征：4 方向 × (己方连子/开放端/对方连子/开放端) + 方向线内子数 + 位置 + 全局棋型统计 */
    private fun fillPointFeatures(
        board: Array<IntArray>,
        x: Int,
        y: Int,
        color: Int,
        stats: BoardStats,
        out: DoubleArray,
        offset: Int,
    ) {
        val rival = Stone.opponent(color)
        for ((i, d) in BoardScanner.DIRS.withIndex()) {
            scanner.lineInfo(board, x, y, color, d)
            val myRun = scanner.count
            val myOpens = scanner.opens
            scanner.lineInfo(board, x, y, rival, d)
            val rivalRun = scanner.count
            val rivalOpens = scanner.opens
            out[offset + 4 * i] = minOf(myRun, 5) / 5.0
            out[offset + 4 * i + 1] = myOpens / 2.0
            out[offset + 4 * i + 2] = minOf(rivalRun, 5) / 5.0
            out[offset + 4 * i + 3] = rivalOpens / 2.0

            var myLine = 0
            var rivalLine = 0
            for (step in -4..4) {
                if (step == 0) continue
                val px = x + d[0] * step
                val py = y + d[1] * step
                if (!scanner.inBoard(px, py)) continue
                when (board[px][py]) {
                    color -> myLine++
                    rival -> rivalLine++
                }
            }
            out[offset + 16 + i] = myLine / 8.0
            out[offset + 20 + i] = rivalLine / 8.0
        }

        val maxX = (width - 1).coerceAtLeast(1)
        val maxY = (height - 1).coerceAtLeast(1)
        out[offset + 24] = x.toDouble() / maxX
        out[offset + 25] = y.toDouble() / maxY
        val centerX = width / 2
        val centerY = height / 2
        val maxDist = (centerX + centerY).coerceAtLeast(1)
        out[offset + 26] = 1.0 - (abs(x - centerX) + abs(y - centerY)).toDouble() / maxDist

        var myNeighbors = 0
        var rivalNeighbors = 0
        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val px = x + dx
                val py = y + dy
                if (!scanner.inBoard(px, py)) continue
                when (board[px][py]) {
                    color -> myNeighbors++
                    rival -> rivalNeighbors++
                }
            }
        }
        out[offset + 27] = myNeighbors / 8.0
        out[offset + 28] = rivalNeighbors / 8.0

        val scale = (width * height / 4.0).coerceAtLeast(1.0)
        out[offset + 29] = stats.myStones / scale
        out[offset + 30] = stats.rivalStones / scale
        out[offset + 31] = minOf(stats.myFive, 3) / 3.0
        out[offset + 32] = minOf(stats.rivalFive, 3) / 3.0
        out[offset + 33] = minOf(stats.myLiveFour, 3) / 3.0
        out[offset + 34] = minOf(stats.rivalLiveFour, 3) / 3.0
        out[offset + 35] = minOf(stats.myLiveThree, 3) / 3.0
        out[offset + 36] = minOf(stats.rivalLiveThree, 3) / 3.0
    }

    private companion object {
        const val MAX_POINTS = 16
        const val BUFFER_CAPACITY = 64
        const val TRAIN_STEPS = 2
        const val TRAIN_BATCH = 16
        const val LEARNING_RATE = 0.05
        const val LAMBDA_DECAY_SAMPLES = 2000.0
        const val LAMBDA_FLOOR = 0.25
        const val STATIC_TEMP = 4.0
    }
}
