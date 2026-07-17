package com.local.virtualkeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapSequenceResolverTest {
    @Test
    fun `a lone tap becomes one left click immediately`() {
        val resolver = TapSequenceResolver(doubleTapTimeoutMillis = 300L, doubleTapSlop = 32f)

        assertEquals(
            listOf(TapAction.LEFT_CLICK),
            resolver.onTap(x = 100f, y = 100f, eventTimeMillis = 1_000L),
        )
    }

    @Test
    fun `two nearby taps become two immediate left clicks`() {
        val resolver = TapSequenceResolver(doubleTapTimeoutMillis = 300L, doubleTapSlop = 32f)

        assertEquals(
            listOf(TapAction.LEFT_CLICK),
            resolver.onTap(x = 100f, y = 100f, eventTimeMillis = 1_000L),
        )
        assertEquals(
            emptyList<TapAction>(),
            resolver.onDown(x = 106f, y = 104f, eventTimeMillis = 1_220L),
        )
        assertEquals(
            listOf(TapAction.LEFT_CLICK),
            resolver.onTap(x = 106f, y = 104f, eventTimeMillis = 1_340L),
        )
    }

    @Test
    fun `aborting an armed second tap does not replay the completed first click`() {
        val resolver = TapSequenceResolver(doubleTapTimeoutMillis = 300L, doubleTapSlop = 32f)

        assertEquals(
            listOf(TapAction.LEFT_CLICK),
            resolver.onTap(x = 100f, y = 100f, eventTimeMillis = 1_000L),
        )
        assertEquals(
            emptyList<TapAction>(),
            resolver.onDown(x = 104f, y = 105f, eventTimeMillis = 1_250L),
        )
        assertEquals(emptyList<TapAction>(), resolver.onContactAborted())
    }

    @Test
    fun `a distant second contact does not pause the pending single click`() {
        val resolver = TapSequenceResolver(doubleTapTimeoutMillis = 300L, doubleTapSlop = 32f)

        assertEquals(
            listOf(TapAction.LEFT_CLICK),
            resolver.onTap(x = 100f, y = 100f, eventTimeMillis = 1_000L),
        )
        assertEquals(
            emptyList<TapAction>(),
            resolver.onDown(x = 200f, y = 200f, eventTimeMillis = 1_200L),
        )
    }

    @Test
    fun `tap then hold and move becomes a left button drag until release`() {
        val resolver = TapSequenceResolver(
            doubleTapTimeoutMillis = 300L,
            doubleTapSlop = 32f,
            touchSlop = 8f,
        )

        assertEquals(
            listOf(TapAction.LEFT_CLICK),
            resolver.onTap(x = 100f, y = 100f, eventTimeMillis = 1_000L),
        )
        assertEquals(
            emptyList<TapAction>(),
            resolver.onDown(x = 104f, y = 104f, eventTimeMillis = 1_180L),
        )
        assertEquals(
            listOf(TapAction.LEFT_BUTTON_DOWN),
            resolver.onMove(x = 114f, y = 104f),
        )
        assertEquals(listOf(TapAction.LEFT_BUTTON_UP), resolver.onGestureEnded())
    }

    @Test
    fun `starting multi touch after a completed tap does not replay that left click`() {
        val resolver = TapSequenceResolver(doubleTapTimeoutMillis = 300L, doubleTapSlop = 32f)

        assertEquals(
            listOf(TapAction.LEFT_CLICK),
            resolver.onTap(x = 100f, y = 100f, eventTimeMillis = 1_000L),
        )
        assertEquals(
            emptyList<TapAction>(),
            resolver.onDown(x = 104f, y = 104f, eventTimeMillis = 1_180L),
        )
        assertEquals(
            emptyList<TapAction>(),
            resolver.onMultiTouchStarted(gestureStartTimeMillis = 1_180L, rightClickEligible = true),
        )
    }

    @Test
    fun `two finger slide remains multi touch until the final finger lifts`() {
        val resolver = TapSequenceResolver(doubleTapTimeoutMillis = 300L, doubleTapSlop = 32f)

        assertEquals(
            emptyList<TapAction>(),
            resolver.onMultiTouchStarted(gestureStartTimeMillis = 1_000L, rightClickEligible = true),
        )
        assertTrue(resolver.isMultiTouchActive())
        resolver.onMultiTouchMoved()
        assertTrue(resolver.isMultiTouchActive())
        assertEquals(emptyList<TapAction>(), resolver.onMultiTouchEnded(eventTimeMillis = 1_100L))
        assertFalse(resolver.isMultiTouchActive())
    }

    @Test
    fun `two finger tap becomes one right click`() {
        val resolver = TapSequenceResolver(
            doubleTapTimeoutMillis = 300L,
            doubleTapSlop = 32f,
            touchSlop = 8f,
        )

        assertEquals(
            emptyList<TapAction>(),
            resolver.onMultiTouchStarted(gestureStartTimeMillis = 1_000L, rightClickEligible = true),
        )
        assertEquals(
            listOf(TapAction.RIGHT_CLICK),
            resolver.onMultiTouchEnded(eventTimeMillis = 1_100L),
        )
    }

    @Test
    fun `remaining finger movement after a two finger contact cannot become right click`() {
        val resolver = TapSequenceResolver(
            doubleTapTimeoutMillis = 300L,
            doubleTapSlop = 32f,
            touchSlop = 8f,
        )

        resolver.onMultiTouchStarted(gestureStartTimeMillis = 1_000L, rightClickEligible = true)
        resolver.onMultiTouchMoved()
        assertEquals(emptyList<TapAction>(), resolver.onMultiTouchEnded(eventTimeMillis = 1_100L))
    }

    @Test
    fun `additional pointer cannot reset a moved multi touch gesture`() {
        val resolver = TapSequenceResolver(doubleTapTimeoutMillis = 300L, doubleTapSlop = 32f)

        resolver.onMultiTouchStarted(gestureStartTimeMillis = 1_000L, rightClickEligible = true)
        resolver.onMultiTouchMoved()
        assertEquals(
            emptyList<TapAction>(),
            resolver.onMultiTouchStarted(gestureStartTimeMillis = 1_050L, rightClickEligible = true),
        )
        assertEquals(emptyList<TapAction>(), resolver.onMultiTouchEnded(eventTimeMillis = 1_100L))
    }

    @Test
    fun `movement before the second finger lands disqualifies right click`() {
        val resolver = TapSequenceResolver(doubleTapTimeoutMillis = 300L, doubleTapSlop = 32f)

        resolver.onMultiTouchStarted(gestureStartTimeMillis = 1_000L, rightClickEligible = false)
        assertEquals(emptyList<TapAction>(), resolver.onMultiTouchEnded(eventTimeMillis = 1_100L))
    }

    @Test
    fun `stationary two finger hold longer than tap timeout does not right click`() {
        val resolver = TapSequenceResolver(
            doubleTapTimeoutMillis = 300L,
            doubleTapSlop = 32f,
            tapTimeoutMillis = 250L,
        )

        resolver.onMultiTouchStarted(gestureStartTimeMillis = 1_000L, rightClickEligible = true)
        assertEquals(emptyList<TapAction>(), resolver.onMultiTouchEnded(eventTimeMillis = 1_251L))
    }

    @Test
    fun `cancelling an active tap drag releases the left button`() {
        val resolver = TapSequenceResolver(
            doubleTapTimeoutMillis = 300L,
            doubleTapSlop = 32f,
            touchSlop = 8f,
        )
        resolver.onTap(x = 100f, y = 100f, eventTimeMillis = 1_000L)
        resolver.onDown(x = 104f, y = 104f, eventTimeMillis = 1_180L)
        assertEquals(listOf(TapAction.LEFT_BUTTON_DOWN), resolver.onMove(x = 114f, y = 104f))

        assertEquals(listOf(TapAction.LEFT_BUTTON_UP), resolver.cancel())
        assertEquals(emptyList<TapAction>(), resolver.cancel())
    }
}
