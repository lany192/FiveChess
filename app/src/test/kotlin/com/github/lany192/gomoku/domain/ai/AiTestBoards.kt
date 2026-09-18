package com.github.lany192.gomoku.domain.ai

/**
 * AI 测试共用局面夹具。
 *
 * 棋盘编码：BLACK=1 是人类，WHITE=2 是 AI；与 `Array<IntArray>` 的 [x][y] 约定一致。
 */
object AiTestBoards {

    fun empty(width: Int = 15, height: Int = 15): Array<IntArray> = Array(width) { IntArray(height) }

    /** stones 依次为 ((x, y), color) */
    fun board(vararg stones: Pair<Pair<Int, Int>, Int>): Array<IntArray> {
        val board = empty()
        for ((pos, color) in stones) board[pos.first][pos.second] = color
        return board
    }

    /** 白（AI）横向四子 (5,7)-(8,7)，两端 (4,7)/(9,7) 都是连五点 */
    fun aiFour(): Array<IntArray> = board(
        (5 to 7) to 2, (6 to 7) to 2, (7 to 7) to 2, (8 to 7) to 2,
    )

    /** 黑（对手）横向四子，两端 (4,7)/(9,7) 是必须封堵的连五点 */
    fun rivalFour(): Array<IntArray> = board(
        (5 to 7) to 1, (6 to 7) to 1, (7 to 7) to 1, (8 to 7) to 1,
    )

    /** 白活三 (5,7)-(7,7)，远端有黑一子 */
    fun aiLiveThree(): Array<IntArray> = board(
        (5 to 7) to 2, (6 to 7) to 2, (7 to 7) to 2, (10 to 10) to 1,
    )

    /** 约 12 子的中局（黑白交错，无即时杀） */
    fun midGame(): Array<IntArray> = board(
        (7 to 7) to 1, (8 to 8) to 2, (7 to 8) to 1, (6 to 8) to 2, (8 to 7) to 1, (9 to 7) to 2,
        (6 to 6) to 1, (5 to 5) to 2, (8 to 6) to 1, (9 to 5) to 2, (5 to 8) to 1, (4 to 9) to 2,
    )
}
