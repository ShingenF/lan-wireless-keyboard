package com.local.virtualkeyboard.settings

data class ConnectionSettings(
    val host: String = "",
    val port: Int = 39421,
    val pairingCode: String = "",
    val pinnedFingerprint: String = "",
    val pointerSensitivity: Float = 1.0f,
    val pointerAcceleration: Float = PointerAcceleration.DEFAULT_GAIN,
    val scrollStripEnabled: Boolean = true,
    val languageToggleShortcut: LanguageToggleShortcut = LanguageToggleShortcut.SHIFT,
    val inputMethodShortcut: InputMethodShortcut = InputMethodShortcut.WINDOWS_SPACE,
    val themeColors: ThemeColors = ThemeColors(),
)

object PointerAcceleration {
    const val MIN_GAIN = 1.0f
    const val MAX_GAIN = 3.0f
    const val DEFAULT_GAIN = MIN_GAIN
}
