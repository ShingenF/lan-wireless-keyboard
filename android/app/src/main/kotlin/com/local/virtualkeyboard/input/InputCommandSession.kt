package com.local.virtualkeyboard.input

import com.local.virtualkeyboard.protocol.OutgoingCommand
import com.local.virtualkeyboard.protocol.RemoteKey

class InputCommandSession(
    private val emit: (OutgoingCommand, ((Boolean) -> Unit)?) -> Boolean,
) {
    private val reducer = ImeCommandReducer { emit(it.preferPhysicalKeys(), null) }

    var isDeferredMode: Boolean = false
        private set

    fun setDeferredMode(enabled: Boolean) {
        if (enabled == isDeferredMode) return
        reducer.reset()
        isDeferredMode = enabled
    }

    fun onCompositionChanged(text: String) {
        if (!isDeferredMode) reducer.onCompositionChanged(text)
    }

    fun onCommit(text: String) {
        if (!isDeferredMode) reducer.onCommit(text)
    }

    fun onCompositionFinished() {
        if (!isDeferredMode) reducer.onCompositionFinished()
    }

    fun onEmptyFieldBackspace() {
        reducer.onEmptyFieldBackspace()
    }

    fun onEnter() {
        emit(OutgoingCommand.KeyPress(RemoteKey.ENTER), null)
    }

    fun onSpace() {
        emit(OutgoingCommand.TextCommit(" "), null)
    }

    fun submitDeferredDraft(text: String, completion: (Boolean) -> Unit): Boolean =
        isDeferredMode && text.isNotEmpty() && emit(OutgoingCommand.TextCommit(text), completion)

    fun reset() = reducer.reset()

    private fun OutgoingCommand.preferPhysicalKeys(): OutgoingCommand = when (this) {
        is OutgoingCommand.TextCommit -> copy(preferPhysicalKeys = true)
        is OutgoingCommand.ReplaceTail -> copy(preferPhysicalKeys = true)
        else -> this
    }
}
