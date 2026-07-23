package com.local.virtualkeyboard.ui

import kotlin.math.abs
import kotlin.math.roundToInt

internal data class ImePanelMotionUpdate(
    val translationY: Int,
    val visibility: ImePanelVisibilityUpdate,
    val toggleVisibility: ImeToggleVisibilityUpdate = ImeToggleVisibilityUpdate.KEEP,
    val body: ImePanelBodyUpdate = ImePanelBodyUpdate.KEEP,
)

internal enum class ImePanelVisibilityUpdate {
    KEEP,
    SHOW,
    HIDE,
}

internal enum class ImeToggleVisibilityUpdate {
    KEEP,
    SHOW,
    HIDE,
}

internal enum class ImePanelBodyUpdate {
    KEEP,
    PREPARE_FOR_HIDE,
    RESTORE_FOR_SHOW,
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
    private var animationStartLayoutBottom = 0
    private var animationInitialTranslationY = 0
    private var lastTranslationY = 0

    fun onAnimationPrepare(): ImePanelMotionUpdate {
        animationRunning = true
        targetImeVisible = settledImeVisible
        animationStartLayoutBottom = currentLayoutBottom
        animationInitialTranslationY = lastTranslationY
        return ImePanelMotionUpdate(
            translationY = lastTranslationY,
            visibility = if (settledImeVisible) {
                ImePanelVisibilityUpdate.KEEP
            } else {
                ImePanelVisibilityUpdate.SHOW
            },
        )
    }

    fun onInsetsApplied(visible: Boolean, layoutBottom: Int): ImePanelMotionUpdate {
        val previousTargetLayoutBottom = targetLayoutBottom
        targetImeVisible = visible
        targetLayoutBottom = layoutBottom
        if (animationRunning) {
            lastTranslationY += targetLayoutBottom - previousTargetLayoutBottom
            animationStartLayoutBottom = currentLayoutBottom
            animationInitialTranslationY = lastTranslationY
            return motionOnlyUpdate(
                translationY = lastTranslationY,
                toggleVisibility = if (visible) {
                    ImeToggleVisibilityUpdate.SHOW
                } else {
                    ImeToggleVisibilityUpdate.KEEP
                },
                body = if (visible) {
                    ImePanelBodyUpdate.RESTORE_FOR_SHOW
                } else {
                    ImePanelBodyUpdate.PREPARE_FOR_HIDE
                },
            )
        }

        settledImeVisible = visible
        currentLayoutBottom = layoutBottom
        animationStartLayoutBottom = layoutBottom
        animationInitialTranslationY = 0
        lastTranslationY = 0
        return ImePanelMotionUpdate(
            translationY = 0,
            visibility = visibilityFor(visible),
        )
    }

    fun onAnimationProgress(layoutBottom: Int): ImePanelMotionUpdate {
        currentLayoutBottom = layoutBottom
        val totalTravel = abs(targetLayoutBottom - animationStartLayoutBottom)
        val completed = if (totalTravel == 0) {
            1f
        } else {
            (abs(currentLayoutBottom - animationStartLayoutBottom).toFloat() / totalTravel)
                .coerceIn(0f, 1f)
        }
        val panelProgress = if (targetImeVisible) {
            completed
        } else {
            (completed / CLOSE_COMPLETION_FRACTION).coerceAtMost(1f)
        }
        lastTranslationY = (animationInitialTranslationY * (1f - panelProgress)).roundToInt()
        return motionOnlyUpdate(
            translationY = lastTranslationY,
            toggleVisibility = if (!targetImeVisible && panelProgress >= 1f) {
                ImeToggleVisibilityUpdate.HIDE
            } else {
                ImeToggleVisibilityUpdate.KEEP
            },
        )
    }

    fun onAnimationEnd(): ImePanelMotionUpdate {
        animationRunning = false
        settledImeVisible = targetImeVisible
        currentLayoutBottom = targetLayoutBottom
        animationStartLayoutBottom = targetLayoutBottom
        animationInitialTranslationY = 0
        lastTranslationY = 0
        return ImePanelMotionUpdate(
            translationY = 0,
            visibility = visibilityFor(settledImeVisible),
            toggleVisibility = if (settledImeVisible) {
                ImeToggleVisibilityUpdate.SHOW
            } else {
                ImeToggleVisibilityUpdate.HIDE
            },
        )
    }

    private fun motionOnlyUpdate(
        translationY: Int,
        toggleVisibility: ImeToggleVisibilityUpdate = ImeToggleVisibilityUpdate.KEEP,
        body: ImePanelBodyUpdate = ImePanelBodyUpdate.KEEP,
    ) = ImePanelMotionUpdate(
        translationY = translationY,
        visibility = ImePanelVisibilityUpdate.KEEP,
        toggleVisibility = toggleVisibility,
        body = body,
    )

    private fun visibilityFor(visible: Boolean) =
        if (visible) ImePanelVisibilityUpdate.SHOW else ImePanelVisibilityUpdate.HIDE

    private companion object {
        const val CLOSE_COMPLETION_FRACTION = 0.75f
    }
}
