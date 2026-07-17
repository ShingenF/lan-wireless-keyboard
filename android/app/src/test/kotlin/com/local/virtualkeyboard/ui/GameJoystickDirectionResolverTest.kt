package com.local.virtualkeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GameJoystickDirectionResolverTest {
    @Test
    fun `dead zone is neutral and diagonal drag resolves both movement axes`() {
        val resolver = GameJoystickDirectionResolver(deadZone = 12f)

        assertEquals(emptySet<JoystickDirection>(), resolver.resolve(dx = 6f, dy = -4f))
        assertEquals(setOf(JoystickDirection.RIGHT), resolver.resolve(dx = 28f, dy = 5f))
        assertEquals(
            setOf(JoystickDirection.UP, JoystickDirection.RIGHT),
            resolver.resolve(dx = 22f, dy = -24f),
        )
        assertEquals(
            setOf(JoystickDirection.DOWN, JoystickDirection.LEFT),
            resolver.resolve(dx = -20f, dy = 25f),
        )
    }
}
