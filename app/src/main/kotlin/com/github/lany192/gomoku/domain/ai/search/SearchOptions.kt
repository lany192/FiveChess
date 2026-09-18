package com.github.lany192.gomoku.domain.ai.search

/** 剪枝方式：NONE=纯极小极大（教学用，慢而弱）、ALPHA_BETA、PVS=主变例搜索 */
enum class Pruning { NONE, ALPHA_BETA, PVS }

/**
 * 搜索参数：7 个搜索预设的差异全部落在这里。
 *
 * 结构参数（裁剪方式/TT/杀手）与档位无关，档位只影响深度与时间预算。
 */
data class SearchOptions(
    val maxDepth: Int,
    val breadth: Int,
    val radius: Int,
    val pruning: Pruning,
    /** 逐层加深，只返回已完成深度的着法 */
    val iterativeDeepening: Boolean = false,
    /** 每手时间预算，0 = 不限时（测试确定性用） */
    val timeBudgetMillis: Long = 0L,
    /** 迭代加深的渴望窗口 */
    val aspiration: Boolean = false,
    val transposition: Boolean = false,
    val tableBits: Int = 16,
    /** 杀手着法槽位数，0 = 关闭 */
    val killerSlots: Int = 0,
    val historyHeuristic: Boolean = false,
    /** >0 时用零窗口 MTD(f) 迭代收敛 */
    val mtdPasses: Int = 0,
    /** 节点上限（保险丝，防止极端局面拖满预算） */
    val nodeLimit: Int = 200_000,
)
