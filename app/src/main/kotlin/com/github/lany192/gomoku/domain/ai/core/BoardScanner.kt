package com.github.lany192.gomoku.domain.ai.core

/**
 * 棋盘扫描：连子数、开放端、成五/成四判定。
 *
 * [count]/[opens] 是 [lineInfo] 的输出，复用实例字段避免热路径分配；
 * 因此实例**非线程安全**，每个引擎实例持有自己的 scanner，不要做成共享单例。
 */
class BoardScanner(private val width: Int, private val height: Int) {

    /** 最近一次 [lineInfo] 的连子数（该点视为已落下 color 子） */
    var count = 0
        private set

    /** 最近一次 [lineInfo] 的两端开放数（0..2） */
    var opens = 0
        private set

    /** 统计 (x,y) 处 color 子在方向 d 上的连子数与两端开放数 */
    fun lineInfo(board: Array<IntArray>, x: Int, y: Int, color: Int, d: IntArray) {
        var stones = 1
        var open = 0
        var i = 1
        while (inBoard(x + d[0] * i, y + d[1] * i) && board[x + d[0] * i][y + d[1] * i] == color) {
            stones++
            i++
        }
        if (isEmpty(board, x + d[0] * i, y + d[1] * i)) open++
        var j = 1
        while (inBoard(x - d[0] * j, y - d[1] * j) && board[x - d[0] * j][y - d[1] * j] == color) {
            stones++
            j++
        }
        if (isEmpty(board, x - d[0] * j, y - d[1] * j)) open++
        count = stones
        opens = open
    }

    /** (x,y) 处落下 color 子后是否连成五子 */
    fun isFiveAt(board: Array<IntArray>, x: Int, y: Int, color: Int): Boolean {
        for (d in DIRS) {
            lineInfo(board, x, y, color, d)
            if (count >= 5) return true
        }
        return false
    }

    /**
     * (x,y) 处落下 color 子后是否形成"四"——即存在某个空点，落子即连五。
     * 用 5 格窗口判定，能识别空心四（如 XX_XX）而不只是连续四。
     */
    fun makesFour(board: Array<IntArray>, x: Int, y: Int, color: Int): Boolean {
        for (d in DIRS) {
            if (makesFourInDir(board, x, y, color, d)) return true
        }
        return false
    }

    private fun makesFourInDir(board: Array<IntArray>, x: Int, y: Int, color: Int, d: IntArray): Boolean {
        for (offset in -4..0) {
            var stones = 0
            var empty = 0
            for (i in 0 until 5) {
                val px = x + d[0] * (offset + i)
                val py = y + d[1] * (offset + i)
                if (!inBoard(px, py)) {
                    empty = -1
                    break
                }
                when (board[px][py]) {
                    color -> stones++
                    Stone.EMPTY -> empty++
                    else -> {
                        empty = -1
                        break
                    }
                }
            }
            if (stones == 4 && empty == 1) return true
        }
        return false
    }

    fun inBoard(x: Int, y: Int) = x in 0 until width && y in 0 until height

    fun isEmpty(board: Array<IntArray>, x: Int, y: Int) = inBoard(x, y) && board[x][y] == Stone.EMPTY

    companion object {
        /** 横、竖、两条对角线 */
        val DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(1, -1))
    }
}
