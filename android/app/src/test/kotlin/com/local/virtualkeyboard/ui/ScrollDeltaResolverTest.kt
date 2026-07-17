package com.local.virtualkeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollDeltaResolverTest {
    @Test
    fun `finger movement follows natural touchpad scroll direction`() {
        assertEquals(
            200,
            ScrollDeltaResolver.resolve(previousY = 100f, currentY = 150f, multiplier = 4f),
        )
        assertEquals(
            -200,
            ScrollDeltaResolver.resolve(previousY = 150f, currentY = 100f, multiplier = 4f),
        )
    }
}
