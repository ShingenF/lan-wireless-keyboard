package com.local.virtualkeyboard.input

import com.local.virtualkeyboard.protocol.OutgoingCommand
import com.local.virtualkeyboard.protocol.RemoteKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ImeCommandReducerTest {
    @Test
    fun `sogou chinese candidate is sent only when committed`() {
        val emitted = mutableListOf<OutgoingCommand>()
        val reducer = ImeCommandReducer(emitted::add)

        reducer.onCommit("你好")

        assertEquals(listOf(OutgoingCommand.TextCommit("你好")), emitted)
    }

    @Test
    fun `sogou english composition is mirrored and its committed space is appended`() {
        val emitted = mutableListOf<OutgoingCommand>()
        val reducer = ImeCommandReducer(emitted::add)

        reducer.onCompositionChanged("Nihao")
        reducer.onCompositionFinished()
        reducer.onCommit(" ")

        assertEquals(
            listOf(
                OutgoingCommand.ReplaceTail(deleteCodePoints = 0, text = "Nihao"),
                OutgoingCommand.TextCommit(" "),
            ),
            emitted,
        )
    }

    @Test
    fun `english correction replaces only the changed composing tail`() {
        val emitted = mutableListOf<OutgoingCommand>()
        val reducer = ImeCommandReducer(emitted::add)

        reducer.onCompositionChanged("hellp")
        reducer.onCommit("hello")

        assertEquals(
            listOf(
                OutgoingCommand.ReplaceTail(deleteCodePoints = 0, text = "hellp"),
                OutgoingCommand.ReplaceTail(deleteCodePoints = 1, text = "o"),
            ),
            emitted,
        )
    }

    @Test
    fun `same committed word is not duplicated after composition`() {
        val emitted = mutableListOf<OutgoingCommand>()
        val reducer = ImeCommandReducer(emitted::add)

        reducer.onCompositionChanged("hello")
        reducer.onCommit("hello")

        assertEquals(
            listOf(OutgoingCommand.ReplaceTail(deleteCodePoints = 0, text = "hello")),
            emitted,
        )
    }

    @Test
    fun `paste and emoji are sent as one committed text command`() {
        val emitted = mutableListOf<OutgoingCommand>()
        val reducer = ImeCommandReducer(emitted::add)

        reducer.onCommit("粘贴🙂")

        assertEquals(
            listOf(OutgoingCommand.TextCommit("粘贴🙂")),
            emitted,
        )
    }

    @Test
    fun `empty commit is ignored`() {
        val emitted = mutableListOf<OutgoingCommand>()
        val reducer = ImeCommandReducer(emitted::add)

        reducer.onCommit("")

        assertEquals(emptyList<OutgoingCommand>(), emitted)
    }

    @Test
    fun `backspace on an empty field is sent as a remote key press`() {
        val emitted = mutableListOf<OutgoingCommand>()
        val reducer = ImeCommandReducer(emitted::add)

        reducer.onEmptyFieldBackspace()

        assertEquals(listOf(OutgoingCommand.KeyPress(RemoteKey.BACKSPACE)), emitted)
    }
}
