package com.github.lany192.gomoku.domain.ai.sim

import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.AiTunings
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.ai.EngineTuning
import com.github.lany192.gomoku.domain.ai.core.AbstractAiEngine
import com.github.lany192.gomoku.domain.ai.core.Candidate
import com.github.lany192.gomoku.domain.ai.core.Stone
import com.github.lany192.gomoku.domain.model.Point
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** MCTS 树节点：move 为 x * height + y（根为 -1），wins 记"走出该着法的一方"的胜场 */
private class McNode(val move: Int, val toMove: Int) {
    var visits = 0
    var wins = 0.0
    var raveVisits = 0
    var raveWins = 0.0
    var terminal = false
    val untried = ArrayList<Int>()
    val children = ArrayList<McNode>()
}

/**
 * 蒙特卡洛家族：MCTS（平铺模拟）/ UCT（启发式 rollout）/ RAVE（AMAF 加速）。
 *
 * 胜率口径统一为"造出这个节点的着法方"：每个子节点记的 wins 是**走出该子着法的一方**
 * （即父节点的 toMove）的胜场，父节点据此挑子；平局按 0.5 计。
 */
class MonteCarloEngine(
    private val algorithm: AiAlgorithm,
    width: Int,
    height: Int,
    level: Difficulty,
    random: Random,
    clock: () -> Long = System::nanoTime,
) : AbstractAiEngine(width, height, level, random, clock) {

    override fun algorithm(): AiAlgorithm = algorithm

    override fun decide(board: Array<IntArray>, tuning: EngineTuning, candidates: List<Candidate>): Point {
        val options = AiTunings.simulation(algorithm, level)
        val rootMoves = ArrayList<Int>(options.rootBreadth)
        for (c in candidates) {
            if (rootMoves.size >= options.rootBreadth) break
            rootMoves.add(c.x * height + c.y)
        }
        if (rootMoves.isEmpty()) return Point(width / 2, height / 2)
        return McSession(board, options, rootMoves).run()
    }

    private inner class McSession(
        private val board: Array<IntArray>,
        private val options: MonteCarloOptions,
        private val rootMoves: List<Int>,
    ) {
        private val startNanos = clock()
        private val root = McNode(-1, self)
        private val path = ArrayList<McNode>(MAX_TREE_DEPTH)
        private val cells = IntArray(width * height + MAX_CELLS_MARGIN)

        /** 本次模拟中双方实际落过的点（树内 + rollout），RAVE 的 AMAF 依据 */
        private val amafSeen = Array(3) { BooleanArray(width * height) }
        private var cellCount = 0

        init {
            root.untried.addAll(rootMoves)
        }

        fun run(): Point {
            for (sim in 0 until options.simulations) {
                if (sim > 0 && sim % ABORT_CHECK_INTERVAL == 0 && aborted()) break
                simulate()
            }
            var best = root.children.firstOrNull() ?: return Point(rootMoves[0] / height, rootMoves[0] % height)
            for (c in root.children) {
                if (c.visits > best.visits) best = c
            }
            return Point(best.move / height, best.move % height)
        }

        private fun simulate() {
            path.clear()
            var winner = Stone.EMPTY
            var rolloutNeeded = true
            var node = root
            while (true) {
                if (node.terminal) {
                    winner = Stone.opponent(node.toMove)
                    rolloutNeeded = false
                    break
                }
                if (node.untried.isNotEmpty()) {
                    node = expand(node)
                    if (node.terminal) {
                        winner = Stone.opponent(node.toMove)
                        rolloutNeeded = false
                    }
                    break
                }
                if (node.children.isEmpty()) {
                    rolloutNeeded = false // 无子可走：平局
                    break
                }
                node = bestChild(node)
                descend(node)
            }
            if (rolloutNeeded) winner = rollout(node.toMove)
            backprop(winner)
            undo()
        }

        /** 展开一个未试着法并落下，返回新节点 */
        private fun expand(parent: McNode): McNode {
            val move = parent.untried.removeAt(random.nextInt(parent.untried.size))
            val x = move / height
            val y = move % height
            board[x][y] = parent.toMove
            cells[cellCount++] = move
            amafSeen[parent.toMove][move] = true
            val child = McNode(move, Stone.opponent(parent.toMove))
            child.terminal = scanner.isFiveAt(board, x, y, parent.toMove)
            parent.children.add(child)
            path.add(child)
            return child
        }

        /** 沿已选子节点下走一步 */
        private fun descend(node: McNode) {
            val mover = Stone.opponent(node.toMove)
            val x = node.move / height
            val y = node.move % height
            board[x][y] = mover
            cells[cellCount++] = node.move
            amafSeen[mover][node.move] = true
            path.add(node)
        }

        private fun bestChild(node: McNode): McNode {
            var best = node.children[0]
            var bestScore = Double.NEGATIVE_INFINITY
            val logVisits = ln(node.visits.toDouble().coerceAtLeast(1.0))
            for (c in node.children) {
                val exploit = if (c.visits > 0) c.wins / c.visits else 0.0
                var score = exploit
                if (options.policy == McPolicy.RAVE) {
                    val rave = if (c.raveVisits > 0) c.raveWins / c.raveVisits else exploit
                    val beta = sqrt(options.raveK / (3.0 * c.visits + options.raveK))
                    score = (1 - beta) * exploit + beta * rave
                }
                score += options.explorationC * sqrt(logVisits / (1 + c.visits))
                if (score > bestScore) {
                    bestScore = score
                    best = c
                }
            }
            return best
        }

        /** 从 [firstToMove] 起随机对弈到终局或深度上限；返回胜方（0=平局） */
        private fun rollout(firstToMove: Int): Int {
            var toMove = firstToMove
            var steps = 0
            while (steps < options.maxRolloutDepth) {
                val move = pickRolloutMove(toMove, steps)
                if (move < 0) return Stone.EMPTY
                val x = move / height
                val y = move % height
                board[x][y] = toMove
                cells[cellCount++] = move
                amafSeen[toMove][move] = true
                if (scanner.isFiveAt(board, x, y, toMove)) return toMove
                toMove = Stone.opponent(toMove)
                steps++
            }
            return Stone.EMPTY
        }

        private fun pickRolloutMove(color: Int, step: Int): Int {
            if (options.rollout == RolloutPolicy.HEURISTIC && step < options.heuristicSteps) {
                val greedy = heuristicMove(color)
                if (greedy >= 0) return greedy
            }
            return randomMove()
        }

        /** 静态分（进攻 + 防守）最高的若干点里随机取一：成五/堵五天然被 1e8 量级顶到最前 */
        private fun heuristicMove(color: Int): Int {
            val rival = Stone.opponent(color)
            val top = IntArray(options.rolloutBreadth)
            val topScores = LongArray(options.rolloutBreadth)
            var size = 0
            for (x in 0 until width) {
                for (y in 0 until height) {
                    if (board[x][y] != Stone.EMPTY || !generator.hasNeighbor(board, x, y, 1)) continue
                    val score = generator.pointScore(board, x, y, color) + generator.pointScore(board, x, y, rival)
                    if (size < options.rolloutBreadth) {
                        top[size] = x * height + y
                        topScores[size] = score
                        size++
                    } else {
                        var minIndex = 0
                        for (i in 1 until size) if (topScores[i] < topScores[minIndex]) minIndex = i
                        if (score > topScores[minIndex]) {
                            top[minIndex] = x * height + y
                            topScores[minIndex] = score
                        }
                    }
                }
            }
            if (size == 0) return -1
            return top[random.nextInt(size)]
        }

        /** 在棋子包围盒内均匀撒点，命中"空且挨着棋子"即接受；撒不中再全盘扫 */
        private fun randomMove(): Int {
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1
            for (x in 0 until width) {
                for (y in 0 until height) {
                    if (board[x][y] == Stone.EMPTY) continue
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
            if (maxX < 0) return width / 2 * height + height / 2
            val x0 = maxOf(0, minX - 1)
            val x1 = minOf(width - 1, maxX + 1)
            val y0 = maxOf(0, minY - 1)
            val y1 = minOf(height - 1, maxY + 1)
            repeat(SAMPLE_TRIES) {
                val x = x0 + random.nextInt(x1 - x0 + 1)
                val y = y0 + random.nextInt(y1 - y0 + 1)
                if (board[x][y] == Stone.EMPTY && generator.hasNeighbor(board, x, y, 1)) return x * height + y
            }
            for (x in 0 until width) {
                for (y in 0 until height) {
                    if (board[x][y] == Stone.EMPTY && generator.hasNeighbor(board, x, y, 1)) return x * height + y
                }
            }
            return -1
        }

        private fun backprop(winner: Int) {
            root.visits++
            for (n in path) {
                n.visits++
                val mover = Stone.opponent(n.toMove)
                if (winner == Stone.EMPTY) {
                    n.wins += DRAW_SCORE
                } else if (winner == mover) {
                    n.wins += 1.0
                }
            }
            if (options.policy != McPolicy.RAVE) return
            raveUpdate(root, winner)
            for (n in path) raveUpdate(n, winner)
        }

        /** AMAF：路径节点的子着法若在本次模拟中被同一方走过，补记一次虚拟胜负 */
        private fun raveUpdate(node: McNode, winner: Int) {
            if (node.children.isEmpty()) return
            val mover = node.toMove
            val seen = amafSeen[mover]
            for (c in node.children) {
                if (!seen[c.move]) continue
                c.raveVisits++
                if (winner == Stone.EMPTY) {
                    c.raveWins += DRAW_SCORE
                } else if (winner == mover) {
                    c.raveWins += 1.0
                }
            }
        }

        private fun undo() {
            for (i in 0 until cellCount) {
                val move = cells[i]
                board[move / height][move % height] = Stone.EMPTY
            }
            cellCount = 0
            java.util.Arrays.fill(amafSeen[1], false)
            java.util.Arrays.fill(amafSeen[2], false)
        }

        private fun aborted(): Boolean = options.timeBudgetMillis > 0 &&
                (clock() - startNanos) / 1_000_000 >= options.timeBudgetMillis
    }

    private companion object {
        const val ABORT_CHECK_INTERVAL = 32
        const val SAMPLE_TRIES = 12
        const val MAX_TREE_DEPTH = 64

        /** cells 缓冲的余量：树内路径最多占满棋盘，rollout 再加深几十手 */
        const val MAX_CELLS_MARGIN = 64

        /** 平局在胜率里的折扣 */
        const val DRAW_SCORE = 0.5
    }
}
