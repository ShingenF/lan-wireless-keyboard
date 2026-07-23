package com.local.virtualkeyboard.input

import com.local.virtualkeyboard.protocol.ShortcutModifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutSelectionTest {
    @Test
    fun `tapping a modifier arms it and tapping again clears it`() {
        val selection = ShortcutSelection()

        selection.tap(ShortcutModifier.SHIFT)
        assertEquals(ShortcutModifierState.ARMED, selection.stateOf(ShortcutModifier.SHIFT))
        assertEquals(listOf(ShortcutModifier.SHIFT), selection.activeModifiers())

        selection.tap(ShortcutModifier.SHIFT)
        assertEquals(ShortcutModifierState.OFF, selection.stateOf(ShortcutModifier.SHIFT))
        assertFalse(selection.isLatchMode)
    }

    @Test
    fun `long press converts every armed modifier and the target to latched`() {
        val selection = ShortcutSelection()
        selection.tap(ShortcutModifier.SHIFT)
        selection.tap(ShortcutModifier.ALT)

        assertTrue(selection.longPress(ShortcutModifier.CONTROL))

        assertEquals(ShortcutModifierState.LATCHED, selection.stateOf(ShortcutModifier.SHIFT))
        assertEquals(ShortcutModifierState.LATCHED, selection.stateOf(ShortcutModifier.CONTROL))
        assertEquals(ShortcutModifierState.LATCHED, selection.stateOf(ShortcutModifier.ALT))
        assertEquals(ShortcutModifierState.OFF, selection.stateOf(ShortcutModifier.META))
        assertTrue(selection.isLatchMode)
    }

    @Test
    fun `latch mode applies to later taps and ends after the final latch is cleared`() {
        val selection = ShortcutSelection()
        selection.longPress(ShortcutModifier.SHIFT)

        selection.tap(ShortcutModifier.META)
        assertEquals(ShortcutModifierState.LATCHED, selection.stateOf(ShortcutModifier.META))

        selection.tap(ShortcutModifier.SHIFT)
        assertTrue(selection.isLatchMode)
        selection.tap(ShortcutModifier.META)
        assertFalse(selection.isLatchMode)

        selection.tap(ShortcutModifier.ALT)
        assertEquals(ShortcutModifierState.ARMED, selection.stateOf(ShortcutModifier.ALT))
    }

    @Test
    fun `queued commands clear armed modifiers but preserve latched modifiers`() {
        val selection = ShortcutSelection()
        selection.tap(ShortcutModifier.SHIFT)
        selection.clearArmedAfterQueued()
        assertEquals(ShortcutModifierState.OFF, selection.stateOf(ShortcutModifier.SHIFT))

        selection.longPress(ShortcutModifier.CONTROL)
        selection.clearArmedAfterQueued()
        assertEquals(ShortcutModifierState.LATCHED, selection.stateOf(ShortcutModifier.CONTROL))

        selection.reset()
        assertEquals(emptyList<ShortcutModifier>(), selection.activeModifiers())
        assertFalse(selection.isLatchMode)
    }

    @Test
    fun `long pressing an already latched modifier is ignored`() {
        val selection = ShortcutSelection()
        selection.longPress(ShortcutModifier.SHIFT)

        assertFalse(selection.longPress(ShortcutModifier.SHIFT))
        assertEquals(ShortcutModifierState.LATCHED, selection.stateOf(ShortcutModifier.SHIFT))
    }

    @Test
    fun `configuration snapshot restores armed and latched states`() {
        val restored = ShortcutSelection().apply {
            restore(
                ShortcutSelectionSnapshot(
                    armed = setOf(ShortcutModifier.SHIFT),
                    latched = setOf(ShortcutModifier.CONTROL, ShortcutModifier.META),
                ),
            )
        }

        assertEquals(ShortcutModifierState.ARMED, restored.stateOf(ShortcutModifier.SHIFT))
        assertEquals(ShortcutModifierState.LATCHED, restored.stateOf(ShortcutModifier.CONTROL))
        assertEquals(ShortcutModifierState.LATCHED, restored.stateOf(ShortcutModifier.META))
        assertEquals(ShortcutModifierState.OFF, restored.stateOf(ShortcutModifier.ALT))
    }
}
