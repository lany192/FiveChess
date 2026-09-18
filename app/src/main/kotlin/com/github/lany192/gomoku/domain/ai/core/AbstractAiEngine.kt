package com.github.lany192.gomoku.domain.ai.core

import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.AiTunings
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.ai.EngineTuning
import com.github.lany192.gomoku.domain.ai.GomokuAI
import com.github.lany192.gomoku.domain.model.Point
import kotlin.random.Random

/**
 * 新引擎的公共骨架：拷贝棋盘、快照档位、必走点预判、弱档失误、兜底合法点。
 *
 * 子类只实现 [algorithm] 与 [decide]，不接触调用方棋盘。
 */
abstract class AbstractAiEngine(
    protected val width: Int,
    protected val height: Int,
    override var level: Difficulty,
    protected val random: Random,
    protected val clock: () -> Long = System::nanoTime,
) : GomokuAI {

    /** 人机对战中 AI 固定执白 */
    protected val self = Stone.WHITE
    protected val rival = Stone.BLACK

    protected val scanner = BoardScanner(width, height)
    protected val generator = CandidateGenerator(width, height, scanner)

    final override fun getPosition(board: Array<IntArray>): Point {
        val local = BoardCopies.copy(board)
        // 快照档位：思考途中改档不应让本次搜索读到新的候选宽度/预算
        val tuning = AiTunings.of(algorithm(), level)
        val candidates = generator.generate(local, tuning.breadth, tuning.radius, self, rival)

        var forced: Point? = null
        if (random.nextInt(100) < tuning.alertPercent) {
            forced = Tactics.mandatory(local, candidates, self, scanner)
        }
        val chosen = forced ?: weakenIfNeeded(decide(local, tuning, candidates), candidates, tuning)
        onMoveDecided(local, tuning, chosen)
        return legalize(local, chosen, candidates)
    }

    protected abstract fun algorithm(): AiAlgorithm

    /**
     * 选定着法（入参 board 是副本，可自由落子/撤销）。
     *
     * [candidates] 已按棋型分降序排好，子类可直接使用。
     */
    protected abstract fun decide(board: Array<IntArray>, tuning: EngineTuning, candidates: List<Candidate>): Point

    /** 选定着法后的回调（学习类在此记录轨迹）；此时 board 尚未落下该子 */
    protected open fun onMoveDecided(board: Array<IntArray>, tuning: EngineTuning, move: Point) {}

    /** 弱档按概率换成候选里靠前的其他点，制造看漏/失误的手感 */
    private fun weakenIfNeeded(chosen: Point, candidates: List<Candidate>, tuning: EngineTuning): Point {
        if (tuning.noisePercent <= 0 || random.nextInt(100) >= tuning.noisePercent) return chosen
        val pool = candidates.filter { it.x != chosen.x || it.y != chosen.y }
        if (pool.isEmpty()) return chosen
        val candidate = pool[random.nextInt(minOf(pool.size, WEAK_SPAN))]
        return Point(candidate.x, candidate.y)
    }

    /** 越界/已占/无候选时退化为最靠前的合法候选 */
    private fun legalize(board: Array<IntArray>, chosen: Point, candidates: List<Candidate>): Point {
        if (scanner.inBoard(chosen.x, chosen.y) && board[chosen.x][chosen.y] == Stone.EMPTY) return chosen
        candidates.firstOrNull { board[it.x][it.y] == Stone.EMPTY }?.let { return Point(it.x, it.y) }
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (board[x][y] == Stone.EMPTY) return Point(x, y)
            }
        }
        return Point(width / 2, height / 2)
    }

    private companion object {
        /** 弱档失误时从候选前几名里挑 */
        const val WEAK_SPAN = 5
    }
}
