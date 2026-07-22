package com.local.virtualkeyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionSettingsTest {
    @Test
    fun `default settings do not embed a development machine address`() {
        assertEquals("", ConnectionSettings().host)
        assertEquals(39421, ConnectionSettings().port)
        assertEquals(true, ConnectionSettings().scrollStripEnabled)
        assertEquals(8f, ConnectionSettings().scrollDetentSpacingDp)
        assertEquals(1f, ConnectionSettings().scrollInertiaScale)
        assertEquals(ScrollHapticProfile.STANDARD, ConnectionSettings().scrollHapticProfile)
        assertEquals(LanguageToggleShortcut.SHIFT, ConnectionSettings().languageToggleShortcut)
        assertEquals(InputMethodShortcut.WINDOWS_SPACE, ConnectionSettings().inputMethodShortcut)
        assertEquals(ThemeMode.FOLLOW_SYSTEM, ConnectionSettings().themeSettings.mode)
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

    @Test
    fun `unknown scroll haptic profile falls back to the standard feel`() {
        assertEquals(ScrollHapticProfile.STANDARD, ScrollHapticProfile.fromStoredValue("future-profile"))
        assertEquals(ScrollHapticProfile.LIGHT, ScrollHapticProfile.fromStoredValue("light"))
    }
}
