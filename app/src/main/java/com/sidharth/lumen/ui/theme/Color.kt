package com.sidharth.lumen.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Four hand-tuned palettes. Deliberately not Material You / dynamic colour: a reader's
 * page should look the same every day regardless of the wallpaper, and sepia and AMOLED
 * have no dynamic equivalent.
 *
 * Light and dark are NOT the same hues at different luminance — the dark palette is
 * warmed slightly so that white text on it doesn't read as blue at night.
 */
internal object LumenColors {

    // --- Light: warm paper ---
    val LightBackground = Color(0xFFFBF7F2)
    val LightSurface = Color(0xFFFFFCF8)
    val LightSurfaceVariant = Color(0xFFEFE7DC)
    val LightOnBackground = Color(0xFF1B1815)
    val LightOnSurfaceVariant = Color(0xFF534B41)
    val LightPrimary = Color(0xFF7A5D33)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFE9DCC6)
    val LightOnPrimaryContainer = Color(0xFF2A1F0D)
    val LightSecondary = Color(0xFF4F5B54)
    val LightOutline = Color(0xFF847A6D)
    val LightOutlineVariant = Color(0xFFD5C9B8)

    // --- Dark: soft charcoal ---
    val DarkBackground = Color(0xFF121212)
    val DarkSurface = Color(0xFF1A1917)
    val DarkSurfaceVariant = Color(0xFF2A2825)
    val DarkOnBackground = Color(0xFFE7E1D8)
    val DarkOnSurfaceVariant = Color(0xFFCFC6B8)
    val DarkPrimary = Color(0xFFDCBE8F)
    val DarkOnPrimary = Color(0xFF3B2C12)
    val DarkPrimaryContainer = Color(0xFF544225)
    val DarkOnPrimaryContainer = Color(0xFFF7E5C7)
    val DarkSecondary = Color(0xFFB6C5BB)
    val DarkOutline = Color(0xFF988F82)
    val DarkOutlineVariant = Color(0xFF4A453E)

    // --- AMOLED: true black, so unlit pixels stay unlit ---
    val AmoledBackground = Color(0xFF000000)
    val AmoledSurface = Color(0xFF000000)
    val AmoledSurfaceVariant = Color(0xFF161513)
    val AmoledOutlineVariant = Color(0xFF34302B)

    // --- Sepia: aged paper, deliberately low contrast ---
    val SepiaBackground = Color(0xFFF4ECD8)
    val SepiaSurface = Color(0xFFF7F0DF)
    val SepiaSurfaceVariant = Color(0xFFE6D9BC)
    val SepiaOnBackground = Color(0xFF4A3B2A)
    val SepiaOnSurfaceVariant = Color(0xFF60513C)
    val SepiaPrimary = Color(0xFF8A6A3D)
    val SepiaOnPrimary = Color(0xFFFFF8EC)
    val SepiaPrimaryContainer = Color(0xFFE0CFA8)
    val SepiaOnPrimaryContainer = Color(0xFF33260F)
    val SepiaSecondary = Color(0xFF6B6350)
    val SepiaOutline = Color(0xFF9A8B70)
    val SepiaOutlineVariant = Color(0xFFC9B896)

    // --- Shared ---
    val ErrorLight = Color(0xFFB3261E)
    val ErrorDark = Color(0xFFF2B8B5)
    val OnErrorLight = Color(0xFFFFFFFF)
    val OnErrorDark = Color(0xFF601410)
}
