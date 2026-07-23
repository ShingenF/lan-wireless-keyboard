package com.local.virtualkeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ImePanelMotionStateTest {
    @Test
    fun `opening hides the body before layout and tracks the keyboard without a jump`() {
        val state = ImePanelMotionState()

        assertEquals(
            ImePanelMotionUpdate(translationY = 0, visibility = ImePanelVisibilityUpdate.HIDE),
            state.onInsetsApplied(visible = false, layoutBottom = 60),
        )
        assertEquals(
            ImePanelMotionUpdate(translationY = 0, visibility = ImePanelVisibilityUpdate.SHOW),
            state.onAnimationPrepare(),
        )

        val endLayout = state.onInsetsApplied(visible = true, layoutBottom = 720)
        assertEquals(660, endLayout.translationY)
        assertEquals(ImePanelVisibilityUpdate.KEEP, endLayout.visibility)
        assertEquals(ImePanelBodyUpdate.RESTORE_FOR_SHOW, endLayout.body)

        assertEquals(
            ImePanelMotionUpdate(translationY = 480, visibility = ImePanelVisibilityUpdate.KEEP),
            state.onAnimationProgress(layoutBottom = 240),
        )
        assertEquals(
            ImePanelMotionUpdate(
                translationY = 0,
                visibility = ImePanelVisibilityUpdate.SHOW,
                toggleVisibility = ImeToggleVisibilityUpdate.SHOW,
            ),
            state.onAnimationEnd(),
        )
    }

    @Test
    fun `closing moves the arrow ahead of the keyboard and finishes early`() {
        val state = ImePanelMotionState()
        state.onInsetsApplied(visible = true, layoutBottom = 720)

        val prepare = state.onAnimationPrepare()
        assertEquals(0, prepare.translationY)
        assertEquals(ImePanelVisibilityUpdate.KEEP, prepare.visibility)

        val endLayout = state.onInsetsApplied(visible = false, layoutBottom = 60)
        assertEquals(-660, endLayout.translationY)
        assertEquals(ImePanelVisibilityUpdate.KEEP, endLayout.visibility)
        assertEquals(ImePanelBodyUpdate.PREPARE_FOR_HIDE, endLayout.body)

        assertEquals(
            ImePanelMotionUpdate(translationY = -100, visibility = ImePanelVisibilityUpdate.KEEP),
            state.onAnimationProgress(layoutBottom = 300),
        )
        assertEquals(
            ImePanelMotionUpdate(
                translationY = 0,
                visibility = ImePanelVisibilityUpdate.KEEP,
                toggleVisibility = ImeToggleVisibilityUpdate.HIDE,
            ),
            state.onAnimationProgress(layoutBottom = 225),
        )
        assertEquals(
            ImePanelMotionUpdate(
                translationY = 0,
                visibility = ImePanelVisibilityUpdate.HIDE,
                toggleVisibility = ImeToggleVisibilityUpdate.HIDE,
            ),
            state.onAnimationEnd(),
        )
    }

    @Test
    fun `non animated insets settle immediately`() {
        val state = ImePanelMotionState()

        assertEquals(
            ImePanelMotionUpdate(translationY = 0, visibility = ImePanelVisibilityUpdate.SHOW),
            state.onInsetsApplied(visible = true, layoutBottom = 600),
        )
        assertEquals(
            ImePanelMotionUpdate(translationY = 0, visibility = ImePanelVisibilityUpdate.HIDE),
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
            ImePanelMotionUpdate(translationY = -100, visibility = ImePanelVisibilityUpdate.KEEP),
            state.onAnimationPrepare(),
        )
        assertEquals(
            ImePanelMotionUpdate(
                translationY = 560,
                visibility = ImePanelVisibilityUpdate.KEEP,
                toggleVisibility = ImeToggleVisibilityUpdate.SHOW,
                body = ImePanelBodyUpdate.RESTORE_FOR_SHOW,
            ),
            state.onInsetsApplied(visible = true, layoutBottom = 720),
        )
        assertEquals(
            ImePanelMotionUpdate(translationY = 280, visibility = ImePanelVisibilityUpdate.KEEP),
            state.onAnimationProgress(layoutBottom = 510),
        )
        assertEquals(
            ImePanelMotionUpdate(
                translationY = 0,
                visibility = ImePanelVisibilityUpdate.SHOW,
                toggleVisibility = ImeToggleVisibilityUpdate.SHOW,
            ),
            state.onAnimationEnd(),
        )
    }
}
