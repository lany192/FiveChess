package com.github.lany192.gomoku.domain.ai

import com.github.lany192.gomoku.domain.model.Move
import com.github.lany192.gomoku.domain.model.Point
import com.github.lany192.gomoku.domain.model.Side

/** 终局结果；winner 为 null 表示和棋 */
data class GameOutcome(val winner: Side?, val moves: List<Move>)

/**
 * 五子棋 AI 引擎统一入口。
 *
 * 棋盘约定与旧 RobotAI 一致：`Array<IntArray>` 按 [x][y] 索引，0=空 1=黑（人）2=白（AI），
 * AI 永远执白；传入棋盘属于调用方，实现必须拷贝后再搜索。
 *
 * 线程约定：同一实例不可并发调用；[onGameReset]/[onGameOver] 由 ViewModel 在搜索空闲时调用，
 * 且必须在后台线程调用（学习类会在此更新并持久化权重）。
 */
interface GomokuAI {

    /** 档位：思考途中改档不得影响本次搜索（实现须在 getPosition 入口快照） */
    var level: Difficulty

    /** 获取最佳落子位置 */
    fun getPosition(board: Array<IntArray>): Point

    /** 棋局被外部重置或回退（新开/悔棋）时回调；学习类据此丢弃未闭合的轨迹 */
    fun onGameReset() {}

    /** 一局终了时回调；学习类在此做终局更新 */
    fun onGameOver(outcome: GameOutcome) {}
}
