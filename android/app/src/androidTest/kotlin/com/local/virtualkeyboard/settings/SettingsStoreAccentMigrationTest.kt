package com.local.virtualkeyboard.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreAccentMigrationTest {
    @Test
    fun legacyDefaultAccentsMigrateOnceAndRemainUserEditableAfterward() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, 0)
        preferences.edit()
            .clear()
            .putString(LIGHT_ACCENT_KEY, LEGACY_ACCENT)
            .putString(DARK_ACCENT_KEY, LEGACY_ACCENT)
            .commit()

        val migrated = SettingsStore(context).load()

        assertEquals("#0071E3", migrated.themeSettings.palettes.light.accent.canonical)
        assertEquals("#0071E3", migrated.themeSettings.palettes.dark.accent.canonical)

        preferences.edit()
            .putString(LIGHT_ACCENT_KEY, LEGACY_ACCENT)
            .putString(DARK_ACCENT_KEY, LEGACY_ACCENT)
            .commit()

        val userEditedAfterMigration = SettingsStore(context).load()

        assertEquals(LEGACY_ACCENT, userEditedAfterMigration.themeSettings.palettes.light.accent.canonical)
        assertEquals(LEGACY_ACCENT, userEditedAfterMigration.themeSettings.palettes.dark.accent.canonical)
        preferences.edit().clear().commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "connection_settings"
        const val LIGHT_ACCENT_KEY = "accent_color"
        const val DARK_ACCENT_KEY = "dark_accent_color"
        const val LEGACY_ACCENT = "#3372DE"
    }
}
