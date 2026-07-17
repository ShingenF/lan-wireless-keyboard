package com.local.virtualkeyboard.ui

import kotlin.math.abs

/** Resolves a virtual movement stick into the one or two held movement directions. */
internal class GameJoystickDirectionResolver(
    private val deadZone: Float,
) {
    fun resolve(dx: Float, dy: Float): Set<JoystickDirection> {
        if (dx * dx + dy * dy < deadZone * deadZone) return emptySet()

        val horizontal = abs(dx)
        val vertical = abs(dy)
        val directions = linkedSetOf<JoystickDirection>()
        if (vertical >= horizontal * DIAGONAL_COMPONENT_RATIO) {
            directions += if (dy < 0f) JoystickDirection.UP else JoystickDirection.DOWN
        }
        if (horizontal >= vertical * DIAGONAL_COMPONENT_RATIO) {
            directions += if (dx < 0f) JoystickDirection.LEFT else JoystickDirection.RIGHT
        }
        return directions
    }

    private companion object {
        // tan(22.5 degrees): divides the stick into eight equal angular sectors.
        const val DIAGONAL_COMPONENT_RATIO = 0.41421357f
    }
}
