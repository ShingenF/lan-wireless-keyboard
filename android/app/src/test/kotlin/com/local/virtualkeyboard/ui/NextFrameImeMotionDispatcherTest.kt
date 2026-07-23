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
    fun `terminal value is applied after the pending progress frame`() {
        val scheduled = mutableListOf<() -> Unit>()
        val applied = mutableListOf<Int>()
        val dispatcher = NextFrameImeMotionDispatcher<Int>(
            postOnAnimation = scheduled::add,
            apply = applied::add,
        )

        dispatcher.dispatchNextFrame(1)
        dispatcher.dispatchAfterPendingFrame(2)
        scheduled.removeFirst().invoke()

        assertEquals(listOf(1), applied)
        assertEquals(1, scheduled.size)
        scheduled.removeFirst().invoke()
        assertEquals(listOf(1, 2), applied)
    }

    @Test
    fun `immediate value cancels deferred progress and terminal values`() {
        val scheduled = mutableListOf<() -> Unit>()
        val applied = mutableListOf<Int>()
        val dispatcher = NextFrameImeMotionDispatcher<Int>(
            postOnAnimation = scheduled::add,
            apply = applied::add,
        )

        dispatcher.dispatchNextFrame(1)
        dispatcher.dispatchAfterPendingFrame(2)
        dispatcher.dispatchImmediately(3)
        scheduled.removeFirst().invoke()

        assertEquals(listOf(3), applied)
    }

    @Test
    fun `terminal reuses an already scheduled but empty frame after an immediate prepare`() {
        val scheduled = mutableListOf<() -> Unit>()
        val applied = mutableListOf<Int>()
        val dispatcher = NextFrameImeMotionDispatcher<Int>(
            postOnAnimation = scheduled::add,
            apply = applied::add,
        )

        dispatcher.dispatchNextFrame(1)
        dispatcher.dispatchImmediately(2)
        dispatcher.dispatchAfterPendingFrame(3)
        scheduled.removeFirst().invoke()

        assertEquals(listOf(2, 3), applied)
        assertTrue(scheduled.isEmpty())
    }
}
