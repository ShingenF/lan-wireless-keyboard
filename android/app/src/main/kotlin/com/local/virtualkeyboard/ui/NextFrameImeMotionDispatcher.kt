package com.local.virtualkeyboard.ui

/**
 * Keeps app-owned IME chrome one composition frame behind the Insets callback.
 *
 * Some OEMs publish the next IME inset before the input-method surface reaches that position.
 * Applying the matching app translation in the same callback exposes a gap for one frame.
 */
internal class NextFrameImeMotionDispatcher<T : Any>(
    private val postOnAnimation: (() -> Unit) -> Unit,
    private val apply: (T) -> Unit,
) {
    private var frameScheduled = false
    private var pendingValue: T? = null
    private var trailingValue: T? = null

    fun dispatchNextFrame(value: T) {
        pendingValue = value
        scheduleFrameIfNeeded()
    }

    fun dispatchAfterPendingFrame(value: T) {
        if (pendingValue != null) {
            trailingValue = value
        } else {
            pendingValue = value
            scheduleFrameIfNeeded()
        }
    }

    fun dispatchImmediately(value: T) {
        pendingValue = null
        trailingValue = null
        apply(value)
    }

    private fun scheduleFrameIfNeeded() {
        if (frameScheduled) return
        frameScheduled = true
        postOnAnimation {
            frameScheduled = false
            val valueToApply = pendingValue
            pendingValue = null
            if (valueToApply != null) apply(valueToApply)

            val valueAfterPending = trailingValue
            trailingValue = null
            if (valueAfterPending != null) {
                pendingValue = valueAfterPending
                scheduleFrameIfNeeded()
            }
        }
    }
}
