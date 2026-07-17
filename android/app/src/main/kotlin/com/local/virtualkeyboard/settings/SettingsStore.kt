package com.local.virtualkeyboard.settings

import android.content.Context
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
        languageToggleShortcut = LanguageToggleShortcut.fromWireNameOrDefault(
            preferences.getString(KEY_LANGUAGE_TOGGLE_SHORTCUT, null),
        ),
        inputMethodShortcut = InputMethodShortcut.fromWireNameOrDefault(
            preferences.getString(KEY_INPUT_METHOD_SHORTCUT, null),
        ),
        themeColors = ThemeColors(
            background = loadHex(KEY_BACKGROUND_COLOR, HexColor.DEFAULT_BACKGROUND),
            icon = loadHex(KEY_ICON_COLOR, HexColor.DEFAULT_ICON),
            primaryText = loadHex(KEY_PRIMARY_TEXT_COLOR, HexColor.DEFAULT_PRIMARY_TEXT),
            secondaryText = loadSecondaryText(),
        ),
    )

    fun saveConnection(
        host: String,
        port: Int,
        pairingCode: String,
        pointerSensitivity: Float,
        pointerAcceleration: Float,
        scrollStripEnabled: Boolean,
        languageToggleShortcut: LanguageToggleShortcut,
        inputMethodShortcut: InputMethodShortcut,
        themeColors: ThemeColors,
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
            .putString(KEY_LANGUAGE_TOGGLE_SHORTCUT, languageToggleShortcut.command.wireName)
            .putString(KEY_INPUT_METHOD_SHORTCUT, inputMethodShortcut.command.wireName)
            .putString(KEY_BACKGROUND_COLOR, themeColors.background.canonical)
            .putString(KEY_ICON_COLOR, themeColors.icon.canonical)
            .putString(KEY_PRIMARY_TEXT_COLOR, themeColors.primaryText.canonical)
            .putString(KEY_SECONDARY_TEXT_COLOR, themeColors.secondaryText.canonical)
            .apply()
        encryptSecret(KEY_PAIRING_CODE, normalizedCode)
        if (endpointOrCodeChanged) encryptSecret(KEY_PINNED_FINGERPRINT, "")
    }

    fun savePinnedFingerprint(fingerprint: String) {
        encryptSecret(KEY_PINNED_FINGERPRINT, fingerprint.uppercase())
    }

    private fun loadHex(preferenceKey: String, fallback: HexColor): HexColor =
        HexColor.parse(preferences.getString(preferenceKey, fallback.canonical).orEmpty()) ?: fallback

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

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_PAIRING_CODE = "pairing_code"
        const val KEY_PINNED_FINGERPRINT = "pinned_fingerprint"
        const val KEY_SENSITIVITY = "pointer_sensitivity"
        const val KEY_ACCELERATION = "pointer_acceleration"
        const val KEY_SCROLL_STRIP_ENABLED = "scroll_strip_enabled"
        const val KEY_LANGUAGE_TOGGLE_SHORTCUT = "language_toggle_shortcut"
        const val KEY_INPUT_METHOD_SHORTCUT = "input_method_shortcut"
        const val KEY_BACKGROUND_COLOR = "background_color"
        const val KEY_ICON_COLOR = "icon_color"
        const val KEY_PRIMARY_TEXT_COLOR = "primary_text_color"
        const val KEY_SECONDARY_TEXT_COLOR = "secondary_text_color"
        const val KEY_SECONDARY_TEXT_COLOR_MIGRATED = "secondary_text_color_migrated"
        const val KEY_ALIAS = "virtual_keyboard_settings_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
