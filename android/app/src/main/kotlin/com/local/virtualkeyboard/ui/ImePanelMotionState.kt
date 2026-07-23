package com.local.virtualkeyboard.ui

import kotlin.math.abs
import kotlin.math.roundToInt

internal data class ImePanelMotionUpdate(
    val toggleTranslationY: Int,
    val bodyTranslationY: Int = 0,
    val visibility: ImePanelVisibilityUpdate,
    val toggleVisibility: ImeToggleVisibilityUpdate = ImeToggleVisibilityUpdate.KEEP,
    val body: ImePanelBodyUpdate = ImePanelBodyUpdate.KEEP,
    val toggleRevealProgress: Float? = null,
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
    private var animationInitialToggleTranslationY = 0
    private var animationInitialBodyTranslationY = 0
    private var animationInitialToggleRevealProgress = 0f
    private var lastToggleTranslationY = 0
    private var lastBodyTranslationY = 0
    private var lastToggleRevealProgress = 0f

    fun onAnimationPrepare(): ImePanelMotionUpdate {
        val reversingRunningAnimation = animationRunning
        animationRunning = true
        if (!reversingRunningAnimation) {
            targetImeVisible = settledImeVisible
        }
        animationStartLayoutBottom = currentLayoutBottom
        animationInitialToggleTranslationY = lastToggleTranslationY
        animationInitialBodyTranslationY = lastBodyTranslationY
        animationInitialToggleRevealProgress = lastToggleRevealProgress
        return ImePanelMotionUpdate(
            toggleTranslationY = lastToggleTranslationY,
            bodyTranslationY = lastBodyTranslationY,
            visibility = if (settledImeVisible) {
                ImePanelVisibilityUpdate.KEEP
            } else {
                ImePanelVisibilityUpdate.SHOW
            },
        )
    }

    fun onInsetsApplied(visible: Boolean, layoutBottom: Int): ImePanelMotionUpdate {
        val previousTargetLayoutBottom = targetLayoutBottom
        val reversingToSettledState =
            animationRunning && visible != targetImeVisible && visible == settledImeVisible
        targetImeVisible = visible
        targetLayoutBottom = layoutBottom
        if (animationRunning) {
            val layoutDelta = targetLayoutBottom - previousTargetLayoutBottom
            lastToggleTranslationY += layoutDelta
            lastBodyTranslationY = if (visible || reversingToSettledState) {
                lastBodyTranslationY + layoutDelta
            } else {
                0
            }
            animationStartLayoutBottom = currentLayoutBottom
            animationInitialToggleTranslationY = lastToggleTranslationY
            animationInitialBodyTranslationY = lastBodyTranslationY
            return motionOnlyUpdate(
                toggleTranslationY = lastToggleTranslationY,
                bodyTranslationY = lastBodyTranslationY,
                toggleRevealProgress = lastToggleRevealProgress,
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
        animationInitialToggleTranslationY = 0
        animationInitialBodyTranslationY = 0
        lastToggleTranslationY = 0
        lastBodyTranslationY = 0
        lastToggleRevealProgress = if (visible) 1f else 0f
        return ImePanelMotionUpdate(
            toggleTranslationY = 0,
            visibility = visibilityFor(visible),
            toggleRevealProgress = lastToggleRevealProgress,
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
        lastToggleTranslationY =
            (animationInitialToggleTranslationY * (1f - panelProgress)).roundToInt()
        lastBodyTranslationY =
            (animationInitialBodyTranslationY * (1f - completed)).roundToInt()
        lastToggleRevealProgress = if (targetImeVisible) {
            animationInitialToggleRevealProgress +
                (1f - animationInitialToggleRevealProgress) * completed
        } else {
            animationInitialToggleRevealProgress * (1f - panelProgress)
        }
        return motionOnlyUpdate(
            toggleTranslationY = lastToggleTranslationY,
            bodyTranslationY = lastBodyTranslationY,
            toggleRevealProgress = lastToggleRevealProgress,
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
        animationInitialToggleTranslationY = 0
        animationInitialBodyTranslationY = 0
        lastToggleTranslationY = 0
        lastBodyTranslationY = 0
        lastToggleRevealProgress = if (settledImeVisible) 1f else 0f
        return ImePanelMotionUpdate(
            toggleTranslationY = 0,
            visibility = visibilityFor(settledImeVisible),
            toggleVisibility = if (settledImeVisible) {
                ImeToggleVisibilityUpdate.SHOW
            } else {
                ImeToggleVisibilityUpdate.HIDE
            },
            toggleRevealProgress = lastToggleRevealProgress,
        )
    }

    private fun motionOnlyUpdate(
        toggleTranslationY: Int,
        bodyTranslationY: Int = 0,
        toggleVisibility: ImeToggleVisibilityUpdate = ImeToggleVisibilityUpdate.KEEP,
        body: ImePanelBodyUpdate = ImePanelBodyUpdate.KEEP,
        toggleRevealProgress: Float? = null,
    ) = ImePanelMotionUpdate(
        toggleTranslationY = toggleTranslationY,
        bodyTranslationY = bodyTranslationY,
        visibility = ImePanelVisibilityUpdate.KEEP,
        toggleVisibility = toggleVisibility,
        body = body,
        toggleRevealProgress = toggleRevealProgress,
    )

    private fun visibilityFor(visible: Boolean) =
        if (visible) ImePanelVisibilityUpdate.SHOW else ImePanelVisibilityUpdate.HIDE

    private companion object {
        const val CLOSE_COMPLETION_FRACTION = 0.75f
    }
}
