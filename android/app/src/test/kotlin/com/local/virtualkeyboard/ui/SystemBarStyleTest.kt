package com.local.virtualkeyboard.ui

import com.local.virtualkeyboard.settings.HexColor
import com.local.virtualkeyboard.settings.ThemeColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarStyleTest {
    @Test
    fun `status and navigation bars follow their adjacent theme surfaces independently`() {
        val colors = ThemeColors(
            background = requireNotNull(HexColor.parse("#101012")),
            inputBackground = requireNotNull(HexColor.parse("#FEFEFE")),
        )

        val style = systemBarStyleFor(colors)

        assertEquals(0xFF101012.toInt(), style.statusBarColor)
        assertEquals(0xFFFEFEFE.toInt(), style.navigationBarColor)
        assertFalse(style.useDarkStatusBarIcons)
        assertTrue(style.useDarkNavigationBarIcons)
    }

    @Test
    fun `dark input backgrounds request light navigation icons`() {
        val style = systemBarStyleFor(ThemeColors.darkDefaults())

        assertEquals(0xFF242428.toInt(), style.navigationBarColor)
        assertFalse(style.useDarkNavigationBarIcons)
    }

    @Test
    fun `midtone custom colors choose the higher contrast dark icons`() {
        val colors = ThemeColors(
            background = requireNotNull(HexColor.parse("#AAAAAA")),
            inputBackground = requireNotNull(HexColor.parse("#AAAAAA")),
        )

        val style = systemBarStyleFor(colors)

        assertTrue(style.useDarkStatusBarIcons)
        assertTrue(style.useDarkNavigationBarIcons)
    }

    @Test
    fun `navigation backdrop remains anchored below an animated ime inset`() {
        assertEquals(60, navigationBarBackdropTranslation(systemBarBottom = 60, imeBottom = 0))
        assertEquals(720, navigationBarBackdropTranslation(systemBarBottom = 60, imeBottom = 720))
    }
}
