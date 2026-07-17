package com.local.virtualkeyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionSettingsTest {
    @Test
    fun `default settings do not embed a development machine address`() {
        assertEquals("", ConnectionSettings().host)
        assertEquals(39421, ConnectionSettings().port)
        assertEquals(true, ConnectionSettings().scrollStripEnabled)
        assertEquals(LanguageToggleShortcut.SHIFT, ConnectionSettings().languageToggleShortcut)
        assertEquals(InputMethodShortcut.WINDOWS_SPACE, ConnectionSettings().inputMethodShortcut)
    }

    @Test
    fun `unknown persisted shortcut values fall back without allowing arbitrary chords`() {
        assertEquals(
            LanguageToggleShortcut.SHIFT,
            LanguageToggleShortcut.fromWireNameOrDefault("customAltF4"),
        )
        assertEquals(
            LanguageToggleShortcut.CONTROL_SPACE,
            LanguageToggleShortcut.fromWireNameOrDefault("controlSpace"),
        )
    }

    @Test
    fun `shortcut preferences reject values belonging only to the other button`() {
        assertEquals(
            LanguageToggleShortcut.SHIFT,
            LanguageToggleShortcut.fromWireNameOrDefault("windowsSpace"),
        )
        assertEquals(
            InputMethodShortcut.WINDOWS_SPACE,
            InputMethodShortcut.fromWireNameOrDefault("shift"),
        )
    }
}
