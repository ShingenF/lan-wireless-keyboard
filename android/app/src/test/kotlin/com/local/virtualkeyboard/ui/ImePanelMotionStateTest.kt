package com.local.virtualkeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ImePanelMotionStateTest {
    @Test
    fun `opening hides the body before layout and tracks the keyboard without a jump`() {
        val state = ImePanelMotionState()

        assertEquals(
            ImePanelMotionUpdate(
                toggleTranslationY = 0,
                visibility = ImePanelVisibilityUpdate.HIDE,
                toggleRevealProgress = 0f,
            ),
            state.onInsetsApplied(visible = false, layoutBottom = 60),
        )
        assertEquals(
            ImePanelMotionUpdate(toggleTranslationY = 0, visibility = ImePanelVisibilityUpdate.SHOW),
            state.onAnimationPrepare(),
        )

        val endLayout = state.onInsetsApplied(visible = true, layoutBottom = 720)
        assertEquals(660, endLayout.toggleTranslationY)
        assertEquals(660, endLayout.bodyTranslationY)
        assertEquals(ImePanelVisibilityUpdate.KEEP, endLayout.visibility)
        assertEquals(ImePanelBodyUpdate.RESTORE_FOR_SHOW, endLayout.body)
        assertEquals(0f, endLayout.toggleRevealProgress)

        val progress = state.onAnimationProgress(layoutBottom = 240)
        assertEquals(480, progress.toggleTranslationY)
        assertEquals(480, progress.bodyTranslationY)
        assertEquals(ImePanelVisibilityUpdate.KEEP, progress.visibility)
        assertEquals(0.27272728f, progress.toggleRevealProgress)
        assertEquals(
            ImePanelMotionUpdate(
                toggleTranslationY = 0,
                visibility = ImePanelVisibilityUpdate.SHOW,
                toggleVisibility = ImeToggleVisibilityUpdate.SHOW,
                toggleRevealProgress = 1f,
            ),
            state.onAnimationEnd(),
        )
    }

    @Test
    fun `closing moves the arrow ahead of the keyboard and finishes early`() {
        val state = ImePanelMotionState()
        state.onInsetsApplied(visible = true, layoutBottom = 720)

        val prepare = state.onAnimationPrepare()
        assertEquals(0, prepare.toggleTranslationY)
        assertEquals(ImePanelVisibilityUpdate.KEEP, prepare.visibility)

        val endLayout = state.onInsetsApplied(visible = false, layoutBottom = 60)
        assertEquals(-660, endLayout.toggleTranslationY)
        assertEquals(0, endLayout.bodyTranslationY)
        assertEquals(ImePanelVisibilityUpdate.KEEP, endLayout.visibility)
        assertEquals(ImePanelBodyUpdate.PREPARE_FOR_HIDE, endLayout.body)
        assertEquals(1f, endLayout.toggleRevealProgress)

        assertEquals(
            ImePanelMotionUpdate(
                toggleTranslationY = -100,
                visibility = ImePanelVisibilityUpdate.KEEP,
                toggleRevealProgress = 0.15151519f,
            ),
            state.onAnimationProgress(layoutBottom = 300),
        )
        assertEquals(
            ImePanelMotionUpdate(
                toggleTranslationY = 0,
                visibility = ImePanelVisibilityUpdate.KEEP,
                toggleVisibility = ImeToggleVisibilityUpdate.HIDE,
                toggleRevealProgress = 0f,
            ),
            state.onAnimationProgress(layoutBottom = 225),
        )
        assertEquals(
            ImePanelMotionUpdate(
                toggleTranslationY = 0,
                visibility = ImePanelVisibilityUpdate.HIDE,
                toggleVisibility = ImeToggleVisibilityUpdate.HIDE,
                toggleRevealProgress = 0f,
            ),
            state.onAnimationEnd(),
        )
    }

    @Test
    fun `non animated insets settle immediately`() {
        val state = ImePanelMotionState()

        assertEquals(
            ImePanelMotionUpdate(
                toggleTranslationY = 0,
                visibility = ImePanelVisibilityUpdate.SHOW,
                toggleRevealProgress = 1f,
            ),
            state.onInsetsApplied(visible = true, layoutBottom = 600),
        )
        assertEquals(
            ImePanelMotionUpdate(
                toggleTranslationY = 0,
                visibility = ImePanelVisibilityUpdate.HIDE,
                toggleRevealProgress = 0f,
            ),
            state.onInsetsApplied(visible = false, layoutBottom = 60),
        )
    }

    @Test
    fun `reversing an animation preserves the current visual position and panel state`() {
        val state = ImePanelMotionState()
        state.onInsetsApplied(visible = true, layoutBottom = 720)
        state.onAnimationPrepare()
        state.onInsetsApplied(visible = false, layoutBottom = 60)
        state.onAnimationProgress(layoutBottom = 300)

        assertEquals(
            ImePanelMotionUpdate(toggleTranslationY = -100, visibility = ImePanelVisibilityUpdate.KEEP),
            state.onAnimationPrepare(),
        )
        assertEquals(
            ImePanelMotionUpdate(
                toggleTranslationY = 560,
                bodyTranslationY = 660,
                visibility = ImePanelVisibilityUpdate.KEEP,
                toggleVisibility = ImeToggleVisibilityUpdate.SHOW,
                body = ImePanelBodyUpdate.RESTORE_FOR_SHOW,
                toggleRevealProgress = 0.15151519f,
            ),
            state.onInsetsApplied(visible = true, layoutBottom = 720),
        )
        assertEquals(
            ImePanelMotionUpdate(
                toggleTranslationY = 280,
                bodyTranslationY = 330,
                visibility = ImePanelVisibilityUpdate.KEEP,
                toggleRevealProgress = 0.5757576f,
            ),
            state.onAnimationProgress(layoutBottom = 510),
        )
        assertEquals(
            ImePanelMotionUpdate(
                toggleTranslationY = 0,
                visibility = ImePanelVisibilityUpdate.SHOW,
                toggleVisibility = ImeToggleVisibilityUpdate.SHOW,
                toggleRevealProgress = 1f,
            ),
            state.onAnimationEnd(),
        )
    }

    @Test
    fun `reversing an opening animation keeps both layers at their current screen position`() {
        val state = ImePanelMotionState()
        state.onInsetsApplied(visible = false, layoutBottom = 60)
        state.onAnimationPrepare()
        state.onInsetsApplied(visible = true, layoutBottom = 720)
        val openingProgress = state.onAnimationProgress(layoutBottom = 240)

        assertEquals(480, openingProgress.toggleTranslationY)
        assertEquals(480, openingProgress.bodyTranslationY)
        assertEquals(
            ImePanelMotionUpdate(
                toggleTranslationY = 480,
                bodyTranslationY = 480,
                visibility = ImePanelVisibilityUpdate.SHOW,
            ),
            state.onAnimationPrepare(),
        )

        val reverseLayout = state.onInsetsApplied(visible = false, layoutBottom = 60)
        assertEquals(-180, reverseLayout.toggleTranslationY)
        assertEquals(-180, reverseLayout.bodyTranslationY)
        assertEquals(
            openingProgress.toggleTranslationY - (720 - 60),
            reverseLayout.toggleTranslationY,
        )
        assertEquals(
            openingProgress.bodyTranslationY - (720 - 60),
            reverseLayout.bodyTranslationY,
        )

        val reverseProgress = state.onAnimationProgress(layoutBottom = 150)
        assertEquals(-60, reverseProgress.toggleTranslationY)
        assertEquals(-90, reverseProgress.bodyTranslationY)
    }

    @Test
    fun `arrow reveal follows opening and retracts ahead of closing`() {
        val state = ImePanelMotionState()
        state.onInsetsApplied(visible = false, layoutBottom = 60)
        state.onAnimationPrepare()
        state.onInsetsApplied(visible = true, layoutBottom = 720)

        assertEquals(0.25f, state.onAnimationProgress(layoutBottom = 225).toggleRevealProgress)
        assertEquals(1f, state.onAnimationEnd().toggleRevealProgress)

        state.onAnimationPrepare()
        state.onInsetsApplied(visible = false, layoutBottom = 60)

        assertEquals(
            0.5f,
            requireNotNull(state.onAnimationProgress(layoutBottom = 472).toggleRevealProgress),
            0.002f,
        )
        assertEquals(0f, state.onAnimationProgress(layoutBottom = 225).toggleRevealProgress)
        assertEquals(0f, state.onAnimationEnd().toggleRevealProgress)
    }
}
