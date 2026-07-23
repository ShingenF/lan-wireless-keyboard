package com.local.virtualkeyboard.input

import com.local.virtualkeyboard.protocol.OutgoingCommand
import com.local.virtualkeyboard.protocol.ShortcutKey
import com.local.virtualkeyboard.protocol.ShortcutModifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutInputRouterTest {
    @Test
    fun `printable input sends a chord and clears armed modifiers after queueing`() {
        val selection = ShortcutSelection().apply {
            tap(ShortcutModifier.CONTROL)
            tap(ShortcutModifier.SHIFT)
        }
        val commands = mutableListOf<OutgoingCommand>()
        val router = ShortcutInputRouter(selection) {
            commands += it
            true
        }

        assertEquals(ShortcutInputResult.HANDLED, router.handleText("c"))
        assertEquals(
            listOf(
                OutgoingCommand.ShortcutChord(
                    listOf(ShortcutModifier.SHIFT, ShortcutModifier.CONTROL),
                    ShortcutKey.Character('c'),
                ),
            ),
            commands,
        )
        assertTrue(selection.activeModifiers().isEmpty())
    }

    @Test
    fun `failed queueing retains armed modifiers for retry`() {
        val selection = ShortcutSelection().apply { tap(ShortcutModifier.ALT) }
        val router = ShortcutInputRouter(selection) { false }

        assertEquals(ShortcutInputResult.HANDLED, router.handleKey(ShortcutKey.Space))
        assertEquals(listOf(ShortcutModifier.ALT), selection.activeModifiers())
    }

    @Test
    fun `latched modifiers persist across shortcut sends`() {
        val selection = ShortcutSelection().apply { longPress(ShortcutModifier.META) }
        val commands = mutableListOf<OutgoingCommand>()
        val router = ShortcutInputRouter(selection) {
            commands += it
            true
        }

        router.handleKey(ShortcutKey.Enter)
        router.handleKey(ShortcutKey.Backspace)

        assertEquals(2, commands.size)
        assertEquals(ShortcutModifierState.LATCHED, selection.stateOf(ShortcutModifier.META))
    }

    @Test
    fun `latched shortcut send does not report an unchanged selection`() {
        val selection = ShortcutSelection().apply { longPress(ShortcutModifier.META) }
        var selectionChanges = 0
        val router = ShortcutInputRouter(
            selection = selection,
            onSelectionChanged = { selectionChanges++ },
            emit = { true },
        )

        router.handleKey(ShortcutKey.Enter)

        assertEquals(0, selectionChanges)
    }

    @Test
    fun `unsupported text is rejected without sending or clearing selection`() {
        val selection = ShortcutSelection().apply { tap(ShortcutModifier.CONTROL) }
        val commands = mutableListOf<OutgoingCommand>()
        val router = ShortcutInputRouter(selection) {
            commands += it
            true
        }

        assertEquals(ShortcutInputResult.INVALID, router.handleText("你好"))
        assertEquals(ShortcutInputResult.INVALID, router.handleText("😀"))
        assertTrue(commands.isEmpty())
        assertEquals(listOf(ShortcutModifier.CONTROL), selection.activeModifiers())
    }

    @Test
    fun `space newline and backspace route as special shortcut keys`() {
        val selection = ShortcutSelection().apply { longPress(ShortcutModifier.CONTROL) }
        val commands = mutableListOf<OutgoingCommand>()
        val router = ShortcutInputRouter(selection) {
            commands += it
            true
        }

        router.handleText(" ")
        router.handleText("\n")
        router.handleKey(ShortcutKey.Backspace)

        assertEquals(
            listOf(ShortcutKey.Space, ShortcutKey.Enter, ShortcutKey.Backspace),
            commands.map { (it as OutgoingCommand.ShortcutChord).key },
        )
    }

    @Test
    fun `composing ASCII waits for finalization and multi-character composition is rejected`() {
        val selection = ShortcutSelection().apply { longPress(ShortcutModifier.CONTROL) }
        val commands = mutableListOf<OutgoingCommand>()
        val coordinator = ShortcutCompositionCoordinator(
            ShortcutInputRouter(selection) {
                commands += it
                true
            },
        )

        assertEquals(ShortcutInputResult.HANDLED, coordinator.setComposingText("n"))
        assertTrue(commands.isEmpty())
        assertEquals(ShortcutInputResult.INVALID, coordinator.setComposingText("ni"))
        assertTrue(commands.isEmpty())
        assertEquals(ShortcutModifierState.LATCHED, selection.stateOf(ShortcutModifier.CONTROL))
    }

    @Test
    fun `finished composition sends once and suppresses its delayed commit on the same connection`() {
        val selection = ShortcutSelection().apply { tap(ShortcutModifier.CONTROL) }
        val commands = mutableListOf<OutgoingCommand>()
        val coordinator = ShortcutCompositionCoordinator(
            ShortcutInputRouter(selection) {
                commands += it
                true
            },
        )

        coordinator.setComposingText("c")
        assertEquals(ShortcutInputResult.HANDLED, coordinator.finishComposingText())
        assertEquals(ShortcutInputResult.HANDLED, coordinator.commitText("c"))

        assertEquals(1, commands.size)
        assertTrue(selection.activeModifiers().isEmpty())
    }

    @Test
    fun `commit suppression is scoped to a single input connection`() {
        val selection = ShortcutSelection().apply { longPress(ShortcutModifier.CONTROL) }
        val commands = mutableListOf<OutgoingCommand>()
        val router = ShortcutInputRouter(selection) {
            commands += it
            true
        }
        val firstConnection = ShortcutCompositionCoordinator(router)

        firstConnection.setComposingText("c")
        firstConnection.finishComposingText()
        ShortcutCompositionCoordinator(router).commitText("c")

        assertEquals(2, commands.size)
    }

    @Test
    fun `duplicate guard survives an input connection replacement`() {
        val selection = ShortcutSelection().apply { longPress(ShortcutModifier.CONTROL) }
        val commands = mutableListOf<OutgoingCommand>()
        val router = ShortcutInputRouter(selection) {
            commands += it
            true
        }
        val guard = ShortcutDuplicateGuard(router)
        guard.record(ShortcutKey.Character('c'))

        val replacementConnection = ShortcutCompositionCoordinator(router, guard)

        assertEquals(ShortcutInputResult.HANDLED, replacementConnection.commitText("c"))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `canceling composition prevents a stale character after a special key`() {
        val selection = ShortcutSelection().apply { longPress(ShortcutModifier.CONTROL) }
        val commands = mutableListOf<OutgoingCommand>()
        val router = ShortcutInputRouter(selection) {
            commands += it
            true
        }
        val coordinator = ShortcutCompositionCoordinator(router)

        coordinator.setComposingText("c")
        router.handleKey(ShortcutKey.Backspace)
        coordinator.cancelPendingComposition()
        coordinator.finishComposingText()

        assertEquals(
            listOf(ShortcutKey.Backspace),
            commands.map { (it as OutgoingCommand.ShortcutChord).key },
        )
    }

    @Test
    fun `invalid shortcut notice is rate limited`() {
        val throttle = ShortcutNoticeThrottle(intervalMillis = 1_500L)

        assertTrue(throttle.shouldNotify(10_000L))
        assertFalse(throttle.shouldNotify(11_499L))
        assertTrue(throttle.shouldNotify(11_500L))
    }
}
