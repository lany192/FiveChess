package com.github.lany192.gomoku.domain.ai.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.tanh
import kotlin.random.Random

/**
 * 神经网络评估：手写三层 MLP —— 20 维棋型/邻域特征 → 32 个 tanh 隐层 → 1 个 tanh 输出，705 个参数。
 *
 * 权重用固定种子生成、只建一次，属于"结构固化"的评估器（学习类在线训练的权重在 learn/ 家族）。
 * 初值结构化：主通路押在归一化棋型总分上（保证量级次序与棋型评估一致），
 * 其余通路小幅正权重，整体对"己方特征更强"单调——评估值不会因随机初值而乱跳。
 *
 * 输出天然落在 (−1,1)，再缩放到 ±[OUTPUT_LIMIT] 并裁剪：绝不产生接近连五的终局分，
 * 否则搜索会把普通优势误判成必胜。
 */
class NeuralEvaluator(private val width: Int, private val height: Int) : Evaluator {

    private val my = ShapeCounts()
    private val rv = ShapeCounts()
    private val feature = DoubleArray(FEATURES)
    private val hidden = DoubleArray(HIDDEN)

    override fun evaluate(board: Array<IntArray>, self: Int): Long {
        collect(board, self)
        for (j in 0 until HIDDEN) {
            var z = b1[j]
            for (i in 0 until FEATURES) {
                z += w1[j * FEATURES + i] * feature[i]
            }
            hidden[j] = tanh(z)
        }
        var out = b2
        for (j in 0 until HIDDEN) {
            out += w2[j] * hidden[j]
        }
        val value = tanh(out) * OUTPUT_LIMIT
        return value.toLong().coerceIn(-OUTPUT_LIMIT + 1, OUTPUT_LIMIT - 1)
    }

