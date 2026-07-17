package com.local.virtualkeyboard.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiTouchMovementTrackerTest {
    @Test
    fun `opposing finger movement cannot remain a tap when centroid stays still`() {
        val tracker = MultiTouchMovementTracker(touchSlop = 8f)
        tracker.start(listOf(contact(0, 100f), contact(1, 200f)))

        assertTrue(tracker.update(listOf(contact(0, 120f), contact(1, 180f))))
    }

    @Test
    fun `movement remains cumulative after one finger lifts`() {
        val tracker = MultiTouchMovementTracker(touchSlop = 8f)
        tracker.start(listOf(contact(0, 100f), contact(1, 200f)))
        assertFalse(tracker.update(listOf(contact(0, 105f), contact(1, 205f))))

        assertTrue(tracker.update(listOf(contact(0, 110f))))
    }

    @Test
    fun `adding another contact permanently disqualifies two finger tap`() {
        val tracker = MultiTouchMovementTracker(touchSlop = 8f)
        tracker.start(listOf(contact(0, 100f), contact(1, 200f)))

        assertTrue(
            tracker.addContacts(
                listOf(contact(0, 100f), contact(1, 200f), contact(2, 300f)),
            ),
        )
        assertTrue(tracker.update(listOf(contact(0, 100f), contact(1, 200f))))
    }

    private fun contact(id: Int, x: Float): TouchContact = TouchContact(id, x, y = 100f)
}
