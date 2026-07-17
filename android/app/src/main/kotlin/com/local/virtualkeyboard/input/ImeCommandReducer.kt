package com.local.virtualkeyboard.input

import com.local.virtualkeyboard.protocol.OutgoingCommand
import com.local.virtualkeyboard.protocol.RemoteKey

/** Converts the event sequence exposed by Sogou into remote input commands. */
class ImeCommandReducer(
    private val emit: (OutgoingCommand) -> Unit,
) {
    private var mirroredComposition = ""
    private var recentlyFinishedComposition: String? = null

    fun onCompositionChanged(text: String) {
        recentlyFinishedComposition = null
        replaceMirroredTailWith(text)
    }

    fun onCommit(text: String) {
        if (text.isEmpty()) return

        val priorTail = when {
            mirroredComposition.isNotEmpty() -> mirroredComposition
            recentlyFinishedComposition != null -> recentlyFinishedComposition
            else -> null
        }
        if (priorTail == null) {
            emit(OutgoingCommand.TextCommit(text))
        } else {
            emitFinalization(priorTail, text)
        }
        mirroredComposition = ""
        recentlyFinishedComposition = null
    }

    fun onCompositionFinished() {
        if (mirroredComposition.isNotEmpty()) {
            recentlyFinishedComposition = mirroredComposition
        }
        mirroredComposition = ""
    }

    fun onEmptyFieldBackspace() {
        emit(OutgoingCommand.KeyPress(RemoteKey.BACKSPACE))
    }

    fun reset() {
        mirroredComposition = ""
        recentlyFinishedComposition = null
    }

    private fun replaceMirroredTailWith(updated: String) {
        if (updated == mirroredComposition) return
        emitTailReplacement(mirroredComposition, updated)
        mirroredComposition = updated
    }

    private fun emitTailReplacement(previous: String, updated: String) {
        val previousCodePoints = previous.codePoints().toArray()
        val updatedCodePoints = updated.codePoints().toArray()
        var commonPrefixLength = 0
        while (
            commonPrefixLength < previousCodePoints.size &&
            commonPrefixLength < updatedCodePoints.size &&
            previousCodePoints[commonPrefixLength] == updatedCodePoints[commonPrefixLength]
        ) {
            commonPrefixLength += 1
        }
        if (commonPrefixLength == previousCodePoints.size && commonPrefixLength == updatedCodePoints.size) return

        emit(
            OutgoingCommand.ReplaceTail(
                deleteCodePoints = previousCodePoints.size - commonPrefixLength,
                text = String(
                    updatedCodePoints,
                    commonPrefixLength,
                    updatedCodePoints.size - commonPrefixLength,
                ),
            ),
        )
    }

    private fun emitFinalization(previous: String, committed: String) {
        when {
            committed == previous -> Unit
            committed.startsWith(previous) -> {
                val suffix = committed.substring(previous.length)
                if (suffix.isNotEmpty()) emit(OutgoingCommand.TextCommit(suffix))
            }
            committed.isBlank() -> emit(OutgoingCommand.TextCommit(committed))
            isLikelyCorrection(previous, committed) -> emitTailReplacement(previous, committed)
            else -> emit(OutgoingCommand.TextCommit(committed))
        }
    }

    private fun isLikelyCorrection(previous: String, committed: String): Boolean =
        previous.isNotEmpty() &&
            committed.isNotEmpty() &&
            previous.first().lowercaseChar() == committed.first().lowercaseChar() &&
            previous.none(Char::isWhitespace) &&
            committed.none(Char::isWhitespace)
}
