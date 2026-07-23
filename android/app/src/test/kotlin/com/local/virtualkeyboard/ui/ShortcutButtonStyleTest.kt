package com.local.virtualkeyboard.ui

import com.local.virtualkeyboard.input.ShortcutModifierState
import com.local.virtualkeyboard.settings.HexColor
import com.local.virtualkeyboard.settings.ThemeColors
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutButtonStyleTest {
    @Test
    fun `off shortcut uses theme text and outline at weight seven hundred`() {
        val colors = ThemeColors()

        val style = shortcutButtonStyle(
            state = ShortcutModifierState.OFF,
            colors = colors,
            isDarkSemanticTheme = false,
        )

        assertEquals(colors.inputBackgroundArgb, style.fill)
        assertEquals(colors.iconArgb, style.stroke)
        assertEquals(colors.primaryTextArgb, style.textColor)
        assertEquals(700, style.fontWeight)
    }

    @Test
    fun `light armed shortcut uses white text and a heavier weight`() {
        val style = shortcutButtonStyle(
            state = ShortcutModifierState.ARMED,
            colors = ThemeColors(),
            isDarkSemanticTheme = false,
        )

        assertEquals(0xFFFFFFFF.toInt(), style.textColor)
        assertEquals(800, style.fontWeight)
    }

    @Test
    fun `light armed shortcut stays white for a user supplied pale accent`() {
        val colors = ThemeColors(accent = requireNotNull(HexColor.parse("#F5E9A6")))

        val style = shortcutButtonStyle(
            state = ShortcutModifierState.ARMED,
            colors = colors,
            isDarkSemanticTheme = false,
        )

        assertEquals(0xFFFFFFFF.toInt(), style.textColor)
    }

    @Test
    fun `dark armed shortcut chooses contrast from its accent`() {
        val style = shortcutButtonStyle(
            state = ShortcutModifierState.ARMED,
            colors = ThemeColors.darkDefaults(),
            isDarkSemanticTheme = true,
        )

        assertEquals(0xFF000000.toInt(), style.textColor)
        assertEquals(800, style.fontWeight)
    }

    @Test
    fun `latched shortcut chooses contrast from primary text in either semantic theme`() {
        val lightStyle = shortcutButtonStyle(
            state = ShortcutModifierState.LATCHED,
            colors = ThemeColors(),
            isDarkSemanticTheme = false,
        )
        val darkStyle = shortcutButtonStyle(
            state = ShortcutModifierState.LATCHED,
            colors = ThemeColors.darkDefaults(),
            isDarkSemanticTheme = true,
        )

        assertEquals(0xFFFFFFFF.toInt(), lightStyle.textColor)
        assertEquals(0xFF000000.toInt(), darkStyle.textColor)
        assertEquals(800, lightStyle.fontWeight)
        assertEquals(800, darkStyle.fontWeight)
    }
}
