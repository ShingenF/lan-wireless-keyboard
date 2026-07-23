package com.local.virtualkeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyImePanelMotionStateTest {
    @Test
    fun `closing is prepared at the final screen position on the first downward frame`() {
        val state = LegacyImePanelMotionState(movementThreshold = 4)

        assertEquals(
            LegacyImePanelMotionUpdate(LegacyImePanelMotionAction.KEEP),
            state.onLayout(true, 700),
        )
        assertEquals(
            LegacyImePanelMotionUpdate(LegacyImePanelMotionAction.KEEP),
            state.onLayout(true, 698),
        )
        assertEquals(
            LegacyImePanelMotionUpdate(
                action = LegacyImePanelMotionAction.PREPARE_FOR_HIDE,
                bodyOffset = 650,
            ),
            state.onLayout(true, 650),
        )
    }

    @Test
    fun `reversing a close restores the keyboard-on panel state`() {
        val state = LegacyImePanelMotionState(movementThreshold = 4)
        state.onLayout(true, 700)
        state.onLayout(true, 500)

        assertEquals(
            LegacyImePanelMotionUpdate(LegacyImePanelMotionAction.RESTORE_FOR_SHOW),
            state.onLayout(true, 560),
        )
    }

    @Test
    fun `closing geometry continues updating below the detection threshold`() {
        val state = LegacyImePanelMotionState(movementThreshold = 4)
        state.onLayout(true, 700)
        state.onLayout(true, 650)

        assertEquals(
            LegacyImePanelMotionUpdate(
                action = LegacyImePanelMotionAction.PREPARE_FOR_HIDE,
                bodyOffset = 648,
            ),
            state.onLayout(true, 648),
        )
    }

    @Test
    fun `a stable keyboard height change restores the panel after motion becomes idle`() {
        val state = LegacyImePanelMotionState(movementThreshold = 4)
        state.onLayout(true, 700)
        state.onLayout(true, 650)

        assertEquals(
            LegacyImePanelMotionUpdate(LegacyImePanelMotionAction.RESTORE_FOR_SHOW),
            state.onMotionIdle(),
        )
        assertEquals(
            LegacyImePanelMotionUpdate(LegacyImePanelMotionAction.KEEP),
            state.onMotionIdle(),
        )
    }

    @Test
    fun `motion is ignored outside a visible ime session`() {
        val state = LegacyImePanelMotionState(movementThreshold = 4)

        assertEquals(
            LegacyImePanelMotionUpdate(LegacyImePanelMotionAction.KEEP),
            state.onLayout(false, 700),
        )
        assertEquals(
            LegacyImePanelMotionUpdate(LegacyImePanelMotionAction.KEEP),
            state.onLayout(false, 60),
        )
    }
}
