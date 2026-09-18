package com.github.lany192.gomoku.domain.ai

import com.github.lany192.gomoku.domain.ai.threat.ThreatEngine
import com.github.lany192.gomoku.domain.model.Point
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ThreatEngineTest {

    private val presets = listOf(AiAlgorithm.VCF, AiAlgorithm.VCT, AiAlgorithm.KILL_SEARCH)

    /** clock 固定为 0：关掉时间预算，保证结果可复现 */
    private fun engine(algorithm: AiAlgorithm, level: Difficulty = Difficulty.MEDIUM): ThreatEngine =
        AiEngineFactory.create(algorithm, level, 15, 15, Random(9), clock = { 0L }) as ThreatEngine

    /**
     * 两手 VCF：白 (5,7) 冲四（唯一连五点 (4,7)）并同时做出竖向活三，
     * 黑被迫堵 (4,7) 后白 (5,4)/(5,8) 成活四，两手锁定胜局。
     */
    private fun twoMoveVcf(): Array<IntArray> = AiTestBoards.board(
        (6 to 7) to 2, (7 to 7) to 2, (8 to 7) to 2,
        (5 to 5) to 2, (5 to 6) to 2,
        (9 to 7) to 1,
    )

    @Test
    fun `三个预设都能算出两手VCF必杀`() {
        val board = twoMoveVcf()
        for (algorithm in presets) {
            val point = engine(algorithm).findKill(board)
            assertTrue("$algorithm 实际落点 ($point)，应走冲四点 (5,7)", point == Point(5, 7))
        }
    }

    /**
     * 白 (5,7)-(7,7) 与 (8,4)-(8,6) 看似有双击：白 (4,7) 成活四（连五点 (3,7)、(8,7)），
     * 白 (8,7) 成双四（连五点 (4,7)、(8,8)）；但黑 (1,2)-(3,2)、(5,2) 预留了连五点 (4,2)，
     * 黑总能抢在白的活四兑现前连五反杀——杀棋搜索必须判定为无杀，而不是报"必胜"。
     * 去掉黑这四子即 [controlKill]，白 (4,7) 活四真杀，说明本盘面确实能区分对错。
     */
    private fun counterKill(): Array<IntArray> = AiTestBoards.board(
        (5 to 7) to 2, (6 to 7) to 2, (7 to 7) to 2,
        (8 to 4) to 2, (8 to 5) to 2, (8 to 6) to 2,
        (9 to 7) to 1, (8 to 3) to 1,
        (1 to 2) to 1, (2 to 2) to 1, (3 to 2) to 1, (5 to 2) to 1,
    )

    /** [counterKill] 去掉黑的反杀四子：白 (4,7) 活四必杀，用作反杀判定的对照组 */
    private fun controlKill(): Array<IntArray> = AiTestBoards.board(
        (5 to 7) to 2, (6 to 7) to 2, (7 to 7) to 2,
        (8 to 4) to 2, (8 to 5) to 2, (8 to 6) to 2,
        (9 to 7) to 1, (8 to 3) to 1,
    )

    @Test
    fun `对手有反杀时判定为无杀`() {
        val board = counterKill()
        for (algorithm in presets) {
            val point = engine(algorithm).findKill(board)
            assertTrue("$algorithm 误报必胜：($point)", point == null)
        }
    }

    @Test
    fun `无反杀的同型盘面必须算出活四必杀`() {
        val board = controlKill()
        for (algorithm in presets) {
            val point = engine(algorithm).findKill(board)
            assertTrue("$algorithm 漏报活四必杀：($point)", point != null)
        }
    }

    @Test
    fun `无杀时兜底返回合法落点`() {
        val board = AiTestBoards.midGame()
        for (algorithm in presets) {
            val point = engine(algorithm).getPosition(board)
            assertTrue("$algorithm 落点越界 ($point)", point.x in 0 until 15 && point.y in 0 until 15)
            assertTrue("$algorithm 落在已有棋子上 ($point)", board[point.x][point.y] == 0)
        }
    }

    @Test
    fun `同种子同局面结果确定`() {
        val board = AiTestBoards.midGame()
        for (algorithm in presets) {
            val first = engine(algorithm).getPosition(board)
            val second = engine(algorithm).getPosition(board)
            assertTrue("$algorithm 结果不确定 ($first vs $second)", first == second)
        }
    }

    @Test
    fun `不修改传入棋盘`() {
        val board = counterKill()
        val before = board.map { it.copyOf() }.toTypedArray()
        for (algorithm in presets) {
            engine(algorithm).getPosition(board)
            for (x in 0 until 15) {
                assertArrayEquals("$algorithm 改动了棋盘第 $x 列", before[x], board[x])
            }
        }
    }

    @Test
    fun `大师难度耗时上界`() {
        val board = AiTestBoards.midGame()
        for (algorithm in presets) {
            val start = System.nanoTime()
            val point = AiEngineFactory.create(
                algorithm, Difficulty.MASTER, 15, 15, Random(4),
            ).getPosition(board)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertTrue("$algorithm 落点不合法 ($point)", board[point.x][point.y] == 0)
            assertTrue("$algorithm 耗时 ${elapsedMs}ms 超出预期", elapsedMs < 4_000)
        }
    }
}
