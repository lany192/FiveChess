package com.github.lany192.gomoku.domain.ai.core

/**
 * 线性价值函数：V(s) = w · φ(s)，φ 是"双方各 10 维"的棋型计数特征。
 *
 * 特征槽位（每方 10 个）：五连 / 活四 / 冲四 / 活三 / 眠三 / 活二 / 眠二 / 活一 / 眠一 / 子数。
 * [priorWeights] 用棋型分值做先验初值，冷启动时的量级与 [ShapeEvaluator] 一致；
 * 学习类引擎在线更新 [weights]，本类同时实现 [Evaluator]，可直接插进搜索核心。
 */
class LinearEvaluator(private val width: Int, private val height: Int) : Evaluator {

    /** 20 维权重：前 10 维为己方特征权重，后 10 维为对方（取负值） */
    var weights: DoubleArray = priorWeights()

    override fun evaluate(board: Array<IntArray>, self: Int): Long {
        val features = DoubleArray(SLOTS * 2)
        fillFeatures(board, self, features)
        var value = 0.0
        for (i in features.indices) {
            value += weights[i] * features[i]
        }
        return value.toLong()
    }

    /** 直接把 φ(s) 写进调用方缓冲（避免热路径分配）；[self] 是特征视角的一方 */
    fun fillFeatures(board: Array<IntArray>, self: Int, out: DoubleArray) {
        java.util.Arrays.fill(out, 0.0)
        val rival = Stone.opponent(self)
        for (d in BoardScanner.DIRS) {
            val dx = d[0]
            val dy = d[1]
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val ex = x + 4 * dx
                    val ey = y + 4 * dy
                    if (!inBoard(ex, ey)) continue
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
                    if (isEmpty(board, x - dx, y - dy)) opens++
                    if (isEmpty(board, ex + dx, ey + dy)) opens++
                    if (mine > 0) out[slot(mine, opens)]++ else out[SLOTS + slot(theirs, opens)]++
                }
            }
        }
        for (x in 0 until width) {
            for (y in 0 until height) {
                when (board[x][y]) {
                    self -> out[SLOTS - 1]++
                    rival -> out[SLOTS * 2 - 1]++
                }
            }
        }
    }

    /** 棋型 → 特征槽位 0..8；槽位 9 是子数（由调用方单独累加） */
    private fun slot(count: Int, opens: Int): Int = when {
        count >= 5 -> 0
        count == 4 && opens >= 2 -> 1
        count == 4 -> 2
        count == 3 && opens >= 2 -> 3
        count == 3 -> 4
        count == 2 && opens >= 2 -> 5
        count == 2 -> 6
        opens >= 2 -> 7
        else -> 8
    }

    private fun inBoard(x: Int, y: Int) = x in 0 until width && y in 0 until height

    private fun isEmpty(board: Array<IntArray>, x: Int, y: Int) =
        inBoard(x, y) && board[x][y] == Stone.EMPTY

    companion object {
        const val SLOTS = 10
        const val SIZE = SLOTS * 2

        /** 特征维度 20，学习类引擎加载存储权重时用它做尺寸校验 */
        const val FEATURE_SIZE = SIZE

        /** 先验：己方权重 = 棋型分值，对方权重 = −1.1 倍（对齐 ShapeEvaluator 的防守偏置） */
        fun priorWeights(): DoubleArray {
            val perSide = doubleArrayOf(
                ShapeScores.FIVE.toDouble(),
                ShapeScores.LIVE_FOUR.toDouble(),
                ShapeScores.RUSH_FOUR.toDouble(),
                ShapeScores.LIVE_THREE.toDouble(),
                ShapeScores.SLEEP_THREE.toDouble(),
                ShapeScores.LIVE_TWO.toDouble(),
                ShapeScores.SLEEP_TWO.toDouble(),
                ShapeScores.LIVE_ONE.toDouble(),
                ShapeScores.SLEEP_ONE.toDouble(),
                0.0,
            )
            val weights = DoubleArray(SIZE)
            for (i in 0 until SLOTS) {
                weights[i] = perSide[i]
                weights[SLOTS + i] = -perSide[i] * RIVAL_FACTOR
            }
            return weights
        }

        private const val RIVAL_FACTOR = 1.1
    }
}
