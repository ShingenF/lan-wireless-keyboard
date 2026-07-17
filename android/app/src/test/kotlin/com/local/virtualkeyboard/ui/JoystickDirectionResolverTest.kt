package com.local.virtualkeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JoystickDirectionResolverTest {
    @Test
    fun `dead zone is neutral and the dominant dragged axis selects one direction`() {
        val resolver = JoystickDirectionResolver(deadZone = 12f)

        assertNull(resolver.resolve(dx = 6f, dy = -4f))
        assertEquals(JoystickDirection.RIGHT, resolver.resolve(dx = 24f, dy = 8f))
        assertEquals(JoystickDirection.UP, resolver.resolve(dx = -5f, dy = -22f))
    }
}
