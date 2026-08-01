package com.sidharth.lumen.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Four hand-tuned palettes. Deliberately not Material You / dynamic colour: a reader's
 * page should look the same every day regardless of the wallpaper, and sepia and AMOLED
 * have no dynamic equivalent.
 *
 * Every role Material 3 defines is specified here, including the whole
 * `surfaceContainer*` ladder. That is not thoroughness for its own sake — `lightColorScheme()`
 * fills anything left unset with Material's *baseline purple*, so a partially specified
 * scheme looks correct until the first menu or dialog opens and renders lavender on a warm
 * paper background. See DECISIONS.md.
 *
 * Light and dark are NOT the same hues at different luminance — the dark palette is
 * warmed slightly so that white text on it doesn't read as blue at night.
 */
internal object LumenColors {

    // --- Light: warm paper ---
    val LightPrimary = Color(0xFF7A5D33)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFE9DCC6)
    val LightOnPrimaryContainer = Color(0xFF2A1F0D)
    val LightSecondary = Color(0xFF4F5B54)
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightSecondaryContainer = Color(0xFFDCE5DE)
    val LightOnSecondaryContainer = Color(0xFF1A241D)
    val LightTertiary = Color(0xFF6A5346)
    val LightOnTertiary = Color(0xFFFFFFFF)
    val LightTertiaryContainer = Color(0xFFEFDFD2)
    val LightOnTertiaryContainer = Color(0xFF2A1A10)
    val LightBackground = Color(0xFFFBF7F2)
    val LightOnBackground = Color(0xFF1B1815)
    val LightSurface = Color(0xFFFBF7F2)
    val LightOnSurface = Color(0xFF1B1815)
    val LightSurfaceVariant = Color(0xFFEFE7DC)
    val LightOnSurfaceVariant = Color(0xFF534B41)
    val LightSurfaceDim = Color(0xFFE4DDD2)
    val LightSurfaceBright = Color(0xFFFFFCF8)
    val LightContainerLowest = Color(0xFFFFFFFF)
    val LightContainerLow = Color(0xFFF7F1E8)
    val LightContainer = Color(0xFFF1EBE0)
    val LightContainerHigh = Color(0xFFEBE4D7)
    val LightContainerHighest = Color(0xFFE5DDCE)
    val LightOutline = Color(0xFF847A6D)
    val LightOutlineVariant = Color(0xFFD5C9B8)
    val LightInverseSurface = Color(0xFF322E29)
    val LightInverseOnSurface = Color(0xFFF5EFE6)
    val LightInversePrimary = Color(0xFFDCBE8F)

    // --- Dark: soft charcoal ---
    val DarkPrimary = Color(0xFFDCBE8F)
    val DarkOnPrimary = Color(0xFF3B2C12)
    val DarkPrimaryContainer = Color(0xFF544225)
    val DarkOnPrimaryContainer = Color(0xFFF7E5C7)
    val DarkSecondary = Color(0xFFB6C5BB)
    val DarkOnSecondary = Color(0xFF213029)
    val DarkSecondaryContainer = Color(0xFF38443C)
    val DarkOnSecondaryContainer = Color(0xFFD3E2D7)
    val DarkTertiary = Color(0xFFD9C0AC)
    val DarkOnTertiary = Color(0xFF3B2A1E)
    val DarkTertiaryContainer = Color(0xFF4F3B2E)
    val DarkOnTertiaryContainer = Color(0xFFF5E0D0)
    val DarkBackground = Color(0xFF121212)
    val DarkOnBackground = Color(0xFFE7E1D8)
    val DarkSurface = Color(0xFF121212)
    val DarkOnSurface = Color(0xFFE7E1D8)
    val DarkSurfaceVariant = Color(0xFF2A2825)
    val DarkOnSurfaceVariant = Color(0xFFCFC6B8)
    val DarkSurfaceDim = Color(0xFF121212)
    val DarkSurfaceBright = Color(0xFF38352F)
    val DarkContainerLowest = Color(0xFF0D0D0C)
    val DarkContainerLow = Color(0xFF1A1917)
    val DarkContainer = Color(0xFF1E1D1B)
    val DarkContainerHigh = Color(0xFF292724)
    val DarkContainerHighest = Color(0xFF34312D)
    val DarkOutline = Color(0xFF988F82)
    val DarkOutlineVariant = Color(0xFF4A453E)
    val DarkInverseSurface = Color(0xFFE7E1D8)
    val DarkInverseOnSurface = Color(0xFF322E29)
    val DarkInversePrimary = Color(0xFF7A5D33)

    // --- AMOLED: true black, so unlit pixels stay unlit ---
    // Shares the dark palette's hues; only the surface ladder is re-based on #000000.
    val AmoledBackground = Color(0xFF000000)
    val AmoledSurface = Color(0xFF000000)
    val AmoledSurfaceVariant = Color(0xFF161513)
    val AmoledSurfaceDim = Color(0xFF000000)
    val AmoledSurfaceBright = Color(0xFF262523)
    val AmoledContainerLowest = Color(0xFF000000)
    val AmoledContainerLow = Color(0xFF0A0A09)
    val AmoledContainer = Color(0xFF101010)
    val AmoledContainerHigh = Color(0xFF1A1A19)
    val AmoledContainerHighest = Color(0xFF232322)
    val AmoledOutlineVariant = Color(0xFF34302B)

    // --- Sepia: aged paper, deliberately low contrast ---
    val SepiaPrimary = Color(0xFF8A6A3D)
    val SepiaOnPrimary = Color(0xFFFFF8EC)
    val SepiaPrimaryContainer = Color(0xFFE0CFA8)
    val SepiaOnPrimaryContainer = Color(0xFF33260F)
    val SepiaSecondary = Color(0xFF6B6350)
    val SepiaOnSecondary = Color(0xFFFFF8EC)
    val SepiaSecondaryContainer = Color(0xFFE3DCC8)
    val SepiaOnSecondaryContainer = Color(0xFF2A2519)
    val SepiaTertiary = Color(0xFF7A5F3F)
    val SepiaOnTertiary = Color(0xFFFFF8EC)
    val SepiaTertiaryContainer = Color(0xFFE8D6B4)
    val SepiaOnTertiaryContainer = Color(0xFF2E2010)
    val SepiaBackground = Color(0xFFF4ECD8)
    val SepiaOnBackground = Color(0xFF4A3B2A)
    val SepiaSurface = Color(0xFFF4ECD8)
    val SepiaOnSurface = Color(0xFF4A3B2A)
    val SepiaSurfaceVariant = Color(0xFFE6D9BC)
    val SepiaOnSurfaceVariant = Color(0xFF60513C)
    val SepiaSurfaceDim = Color(0xFFE2D7BC)
    val SepiaSurfaceBright = Color(0xFFFBF5E6)
    val SepiaContainerLowest = Color(0xFFFDF8EC)
    val SepiaContainerLow = Color(0xFFF7F0DF)
    val SepiaContainer = Color(0xFFEFE6D0)
    val SepiaContainerHigh = Color(0xFFE8DCC0)
    val SepiaContainerHighest = Color(0xFFE0D3B3)
    val SepiaOutline = Color(0xFF9A8B70)
    val SepiaOutlineVariant = Color(0xFFC9B896)
    val SepiaInverseSurface = Color(0xFF3A3025)
    val SepiaInverseOnSurface = Color(0xFFF7F0DF)
    val SepiaInversePrimary = Color(0xFFE0CFA8)

    // --- Shared ---
    val ErrorLight = Color(0xFFB3261E)
    val OnErrorLight = Color(0xFFFFFFFF)
    val ErrorContainerLight = Color(0xFFF9DEDC)
    val OnErrorContainerLight = Color(0xFF410E0B)
    val ErrorDark = Color(0xFFF2B8B5)
    val OnErrorDark = Color(0xFF601410)
    val ErrorContainerDark = Color(0xFF8C1D18)
    val OnErrorContainerDark = Color(0xFFF9DEDC)
    val Scrim = Color(0xFF000000)
}
