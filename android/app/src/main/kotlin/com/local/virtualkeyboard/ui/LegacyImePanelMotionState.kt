package com.local.virtualkeyboard.ui

internal enum class LegacyImePanelMotionAction {
    KEEP,
    PREPARE_FOR_HIDE,
    RESTORE_FOR_SHOW,
}

internal data class LegacyImePanelMotionUpdate(
    val action: LegacyImePanelMotionAction,
    val bodyOffset: Int = 0,
)

/**
 * Detects the beginning of IME motion on API 26-29 from successive visible-frame layouts.
 */
internal class LegacyImePanelMotionState(
    private val movementThreshold: Int,
) {
    private var previousRemainingImeHeight: Int? = null
    private var closing = false
    private var closingMinimumRemainingImeHeight = 0

    fun onLayout(imeVisible: Boolean, remainingImeHeight: Int): LegacyImePanelMotionUpdate {
        val previous = previousRemainingImeHeight
        previousRemainingImeHeight = remainingImeHeight
        if (!imeVisible) {
            closing = false
            return LegacyImePanelMotionUpdate(LegacyImePanelMotionAction.KEEP)
        }
        if (previous == null) {
            return LegacyImePanelMotionUpdate(LegacyImePanelMotionAction.KEEP)
        }

        val movement = remainingImeHeight - previous
        if (closing) {
            closingMinimumRemainingImeHeight = minOf(
                closingMinimumRemainingImeHeight,
                remainingImeHeight,
            )
            if (remainingImeHeight - closingMinimumRemainingImeHeight >= movementThreshold) {
                closing = false
                return LegacyImePanelMotionUpdate(LegacyImePanelMotionAction.RESTORE_FOR_SHOW)
            }
            return if (movement != 0) {
                hideUpdate(remainingImeHeight)
            } else {
                LegacyImePanelMotionUpdate(LegacyImePanelMotionAction.KEEP)
            }
        }

        return when {
            movement <= -movementThreshold -> {
                closing = true
                closingMinimumRemainingImeHeight = remainingImeHeight
                hideUpdate(remainingImeHeight)
            }
            else -> LegacyImePanelMotionUpdate(LegacyImePanelMotionAction.KEEP)
        }
    }

    fun onMotionIdle(): LegacyImePanelMotionUpdate {
        if (!closing) return LegacyImePanelMotionUpdate(LegacyImePanelMotionAction.KEEP)
        closing = false
        return LegacyImePanelMotionUpdate(LegacyImePanelMotionAction.RESTORE_FOR_SHOW)
    }

    private fun hideUpdate(remainingImeHeight: Int) = LegacyImePanelMotionUpdate(
        action = LegacyImePanelMotionAction.PREPARE_FOR_HIDE,
        bodyOffset = remainingImeHeight,
    )
}
