package com.sidharth.lumen.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.fromStorageKey(prefs[Keys.ThemeMode]),
            typography = ReaderTypography(
                font = ReaderFont.fromStorageKey(prefs[Keys.Font]),
                fontSizePercent = prefs[Keys.FontSizePercent]
                    ?: ReaderTypography.DEFAULT_FONT_SIZE_PERCENT,
                lineHeight = prefs[Keys.LineHeight] ?: ReaderTypography.DEFAULT_LINE_HEIGHT,
                pageMarginsPercent = prefs[Keys.MarginsPercent]
                    ?: ReaderTypography.DEFAULT_MARGINS_PERCENT,
                textAlign = ReaderTextAlign.fromStorageKey(prefs[Keys.TextAlign]),
                hyphens = prefs[Keys.Hyphens] ?: true,
            ),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.ThemeMode] = mode.storageKey }
    }

    suspend fun updateTypography(transform: (ReaderTypography) -> ReaderTypography) {
        dataStore.edit { prefs ->
            val current = ReaderTypography(
                font = ReaderFont.fromStorageKey(prefs[Keys.Font]),
                fontSizePercent = prefs[Keys.FontSizePercent]
                    ?: ReaderTypography.DEFAULT_FONT_SIZE_PERCENT,
                lineHeight = prefs[Keys.LineHeight] ?: ReaderTypography.DEFAULT_LINE_HEIGHT,
                pageMarginsPercent = prefs[Keys.MarginsPercent]
                    ?: ReaderTypography.DEFAULT_MARGINS_PERCENT,
                textAlign = ReaderTextAlign.fromStorageKey(prefs[Keys.TextAlign]),
                hyphens = prefs[Keys.Hyphens] ?: true,
            )
            val updated = transform(current)
            prefs[Keys.Font] = updated.font.storageKey
            prefs[Keys.FontSizePercent] = updated.fontSizePercent
            prefs[Keys.LineHeight] = updated.lineHeight
            prefs[Keys.MarginsPercent] = updated.pageMarginsPercent
            prefs[Keys.TextAlign] = updated.textAlign.storageKey
            prefs[Keys.Hyphens] = updated.hyphens
        }
    }

    private object Keys {
        val ThemeMode = stringPreferencesKey("theme_mode")
        val Font = stringPreferencesKey("reader_font")
        val FontSizePercent = intPreferencesKey("reader_font_size_percent")
        val LineHeight = floatPreferencesKey("reader_line_height")
        val MarginsPercent = intPreferencesKey("reader_margins_percent")
        val TextAlign = stringPreferencesKey("reader_text_align")
        val Hyphens = booleanPreferencesKey("reader_hyphens")
    }
}
