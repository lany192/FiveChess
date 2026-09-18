package com.github.lany192.gomoku.domain.ai

/**
 * AI 难度：depth=搜索层数（0 表示不搜索，只按启发式评分选点），breadth=候选点数量上限，
 * radius=邻域半径；noise=不搜索档位的选点随机度（概率放弃当前候选取下一个，越大越常失误），
 * alertPercent=留意战术（连五取胜/封堵）的概率，剩余概率会看漏。
 */
enum class Difficulty(
    val depth: Int,
    val breadth: Int,
    val radius: Int,
    val noise: Int = 0,
    val alertPercent: Int = 100,
) {
    NOVICE(0, 8, 1, 85, 30),
    EASY(0, 8, 1, 70, 60),
    MEDIUM(2, 12, 2),
    HARD(4, 16, 2),
    MASTER(6, 12, 2),
}
