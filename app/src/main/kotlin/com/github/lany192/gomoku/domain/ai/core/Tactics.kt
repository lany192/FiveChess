package com.github.lany192.gomoku.domain.ai.core

import com.github.lany192.gomoku.domain.model.Point

/**
 * 战术底线：所有引擎共用的"必走点"判定。
 *
 * 弱引擎可以看漏（由档位的 alertPercent 决定是否调用），但一旦调用就必须走对，
 * 否则弱档会表现出"棋盘坏掉"的手感。
 */
object Tactics {

    /** 必走点：自己连五 > 堵对手连五 > 自己形成活四（对手堵不住，必胜）；没有返回 null */
    fun mandatory(board: Array<IntArray>, candidates: List<Candidate>, self: Int, scanner: BoardScanner): Point? {
        findFive(board, candidates, self, scanner)?.let { return it }
        findFive(board, candidates, Stone.opponent(self), scanner)?.let { return it }
        return findLiveFour(board, candidates, self, scanner)
    }

    /** 在候选点中寻找 color 落子即连五的位置 */
    fun findFive(board: Array<IntArray>, candidates: List<Candidate>, color: Int, scanner: BoardScanner): Point? {
        for (c in candidates) {
            if (board[c.x][c.y] != Stone.EMPTY) continue
            board[c.x][c.y] = color
            val five = scanner.isFiveAt(board, c.x, c.y, color)
            board[c.x][c.y] = Stone.EMPTY
            if (five) return Point(c.x, c.y)
        }
        return null
    }

    /** 能形成活四的点（两个连五点，对手堵不住） */
    fun findLiveFour(board: Array<IntArray>, candidates: List<Candidate>, color: Int, scanner: BoardScanner): Point? {
        for (c in candidates) {
            if (board[c.x][c.y] != Stone.EMPTY) continue
            board[c.x][c.y] = color
            var liveFour = false
            for (d in BoardScanner.DIRS) {
                scanner.lineInfo(board, c.x, c.y, color, d)
                if (scanner.count == 4 && scanner.opens >= 2) {
                    liveFour = true
                    break
                }
            }
            board[c.x][c.y] = Stone.EMPTY
            if (liveFour) return Point(c.x, c.y)
        }
        return null
    }

    /** (cx,cy) 附近能让 color 连五的空点——威胁搜索里既是攻击方的杀点也是防守方的堵点 */
    fun fivePointsNear(
        board: Array<IntArray>,
        cx: Int,
        cy: Int,
        color: Int,
        radius: Int,
        scanner: BoardScanner,
    ): List<Point> {
        val points = ArrayList<Point>(4)
        for (x in maxOf(0, cx - radius)..minOf(board.size - 1, cx + radius)) {
            for (y in maxOf(0, cy - radius)..minOf(board[0].size - 1, cy + radius)) {
                if (board[x][y] != Stone.EMPTY) continue
                board[x][y] = color
                val five = scanner.isFiveAt(board, x, y, color)
                board[x][y] = Stone.EMPTY
                if (five) points.add(Point(x, y))
            }
        }
        return points
    }
}
