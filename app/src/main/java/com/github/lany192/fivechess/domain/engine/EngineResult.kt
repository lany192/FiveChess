package com.github.lany192.fivechess.domain.engine

import com.github.lany192.fivechess.domain.model.GameEvent
import com.github.lany192.fivechess.domain.model.GameState

/**
 * 一次引擎操作的结果：操作后的棋局快照 + 产生的事件列表
 */
data class EngineResult(
    val state: GameState,
    val events: List<GameEvent>,
)
