package com.local.virtualkeyboard.input

import com.local.virtualkeyboard.protocol.ButtonAction
import com.local.virtualkeyboard.protocol.GameKey
import com.local.virtualkeyboard.protocol.OutgoingCommand
import com.local.virtualkeyboard.ui.JoystickDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class GameMovementControllerTest {
    @Test
    fun `diagonal movement holds both keys and direction changes release only the removed key`() {
        val emitted = mutableListOf<OutgoingCommand>()
        val controller = GameMovementController(emitted::add)

        controller.update(
            GameControlSource.JOYSTICK,
            setOf(JoystickDirection.UP, JoystickDirection.RIGHT),
        )
        controller.update(GameControlSource.JOYSTICK, setOf(JoystickDirection.RIGHT))

        assertEquals(
            listOf(
                OutgoingCommand.KeyState(GameKey.W, ButtonAction.DOWN),
                OutgoingCommand.KeyState(GameKey.D, ButtonAction.DOWN),
                OutgoingCommand.KeyState(GameKey.W, ButtonAction.UP),
            ),
            emitted,
        )
    }

    @Test
    fun `overlapping controls keep a shared movement key held until every source releases it`() {
        val emitted = mutableListOf<OutgoingCommand>()
        val controller = GameMovementController(emitted::add)

        controller.update(GameControlSource.JOYSTICK, setOf(JoystickDirection.UP))
        controller.update(GameControlSource.UP_BUTTON, setOf(JoystickDirection.UP))
        controller.update(GameControlSource.JOYSTICK, emptySet())
        controller.update(GameControlSource.UP_BUTTON, emptySet())

        assertEquals(
            listOf(
                OutgoingCommand.KeyState(GameKey.W, ButtonAction.DOWN),
                OutgoingCommand.KeyState(GameKey.W, ButtonAction.UP),
            ),
            emitted,
        )
    }

    @Test
    fun `release all raises every held key once and is idempotent`() {
        val emitted = mutableListOf<OutgoingCommand>()
        val controller = GameMovementController(emitted::add)

        controller.update(
            GameControlSource.JOYSTICK,
            setOf(JoystickDirection.DOWN, JoystickDirection.LEFT),
        )
        controller.releaseAll()
        controller.releaseAll()

        assertEquals(
            listOf(
                OutgoingCommand.KeyState(GameKey.A, ButtonAction.DOWN),
                OutgoingCommand.KeyState(GameKey.S, ButtonAction.DOWN),
                OutgoingCommand.KeyState(GameKey.A, ButtonAction.UP),
                OutgoingCommand.KeyState(GameKey.S, ButtonAction.UP),
            ),
            emitted,
        )
    }
}
