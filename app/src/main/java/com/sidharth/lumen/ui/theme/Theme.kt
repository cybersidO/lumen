package com.sidharth.lumen.ui.theme

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import com.sidharth.lumen.core.settings.ResolvedTheme
import com.sidharth.lumen.core.settings.ThemeMode

@Composable
fun LumenTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val resolved = remember(themeMode, systemDark) { themeMode.resolve(systemDark) }
    val colorScheme = remember(resolved) { colorSchemeFor(resolved) }

    val activity = LocalActivity.current
    SideEffect {
        // System bars are transparent (edge-to-edge); only the icon tint needs to follow
        // the theme. Sepia is a light theme with dark icons even though it isn't white.
        activity?.applyBarIconAppearance(useDarkIcons = !resolved.isDark)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LumenTypography,
        content = content,
    )
}

private fun Activity.applyBarIconAppearance(useDarkIcons: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.isAppearanceLightStatusBars = useDarkIcons
    controller.isAppearanceLightNavigationBars = useDarkIcons
}

internal fun colorSchemeFor(theme: ResolvedTheme): ColorScheme = when (theme) {
    ResolvedTheme.LIGHT -> lightColorScheme(
        primary = LumenColors.LightPrimary,
        onPrimary = LumenColors.LightOnPrimary,
        primaryContainer = LumenColors.LightPrimaryContainer,
        onPrimaryContainer = LumenColors.LightOnPrimaryContainer,
        secondary = LumenColors.LightSecondary,
        background = LumenColors.LightBackground,
        onBackground = LumenColors.LightOnBackground,
        surface = LumenColors.LightSurface,
        onSurface = LumenColors.LightOnBackground,
        surfaceVariant = LumenColors.LightSurfaceVariant,
        onSurfaceVariant = LumenColors.LightOnSurfaceVariant,
        outline = LumenColors.LightOutline,
        outlineVariant = LumenColors.LightOutlineVariant,
        error = LumenColors.ErrorLight,
        onError = LumenColors.OnErrorLight,
    )

    ResolvedTheme.DARK -> darkColorScheme(
        primary = LumenColors.DarkPrimary,
        onPrimary = LumenColors.DarkOnPrimary,
        primaryContainer = LumenColors.DarkPrimaryContainer,
        onPrimaryContainer = LumenColors.DarkOnPrimaryContainer,
        secondary = LumenColors.DarkSecondary,
        background = LumenColors.DarkBackground,
        onBackground = LumenColors.DarkOnBackground,
        surface = LumenColors.DarkSurface,
        onSurface = LumenColors.DarkOnBackground,
        surfaceVariant = LumenColors.DarkSurfaceVariant,
        onSurfaceVariant = LumenColors.DarkOnSurfaceVariant,
        outline = LumenColors.DarkOutline,
        outlineVariant = LumenColors.DarkOutlineVariant,
        error = LumenColors.ErrorDark,
        onError = LumenColors.OnErrorDark,
    )

    ResolvedTheme.AMOLED -> darkColorScheme(
        primary = LumenColors.DarkPrimary,
        onPrimary = LumenColors.DarkOnPrimary,
        primaryContainer = LumenColors.DarkPrimaryContainer,
        onPrimaryContainer = LumenColors.DarkOnPrimaryContainer,
        secondary = LumenColors.DarkSecondary,
        background = LumenColors.AmoledBackground,
        onBackground = LumenColors.DarkOnBackground,
        surface = LumenColors.AmoledSurface,
        onSurface = LumenColors.DarkOnBackground,
        surfaceVariant = LumenColors.AmoledSurfaceVariant,
        onSurfaceVariant = LumenColors.DarkOnSurfaceVariant,
        outline = LumenColors.DarkOutline,
        outlineVariant = LumenColors.AmoledOutlineVariant,
        error = LumenColors.ErrorDark,
        onError = LumenColors.OnErrorDark,
    )

    // Sepia is a *light* scheme: Material's contrast maths assumes a light surface, and
    // building it on darkColorScheme() makes every elevated surface come out muddy.
    ResolvedTheme.SEPIA -> lightColorScheme(
        primary = LumenColors.SepiaPrimary,
        onPrimary = LumenColors.SepiaOnPrimary,
        primaryContainer = LumenColors.SepiaPrimaryContainer,
        onPrimaryContainer = LumenColors.SepiaOnPrimaryContainer,
        secondary = LumenColors.SepiaSecondary,
        background = LumenColors.SepiaBackground,
        onBackground = LumenColors.SepiaOnBackground,
        surface = LumenColors.SepiaSurface,
        onSurface = LumenColors.SepiaOnBackground,
        surfaceVariant = LumenColors.SepiaSurfaceVariant,
        onSurfaceVariant = LumenColors.SepiaOnSurfaceVariant,
        outline = LumenColors.SepiaOutline,
        outlineVariant = LumenColors.SepiaOutlineVariant,
        error = LumenColors.ErrorLight,
        onError = LumenColors.OnErrorLight,
    )
}
