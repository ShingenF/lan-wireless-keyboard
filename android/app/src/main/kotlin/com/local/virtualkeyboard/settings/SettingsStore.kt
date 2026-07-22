package com.local.virtualkeyboard.settings

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.local.virtualkeyboard.protocol.AuthenticationProof
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("connection_settings", Context.MODE_PRIVATE)

    fun load(): ConnectionSettings = ConnectionSettings(
        host = preferences.getString(KEY_HOST, "").orEmpty(),
        port = preferences.getInt(KEY_PORT, 39421),
        pairingCode = decryptSecret(KEY_PAIRING_CODE),
        pinnedFingerprint = decryptSecret(KEY_PINNED_FINGERPRINT),
        pointerSensitivity = preferences.getFloat(KEY_SENSITIVITY, 1.0f),
        pointerAcceleration = preferences.getFloat(KEY_ACCELERATION, PointerAcceleration.DEFAULT_GAIN)
            .coerceIn(PointerAcceleration.MIN_GAIN, PointerAcceleration.MAX_GAIN),
        scrollStripEnabled = preferences.getBoolean(KEY_SCROLL_STRIP_ENABLED, true),
        scrollDetentSpacingDp = preferences.getFloat(
            KEY_SCROLL_DETENT_SPACING,
            ScrollWheelTuning.DEFAULT_DETENT_SPACING_DP,
        ).coerceIn(
            ScrollWheelTuning.MIN_DETENT_SPACING_DP,
            ScrollWheelTuning.MAX_DETENT_SPACING_DP,
        ),
        scrollInertiaScale = preferences.getFloat(
            KEY_SCROLL_INERTIA_SCALE,
            ScrollWheelTuning.DEFAULT_INERTIA_SCALE,
        ).coerceIn(
            ScrollWheelTuning.MIN_INERTIA_SCALE,
            ScrollWheelTuning.MAX_INERTIA_SCALE,
        ),
        scrollHapticProfile = ScrollHapticProfile.fromStoredValue(
            preferences.getString(KEY_SCROLL_HAPTIC_PROFILE, null),
        ),
        languageToggleShortcut = LanguageToggleShortcut.fromWireNameOrDefault(
            preferences.getString(KEY_LANGUAGE_TOGGLE_SHORTCUT, null),
        ),
        inputMethodShortcut = InputMethodShortcut.fromWireNameOrDefault(
            preferences.getString(KEY_INPUT_METHOD_SHORTCUT, null),
        ),
        themeSettings = ThemeSettings(
            mode = loadThemeMode(),
            palettes = ThemePalettes(
                light = loadPalette(
                    keys = LIGHT_PALETTE_KEYS,
                    defaults = ThemeColors(),
                    secondaryText = loadSecondaryText(),
                ),
                dark = loadPalette(DARK_PALETTE_KEYS, ThemeColors.darkDefaults()),
            ),
        ),
    )

    fun saveConnection(
        host: String,
        port: Int,
        pairingCode: String,
        pointerSensitivity: Float,
        pointerAcceleration: Float,
        scrollStripEnabled: Boolean,
        scrollDetentSpacingDp: Float,
        scrollInertiaScale: Float,
        scrollHapticProfile: ScrollHapticProfile,
        languageToggleShortcut: LanguageToggleShortcut,
        inputMethodShortcut: InputMethodShortcut,
        themeSettings: ThemeSettings,
    ) {
        val previous = load()
        val normalizedCode = AuthenticationProof.normalizePairingCode(pairingCode)
        val endpointOrCodeChanged =
            previous.host != host || previous.port != port || previous.pairingCode != normalizedCode

        preferences.edit()
            .putString(KEY_HOST, host)
            .putInt(KEY_PORT, port)
            .putFloat(KEY_SENSITIVITY, pointerSensitivity.coerceIn(0.5f, 2.0f))
            .putFloat(
                KEY_ACCELERATION,
                pointerAcceleration.coerceIn(PointerAcceleration.MIN_GAIN, PointerAcceleration.MAX_GAIN),
            )
            .putBoolean(KEY_SCROLL_STRIP_ENABLED, scrollStripEnabled)
            .putFloat(
                KEY_SCROLL_DETENT_SPACING,
                scrollDetentSpacingDp.coerceIn(
                    ScrollWheelTuning.MIN_DETENT_SPACING_DP,
                    ScrollWheelTuning.MAX_DETENT_SPACING_DP,
                ),
            )
            .putFloat(
                KEY_SCROLL_INERTIA_SCALE,
                scrollInertiaScale.coerceIn(
                    ScrollWheelTuning.MIN_INERTIA_SCALE,
                    ScrollWheelTuning.MAX_INERTIA_SCALE,
                ),
            )
            .putString(KEY_SCROLL_HAPTIC_PROFILE, scrollHapticProfile.storedValue)
            .putString(KEY_LANGUAGE_TOGGLE_SHORTCUT, languageToggleShortcut.command.wireName)
            .putString(KEY_INPUT_METHOD_SHORTCUT, inputMethodShortcut.command.wireName)
            .putString(KEY_THEME_MODE, themeSettings.mode.storedValue)
            .remove(KEY_THEME_FOLLOW_SYSTEM)
            .remove(KEY_THEME_FORCE_DARK)
            .putPalette(LIGHT_PALETTE_KEYS, themeSettings.palettes.light)
            .putPalette(DARK_PALETTE_KEYS, themeSettings.palettes.dark)
            .apply()
        encryptSecret(KEY_PAIRING_CODE, normalizedCode)
        if (endpointOrCodeChanged) encryptSecret(KEY_PINNED_FINGERPRINT, "")
    }

    fun savePinnedFingerprint(fingerprint: String) {
        encryptSecret(KEY_PINNED_FINGERPRINT, fingerprint.uppercase())
    }

    private fun loadHex(preferenceKey: String, fallback: HexColor): HexColor =
        HexColor.parse(preferences.getString(preferenceKey, fallback.canonical).orEmpty()) ?: fallback

    private fun loadThemeMode(): ThemeMode {
        ThemeMode.fromStoredValue(preferences.getString(KEY_THEME_MODE, null))?.let { return it }
        if (preferences.getBoolean(KEY_THEME_FOLLOW_SYSTEM, true)) return ThemeMode.FOLLOW_SYSTEM
        return if (preferences.getBoolean(KEY_THEME_FORCE_DARK, false)) {
            ThemeMode.DARK
        } else {
            ThemeMode.LIGHT
        }
    }

    private fun loadPalette(
        keys: ThemePalettePreferenceKeys,
        defaults: ThemeColors,
        secondaryText: HexColor? = null,
    ): ThemeColors = ThemeColors(
        background = loadHex(keys.background, defaults.background),
        icon = loadHex(keys.icon, defaults.icon),
        primaryText = loadHex(keys.primaryText, defaults.primaryText),
        secondaryText = secondaryText ?: loadHex(keys.secondaryText, defaults.secondaryText),
        inputBackground = loadHex(keys.inputBackground, defaults.inputBackground),
        touchpadBackground = loadHex(keys.touchpadBackground, defaults.touchpadBackground),
    )

    private fun SharedPreferences.Editor.putPalette(
        keys: ThemePalettePreferenceKeys,
        colors: ThemeColors,
    ): SharedPreferences.Editor = apply {
        putString(keys.background, colors.background.canonical)
        putString(keys.icon, colors.icon.canonical)
        putString(keys.primaryText, colors.primaryText.canonical)
        putString(keys.secondaryText, colors.secondaryText.canonical)
        putString(keys.inputBackground, colors.inputBackground.canonical)
        putString(keys.touchpadBackground, colors.touchpadBackground.canonical)
    }

    private fun loadSecondaryText(): HexColor {
        val stored = HexColor.parse(preferences.getString(KEY_SECONDARY_TEXT_COLOR, "").orEmpty())
        if (preferences.getBoolean(KEY_SECONDARY_TEXT_COLOR_MIGRATED, false)) {
            return stored ?: HexColor.DEFAULT_SECONDARY_TEXT
        }

        val resolved = if (stored == HexColor.LEGACY_DEFAULT_SECONDARY_TEXT) {
            HexColor.DEFAULT_SECONDARY_TEXT
        } else {
            stored ?: HexColor.DEFAULT_SECONDARY_TEXT
        }
        preferences.edit()
            .putBoolean(KEY_SECONDARY_TEXT_COLOR_MIGRATED, true)
            .putString(KEY_SECONDARY_TEXT_COLOR, resolved.canonical)
            .apply()
        return resolved
    }

    private fun encryptSecret(preferenceKey: String, plaintext: String) {
        if (plaintext.isEmpty()) {
            preferences.edit().remove(preferenceKey).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(plaintext.toByteArray()), Base64.NO_WRAP)
        preferences.edit().putString(preferenceKey, encoded).apply()
    }

    private fun decryptSecret(preferenceKey: String): String {
        val encoded = preferences.getString(preferenceKey, null) ?: return ""
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            if (bytes.size <= IV_LENGTH_BYTES) return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, bytes.copyOfRange(0, IV_LENGTH_BYTES)),
            )
            String(cipher.doFinal(bytes.copyOfRange(IV_LENGTH_BYTES, bytes.size)))
        } catch (_: Exception) {
            preferences.edit().remove(preferenceKey).apply()
            ""
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private data class ThemePalettePreferenceKeys(
        val background: String,
        val icon: String,
        val primaryText: String,
        val secondaryText: String,
        val inputBackground: String,
        val touchpadBackground: String,
    )

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_PAIRING_CODE = "pairing_code"
        const val KEY_PINNED_FINGERPRINT = "pinned_fingerprint"
        const val KEY_SENSITIVITY = "pointer_sensitivity"
        const val KEY_ACCELERATION = "pointer_acceleration"
        const val KEY_SCROLL_STRIP_ENABLED = "scroll_strip_enabled"
        const val KEY_SCROLL_DETENT_SPACING = "scroll_detent_spacing_dp"
        const val KEY_SCROLL_INERTIA_SCALE = "scroll_inertia_scale"
        const val KEY_SCROLL_HAPTIC_PROFILE = "scroll_haptic_profile"
        const val KEY_LANGUAGE_TOGGLE_SHORTCUT = "language_toggle_shortcut"
        const val KEY_INPUT_METHOD_SHORTCUT = "input_method_shortcut"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_THEME_FOLLOW_SYSTEM = "theme_follow_system"
        const val KEY_THEME_FORCE_DARK = "theme_force_dark"
        const val KEY_BACKGROUND_COLOR = "background_color"
        const val KEY_ICON_COLOR = "icon_color"
        const val KEY_PRIMARY_TEXT_COLOR = "primary_text_color"
        const val KEY_SECONDARY_TEXT_COLOR = "secondary_text_color"
        const val KEY_INPUT_BACKGROUND_COLOR = "input_background_color"
        const val KEY_TOUCHPAD_BACKGROUND_COLOR = "touchpad_background_color"
        const val KEY_DARK_BACKGROUND_COLOR = "dark_background_color"
        const val KEY_DARK_ICON_COLOR = "dark_icon_color"
        const val KEY_DARK_PRIMARY_TEXT_COLOR = "dark_primary_text_color"
        const val KEY_DARK_SECONDARY_TEXT_COLOR = "dark_secondary_text_color"
        const val KEY_DARK_INPUT_BACKGROUND_COLOR = "dark_input_background_color"
        const val KEY_DARK_TOUCHPAD_BACKGROUND_COLOR = "dark_touchpad_background_color"
        const val KEY_SECONDARY_TEXT_COLOR_MIGRATED = "secondary_text_color_migrated"
        const val KEY_ALIAS = "virtual_keyboard_settings_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128

        val LIGHT_PALETTE_KEYS = ThemePalettePreferenceKeys(
            background = KEY_BACKGROUND_COLOR,
            icon = KEY_ICON_COLOR,
            primaryText = KEY_PRIMARY_TEXT_COLOR,
            secondaryText = KEY_SECONDARY_TEXT_COLOR,
            inputBackground = KEY_INPUT_BACKGROUND_COLOR,
            touchpadBackground = KEY_TOUCHPAD_BACKGROUND_COLOR,
        )
        val DARK_PALETTE_KEYS = ThemePalettePreferenceKeys(
            background = KEY_DARK_BACKGROUND_COLOR,
            icon = KEY_DARK_ICON_COLOR,
            primaryText = KEY_DARK_PRIMARY_TEXT_COLOR,
            secondaryText = KEY_DARK_SECONDARY_TEXT_COLOR,
            inputBackground = KEY_DARK_INPUT_BACKGROUND_COLOR,
            touchpadBackground = KEY_DARK_TOUCHPAD_BACKGROUND_COLOR,
        )
    }
}
