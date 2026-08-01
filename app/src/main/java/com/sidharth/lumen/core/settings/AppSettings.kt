package com.sidharth.lumen.core.settings

/**
 * Everything that lives in DataStore and is read app-wide. Grows a field per milestone
 * (typography in M2, gesture zones in M3, dictionary choice in M5); it stays one object
 * so that a single Flow emission carries a consistent snapshot.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    companion object {
        val Defaults = AppSettings()
    }
}
