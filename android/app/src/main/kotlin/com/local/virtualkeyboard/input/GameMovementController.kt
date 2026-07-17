package com.local.virtualkeyboard.input

import com.local.virtualkeyboard.protocol.ButtonAction
import com.local.virtualkeyboard.protocol.GameKey
import com.local.virtualkeyboard.protocol.OutgoingCommand
import com.local.virtualkeyboard.ui.JoystickDirection

enum class GameControlSource {
    UP_BUTTON,
    DOWN_BUTTON,
    LEFT_BUTTON,
    RIGHT_BUTTON,
    JOYSTICK,
}

/** Merges game-control sources and emits only the key-state transitions between them. */
class GameMovementController(
    private val emit: (OutgoingCommand) -> Unit,
) {
    private val directionsBySource = mutableMapOf<GameControlSource, Set<JoystickDirection>>()
    private var heldKeys = emptySet<GameKey>()

    fun update(source: GameControlSource, directions: Set<JoystickDirection>) {
        if (directions.isEmpty()) {
            directionsBySource.remove(source)
        } else {
            directionsBySource[source] = directions
        }

        val updatedKeys = directionsBySource.values.flatten().mapTo(linkedSetOf(), ::toGameKey)
        GameKey.entries.filter { it in heldKeys && it !in updatedKeys }.forEach { key ->
            emit(OutgoingCommand.KeyState(key, ButtonAction.UP))
        }
        GameKey.entries.filter { it in updatedKeys && it !in heldKeys }.forEach { key ->
            emit(OutgoingCommand.KeyState(key, ButtonAction.DOWN))
        }
        heldKeys = updatedKeys
    }

    fun releaseAll() {
        directionsBySource.clear()
        GameKey.entries.filter(heldKeys::contains).forEach { key ->
            emit(OutgoingCommand.KeyState(key, ButtonAction.UP))
        }
        heldKeys = emptySet()
    }

    private fun toGameKey(direction: JoystickDirection): GameKey = when (direction) {
        JoystickDirection.UP -> GameKey.W
        JoystickDirection.DOWN -> GameKey.S
        JoystickDirection.LEFT -> GameKey.A
        JoystickDirection.RIGHT -> GameKey.D
    }
}
