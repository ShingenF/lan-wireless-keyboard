package com.local.virtualkeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextFrameImeMotionDispatcherTest {
    @Test
    fun `progress is committed on the next frame and coalesces to the newest value`() {
        val scheduled = mutableListOf<() -> Unit>()
        val applied = mutableListOf<Int>()
        val dispatcher = NextFrameImeMotionDispatcher<Int>(
            postOnAnimation = scheduled::add,
            apply = applied::add,
        )

        dispatcher.dispatchNextFrame(1)
        dispatcher.dispatchNextFrame(2)

        assertTrue(applied.isEmpty())
        assertEquals(1, scheduled.size)
        scheduled.removeFirst().invoke()
        assertEquals(listOf(2), applied)
    }

    @Test
    fun `immediate terminal value cancels a stale deferred progress value`() {
        val scheduled = mutableListOf<() -> Unit>()
        val applied = mutableListOf<Int>()
        val dispatcher = NextFrameImeMotionDispatcher<Int>(
            postOnAnimation = scheduled::add,
            apply = applied::add,
        )

        dispatcher.dispatchNextFrame(1)
        dispatcher.dispatchImmediately(2)
        scheduled.removeFirst().invoke()

        assertEquals(listOf(2), applied)
    }
}