    /** 全盘扫描一次填出 20 维特征：主优势项 + 双方棋型类计数 + 位置/子力项 */
    private fun collect(board: Array<IntArray>, self: Int) {
        my.reset()
        rv.reset()
        val rival = Stone.opponent(self)
        for (d in BoardScanner.DIRS) {
            val dx = d[0]
            val dy = d[1]
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val ex = x + 4 * dx
                    val ey = y + 4 * dy
                    if (!scannerInBoard(ex, ey)) continue
                    var mine = 0
                    var theirs = 0
                    for (i in 0 until 5) {
                        when (board[x + i * dx][y + i * dy]) {
                            self -> mine++
                            rival -> theirs++
                        }
                    }
                    if ((mine > 0) == (theirs > 0)) continue
                    var opens = 0
                    if (scannerEmpty(board, x - dx, y - dy)) opens++
                    if (scannerEmpty(board, ex + dx, ey + dy)) opens++
                    if (mine > 0) my.add(mine, opens) else rv.add(theirs, opens)
                }
            }
        }

        var myStones = 0
        var rvStones = 0
        var myCenter = 0.0
        var rvCenter = 0.0
        val mx = (width - 1) / 2.0
        val my0 = (height - 1) / 2.0
        val maxDist = mx + my0
        for (x in 0 until width) {
            for (y in 0 until height) {
                val cell = board[x][y]
                if (cell == Stone.EMPTY) continue
                val near = 1.0 - (abs(x - mx) + abs(y - my0)) / maxDist
                if (cell == self) {
                    myStones++
                    myCenter += near
                } else {
                    rvStones++
                    rvCenter += near
                }
            }
        }

        feature[0] = norm(my.agg) - norm(rv.agg)
        feature[1] = my.five * COUNT_SCALE
        feature[2] = my.liveFour * COUNT_SCALE
        feature[3] = my.rushFour * COUNT_SCALE
        feature[4] = my.liveThree * COUNT_SCALE
        feature[5] = my.sleepThree * COUNT_SCALE
        feature[6] = my.liveTwo * COUNT_SCALE
        feature[7] = my.low * COUNT_SCALE
        feature[8] = -rv.five * COUNT_SCALE
        feature[9] = -rv.liveFour * COUNT_SCALE
        feature[10] = -rv.rushFour * COUNT_SCALE
        feature[11] = -rv.liveThree * COUNT_SCALE
        feature[12] = -rv.sleepThree * COUNT_SCALE
        feature[13] = -rv.liveTwo * COUNT_SCALE
        feature[14] = -rv.low * COUNT_SCALE
        feature[15] = tanh((myCenter - rvCenter) / 5.0)
        feature[16] = tanh((myStones - rvStones) / 10.0)
        feature[17] = tanh((my.threePlus() - rv.threePlus()) / 3.0)
        feature[18] = 1.0
        feature[19] = tanh((my.fourPlus() - rv.fourPlus()) / 2.0)
    }

    /** 棋型总分的对数归一化：五 / 活四 / 活三 分别落在 ~1.0 / ~0.87 / ~0.66，次序严格 */
    private fun norm(agg: Long): Double =
        if (agg <= 0L) 0.0 else ln(1.0 + agg) / ln(1.0 + ShapeScores.FIVE)

    private fun scannerInBoard(x: Int, y: Int) = x in 0 until width && y in 0 until height

    private fun scannerEmpty(board: Array<IntArray>, x: Int, y: Int) =
        scannerInBoard(x, y) && board[x][y] == Stone.EMPTY

    /** 一种颜色的棋型统计：各档棋型出现次数 + 总分 */
    private class ShapeCounts {
        var five = 0
        var liveFour = 0
        var rushFour = 0
        var liveThree = 0
        var sleepThree = 0
        var liveTwo = 0
        var low = 0
        var agg = 0L

        fun reset() {
            five = 0
            liveFour = 0
            rushFour = 0
            liveThree = 0
            sleepThree = 0
            liveTwo = 0
            low = 0
            agg = 0L
        }

        fun add(count: Int, opens: Int) {
            agg += ShapeScores.score(count, opens)
            when {
                count >= 5 -> five++
                count == 4 && opens >= 2 -> liveFour++
                count == 4 -> rushFour++
                count == 3 && opens >= 2 -> liveThree++
                count == 3 -> sleepThree++
                count == 2 && opens >= 2 -> liveTwo++
                else -> low++
            }
        }

        fun threePlus() = five + liveFour + rushFour + liveThree + sleepThree

        fun fourPlus() = five + liveFour + rushFour
    }

    private companion object {
        const val FEATURES = 20
        const val HIDDEN = 32

        /** 20×32 + 32 + 32 + 1 = 705 个参数 */
        const val COUNT_SCALE = 0.02

        /** 输出上限：远低于终局分（WIN−ply），保证评估不会冒充杀棋 */
        const val OUTPUT_LIMIT = ShapeScores.WIN / 2

        /** 结构化初值：主通路（特征 0）权重大，其余小权重正扰动，整体单调 */
        private val INIT_SEED = 20250918L

        val w1: DoubleArray
        val b1: DoubleArray
        val w2: DoubleArray
        val b2: Double

        init {
            val random = Random(INIT_SEED)
            w1 = DoubleArray(HIDDEN * FEATURES)
            b1 = DoubleArray(HIDDEN)
            for (j in 0 until HIDDEN) {
                w1[j * FEATURES] = MAIN_PATH_WEIGHT
                for (i in 1 until FEATURES) {
                    w1[j * FEATURES + i] = random.nextDouble() * SIDE_PATH_WEIGHT
                }
                b1[j] = (random.nextDouble() - 0.5) * BIAS_SPAN
            }
            w2 = DoubleArray(HIDDEN) { OUTPUT_SCALE * (0.5 + random.nextDouble()) }
            b2 = (random.nextDouble() - 0.5) * BIAS_SPAN
        }

        const val MAIN_PATH_WEIGHT = 4.0
        const val SIDE_PATH_WEIGHT = 0.01
        const val BIAS_SPAN = 0.1
        const val OUTPUT_SCALE = 1.0 / HIDDEN
    }
}
