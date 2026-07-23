package com.local.virtualkeyboard.ui

internal data class ImePanelMotionUpdate(
    val translationY: Int,
    val visibility: ImePanelVisibilityUpdate,
)

internal enum class ImePanelVisibilityUpdate {
    KEEP,
    SHOW,
    HIDE,
}

/**
 * Keeps the shortcut panel visually attached to the animated IME edge.
 *
 * Android lays the window out using the IME's final inset before dispatching animation progress.
 * The translation returned here compensates that end-state layout until the IME catches up.
 */
internal class ImePanelMotionState {
    private var animationRunning = false
    private var settledImeVisible = false
    private var currentLayoutBottom = 0
    private var targetImeVisible = false
    private var targetLayoutBottom = 0

    fun onAnimationPrepare(): ImePanelMotionUpdate {
        val currentTranslationY = targetLayoutBottom - currentLayoutBottom
        animationRunning = true
        targetImeVisible = settledImeVisible
        targetLayoutBottom = currentLayoutBottom
        return ImePanelMotionUpdate(
            translationY = currentTranslationY,
            visibility = if (settledImeVisible) {
                ImePanelVisibilityUpdate.KEEP
            } else {
                ImePanelVisibilityUpdate.SHOW
            },
        )
    }

    fun onInsetsApplied(visible: Boolean, layoutBottom: Int): ImePanelMotionUpdate {
        targetImeVisible = visible
        targetLayoutBottom = layoutBottom
        if (animationRunning) {
            return motionOnlyUpdate()
        }

        settledImeVisible = visible
        currentLayoutBottom = layoutBottom
        return ImePanelMotionUpdate(
            translationY = 0,
            visibility = visibilityFor(visible),
        )
    }

    fun onAnimationProgress(layoutBottom: Int): ImePanelMotionUpdate {
        currentLayoutBottom = layoutBottom
        return motionOnlyUpdate()
    }

    fun onAnimationEnd(): ImePanelMotionUpdate {
        animationRunning = false
        settledImeVisible = targetImeVisible
        currentLayoutBottom = targetLayoutBottom
        return ImePanelMotionUpdate(
            translationY = 0,
            visibility = visibilityFor(settledImeVisible),
        )
    }

    private fun motionOnlyUpdate() = ImePanelMotionUpdate(
        translationY = targetLayoutBottom - currentLayoutBottom,
        visibility = ImePanelVisibilityUpdate.KEEP,
    )

    private fun visibilityFor(visible: Boolean) =
        if (visible) ImePanelVisibilityUpdate.SHOW else ImePanelVisibilityUpdate.HIDE
}
