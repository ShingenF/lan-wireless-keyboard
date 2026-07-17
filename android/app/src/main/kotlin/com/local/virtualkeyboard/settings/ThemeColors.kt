package com.local.virtualkeyboard.settings

@JvmInline
value class HexColor private constructor(val canonical: String) {
    val argb: Int
        get() = (0xFF000000L or canonical.removePrefix("#").toLong(16)).toInt()

    companion object {
        val DEFAULT_BACKGROUND = HexColor("#F2F2F2")
        val DEFAULT_ICON = HexColor("#B4B4B4")
        val DEFAULT_PRIMARY_TEXT = HexColor("#2F2F32")
        val DEFAULT_SECONDARY_TEXT = HexColor("#8E8E93")
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
    val primaryText: HexColor = HexColor.DEFAULT_PRIMARY_TEXT,
    val secondaryText: HexColor = HexColor.DEFAULT_SECONDARY_TEXT,
) {
    val backgroundArgb: Int get() = background.argb
    val iconArgb: Int get() = icon.argb
    val primaryTextArgb: Int get() = primaryText.argb
    val secondaryTextArgb: Int get() = secondaryText.argb
}
