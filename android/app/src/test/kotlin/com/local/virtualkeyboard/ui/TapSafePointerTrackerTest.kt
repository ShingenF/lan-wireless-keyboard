package com.local.virtualkeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapSafePointerTrackerTest {
    @Test
    fun `tap-sized motion stays local but a real drag starts from the original touch point`() {
        val tracker = TapSafePointerTracker(touchSlop = 8f)
        tracker.onDown(x = 100f, y = 100f)

        assertNull(tracker.onMove(x = 105f, y = 103f))
        assertEquals(PointerDelta(10f, 0f), tracker.onMove(x = 110f, y = 100f))
        assertEquals(PointerDelta(3f, -2f), tracker.onMove(x = 113f, y = 98f))
    }
}
