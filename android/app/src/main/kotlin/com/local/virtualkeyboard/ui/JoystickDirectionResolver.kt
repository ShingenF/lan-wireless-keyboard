package com.local.virtualkeyboard.ui

import kotlin.math.abs

enum class JoystickDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

internal class JoystickDirectionResolver(
    private val deadZone: Float,
) {
    fun resolve(dx: Float, dy: Float): JoystickDirection? {
        if (dx * dx + dy * dy < deadZone * deadZone) return null
        return if (abs(dx) >= abs(dy)) {
            if (dx < 0f) JoystickDirection.LEFT else JoystickDirection.RIGHT
        } else {
            if (dy < 0f) JoystickDirection.UP else JoystickDirection.DOWN
        }
    }
}
