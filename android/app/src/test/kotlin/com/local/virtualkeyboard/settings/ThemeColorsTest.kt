package com.local.virtualkeyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeColorsTest {
    @Test
    fun `new installations use the public neutral palette`() {
        val defaults = ThemeColors()

        assertEquals("#F2F2F2", defaults.background.canonical)
        assertEquals("#B4B4B4", defaults.icon.canonical)
        assertEquals("#2F2F32", defaults.primaryText.canonical)
        assertEquals("#8E8E93", defaults.secondaryText.canonical)
    }

    @Test
    fun `pasted six digit HEX values are normalized for storage`() {
        assertEquals("#F4F85A", HexColor.parse("  f4f85a ")?.canonical)
        assertEquals("#112233", HexColor.parse("#112233")?.canonical)
        assertEquals(0xFFF4F85A.toInt(), HexColor.parse("F4F85A")?.argb)
    }

    @Test
    fun `invalid or alpha HEX values are rejected`() {
        assertNull(HexColor.parse("#12345"))
        assertNull(HexColor.parse("#GGGGGG"))
        assertNull(HexColor.parse("#11223344"))
        assertNull(HexColor.parse("１２３４５６"))
    }
}
