package com.local.virtualkeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PointerDeltaAccumulatorTest {
    @Test
    fun `fractional movement stays local until it forms an integer protocol delta`() {
        val accumulator = PointerDeltaAccumulator()

        accumulator.add(0.4f, -0.4f)
        assertEquals(0 to 0, accumulator.drain(1f))

        accumulator.add(0.7f, -0.7f)
        assertEquals(1 to -1, accumulator.drain(1f))
    }

    @Test
    fun `sensitivity is applied without losing accumulated distance`() {
        val accumulator = PointerDeltaAccumulator()

        accumulator.add(1f, 0f)
        val first = accumulator.drain(1.5f)
        accumulator.add(1f, 0f)
        val second = accumulator.drain(1.5f)

        assertEquals(3, first.first + second.first)
    }

    @Test
    fun `reset discards movement from an earlier gesture`() {
        val accumulator = PointerDeltaAccumulator()
        accumulator.add(0.9f, 0.9f)

        accumulator.reset()

        assertEquals(0 to 0, accumulator.drain(1f))
    }
}
