package com.local.virtualkeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollDetentAccumulatorTest {
    @Test
    fun `movement below one groove does not emit a wheel detent`() {
        val accumulator = ScrollDetentAccumulator(spacingPx = 8f)

        assertEquals(0, accumulator.add(7.9f))
    }

    @Test
    fun `crossed grooves emit detents and retain partial movement`() {
        val accumulator = ScrollDetentAccumulator(spacingPx = 8f)

        assertEquals(1, accumulator.add(10f))
        assertEquals(0, accumulator.add(5f))
        assertEquals(1, accumulator.add(1f))
        assertEquals(-2, accumulator.add(-16f))
    }

    @Test
    fun `reversing direction cancels uncommitted movement`() {
        val accumulator = ScrollDetentAccumulator(spacingPx = 8f)

        assertEquals(0, accumulator.add(6f))
        assertEquals(0, accumulator.add(-6f))
        assertEquals(-1, accumulator.add(-8f))
    }

    @Test
    fun `reset prevents movement leaking into the next gesture`() {
        val accumulator = ScrollDetentAccumulator(spacingPx = 8f)

        assertEquals(0, accumulator.add(7f))
        accumulator.reset()

        assertEquals(0, accumulator.add(1f))
    }
}
