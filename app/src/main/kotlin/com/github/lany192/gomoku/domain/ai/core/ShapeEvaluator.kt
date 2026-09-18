package com.github.lany192.gomoku.domain.ai.core

/**
 * 棋型评分评估：4 个方向 × 5 格滑窗，按窗口内连子数与开放端数计分。
 *
 * 这是经典 RobotAI 的评估函数等价搬运，窗口式扫描能识别空心棋型（如 11011）；
 * 对手棋型权重略高，让己方走子更偏防守。
 */
class ShapeEvaluator(private val width: Int, private val height: Int) : Evaluator {

    override fun evaluate(board: Array<IntArray>, self: Int): Long {
        val rival = Stone.opponent(self)
        var mine = 0L
        var theirs = 0L
        for (d in BoardScanner.DIRS) {
            val dx = d[0]
            val dy = d[1]
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val ex = x + 4 * dx
                    val ey = y + 4 * dy
                    if (!inBoard(ex, ey)) continue
                    var myCount = 0
                    var rivalCount = 0
                    for (i in 0 until 5) {
                        when (board[x + i * dx][y + i * dy]) {
                            self -> myCount++
                            rival -> rivalCount++
                        }
                    }
                    // 同时包含双方棋子或全空的窗口没有价值
                    if (myCount > 0 && rivalCount > 0) continue
                    if (myCount == 0 && rivalCount == 0) continue
                    var opens = 0
                    if (isEmpty(board, x - dx, y - dy)) opens++
                    if (isEmpty(board, ex + dx, ey + dy)) opens++
                    if (myCount > 0) {
                        mine += ShapeScores.score(myCount, opens)
                    } else {
                        theirs += ShapeScores.score(rivalCount, opens)
                    }
                }
            }
        }
        return mine - theirs - theirs / 10
    }

    private fun inBoard(x: Int, y: Int) = x in 0 until width && y in 0 until height

    private fun isEmpty(board: Array<IntArray>, x: Int, y: Int) =
        inBoard(x, y) && board[x][y] == Stone.EMPTY
}
