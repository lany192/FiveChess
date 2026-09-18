package com.github.lany192.gomoku.domain.ai.threat

/** 威胁搜索模式：VCF=只算冲四连击、VCT=冲四+活三、KILL=先 VCF 再 VCT，无杀回退普通搜索 */
enum class ThreatMode { VCF, VCT, KILL }

/**
 * 威胁搜索参数：分支极窄（只有逼着），所以层数可以比同档普通搜索更深。
 *
 * 深度按"攻击方手数"计，防守方的应手不额外计深——被逼应的棋没有选择。
 */
data class ThreatOptions(
    val mode: ThreatMode,
    val depth: Int,
    val breadth: Int,
    val radius: Int,
    val timeBudgetMillis: Long,
    val nodeLimit: Int = 150_000,
)
