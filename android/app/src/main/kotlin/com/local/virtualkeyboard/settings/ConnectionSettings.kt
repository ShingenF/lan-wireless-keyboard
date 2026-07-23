package com.local.virtualkeyboard.settings

data class ConnectionSettings(
    val host: String = "",
    val port: Int = 39421,
    val pairingCode: String = "",
    val pinnedFingerprint: String = "",
    val pointerSensitivity: Float = 1.0f,
    val pointerAcceleration: Float = PointerAcceleration.DEFAULT_GAIN,
    val scrollStripEnabled: Boolean = true,
    val scrollDetentSpacingDp: Float = ScrollWheelTuning.DEFAULT_DETENT_SPACING_DP,
    val scrollInertiaScale: Float = ScrollWheelTuning.DEFAULT_INERTIA_SCALE,
    val scrollHapticProfile: ScrollHapticProfile = ScrollHapticProfile.STANDARD,
    val languageToggleShortcut: LanguageToggleShortcut = LanguageToggleShortcut.SHIFT,
    val inputMethodShortcut: InputMethodShortcut = InputMethodShortcut.WINDOWS_SPACE,
    val themeSettings: ThemeSettings = ThemeSettings(),
)

fun ConnectionSettings.requiresConnectionRestart(updated: ConnectionSettings): Boolean =
    host != updated.host ||
        port != updated.port ||
        pairingCode != updated.pairingCode

object PointerAcceleration {
    const val MIN_GAIN = 1.0f
    const val MAX_GAIN = 3.0f
    const val DEFAULT_GAIN = MIN_GAIN
}

object ScrollWheelTuning {
    const val MIN_DETENT_SPACING_DP = 4f
    const val MAX_DETENT_SPACING_DP = 16f
    const val DEFAULT_DETENT_SPACING_DP = 8f
    const val MIN_INERTIA_SCALE = 0f
    const val MAX_INERTIA_SCALE = 2f
    const val DEFAULT_INERTIA_SCALE = 1f
}

enum class ScrollHapticProfile(val storedValue: String) {
    OFF("off"),
    LIGHT("light"),
    STANDARD("standard"),
    STRONG("strong");

    companion object {
        fun fromStoredValue(value: String?): ScrollHapticProfile =
            entries.firstOrNull { it.storedValue == value } ?: STANDARD
    }
}
