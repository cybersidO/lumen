package com.sidharth.lumen.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `every storage key round-trips`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStorageKey(mode.storageKey))
        }
    }

    @Test
    fun `storage keys are unique`() {
        val keys = ThemeMode.entries.map { it.storageKey }
        assertEquals(keys.size, keys.toSet().size)
    }

    /**
     * Guards the rename hazard called out in [ThemeMode]: changing a key silently resets
     * the user's preference on next launch. If this test needs editing, that is the bug.
     */
    @Test
    fun `storage keys are frozen`() {
        assertEquals(
            listOf("system", "light", "dark", "amoled", "sepia"),
            ThemeMode.entries.map { it.storageKey },
        )
    }

    @Test
    fun `unknown or missing keys fall back to system`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageKey(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageKey(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageKey("solarized"))
    }

    @Test
    fun `system mode follows the platform`() {
        assertEquals(ResolvedTheme.DARK, ThemeMode.SYSTEM.resolve(systemInDarkTheme = true))
        assertEquals(ResolvedTheme.LIGHT, ThemeMode.SYSTEM.resolve(systemInDarkTheme = false))
    }

    @Test
    fun `explicit modes ignore the platform`() {
        listOf(true, false).forEach { systemDark ->
            assertEquals(ResolvedTheme.LIGHT, ThemeMode.LIGHT.resolve(systemDark))
            assertEquals(ResolvedTheme.DARK, ThemeMode.DARK.resolve(systemDark))
            assertEquals(ResolvedTheme.AMOLED, ThemeMode.AMOLED.resolve(systemDark))
            assertEquals(ResolvedTheme.SEPIA, ThemeMode.SEPIA.resolve(systemDark))
        }
    }

    @Test
    fun `sepia is a light theme and amoled is a dark one`() {
        assertFalse(ResolvedTheme.SEPIA.isDark)
        assertFalse(ResolvedTheme.LIGHT.isDark)
        assertTrue(ResolvedTheme.AMOLED.isDark)
        assertTrue(ResolvedTheme.DARK.isDark)
    }
}
