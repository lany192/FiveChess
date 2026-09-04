package com.github.lany192.fivechess.domain.ai

import com.github.lany192.fivechess.domain.model.Point
import java.util.Collections
import kotlin.random.Random

/**
 * 电脑AI：棋型评估 + 带Alpha-Beta剪枝的极小极大搜索
 *
 * 输入棋盘沿用整型编码：0=空 1=黑 2=白。
 * 非线程安全：同一实例不可并发调用（由 ViewModel 保证同一时刻只有一个搜索任务）。
 */
class RobotAI(
    private val width: Int,
    private val height: Int,
    level: Difficulty = Difficulty.MEDIUM,
    private val random: Random = Random.Default,
) {
    var level: Difficulty = level

    /**
     * 获取最佳下棋位置
     */
    fun getPosition(map: Array<IntArray>): Point {
        // 复制棋盘，避免搜索过程修改调用方的棋盘
        val board = Array(width) { x -> map[x].copyOf() }

        val candidates = generateCandidates(board, level.breadth)

        // 1.自己下一手能连五，直接取胜
        findFivePoint(board, candidates, AI)?.let { return it }
        // 2.对手下一手能连五，必须封堵
        findFivePoint(board, candidates, HUMAN)?.let { return it }
        // 3.自己能形成活四（对手堵不住），必胜
        if (level >= Difficulty.MEDIUM) {
            findLiveFourPoint(board, candidates, AI)?.let { return it }
        }
        // 简单难度不搜索，在排名靠前的点里随机挑一个，留出失误空间
        if (level == Difficulty.EASY) {
            return easyMove(candidates)
        }

        val best = searchRoot(board, candidates, level.depth)
        if (best != null) {
            return Point(best.x, best.y)
        }
        // 兜底：找任意空位
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (board[x][y] == EMPTY) return Point(x, y)
            }
        }
        return Point(width / 2, height / 2)
    }

    /**
     * 根层搜索，带Alpha-Beta窗口传递
     */
    private fun searchRoot(board: Array<IntArray>, candidates: List<Candidate>, depth: Int): Candidate? {
        var best: Candidate? = null
        var alpha = -WIN_SCORE * 2
        val beta = WIN_SCORE * 2
        for (c in candidates) {
            board[c.x][c.y] = AI
            val score = if (isFiveAt(board, c.x, c.y, AI)) {
                WIN_SCORE
            } else {
                search(board, depth - 1, alpha, beta, aiTurn = false, ply = 1)
            }
            board[c.x][c.y] = EMPTY
            if (best == null || score > best.score) {
                best = Candidate(c.x, c.y, score)
            }
            if (score > alpha) {
                alpha = score
            }
        }
        return best
    }

    /**
     * 极小极大搜索，带Alpha-Beta剪枝，评分始终以AI视角为准
     *
     * @param aiTurn 当前是否轮到AI落子
     * @param ply    当前步数（用于优先选择更快的胜利）
     */
    private fun search(
        board: Array<IntArray>,
        depth: Int,
        alpha: Long,
        beta: Long,
        aiTurn: Boolean,
        ply: Int,
    ): Long {
        if (depth <= 0) {
            return evaluate(board)
        }
        val candidates = generateCandidates(board, level.breadth)
        if (candidates.isEmpty()) {
            return evaluate(board)
        }
        val color = if (aiTurn) AI else HUMAN
        var best = if (aiTurn) -WIN_SCORE * 2 else WIN_SCORE * 2
        var a = alpha
        var b = beta
        for (c in candidates) {
            board[c.x][c.y] = color
            val score = if (isFiveAt(board, c.x, c.y, color)) {
                // 落子即连五，越早赢分越高
                if (aiTurn) WIN_SCORE - ply else -(WIN_SCORE - ply)
            } else {
                search(board, depth - 1, a, b, !aiTurn, ply + 1)
            }
            board[c.x][c.y] = EMPTY

            if (aiTurn) {
                if (score > best) best = score
                if (best > a) a = best
            } else {
                if (score < best) best = score
                if (best < b) b = best
            }
            if (a >= b) break
        }
        return best
    }

    /**
     * 生成候选点：只考虑已有棋子附近的空位，按落子后的局部棋型分排序，取前limit个
     */
    private fun generateCandidates(board: Array<IntArray>, limit: Int): List<Candidate> {
        val list = ArrayList<Candidate>()
        val radius = level.radius
        val centerX = width / 2
        val centerY = height / 2
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (board[x][y] != EMPTY || !hasNeighbor(board, x, y, radius)) continue
                val score = pointScore(board, x, y, AI) + pointScore(board, x, y, HUMAN) -
                        (Math.abs(x - centerX) + Math.abs(y - centerY))
                list.add(Candidate(x, y, score))
            }
        }
        if (list.isEmpty()) {
            list.add(Candidate(width / 2, height / 2, 0))
        }
        Collections.sort(list) { a, b -> b.score.compareTo(a.score) }
        return if (list.size > limit) list.subList(0, limit) else list
    }

    /**
     * 假设在(x,y)落下color子，四个方向的局部棋型分之和（用于候选点排序）
     */
    private fun pointScore(board: Array<IntArray>, x: Int, y: Int, color: Int): Long {
        var total = 0L
        for (d in DIRS) {
            lineInfo(board, x, y, color, d)
            total += shapeScore(lineCount, lineOpens)
        }
        return total
    }

    /**
     * 评估整个棋盘，返回AI视角的局分（正值对AI有利）
     */
    private fun evaluate(board: Array<IntArray>): Long {
        var mine = 0L
        var opponent = 0L
        for (d in DIRS) {
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val ex = x + 4 * d[0]
                    val ey = y + 4 * d[1]
                    if (!inBoard(ex, ey)) continue
                    var mineCount = 0
                    var oppCount = 0
                    for (i in 0 until 5) {
                        when (board[x + i * d[0]][y + i * d[1]]) {
                            AI -> mineCount++
                            HUMAN -> oppCount++
                        }
                    }
                    // 同时包含双方棋子或全空的窗口没有价值
                    if (mineCount > 0 && oppCount > 0) continue
                    if (mineCount == 0 && oppCount == 0) continue
                    var opens = 0
                    if (isEmpty(board, x - d[0], y - d[1])) opens++
                    if (isEmpty(board, ex + d[0], ey + d[1])) opens++
                    if (mineCount > 0) {
                        mine += shapeScore(mineCount, opens)
                    } else {
                        opponent += shapeScore(oppCount, opens)
                    }
                }
            }
        }
        // 对手棋型权重略高，让AI防守更稳
        return mine - opponent - opponent / 10
    }

    /**
     * 在候选点中寻找color落子即连五的位置
     */
    private fun findFivePoint(board: Array<IntArray>, candidates: List<Candidate>, color: Int): Point? {
        for (c in candidates) {
            board[c.x][c.y] = color
            val five = isFiveAt(board, c.x, c.y, color)
            board[c.x][c.y] = EMPTY
            if (five) return Point(c.x, c.y)
        }
        return null
    }

    /**
     * 寻找能形成活四的必胜点（两个连五点，对手只能堵一个）
     */
    private fun findLiveFourPoint(board: Array<IntArray>, candidates: List<Candidate>, color: Int): Point? {
        for (c in candidates) {
            board[c.x][c.y] = color
            var liveFour = 0
            for (d in DIRS) {
                lineInfo(board, c.x, c.y, color, d)
                if (lineCount == 4 && lineOpens >= 2) liveFour++
            }
            board[c.x][c.y] = EMPTY
            if (liveFour > 0) return Point(c.x, c.y)
        }
        return null
    }

    /**
     * 简单难度：55%最优、30%次优、15%第三优
     */
    private fun easyMove(candidates: List<Candidate>): Point {
        val r = random.nextInt(100)
        val candidate = when {
            candidates.size > 2 && r >= 85 -> candidates[2]
            candidates.size > 1 && r >= 55 -> candidates[1]
            else -> candidates[0]
        }
        return Point(candidate.x, candidate.y)
    }

    /**
     * 统计(x,y)处color子在方向d上的连子数（该处视为已落下color子）及两端的开放数
     */
    private fun lineInfo(board: Array<IntArray>, x: Int, y: Int, color: Int, d: IntArray) {
        var count = 1
        var opens = 0
        var i = 1
        while (inBoard(x + d[0] * i, y + d[1] * i) && board[x + d[0] * i][y + d[1] * i] == color) {
            count++
            i++
        }
        if (isEmpty(board, x + d[0] * i, y + d[1] * i)) opens++
        var j = 1
        while (inBoard(x - d[0] * j, y - d[1] * j) && board[x - d[0] * j][y - d[1] * j] == color) {
            count++
            j++
        }
        if (isEmpty(board, x - d[0] * j, y - d[1] * j)) opens++
        lineCount = count
        lineOpens = opens
    }

    /**
     * 判断(x,y)处落下color子后是否连成五子
     */
    private fun isFiveAt(board: Array<IntArray>, x: Int, y: Int, color: Int): Boolean {
        for (d in DIRS) {
            lineInfo(board, x, y, color, d)
            if (lineCount >= 5) return true
        }
        return false
    }

    /**
     * 棋型分：按连子数和开放端数取值
     */
    private fun shapeScore(count: Int, opens: Int): Long {
        if (count >= 5) return SCORE_FIVE
        return when (count) {
            4 -> when {
                opens >= 2 -> SCORE_LIVE_FOUR
                opens == 1 -> SCORE_RUSH_FOUR
                else -> 0
            }
            3 -> when {
                opens >= 2 -> SCORE_LIVE_THREE
                opens == 1 -> SCORE_SLEEP_THREE
                else -> 0
            }
            2 -> when {
                opens >= 2 -> SCORE_LIVE_TWO
                opens == 1 -> SCORE_SLEEP_TWO
                else -> 0
            }
            1 -> when {
                opens >= 2 -> SCORE_LIVE_ONE
                opens == 1 -> SCORE_SLEEP_ONE
                else -> 0
            }
            else -> 0
        }
    }

    private fun hasNeighbor(board: Array<IntArray>, x: Int, y: Int, radius: Int): Boolean {
        for (i in maxOf(0, x - radius)..minOf(width - 1, x + radius)) {
            for (j in maxOf(0, y - radius)..minOf(height - 1, y + radius)) {
                if (board[i][j] != EMPTY) return true
            }
        }
        return false
    }

    private fun inBoard(x: Int, y: Int) = x in 0 until width && y in 0 until height

    private fun isEmpty(board: Array<IntArray>, x: Int, y: Int) =
        inBoard(x, y) && board[x][y] == EMPTY

    private class Candidate(val x: Int, val y: Int, val score: Long)

    private companion object {
        // 棋盘整型编码：1=黑（人类），2=白（AI）
        const val EMPTY = 0
        const val HUMAN = 1
        const val AI = 2

        // 横、竖、两条对角线
        val DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(1, -1))

        // 棋型分值：连五 > 活四 > 冲四 > 活三 > 眠三 > 活二 > 眠二
        const val SCORE_FIVE = 100000000L
        const val SCORE_LIVE_FOUR = 10000000L
        const val SCORE_RUSH_FOUR = 1000000L
        const val SCORE_LIVE_THREE = 200000L
        const val SCORE_SLEEP_THREE = 20000L
        const val SCORE_LIVE_TWO = 5000L
        const val SCORE_SLEEP_TWO = 500L
        const val SCORE_LIVE_ONE = 100L
        const val SCORE_SLEEP_ONE = 10L
        const val WIN_SCORE = SCORE_FIVE * 10
    }

    // lineInfo 的输出，复用字段避免热路径上的对象分配
    private var lineCount = 0
    private var lineOpens = 0
}
