package com.github.lany192.gomoku.domain.ai.misc

import com.github.lany192.gomoku.domain.ai.AiAlgorithm
import com.github.lany192.gomoku.domain.ai.AiTunings
import com.github.lany192.gomoku.domain.ai.Difficulty
import com.github.lany192.gomoku.domain.ai.EngineTuning
import com.github.lany192.gomoku.domain.ai.core.AbstractAiEngine
import com.github.lany192.gomoku.domain.ai.core.BoardScanner
import com.github.lany192.gomoku.domain.ai.core.Candidate
import com.github.lany192.gomoku.domain.ai.core.Stone
import com.github.lany192.gomoku.domain.ai.core.Tactics
import com.github.lany192.gomoku.domain.model.Point
import kotlin.random.Random

/**
 * 专家系统：有序产生式规则，首条产出非空者胜出。
 *
 * 规则链从"必胜/必堵"到"一层前瞻"逐级兜底，是弱引擎里唯一带战术层级的实现。
 * [lastFiredRuleId] 暴露最近一次命中的规则，供测试与调试断言。
 */
class ExpertSystemEngine(
    private val algorithm: AiAlgorithm,
    width: Int,
    height: Int,
    level: Difficulty,
    random: Random,
    clock: () -> Long = System::nanoTime,
) : AbstractAiEngine(width, height, level, random, clock) {

    /** 最近一次 decide 命中的规则 id；无候选时为 null */
    var lastFiredRuleId: String? = null
        private set

    override fun algorithm(): AiAlgorithm = algorithm

    override fun decide(board: Array<IntArray>, tuning: EngineTuning, candidates: List<Candidate>): Point {
        val options = AiTunings.misc(algorithm, level)
        val hit = select(board, candidates, options.lookahead, options.radius)
        lastFiredRuleId = hit?.ruleId
        if (hit != null) return hit.point
        val fallback = candidates.firstOrNull()
        return if (fallback != null) Point(fallback.x, fallback.y) else Point(width / 2, height / 2)
    }

    /** 规则链本体：入参 board 是副本，方法内自行恢复落子 */
    internal fun select(board: Array<IntArray>, candidates: List<Candidate>, lookahead: Int, radius: Int): RuleHit? {
        Tactics.findFive(board, candidates, self, scanner)?.let {
            return RuleHit(RULE_SELF_FIVE, it)
        }
        Tactics.findFive(board, candidates, rival, scanner)?.let {
            return RuleHit(RULE_BLOCK_FIVE, it)
        }
        Tactics.findLiveFour(board, candidates, self, scanner)?.let {
            return RuleHit(RULE_SELF_LIVE_FOUR, it)
        }
        if (liveThreePoint(board, candidates, rival) != null) {
            // 对手能出活三时优先看自己的双重威胁（反杀），没有才回到堵点
            doubleThreePoint(board, candidates, self)?.let {
                return RuleHit(RULE_COUNTER_DOUBLE_THREE, it)
            }
        }
        doubleThreePoint(board, candidates, self)?.let {
            return RuleHit(RULE_MAKE_DOUBLE_THREE, it)
        }
        liveThreePoint(board, candidates, rival)?.let {
            return RuleHit(RULE_BLOCK_LIVE_THREE, it)
        }
        onePlyLookahead(board, candidates, lookahead, radius)?.let {
            return RuleHit(RULE_ONE_PLY_LOOKAHEAD, it)
        }
        return candidates.firstOrNull()?.let { RuleHit(RULE_GREEDY_CENTER, Point(it.x, it.y)) }
    }

    /** color 落子即形成活三的点（对手要堵的关键点） */
    private fun liveThreePoint(board: Array<IntArray>, candidates: List<Candidate>, color: Int): Point? {
        for (c in candidates) {
            if (board[c.x][c.y] != Stone.EMPTY) continue
            board[c.x][c.y] = color
            var liveThree = false
            for (d in BoardScanner.DIRS) {
                scanner.lineInfo(board, c.x, c.y, color, d)
                if (scanner.count == 3 && scanner.opens >= 2) {
                    liveThree = true
                    break
                }
            }
            board[c.x][c.y] = Stone.EMPTY
            if (liveThree) return Point(c.x, c.y)
        }
        return null
    }

    /** color 落子同时形成两个活三的点（双三，对手单点堵不住） */
    private fun doubleThreePoint(board: Array<IntArray>, candidates: List<Candidate>, color: Int): Point? {
        for (c in candidates) {
            if (board[c.x][c.y] != Stone.EMPTY) continue
            board[c.x][c.y] = color
            var threes = 0
            for (d in BoardScanner.DIRS) {
                scanner.lineInfo(board, c.x, c.y, color, d)
                if (scanner.count == 3 && scanner.opens >= 2) threes++
            }
            board[c.x][c.y] = Stone.EMPTY
            if (threes >= 2) return Point(c.x, c.y)
        }
        return null
    }

    /** 一层前瞻：假设我在 c 落子，对手贪心最优应答后我的收益（攻击 − 1.2 × 威胁） */
    private fun onePlyLookahead(board: Array<IntArray>, candidates: List<Candidate>, lookahead: Int, radius: Int): Point? {
        var best: Point? = null
        var bestScore = Long.MIN_VALUE
        var examined = 0
        for (c in candidates) {
            if (board[c.x][c.y] != Stone.EMPTY) continue
            if (examined >= lookahead) break
            examined++
            val attack = generator.pointScore(board, c.x, c.y, self)
            board[c.x][c.y] = self
            val replies = generator.generate(board, LOOKAHEAD_REPLIES, radius, rival, self)
            var counter = 0L
            for (r in replies) {
                if (board[r.x][r.y] != Stone.EMPTY) continue
                val threat = generator.pointScore(board, r.x, r.y, rival)
                if (threat > counter) counter = threat
            }
            board[c.x][c.y] = Stone.EMPTY
            val score = attack - counter * COUNTER_WEIGHT / 10
            if (score > bestScore) {
                bestScore = score
                best = Point(c.x, c.y)
            }
        }
        return best
    }

    internal class RuleHit(val ruleId: String, val point: Point)

    companion object {
        const val RULE_SELF_FIVE = "SELF_FIVE"
        const val RULE_BLOCK_FIVE = "BLOCK_FIVE"
        const val RULE_SELF_LIVE_FOUR = "SELF_LIVE_FOUR"
        const val RULE_COUNTER_DOUBLE_THREE = "COUNTER_DOUBLE_THREE"
        const val RULE_MAKE_DOUBLE_THREE = "MAKE_DOUBLE_THREE"
        const val RULE_BLOCK_LIVE_THREE = "BLOCK_LIVE_THREE"
        const val RULE_ONE_PLY_LOOKAHEAD = "ONE_PLY_LOOKAHEAD"
        const val RULE_GREEDY_CENTER = "GREEDY_CENTER"

        /** 前瞻时只看对手的前几个应答（够用且省时） */
        private const val LOOKAHEAD_REPLIES = 4

        /** 对手威胁按 1.2 倍计入（"宁让一子不让一先"的经验权重） */
        private const val COUNTER_WEIGHT = 12L
    }
}
