package com.github.lany192.fivechess.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 手写轻量 MVI 基类
 *
 * - Intent 通过无限容量 Channel 串行分发，保证用户操作有序（对齐旧 Handler 主线程队列语义）
 * - State 用 StateFlow 承载，Activity 整体幂等渲染快照
 * - Effect 用 SharedFlow(replay=0) 承载一次性事件（toast/弹窗/导航），订阅方需处于 STARTED
 */
abstract class MviViewModel<I : Any, S : Any, E : Any>(initialState: S) : ViewModel() {

    private val intents = Channel<I>(Channel.UNLIMITED)

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<E>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    val effects: SharedFlow<E> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            for (intent in intents) onIntent(intent)
        }
    }

    /** UI 唯一的事件入口，永不错过、不阻塞 */
    fun dispatch(intent: I) {
        intents.trySend(intent)
    }

    /** 子类唯一入口，串行执行；耗时操作自行 launch 到对应 Dispatcher */
    protected abstract fun onIntent(intent: I)

    protected fun updateState(transform: (S) -> S) {
        _state.update(transform)
    }

    protected suspend fun emitEffect(effect: E) {
        _effects.emit(effect)
    }
}
