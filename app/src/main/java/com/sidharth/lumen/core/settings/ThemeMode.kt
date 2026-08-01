package com.sidharth.lumen.core.settings

/**
 * What the user picked in Settings. [SYSTEM] is a deferral, not a palette — it resolves
 * to [ResolvedTheme.LIGHT] or [ResolvedTheme.DARK] at render time.
 *
 * [storageKey] is written to DataStore and must stay stable forever; renaming an enum
 * constant is free, renaming its key silently resets everyone's preference.
 */
enum class ThemeMode(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    AMOLED("amoled"),
    SEPIA("sepia"),
    ;

    fun resolve(systemInDarkTheme: Boolean): ResolvedTheme = when (this) {
        SYSTEM -> if (systemInDarkTheme) ResolvedTheme.DARK else ResolvedTheme.LIGHT
        LIGHT -> ResolvedTheme.LIGHT
        DARK -> ResolvedTheme.DARK
        AMOLED -> ResolvedTheme.AMOLED
        SEPIA -> ResolvedTheme.SEPIA
    }

    companion object {
        /** Unknown or absent keys fall back to [SYSTEM] rather than throwing. */
        fun fromStorageKey(key: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == key } ?: SYSTEM
    }
}

/** An actual palette. Nothing downstream of this needs to know about "follow system". */
enum class ResolvedTheme {
    LIGHT,
    DARK,
    AMOLED,
    SEPIA,
    ;

    /** Drives status/navigation bar icon colour and, later, the book's injected CSS. */
    val isDark: Boolean get() = this == DARK || this == AMOLED
}
