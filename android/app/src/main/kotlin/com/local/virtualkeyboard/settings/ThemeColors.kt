package com.local.virtualkeyboard.settings

@JvmInline
value class HexColor private constructor(val canonical: String) {
    val argb: Int
        get() = (0xFF000000L or canonical.removePrefix("#").toLong(16)).toInt()

    companion object {
        val DEFAULT_BACKGROUND = HexColor("#F2F2F2")
        val DEFAULT_ICON = HexColor("#B4B4B4")
        val DEFAULT_ACCENT = HexColor("#3372DE")
        val DEFAULT_PRIMARY_TEXT = HexColor("#2F2F32")
        val DEFAULT_SECONDARY_TEXT = HexColor("#8E8E93")
        val DEFAULT_INPUT_BACKGROUND = HexColor("#FFFFFF")
        val DEFAULT_TOUCHPAD_BACKGROUND = HexColor("#FFFFFF")
        val DEFAULT_DARK_BACKGROUND = HexColor("#121214")
        val DEFAULT_DARK_ICON = HexColor("#B8B8C0")
        val DEFAULT_DARK_PRIMARY_TEXT = HexColor("#F4F4F5")
        val DEFAULT_DARK_SECONDARY_TEXT = HexColor("#A1A1AA")
        val DEFAULT_DARK_INPUT_BACKGROUND = HexColor("#242428")
        val DEFAULT_DARK_TOUCHPAD_BACKGROUND = HexColor("#1C1C20")
        val LEGACY_DEFAULT_SECONDARY_TEXT = HexColor("#5C5C5E")

        fun parse(value: String): HexColor? {
            val digits = value.trim().removePrefix("#")
            if (
                digits.length != 6 ||
                digits.any { it !in '0'..'9' && it !in 'A'..'F' && it !in 'a'..'f' }
            ) {
                return null
            }
            return HexColor("#${digits.uppercase()}")
        }
    }
}

data class ThemeColors(
    val background: HexColor = HexColor.DEFAULT_BACKGROUND,
    val icon: HexColor = HexColor.DEFAULT_ICON,
    val accent: HexColor = HexColor.DEFAULT_ACCENT,
    val primaryText: HexColor = HexColor.DEFAULT_PRIMARY_TEXT,
    val secondaryText: HexColor = HexColor.DEFAULT_SECONDARY_TEXT,
    val inputBackground: HexColor = HexColor.DEFAULT_INPUT_BACKGROUND,
    val touchpadBackground: HexColor = HexColor.DEFAULT_TOUCHPAD_BACKGROUND,
) {
    val backgroundArgb: Int get() = background.argb
    val iconArgb: Int get() = icon.argb
    val accentArgb: Int get() = accent.argb
    val primaryTextArgb: Int get() = primaryText.argb
    val secondaryTextArgb: Int get() = secondaryText.argb
    val inputBackgroundArgb: Int get() = inputBackground.argb
    val touchpadBackgroundArgb: Int get() = touchpadBackground.argb

    companion object {
        fun darkDefaults(): ThemeColors = ThemeColors(
            background = HexColor.DEFAULT_DARK_BACKGROUND,
            icon = HexColor.DEFAULT_DARK_ICON,
            primaryText = HexColor.DEFAULT_DARK_PRIMARY_TEXT,
            secondaryText = HexColor.DEFAULT_DARK_SECONDARY_TEXT,
            inputBackground = HexColor.DEFAULT_DARK_INPUT_BACKGROUND,
            touchpadBackground = HexColor.DEFAULT_DARK_TOUCHPAD_BACKGROUND,
        )
    }
}

data class ThemePalettes(
    val light: ThemeColors = ThemeColors(),
    val dark: ThemeColors = ThemeColors.darkDefaults(),
)

enum class ThemeMode(val storedValue: String) {
    FOLLOW_SYSTEM("follow_system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStoredValue(value: String?): ThemeMode? =
            entries.firstOrNull { it.storedValue == value }
    }
}

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val palettes: ThemePalettes = ThemePalettes(),
) {
    fun resolve(systemDark: Boolean): ThemeColors = when (mode) {
        ThemeMode.FOLLOW_SYSTEM -> if (systemDark) palettes.dark else palettes.light
        ThemeMode.LIGHT -> palettes.light
        ThemeMode.DARK -> palettes.dark
    }
}

