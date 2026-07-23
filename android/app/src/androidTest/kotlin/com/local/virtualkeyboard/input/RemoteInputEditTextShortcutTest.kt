package com.local.virtualkeyboard.input

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.virtualkeyboard.protocol.OutgoingCommand
import com.local.virtualkeyboard.protocol.ShortcutKey
import com.local.virtualkeyboard.protocol.ShortcutModifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteInputEditTextShortcutTest {
    @Test
    fun shortcutInputUsesANonPasswordFilterEditorType() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val selection = ShortcutSelection()
            val input = RemoteInputEditText(instrumentation.targetContext).apply {
                configureShortcutInput(
                    ShortcutInputRouter(selection) { true },
                ) {}
            }

            selection.tap(ShortcutModifier.CONTROL)
            input.refreshShortcutInputConnection()

            val activeType = input.inputType
            val activeVariation = activeType and InputType.TYPE_MASK_VARIATION
            assertEquals(InputType.TYPE_TEXT_VARIATION_FILTER, activeVariation)
            assertTrue(activeType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0)
            assertFalse(activeType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
            assertFalse(activeVariation == InputType.TYPE_TEXT_VARIATION_PASSWORD)
            assertFalse(activeVariation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
            assertFalse(activeVariation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)

            val editorInfo = EditorInfo()
            requireNotNull(input.onCreateInputConnection(editorInfo))
            assertEquals(activeType, editorInfo.inputType)

            assertTrue(input.setDeferredMode(true))
            val deferredActiveType = input.inputType
            assertEquals(
                InputType.TYPE_TEXT_VARIATION_FILTER,
                deferredActiveType and InputType.TYPE_MASK_VARIATION,
            )
            assertTrue(deferredActiveType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
            assertFalse(
                deferredActiveType and InputType.TYPE_MASK_VARIATION ==
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            )

            selection.longPress(ShortcutModifier.ALT)
            input.refreshShortcutInputConnection()
            assertEquals(
                InputType.TYPE_TEXT_VARIATION_FILTER,
                input.inputType and InputType.TYPE_MASK_VARIATION,
            )

            selection.reset()
            input.refreshShortcutInputConnection()
            assertEquals(
                InputType.TYPE_TEXT_VARIATION_NORMAL,
                input.inputType and InputType.TYPE_MASK_VARIATION,
            )
            assertTrue(input.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT != 0)
            assertTrue(input.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
            assertTrue(input.setDeferredMode(false))
            assertFalse(input.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)

            val assembledDeferredShortcutType = remoteInputType(
                deferred = true,
                shortcutActive = true,
            )
            assertEquals(
                InputType.TYPE_TEXT_VARIATION_FILTER,
                assembledDeferredShortcutType and InputType.TYPE_MASK_VARIATION,
            )
            assertTrue(assembledDeferredShortcutType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
        }
    }

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
