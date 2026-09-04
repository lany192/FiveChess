package com.github.lany192.fivechess.domain.ai

/**
 * AI 难度：depth=搜索层数，breadth=候选点数量上限，radius=邻域半径
 */
enum class Difficulty(val depth: Int, val breadth: Int, val radius: Int) {
    EASY(0, 6, 1),
    MEDIUM(2, 12, 2),
    HARD(4, 16, 2),
}