object ThemeFramework {
    private enum class ThemeField(
        val key: String,
        val read: (ThemeColors) -> HexColor,
    ) {
        BACKGROUND("background", { it.background }),
        ICON("icon", { it.icon }),
        ACCENT("accent", { it.accent }),
        PRIMARY_TEXT("primary_text", { it.primaryText }),
        SECONDARY_TEXT("secondary_text", { it.secondaryText }),
        INPUT_BACKGROUND("input_background", { it.inputBackground }),
        TOUCHPAD_BACKGROUND("touchpad_background", { it.touchpadBackground });

        companion object {
            private val byKey = entries.associateBy(ThemeField::key)

            fun fromKey(key: String): ThemeField? = byKey[key]
        }
    }

    fun format(palettes: ThemePalettes): String = buildString {
        appendPalette("light", palettes.light)
        append("\n\n")
        appendPalette("dark", palettes.dark)
    }

    fun parse(value: String): ThemePalettes {
        val sections = linkedMapOf<String, MutableMap<ThemeField, HexColor>>()
        var currentSection: String? = null

        value.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("```") || line.startsWith("//")) {
                return@forEachIndexed
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                val section = line.substring(1, line.length - 1).lowercase()
                require(section == "light" || section == "dark") {
                    "第 ${index + 1} 行包含未知主题 [$section]"
                }
                require(section !in sections) { "第 ${index + 1} 行重复了 [$section]" }
                sections[section] = linkedMapOf()
                currentSection = section
                return@forEachIndexed
            }

            val section = currentSection
                ?: throw IllegalArgumentException("第 ${index + 1} 行必须放在 [light] 或 [dark] 下")
            val separator = line.indexOf('=')
            require(separator > 0) { "第 ${index + 1} 行应使用 color_name=#RRGGBB" }
            val key = line.substring(0, separator).trim().lowercase()
            val field = ThemeField.fromKey(key)
                ?: throw IllegalArgumentException("第 ${index + 1} 行包含未知颜色项 $key")
            val sectionColors = sections.getValue(section)
            require(field !in sectionColors) { "第 ${index + 1} 行重复了 $key" }
            val color = HexColor.parse(line.substring(separator + 1))
                ?: throw IllegalArgumentException("第 ${index + 1} 行的 $key 必须是 6 位 HEX")
            sectionColors[field] = color
        }

        return ThemePalettes(
            light = parsePalette("light", sections["light"]),
            dark = parsePalette("dark", sections["dark"]),
        )
    }

    private fun StringBuilder.appendPalette(name: String, colors: ThemeColors) {
        append("[$name]\n")
        ThemeField.entries.forEachIndexed { index, field ->
            append("${field.key}=${field.read(colors).canonical}")
            if (index != ThemeField.entries.lastIndex) append('\n')
        }
    }

    private fun parsePalette(name: String, values: Map<ThemeField, HexColor>?): ThemeColors {
        val paletteValues = requireNotNull(values) { "缺少 [$name]" }
        ThemeField.entries.firstOrNull { it != ThemeField.ACCENT && it !in paletteValues }?.let { missing ->
            throw IllegalArgumentException("[$name] 缺少 ${missing.key}")
        }
        return ThemeColors(
            background = paletteValues.getValue(ThemeField.BACKGROUND),
            icon = paletteValues.getValue(ThemeField.ICON),
            accent = paletteValues[ThemeField.ACCENT] ?: HexColor.DEFAULT_ACCENT,
            primaryText = paletteValues.getValue(ThemeField.PRIMARY_TEXT),
            secondaryText = paletteValues.getValue(ThemeField.SECONDARY_TEXT),
            inputBackground = paletteValues.getValue(ThemeField.INPUT_BACKGROUND),
            touchpadBackground = paletteValues.getValue(ThemeField.TOUCHPAD_BACKGROUND),
        )
    }
}
