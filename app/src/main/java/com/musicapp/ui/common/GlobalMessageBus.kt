package com.musicapp.ui.common

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全局消息总线：任意 ViewModel/Service/Repository 都能 send 一条消息，
 * 由 MainActivity 的全局 SnackbarHost 统一消费展示。
 *
 * 用 SharedFlow 而不是 Channel：保留最后一帧即可，多消费者不会重复弹出。
 * 额外 buffer=8 防止冷启动时主入口还没订阅就被丢弃。
 */
object GlobalMessageBus {

    private val _messages = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun send(text: String) {
        if (text.isBlank()) return
        // tryEmit 在我们的 buffer/replay 配置下永远返回 true
        _messages.tryEmit(text)
    }
}
