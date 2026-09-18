package com.sidharth.lumen.core.settings

/**
 * Everything that lives in DataStore and is read app-wide. Grows a field per milestone
 * (typography in M2, gesture zones in M3, dictionary choice in M5, reading comfort in M10); it stays one object
 * so that a single Flow emission carries a consistent snapshot.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val typography: ReaderTypography = ReaderTypography.Defaults,
    val gestures: GestureSettings = GestureSettings.Defaults,
    val brightness: BrightnessSettings = BrightnessSettings.Defaults,
    val reading: ReadingSettings = ReadingSettings.Defaults,
) {
    companion object {
        val Defaults = AppSettings()
    }
}
