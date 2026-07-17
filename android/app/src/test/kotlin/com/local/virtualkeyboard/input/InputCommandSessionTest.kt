package com.local.virtualkeyboard.input

import com.local.virtualkeyboard.protocol.OutgoingCommand
import com.local.virtualkeyboard.protocol.RemoteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputCommandSessionTest {
    @Test
    fun `synchronous input prefers physical keys while deferred input remains unicode text`() {
        val emitted = mutableListOf<OutgoingCommand>()
        val session = InputCommandSession { command, _ ->
            emitted += command
            true
        }

        session.onCompositionChanged("w")
        session.onCommit("w")
        session.onCommit("asd")
        session.setDeferredMode(true)
        session.submitDeferredDraft("wasd") { }

        assertEquals(
            listOf(
                OutgoingCommand.ReplaceTail(
                    deleteCodePoints = 0,
                    text = "w",
                    preferPhysicalKeys = true,
                ),
                OutgoingCommand.TextCommit("asd", preferPhysicalKeys = true),
                OutgoingCommand.TextCommit("wasd"),
            ),
            emitted,
        )
    }

    @Test
    fun `deferred mode keeps IME composition and commits local`() {
        val emitted = mutableListOf<OutgoingCommand>()
        val session = InputCommandSession { command, _ ->
            emitted += command
            true
        }

        session.setDeferredMode(true)
        session.onCompositionChanged("nihao")
        session.onCompositionFinished()
        session.onCommit("你好")

        assertTrue(emitted.isEmpty())
    }

    @Test
    fun `deferred empty field backspace is sent to the computer`() {
        val emitted = mutableListOf<OutgoingCommand>()
        val session = InputCommandSession { command, _ ->
            emitted += command
            true
        }
        session.setDeferredMode(true)

        session.onEmptyFieldBackspace()

        assertEquals(
            listOf(OutgoingCommand.KeyPress(RemoteKey.BACKSPACE)),
            emitted,
        )
    }

    @Test
    fun `deferred empty field enter and space are sent to the computer`() {
        val emitted = mutableListOf<OutgoingCommand>()
        val session = InputCommandSession { command, _ ->
            emitted += command
            true
        }
        session.setDeferredMode(true)

        session.onEnter()
        session.onSpace()

        assertEquals(
            listOf(
                OutgoingCommand.KeyPress(RemoteKey.ENTER),
                OutgoingCommand.TextCommit(" "),
            ),
            emitted,
        )
    }

    @Test
    fun `deferred draft is emitted once only after explicit submit`() {
        val emitted = mutableListOf<OutgoingCommand>()
        val session = InputCommandSession { command, _ ->
            emitted += command
            true
        }
        session.setDeferredMode(true)

        val accepted = session.submitDeferredDraft("第一行\n第二行") { }

        assertTrue(accepted)
        assertEquals(
            listOf(OutgoingCommand.TextCommit("第一行\n第二行")),
            emitted,
        )
    }
}
