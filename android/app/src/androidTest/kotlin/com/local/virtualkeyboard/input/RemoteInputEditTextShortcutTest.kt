package com.local.virtualkeyboard.input

import android.view.inputmethod.EditorInfo
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.virtualkeyboard.protocol.OutgoingCommand
import com.local.virtualkeyboard.protocol.ShortcutKey
import com.local.virtualkeyboard.protocol.ShortcutModifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteInputEditTextShortcutTest {
    @Test
    fun deferredDraftSurvivesShortcutAndInvalidComposition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val commands = mutableListOf<OutgoingCommand>()
        var invalidNotices = 0

        instrumentation.runOnMainSync {
            val selection = ShortcutSelection().apply { tap(ShortcutModifier.CONTROL) }
            val input = RemoteInputEditText(instrumentation.targetContext).apply {
                configure { _, _ -> true }
                setDeferredMode(true)
                restoreDeferredDraft("existing draft")
                configureShortcutInput(
                    ShortcutInputRouter(selection) {
                        commands += it
                        true
                    },
                ) { invalidNotices++ }
            }
            val connection = requireNotNull(input.onCreateInputConnection(EditorInfo()))

            connection.setComposingText("n", 1)
            connection.setComposingText("ni", 1)
            connection.commitText("你", 1)

            assertTrue(commands.isEmpty())
            assertEquals("existing draft", input.deferredDraftSnapshot())
            assertEquals(1, invalidNotices)
            assertEquals(listOf(ShortcutModifier.CONTROL), selection.activeModifiers())

            connection.commitText("c", 1)
            assertEquals(1, commands.size)
            assertEquals("existing draft", input.deferredDraftSnapshot())
        }
    }

    @Test
    fun composingCommitIsDeduplicatedAndSpecialInputsAreIntercepted() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val commands = mutableListOf<OutgoingCommand>()

        instrumentation.runOnMainSync {
            val selection = ShortcutSelection().apply { longPress(ShortcutModifier.CONTROL) }
            val input = RemoteInputEditText(instrumentation.targetContext).apply {
                configureShortcutInput(
                    ShortcutInputRouter(selection) {
                        commands += it
                        true
                    },
                ) {}
            }
            val connection = requireNotNull(input.onCreateInputConnection(EditorInfo()))

            connection.setComposingText("x", 1)
            connection.finishComposingText()
            connection.commitText("x", 1)

            connection.setComposingText("c", 1)
            connection.deleteSurroundingText(1, 0)
            connection.finishComposingText()

            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C))
            connection.sendKeyEvent(
                KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, 1),
            )
            connection.commitText("c", 1)
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_C))

            connection.performEditorAction(EditorInfo.IME_ACTION_DONE)
            connection.commitText("\n", 1)

            assertEquals(
                listOf(
                    ShortcutKey.Character('x'),
                    ShortcutKey.Backspace,
                    ShortcutKey.Character('c'),
                    ShortcutKey.Enter,
                ),
                commands.map { (it as OutgoingCommand.ShortcutChord).key },
            )
            assertEquals(ShortcutModifierState.LATCHED, selection.stateOf(ShortcutModifier.CONTROL))
        }
    }
}
