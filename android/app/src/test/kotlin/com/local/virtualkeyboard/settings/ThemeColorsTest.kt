package com.local.virtualkeyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ThemeColorsTest {
    @Test
    fun `new installations use the public neutral light palette`() {
        val defaults = ThemePalettes().light

        assertEquals("#F2F2F2", defaults.background.canonical)
        assertEquals("#B4B4B4", defaults.icon.canonical)
        assertEquals("#0071E3", defaults.accent.canonical)
        assertEquals("#2F2F32", defaults.primaryText.canonical)
        assertEquals("#8E8E93", defaults.secondaryText.canonical)
        assertEquals("#FFFFFF", defaults.inputBackground.canonical)
        assertEquals("#FFFFFF", defaults.touchpadBackground.canonical)
    }

    @Test
    fun `new installations include a dark palette`() {
        val defaults = ThemePalettes().dark

        assertEquals("#121214", defaults.background.canonical)
        assertEquals("#B8B8C0", defaults.icon.canonical)
        assertEquals("#0071E3", defaults.accent.canonical)
        assertEquals("#F4F4F5", defaults.primaryText.canonical)
        assertEquals("#A1A1AA", defaults.secondaryText.canonical)
        assertEquals("#242428", defaults.inputBackground.canonical)
        assertEquals("#1C1C20", defaults.touchpadBackground.canonical)
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

    @Test
    fun `theme follows Android by default and supports forced light or dark`() {
        val defaults = ThemeSettings()

        assertEquals(defaults.palettes.light, defaults.resolve(systemDark = false))
        assertEquals(defaults.palettes.dark, defaults.resolve(systemDark = true))
        assertEquals(defaults.palettes.light, defaults.copy(mode = ThemeMode.LIGHT).resolve(true))
        assertEquals(defaults.palettes.dark, defaults.copy(mode = ThemeMode.DARK).resolve(false))
    }

    @Test
    fun `theme framework round trips both palettes`() {
        val framework = ThemeFramework.format(ThemePalettes())

        assertEquals(
            """[light]
                |background=#F2F2F2
                |icon=#B4B4B4
                |accent=#0071E3
                |primary_text=#2F2F32
                |secondary_text=#8E8E93
                |input_background=#FFFFFF
                |touchpad_background=#FFFFFF
                |
                |[dark]
                |background=#121214
                |icon=#B8B8C0
                |accent=#0071E3
                |primary_text=#F4F4F5
                |secondary_text=#A1A1AA
                |input_background=#242428
                |touchpad_background=#1C1C20""".trimMargin(),
            framework,
        )
        assertEquals(ThemePalettes(), ThemeFramework.parse(framework))
    }

    @Test
    fun `theme framework accepts edited lowercase colors and markdown fences`() {
        val edited = ThemeFramework.format(ThemePalettes())
            .replace("[light]", "```ini\n[light]")
            .replace("#F2F2F2", "f4f85a") + "\n```"

        assertEquals("#F4F85A", ThemeFramework.parse(edited).light.background.canonical)
    }

    @Test
    fun `theme framework rejects a missing accent`() {
        val missingAccent = ThemeFramework.format(ThemePalettes())
            .lineSequence()
            .filterNot { it == "accent=#0071E3" }
            .joinToString("\n")

        assertEquals(
            "[light] 缺少 accent",
            assertThrows(IllegalArgumentException::class.java) {
                ThemeFramework.parse(missingAccent)
            }.message,
        )
    }

    @Test
    fun `theme framework reports missing and unknown fields`() {
        val missing = ThemeFramework.format(ThemePalettes())
            .replace("touchpad_background=#1C1C20", "")
        val unknown = ThemeFramework.format(ThemePalettes())
            .replace("icon=#B4B4B4", "icons=#B4B4B4")

        assertEquals(
            "[dark] 缺少 touchpad_background",
            assertThrows(IllegalArgumentException::class.java) { ThemeFramework.parse(missing) }.message,
        )
        assertEquals(
            "第 3 行包含未知颜色项 icons",
            assertThrows(IllegalArgumentException::class.java) { ThemeFramework.parse(unknown) }.message,
        )
    }
}
