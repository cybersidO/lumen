package com.sidharth.lumen.feature.highlight

import androidx.compose.ui.graphics.Color

/**
 * The five §2.5 colours. [lightTint]/[darkTint] are what Readium's decoration CSS gets —
 * deliberately different per theme, not one colour at two alphas: a saturated marker
 * yellow that reads well on paper turns to mud on #121212, so dark tints are brighter and
 * lean on the engine's blend rather than heavier opacity.
 */
enum class HighlightColor(
    val id: Int,
    val displayName: String,
    val lightTint: Long,
    val darkTint: Long,
) {
    YELLOW(0, "Yellow", 0xFFF2D14E, 0xFFB89A2A),
    GREEN(1, "Green", 0xFF9CD67C, 0xFF5F9147),
    BLUE(2, "Blue", 0xFF8EC7EE, 0xFF4A7FA8),
    PINK(3, "Pink", 0xFFF2A9C4, 0xFFA85E7D),
    PURPLE(4, "Purple", 0xFFC5A9F2, 0xFF7C5EA8),
    ;

    fun tintFor(isDark: Boolean): Int = (if (isDark) darkTint else lightTint).toInt()

    /** Swatch colour for pickers, in the app's own UI rather than the book. */
    val swatch: Color get() = Color(lightTint)

    companion object {
        fun fromId(id: Int): HighlightColor = entries.firstOrNull { it.id == id } ?: YELLOW
    }
}
