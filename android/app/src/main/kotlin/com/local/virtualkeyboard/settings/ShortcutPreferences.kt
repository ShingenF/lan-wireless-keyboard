package com.local.virtualkeyboard.settings

import com.local.virtualkeyboard.protocol.SystemShortcut

enum class LanguageToggleShortcut(val command: SystemShortcut) {
    SHIFT(SystemShortcut.SHIFT),
    CONTROL_SPACE(SystemShortcut.CONTROL_SPACE),
    CAPS_LOCK(SystemShortcut.CAPS_LOCK),
    ;

    companion object {
        fun fromWireNameOrDefault(value: String?): LanguageToggleShortcut =
            entries.firstOrNull { it.command.wireName == value } ?: SHIFT
    }
}

enum class InputMethodShortcut(val command: SystemShortcut) {
    WINDOWS_SPACE(SystemShortcut.WINDOWS_SPACE),
    ALT_SHIFT(SystemShortcut.ALT_SHIFT),
    CONTROL_SHIFT(SystemShortcut.CONTROL_SHIFT),
    CONTROL_SPACE(SystemShortcut.CONTROL_SPACE),
    ;

    companion object {
        fun fromWireNameOrDefault(value: String?): InputMethodShortcut =
            entries.firstOrNull { it.command.wireName == value } ?: WINDOWS_SPACE
    }
}
