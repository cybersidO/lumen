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
            gestures = prefs.readGestures(),
            brightness = prefs.readBrightness(),
        )
    }

    private fun Preferences.readGestures() = GestureSettings(
        edgeStripFraction = this[Keys.EdgeStrip] ?: GestureSettings.DEFAULT_EDGE_STRIP,
        tapZoneFraction = this[Keys.TapZone] ?: GestureSettings.DEFAULT_TAP_ZONE,
        brightnessDragEnabled = this[Keys.BrightnessDrag] ?: true,
        fontSizeDragEnabled = this[Keys.FontSizeDrag] ?: true,
        tapToTurnPageEnabled = this[Keys.TapToTurn] ?: true,
        twoFingerTapThemeEnabled = this[Keys.TwoFingerTheme] ?: false,
    ).sanitised()

    private fun Preferences.readBrightness() = BrightnessSettings(
        // The sentinel is stored rather than absence, so "follow system" is a choice the
        // user made and not merely a key that has never been written.
        windowBrightness = this[Keys.Brightness]
            ?.takeIf { it >= 0f },
        extraDim = this[Keys.ExtraDim] ?: 0f,
    ).sanitised()

    suspend fun updateGestures(transform: (GestureSettings) -> GestureSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.readGestures()).sanitised()
            prefs[Keys.EdgeStrip] = updated.edgeStripFraction
            prefs[Keys.TapZone] = updated.tapZoneFraction
            prefs[Keys.BrightnessDrag] = updated.brightnessDragEnabled
            prefs[Keys.FontSizeDrag] = updated.fontSizeDragEnabled
            prefs[Keys.TapToTurn] = updated.tapToTurnPageEnabled
            prefs[Keys.TwoFingerTheme] = updated.twoFingerTapThemeEnabled
        }
    }

    suspend fun updateBrightness(transform: (BrightnessSettings) -> BrightnessSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.readBrightness()).sanitised()
            prefs[Keys.Brightness] = updated.windowBrightness ?: BrightnessSettings.FOLLOW_SYSTEM
            prefs[Keys.ExtraDim] = updated.extraDim
        }
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
        val EdgeStrip = floatPreferencesKey("gesture_edge_strip")
        val TapZone = floatPreferencesKey("gesture_tap_zone")
        val BrightnessDrag = booleanPreferencesKey("gesture_brightness_drag")
        val FontSizeDrag = booleanPreferencesKey("gesture_font_size_drag")
        val TapToTurn = booleanPreferencesKey("gesture_tap_to_turn")
        val TwoFingerTheme = booleanPreferencesKey("gesture_two_finger_theme")
        val Brightness = floatPreferencesKey("reader_brightness")
        val ExtraDim = floatPreferencesKey("reader_extra_dim")
    }
}
