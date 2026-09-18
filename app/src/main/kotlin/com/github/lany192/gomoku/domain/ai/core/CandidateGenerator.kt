package com.github.lany192.gomoku.domain.ai.core

import java.util.Collections
import kotlin.math.abs

/** 候选点：score 越大越优先（量级与 [ShapeScores] 一致） */
class Candidate(val x: Int, val y: Int, val score: Long)

/**
 * 候选点生成：只考虑已有棋子附近的空位。
 *
 * 排序必须是**全序**（分数降序、x 升序、y 升序），否则不同 JVM 的排序实现会让
 * 同种子搜索结果不可复现。
 */
class CandidateGenerator(
    private val width: Int,
    private val height: Int,
    private val scanner: BoardScanner,
) {

    /** 邻域内有子且未被占用的点，按"进攻分 + 防守分 - 中心距离"降序取前 limit 个（返回可变列表，供搜索排序） */
    fun generate(board: Array<IntArray>, limit: Int, radius: Int, self: Int, rival: Int): MutableList<Candidate> {
        val list = ArrayList<Candidate>()
        val centerX = width / 2
        val centerY = height / 2
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (board[x][y] != Stone.EMPTY || !hasNeighbor(board, x, y, radius)) continue
                val score = pointScore(board, x, y, self) + pointScore(board, x, y, rival) -
                        (abs(x - centerX) + abs(y - centerY))
                list.add(Candidate(x, y, score))
            }
        }
        if (list.isEmpty()) {
            list.add(Candidate(width / 2, height / 2, 0L))
        }
        return rank(list, limit)
    }

    /** 假设在 (x,y) 落子后的局部棋型分（四方向之和），用于候选点排序 */
    fun pointScore(board: Array<IntArray>, x: Int, y: Int, color: Int): Long {
        var total = 0L
        for (d in BoardScanner.DIRS) {
            scanner.lineInfo(board, x, y, color, d)
            total += ShapeScores.score(scanner.count, scanner.opens)
        }
        return total
    }

    /** 逼着：落子即成五或成四（含空心四）的点，威胁搜索的攻击着法 */
    fun forcingMoves(board: Array<IntArray>, color: Int, radius: Int = 2, limit: Int = 12): MutableList<Candidate> {
        val list = ArrayList<Candidate>()
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (board[x][y] != Stone.EMPTY || !hasNeighbor(board, x, y, radius)) continue
                board[x][y] = color
                val forcing = scanner.isFiveAt(board, x, y, color) || scanner.makesFour(board, x, y, color)
                board[x][y] = Stone.EMPTY
                if (forcing) list.add(Candidate(x, y, pointScore(board, x, y, color)))
            }
        }
        return rank(list, limit)
    }

    /** 落子后能形成活三的点，VCT 在逼着之外的额外攻击着法 */
    fun liveThreeMoves(board: Array<IntArray>, color: Int, radius: Int = 2, limit: Int = 12): MutableList<Candidate> {
        val list = ArrayList<Candidate>()
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (board[x][y] != Stone.EMPTY || !hasNeighbor(board, x, y, radius)) continue
                board[x][y] = color
                var liveThree = false
                for (d in BoardScanner.DIRS) {
                    scanner.lineInfo(board, x, y, color, d)
                    if (scanner.count == 3 && scanner.opens >= 2) {
                        liveThree = true
                        break
                    }
                }
                board[x][y] = Stone.EMPTY
                if (liveThree) list.add(Candidate(x, y, pointScore(board, x, y, color)))
            }
        }
        return rank(list, limit)
    }

    fun hasNeighbor(board: Array<IntArray>, x: Int, y: Int, radius: Int): Boolean {
        for (i in maxOf(0, x - radius)..minOf(width - 1, x + radius)) {
            for (j in maxOf(0, y - radius)..minOf(height - 1, y + radius)) {
                if (board[i][j] != Stone.EMPTY) return true
            }
        }
        return false
    }

    /** 按全序（分数降序、x 升序、y 升序）排序并截断；合并多来源候选的引擎也复用它 */
    fun rank(list: MutableList<Candidate>, limit: Int): MutableList<Candidate> {
        Collections.sort(list) { a, b ->
            if (a.score != b.score) {
                b.score.compareTo(a.score)
            } else if (a.x != b.x) {
                a.x - b.x
            } else {
                a.y - b.y
            }
        }
        return if (list.size > limit) list.subList(0, limit) else list
    }
}
