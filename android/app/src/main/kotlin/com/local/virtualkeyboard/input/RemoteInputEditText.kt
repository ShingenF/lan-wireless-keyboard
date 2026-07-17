package com.local.virtualkeyboard.input

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.local.virtualkeyboard.protocol.OutgoingCommand

class RemoteInputEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : EditText(context, attrs) {
    private var commandSession = InputCommandSession { _, _ -> false }
    private var deferredDraft = ""
    private var pendingDeferredDraft: String? = null
    private val pendingEditFilter = InputFilter { _, _, _, destination: Spanned, start, end ->
        destination.subSequence(start, end)
    }

    fun configure(commandSink: (OutgoingCommand, ((Boolean) -> Unit)?) -> Boolean) {
        commandSession = InputCommandSession(commandSink)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val target = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(target, false) {
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                commandSession.onCompositionChanged(text?.toString().orEmpty())
                return super.setComposingText(text, newCursorPosition)
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                val committedText = text?.toString().orEmpty()
                if (consumeEmptyDeferredControlCommit(committedText)) return true
                commandSession.onCommit(committedText)
                val committed = super.commitText(text, newCursorPosition)
                post { clearCommittedTextIfCompositionEnded() }
                return committed
            }

            override fun finishComposingText(): Boolean {
                val finished = super.finishComposingText()
                commandSession.onCompositionFinished()
                post { clearCommittedTextIfCompositionEnded() }
                return finished
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0 && canRouteEmptyControl(EmptyInputControl.BACKSPACE)) {
                    repeat(beforeLength.coerceAtMost(MAX_REMOTE_BACKSPACES)) {
                        routeEmptyControl(EmptyInputControl.BACKSPACE)
                    }
                    return true
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0 && canRouteEmptyControl(EmptyInputControl.BACKSPACE)) {
                    repeat(beforeLength.coerceAtMost(MAX_REMOTE_BACKSPACES)) {
                        routeEmptyControl(EmptyInputControl.BACKSPACE)
                    }
                    return true
                }
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                val emptyControl = EmptyInputControl.fromKeyCode(event.keyCode)
                if (emptyControl != null && canRouteEmptyControl(emptyControl)) {
                    if (event.action == KeyEvent.ACTION_DOWN) routeEmptyControl(emptyControl)
                    return true
                }
                if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (event.action == KeyEvent.ACTION_DOWN) handleEnter()
                    return true
                }
                return super.sendKeyEvent(event)
            }

            override fun performEditorAction(editorAction: Int): Boolean {
                handleEnter()
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val emptyControl = EmptyInputControl.fromKeyCode(keyCode)
        if (
            commandSession.isDeferredMode &&
            emptyControl != null &&
            canRouteEmptyControl(emptyControl)
        ) {
            routeEmptyControl(emptyControl)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (
            commandSession.isDeferredMode &&
            EmptyInputControl.fromKeyCode(keyCode)?.let(::canRouteEmptyControl) == true
        ) {
            true
        } else {
            super.onKeyUp(keyCode, event)
        }

    fun handleEditorAction(): Boolean {
        if (commandSession.isDeferredMode && !isEmptyWithoutComposition()) return false
        handleEnter()
        return true
    }

    fun setDeferredMode(enabled: Boolean): Boolean {
        if (enabled == commandSession.isDeferredMode) return true
        if (pendingDeferredDraft != null) return false
        if (commandSession.isDeferredMode) deferredDraft = text?.toString().orEmpty()
        text?.let {
            BaseInputConnection.removeComposingSpans(it)
            it.clear()
        }
        commandSession.setDeferredMode(enabled)
        configureInputMode(enabled)
        if (enabled && deferredDraft.isNotEmpty()) {
            setText(deferredDraft)
            setSelection(length())
        }
        restartInput()
        return true
    }

    fun submitDeferredDraft(): Boolean {
        if (pendingDeferredDraft != null) return false
        val draft = text?.toString().orEmpty()
        val submitted = commandSession.submitDeferredDraft(draft) { delivered ->
            completeDeferredSubmission(draft, delivered)
        }
        if (submitted) {
            pendingDeferredDraft = draft
            filters = filters + pendingEditFilter
        }
        return submitted
    }

    fun deferredDraftSnapshot(): String =
        if (commandSession.isDeferredMode) text?.toString().orEmpty() else deferredDraft

    fun restoreDeferredDraft(draft: String) {
        deferredDraft = draft
        if (!commandSession.isDeferredMode) return
        setText(draft)
        setSelection(length())
    }

    private fun handleEnter() {
        if (commandSession.isDeferredMode) {
            if (canRouteEmptyControl(EmptyInputControl.ENTER)) {
                routeEmptyControl(EmptyInputControl.ENTER)
            } else {
                insertNewline()
            }
            return
        }
        commandSession.onCompositionFinished()
        commandSession.onEnter()
        post { clearCommittedTextIfCompositionEnded() }
    }

    private fun configureInputMode(deferred: Boolean) {
        setSingleLine(!deferred)
        minLines = 1
        maxLines = 1
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        inputType =
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
                if (deferred) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            if (deferred) EditorInfo.IME_FLAG_NO_ENTER_ACTION else 0
        setHorizontallyScrolling(!deferred)
        isVerticalScrollBarEnabled = deferred
    }

    private fun insertNewline() {
        val editable = text ?: return
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)
        editable.replace(minOf(start, end), maxOf(start, end), "\n")
    }

    private fun restartInput() {
        post {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .restartInput(this)
        }
    }

    private fun consumeEmptyDeferredControlCommit(text: String): Boolean {
        val emptyControl = EmptyInputControl.fromCommittedText(text) ?: return false
        if (!canRouteEmptyControl(emptyControl)) return false
        routeEmptyControl(emptyControl)
        return true
    }

    private fun canRouteEmptyControl(control: EmptyInputControl): Boolean =
        isEmptyWithoutComposition() &&
            (commandSession.isDeferredMode || control == EmptyInputControl.BACKSPACE)

    private fun routeEmptyControl(control: EmptyInputControl) {
        when (control) {
            EmptyInputControl.BACKSPACE -> commandSession.onEmptyFieldBackspace()
            EmptyInputControl.ENTER -> commandSession.onEnter()
            EmptyInputControl.SPACE -> commandSession.onSpace()
        }
    }

    private fun completeDeferredSubmission(submittedDraft: String, delivered: Boolean) {
        if (pendingDeferredDraft != submittedDraft) return
        pendingDeferredDraft = null
        filters = filters.filterNot { it === pendingEditFilter }.toTypedArray()
        if (!delivered) return

        if (commandSession.isDeferredMode) {
            val editable = text ?: return
            val remaining = remainingAfterAcknowledgement(editable.toString(), submittedDraft) ?: return
            BaseInputConnection.removeComposingSpans(editable)
            editable.replace(0, editable.length, remaining)
            setSelection(editable.length)
            restartInput()
        } else {
            remainingAfterAcknowledgement(deferredDraft, submittedDraft)?.let { deferredDraft = it }
        }
    }

    private fun remainingAfterAcknowledgement(current: String, submitted: String): String? = when {
        current == submitted -> ""
        current.startsWith(submitted) -> current.removePrefix(submitted)
        else -> null
    }

    private fun isEmptyWithoutComposition(): Boolean {
        val editable = text ?: return true
        return editable.isEmpty() && BaseInputConnection.getComposingSpanStart(editable) < 0
    }

    private fun clearCommittedTextIfCompositionEnded() {
        if (commandSession.isDeferredMode) return
        val editable = text ?: return
        if (BaseInputConnection.getComposingSpanStart(editable) < 0) editable.clear()
    }

    override fun onDetachedFromWindow() {
        commandSession.reset()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MAX_REMOTE_BACKSPACES = 32
    }

    private enum class EmptyInputControl {
        BACKSPACE,
        ENTER,
        SPACE;

        companion object {
            fun fromKeyCode(keyCode: Int): EmptyInputControl? = when (keyCode) {
                KeyEvent.KEYCODE_DEL -> BACKSPACE
                KeyEvent.KEYCODE_ENTER -> ENTER
                KeyEvent.KEYCODE_SPACE -> SPACE
                else -> null
            }

            fun fromCommittedText(text: String): EmptyInputControl? = when (text) {
                " " -> SPACE
                "\r", "\n", "\r\n" -> ENTER
                else -> null
            }
        }
    }
}
