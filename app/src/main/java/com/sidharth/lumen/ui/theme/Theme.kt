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
        inversePrimary = LumenColors.LightInversePrimary,
        secondary = LumenColors.LightSecondary,
        onSecondary = LumenColors.LightOnSecondary,
        secondaryContainer = LumenColors.LightSecondaryContainer,
        onSecondaryContainer = LumenColors.LightOnSecondaryContainer,
        tertiary = LumenColors.LightTertiary,
        onTertiary = LumenColors.LightOnTertiary,
        tertiaryContainer = LumenColors.LightTertiaryContainer,
        onTertiaryContainer = LumenColors.LightOnTertiaryContainer,
        background = LumenColors.LightBackground,
        onBackground = LumenColors.LightOnBackground,
        surface = LumenColors.LightSurface,
        onSurface = LumenColors.LightOnSurface,
        surfaceVariant = LumenColors.LightSurfaceVariant,
        onSurfaceVariant = LumenColors.LightOnSurfaceVariant,
        surfaceDim = LumenColors.LightSurfaceDim,
        surfaceBright = LumenColors.LightSurfaceBright,
        surfaceContainerLowest = LumenColors.LightContainerLowest,
        surfaceContainerLow = LumenColors.LightContainerLow,
        surfaceContainer = LumenColors.LightContainer,
        surfaceContainerHigh = LumenColors.LightContainerHigh,
        surfaceContainerHighest = LumenColors.LightContainerHighest,
        inverseSurface = LumenColors.LightInverseSurface,
        inverseOnSurface = LumenColors.LightInverseOnSurface,
        outline = LumenColors.LightOutline,
        outlineVariant = LumenColors.LightOutlineVariant,
        error = LumenColors.ErrorLight,
        onError = LumenColors.OnErrorLight,
        errorContainer = LumenColors.ErrorContainerLight,
        onErrorContainer = LumenColors.OnErrorContainerLight,
        scrim = LumenColors.Scrim,
    )

    ResolvedTheme.DARK -> darkColorScheme(
        primary = LumenColors.DarkPrimary,
        onPrimary = LumenColors.DarkOnPrimary,
        primaryContainer = LumenColors.DarkPrimaryContainer,
        onPrimaryContainer = LumenColors.DarkOnPrimaryContainer,
        inversePrimary = LumenColors.DarkInversePrimary,
        secondary = LumenColors.DarkSecondary,
        onSecondary = LumenColors.DarkOnSecondary,
        secondaryContainer = LumenColors.DarkSecondaryContainer,
        onSecondaryContainer = LumenColors.DarkOnSecondaryContainer,
        tertiary = LumenColors.DarkTertiary,
        onTertiary = LumenColors.DarkOnTertiary,
        tertiaryContainer = LumenColors.DarkTertiaryContainer,
        onTertiaryContainer = LumenColors.DarkOnTertiaryContainer,
        background = LumenColors.DarkBackground,
        onBackground = LumenColors.DarkOnBackground,
        surface = LumenColors.DarkSurface,
        onSurface = LumenColors.DarkOnSurface,
        surfaceVariant = LumenColors.DarkSurfaceVariant,
        onSurfaceVariant = LumenColors.DarkOnSurfaceVariant,
        surfaceDim = LumenColors.DarkSurfaceDim,
        surfaceBright = LumenColors.DarkSurfaceBright,
        surfaceContainerLowest = LumenColors.DarkContainerLowest,
        surfaceContainerLow = LumenColors.DarkContainerLow,
        surfaceContainer = LumenColors.DarkContainer,
        surfaceContainerHigh = LumenColors.DarkContainerHigh,
        surfaceContainerHighest = LumenColors.DarkContainerHighest,
        inverseSurface = LumenColors.DarkInverseSurface,
        inverseOnSurface = LumenColors.DarkInverseOnSurface,
        outline = LumenColors.DarkOutline,
        outlineVariant = LumenColors.DarkOutlineVariant,
        error = LumenColors.ErrorDark,
        onError = LumenColors.OnErrorDark,
        errorContainer = LumenColors.ErrorContainerDark,
        onErrorContainer = LumenColors.OnErrorContainerDark,
        scrim = LumenColors.Scrim,
    )

    // Same hues as DARK; only the surface ladder is re-based on true black so that an
    // OLED panel actually leaves those pixels unlit.
    ResolvedTheme.AMOLED -> darkColorScheme(
        primary = LumenColors.DarkPrimary,
        onPrimary = LumenColors.DarkOnPrimary,
        primaryContainer = LumenColors.DarkPrimaryContainer,
        onPrimaryContainer = LumenColors.DarkOnPrimaryContainer,
        inversePrimary = LumenColors.DarkInversePrimary,
        secondary = LumenColors.DarkSecondary,
        onSecondary = LumenColors.DarkOnSecondary,
        secondaryContainer = LumenColors.DarkSecondaryContainer,
        onSecondaryContainer = LumenColors.DarkOnSecondaryContainer,
        tertiary = LumenColors.DarkTertiary,
        onTertiary = LumenColors.DarkOnTertiary,
        tertiaryContainer = LumenColors.DarkTertiaryContainer,
        onTertiaryContainer = LumenColors.DarkOnTertiaryContainer,
        background = LumenColors.AmoledBackground,
        onBackground = LumenColors.DarkOnBackground,
        surface = LumenColors.AmoledSurface,
        onSurface = LumenColors.DarkOnSurface,
        surfaceVariant = LumenColors.AmoledSurfaceVariant,
        onSurfaceVariant = LumenColors.DarkOnSurfaceVariant,
        surfaceDim = LumenColors.AmoledSurfaceDim,
        surfaceBright = LumenColors.AmoledSurfaceBright,
        surfaceContainerLowest = LumenColors.AmoledContainerLowest,
        surfaceContainerLow = LumenColors.AmoledContainerLow,
        surfaceContainer = LumenColors.AmoledContainer,
        surfaceContainerHigh = LumenColors.AmoledContainerHigh,
        surfaceContainerHighest = LumenColors.AmoledContainerHighest,
        inverseSurface = LumenColors.DarkInverseSurface,
        inverseOnSurface = LumenColors.DarkInverseOnSurface,
        outline = LumenColors.DarkOutline,
        outlineVariant = LumenColors.AmoledOutlineVariant,
        error = LumenColors.ErrorDark,
        onError = LumenColors.OnErrorDark,
        errorContainer = LumenColors.ErrorContainerDark,
        onErrorContainer = LumenColors.OnErrorContainerDark,
        scrim = LumenColors.Scrim,
    )

    // Sepia is a *light* scheme: Material's contrast maths assumes a light surface, and
    // building it on darkColorScheme() makes every elevated surface come out muddy.
    ResolvedTheme.SEPIA -> lightColorScheme(
        primary = LumenColors.SepiaPrimary,
        onPrimary = LumenColors.SepiaOnPrimary,
        primaryContainer = LumenColors.SepiaPrimaryContainer,
        onPrimaryContainer = LumenColors.SepiaOnPrimaryContainer,
        inversePrimary = LumenColors.SepiaInversePrimary,
        secondary = LumenColors.SepiaSecondary,
        onSecondary = LumenColors.SepiaOnSecondary,
        secondaryContainer = LumenColors.SepiaSecondaryContainer,
        onSecondaryContainer = LumenColors.SepiaOnSecondaryContainer,
        tertiary = LumenColors.SepiaTertiary,
        onTertiary = LumenColors.SepiaOnTertiary,
        tertiaryContainer = LumenColors.SepiaTertiaryContainer,
        onTertiaryContainer = LumenColors.SepiaOnTertiaryContainer,
        background = LumenColors.SepiaBackground,
        onBackground = LumenColors.SepiaOnBackground,
        surface = LumenColors.SepiaSurface,
        onSurface = LumenColors.SepiaOnSurface,
        surfaceVariant = LumenColors.SepiaSurfaceVariant,
        onSurfaceVariant = LumenColors.SepiaOnSurfaceVariant,
        surfaceDim = LumenColors.SepiaSurfaceDim,
        surfaceBright = LumenColors.SepiaSurfaceBright,
        surfaceContainerLowest = LumenColors.SepiaContainerLowest,
        surfaceContainerLow = LumenColors.SepiaContainerLow,
        surfaceContainer = LumenColors.SepiaContainer,
        surfaceContainerHigh = LumenColors.SepiaContainerHigh,
        surfaceContainerHighest = LumenColors.SepiaContainerHighest,
        inverseSurface = LumenColors.SepiaInverseSurface,
        inverseOnSurface = LumenColors.SepiaInverseOnSurface,
        outline = LumenColors.SepiaOutline,
        outlineVariant = LumenColors.SepiaOutlineVariant,
        error = LumenColors.ErrorLight,
        onError = LumenColors.OnErrorLight,
        errorContainer = LumenColors.ErrorContainerLight,
        onErrorContainer = LumenColors.OnErrorContainerLight,
        scrim = LumenColors.Scrim,
    )
}
