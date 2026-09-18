package com.github.lany192.gomoku.domain.ai.core

/** 棋盘深拷贝：引擎的搜索会大量落子/撤销，必须先拷走调用方的棋盘 */
object BoardCopies {
    fun copy(board: Array<IntArray>): Array<IntArray> = Array(board.size) { board[it].copyOf() }
}
