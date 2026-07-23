package com.local.virtualkeyboard.input

import com.local.virtualkeyboard.protocol.OutgoingCommand
import com.local.virtualkeyboard.protocol.ShortcutKey

enum class ShortcutInputResult { NOT_HANDLED, HANDLED, INVALID }

class ShortcutInputRouter(
    private val selection: ShortcutSelection,
    private val onSelectionChanged: () -> Unit = {},
    private val emit: (OutgoingCommand) -> Boolean,
) {
    val hasActiveModifiers: Boolean
        get() = selection.activeModifiers().isNotEmpty()

    fun classifyText(text: String): ShortcutInputResult {
        if (!hasActiveModifiers) return ShortcutInputResult.NOT_HANDLED
        if (text.isEmpty()) return ShortcutInputResult.NOT_HANDLED
        return if (keyFromText(text) == null) ShortcutInputResult.INVALID else ShortcutInputResult.HANDLED
    }

    fun handleText(text: String): ShortcutInputResult {
        val classification = classifyText(text)
        if (classification != ShortcutInputResult.HANDLED) return classification
        return handleKey(requireNotNull(keyFromText(text)))
    }

    internal fun keyFromText(text: String): ShortcutKey? = when {
            text == " " -> ShortcutKey.Space
            text == "\n" || text == "\r" || text == "\r\n" -> ShortcutKey.Enter
            text.length == 1 && text[0].code in 0x21..0x7E -> ShortcutKey.Character(text[0])
            else -> null
        }

    fun handleKey(key: ShortcutKey): ShortcutInputResult {
        val modifiers = selection.activeModifiers()
        if (modifiers.isEmpty()) return ShortcutInputResult.NOT_HANDLED
        if (emit(OutgoingCommand.ShortcutChord(modifiers, key))) {
            if (selection.clearArmedAfterQueued()) onSelectionChanged()
        }
        return ShortcutInputResult.HANDLED
    }
}

/**
 * Keeps IME composition local until the IME confirms a complete value. This prevents the first
 * ASCII letter of a Chinese Pinyin composition from being sent as a shortcut chord.
 *
 * Instances are scoped to one InputConnection. A commit suppressed after finishComposingText()
 * therefore cannot swallow an unrelated commit delivered to a replacement connection.
 */
class ShortcutCompositionCoordinator(
    private val router: ShortcutInputRouter,
    private val duplicateGuard: ShortcutDuplicateGuard = ShortcutDuplicateGuard(router),
) {
    private var pendingComposition: String? = null
    private var suppressedCommit: String? = null

    fun setComposingText(text: String): ShortcutInputResult {
        if (duplicateGuard.consumeText(text)) {
            pendingComposition = null
            suppressedCommit = null
            return ShortcutInputResult.HANDLED
        }
        val result = router.classifyText(text)
        pendingComposition = text.takeIf { result == ShortcutInputResult.HANDLED }
        if (result != ShortcutInputResult.HANDLED) suppressedCommit = null
        return result
    }

    fun commitText(text: String): ShortcutInputResult {
        if (duplicateGuard.consumeText(text)) {
            pendingComposition = null
            suppressedCommit = null
            return ShortcutInputResult.HANDLED
        }
        if (suppressedCommit == text) {
            suppressedCommit = null
            pendingComposition = null
            return ShortcutInputResult.HANDLED
        }
        suppressedCommit = null
        pendingComposition = null
        return router.handleText(text)
    }

    fun finishComposingText(): ShortcutInputResult {
        val text = pendingComposition ?: return ShortcutInputResult.NOT_HANDLED
        pendingComposition = null
        return router.handleText(text).also { result ->
            if (result == ShortcutInputResult.HANDLED) suppressedCommit = text
        }
    }

    fun cancelPendingComposition() {
        pendingComposition = null
        suppressedCommit = null
    }
}

class ShortcutDuplicateGuard(
    private val router: ShortcutInputRouter,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val duplicateWindowMillis: Long = DEFAULT_DUPLICATE_WINDOW_MILLIS,
) {
    private data class ExpectedKey(
        val key: ShortcutKey,
        val expiresAtMillis: Long,
    )

    private var expectedKey: ExpectedKey? = null

    init {
        require(duplicateWindowMillis >= 0L) {
            "Duplicate window must not be negative."
        }
    }

    fun record(key: ShortcutKey) {
        expectedKey = ExpectedKey(
            key = key,
            expiresAtMillis = nowMillis() + duplicateWindowMillis,
        )
    }

    fun consumeText(text: String): Boolean {
        val expected = expectedKey ?: return false
        if (text.isEmpty()) return false
        expectedKey = null
        if (nowMillis() > expected.expiresAtMillis) return false
        return router.keyFromText(text) == expected.key
    }

    fun clear() {
        expectedKey = null
    }

    private companion object {
        const val DEFAULT_DUPLICATE_WINDOW_MILLIS = 250L
    }
}

class ShortcutNoticeThrottle(
    private val intervalMillis: Long,
) {
    private var lastNoticeMillis: Long? = null

    fun shouldNotify(nowMillis: Long): Boolean {
        if (lastNoticeMillis?.let { nowMillis - it < intervalMillis } == true) return false
        lastNoticeMillis = nowMillis
        return true
    }
}
