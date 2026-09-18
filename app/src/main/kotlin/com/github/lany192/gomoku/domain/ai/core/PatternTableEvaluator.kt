package com.github.lany192.gomoku.domain.ai.core

/**
 * 模式匹配评估：以棋子为中心，把它所在方向的 9 格线段编码成 18bit 索引查表。
 *
 * 表在首次评估时构建（搜索线程），构建过程即"模式匹配"——把 2^18 种线段模式
 * 逐一归类成棋型分；评估时只有数组查表，没有滑窗与分支开销。
 *
 * 与 [ShapeEvaluator] 的窗口式扫描不同：这里按"每颗子的四方向最强窗"累加，
 * 单个棋型会被其包含的每颗子各记一次，量级次序（五 > 活四 > 活三）保持一致。
 */
class PatternTableEvaluator(private val width: Int, private val height: Int) : Evaluator {

    override fun evaluate(board: Array<IntArray>, self: Int): Long {
        val rival = Stone.opponent(self)
        var mine = 0L
        var theirs = 0L
        for (x in 0 until width) {
            for (y in 0 until height) {
                when (board[x][y]) {
                    self -> mine += stoneScore(board, x, y, self)
                    rival -> theirs += stoneScore(board, x, y, rival)
                    else -> {}
                }
            }
        }
        return mine - theirs - theirs / 10
    }

    /** 一颗棋子四个方向的查表分之和 */
    private fun stoneScore(board: Array<IntArray>, x: Int, y: Int, color: Int): Long {
        var total = 0L
        for (d in BoardScanner.DIRS) {
            total += PatternTable.table[lineIndex(board, x, y, color, d)]
        }
        return total
    }

    /** 以 (x,y) 为中心沿 d 方向取 9 格，每格 2bit（空 0 / 己方 1 / 对方 2 / 棋盘外 3）拼成 18bit 索引 */
    private fun lineIndex(board: Array<IntArray>, x: Int, y: Int, color: Int, d: IntArray): Int {
        val rival = Stone.opponent(color)
        var index = 0
        for (i in -4..4) {
            val px = x + d[0] * i
            val py = y + d[1] * i
            val code = when {
                px !in 0 until width || py !in 0 until height -> PatternTable.CODE_OUT
                board[px][py] == color -> PatternTable.CODE_SELF
                board[px][py] == rival -> PatternTable.CODE_RIVAL
                else -> PatternTable.CODE_EMPTY
            }
            index = (index shl 2) or code
        }
        return index
    }
}

/**
 * 18bit 线段表：内容只取决于线段本身，与棋盘尺寸、实例无关，全局一份。
 *
 * by lazy 保证建表发生在首次评估（引擎都在后台线程搜索）而不是引擎构造时。
 */
private object PatternTable {
    const val CODE_EMPTY = 0
    const val CODE_SELF = 1
    const val CODE_RIVAL = 2

    /** 棋盘外：按被堵死处理（与滑窗评估的开端判定一致） */
    const val CODE_OUT = 3

    private const val LINE_LEN = 9
    private const val TABLE_SIZE = 1 shl (LINE_LEN * 2)

    /** 2bit/格 × 9 格 = 18bit 索引 → 该位置上的棋型分 */
    val table: LongArray by lazy { LongArray(TABLE_SIZE) { buildScore(it) } }

    /**
     * 打表：中心必须为己方子（否则 0 分，这类索引实际查不到）；
     * 取"包含中心的 5 格窗"里最强的棋型分——9 格线段中 5 格窗恰好都包含中心。
     */
    private fun buildScore(index: Int): Long {
        val cells = IntArray(LINE_LEN)
        var v = index
        for (i in LINE_LEN - 1 downTo 0) {
            cells[i] = v and 3
            v = v shr 2
        }
        if (cells[LINE_LEN / 2] != CODE_SELF) return 0L
        var best = 0L
        for (start in 0..LINE_LEN - 5) {
            var mine = 0
            var blocked = false
            for (i in 0 until 5) {
                when (cells[start + i]) {
                    CODE_SELF -> mine++
                    CODE_EMPTY -> {}
                    else -> {
                        blocked = true
                        break
                    }
                }
            }
            if (blocked) continue
            var opens = 0
            if (start - 1 >= 0 && cells[start - 1] == CODE_EMPTY) opens++
            if (start + 5 < LINE_LEN && cells[start + 5] == CODE_EMPTY) opens++
            val score = ShapeScores.score(mine, opens)
            if (score > best) best = score
        }
        return best
    }
}
