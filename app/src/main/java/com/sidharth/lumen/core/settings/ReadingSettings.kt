package com.sidharth.lumen.core.settings

/** How a saved highlight is drawn on the page. The colour is the same either way. */
enum class HighlightStyle(val storageKey: String, val displayName: String) {
    FILL("fill", "Filled"),
    UNDERLINE("underline", "Underlined"),
    ;

    companion object {
        fun fromStorageKey(key: String?): HighlightStyle =
            entries.firstOrNull { it.storageKey == key } ?: FILL
    }
}

/**
 * Whether the book turns like pages or scrolls like a web page. Readium implements both;
 * this is the one switch that flips it. Tap zones still work in either — in scroll mode a
 * "page turn" scrolls by one screen.
 */
enum class PageFlow(val storageKey: String, val displayName: String) {
    PAGED("paged", "Turn pages (left ↔ right)"),
    SCROLL("scroll", "Scroll (up ↕ down)"),
    ;

    val isScroll: Boolean get() = this == SCROLL

    companion object {
        fun fromStorageKey(key: String?): PageFlow =
            entries.firstOrNull { it.storageKey == key } ?: SCROLL
    }
}

/**
 * M10's reading-comfort switches. All app-wide, like typography: they describe how this
 * reader likes to read, not any one book.
 */
data class ReadingSettings(
    val highlightStyle: HighlightStyle = HighlightStyle.FILL,
    val hapticsEnabled: Boolean = true,
    // Scrolling is the default (1.4.0): the owner found continuous scrolling easier than
    // tapping to turn, and page-turning is one tap away in the Aa sheet for anyone who
    // prefers it.
    val pageFlow: PageFlow = PageFlow.SCROLL,
    // Off by default (1.4.0): the owner found it fired while reaching for the volume and
    // turned pages by accident. Still available in Settings → Reading.
    val volumeButtonsTurnPages: Boolean = false,
    val keepScreenOn: Boolean = false,
    /** Idle minutes before the screen may sleep again. 0 means never. */
    val keepScreenOnMinutes: Int = DEFAULT_KEEP_SCREEN_ON_MINUTES,
    /** 0 = off, 1 = strongest. Applied as a warm overlay above the page. */
    val warmTint: Float = 0f,
    val lockOrientation: Boolean = false,
    val showChapterTimeLeft: Boolean = true,
    /** Hide the status and navigation bars while reading (M13). */
    val fullScreen: Boolean = true,
    /** Minutes a day the reader is aiming for; 0 means no goal (M13). */
    val dailyGoalMinutes: Int = DEFAULT_DAILY_GOAL_MINUTES,
    /** Auto-scroll speed as a multiplier of a comfortable base rate. */
    val autoScrollSpeed: Float = DEFAULT_AUTO_SCROLL_SPEED,
) {
    fun sanitised(): ReadingSettings = copy(
        keepScreenOnMinutes = keepScreenOnMinutes.coerceIn(0, MAX_KEEP_SCREEN_ON_MINUTES),
        dailyGoalMinutes = dailyGoalMinutes.coerceIn(0, MAX_DAILY_GOAL_MINUTES),
        warmTint = warmTint.coerceIn(0f, 1f),
        autoScrollSpeed = autoScrollSpeed.coerceIn(MIN_AUTO_SCROLL_SPEED, MAX_AUTO_SCROLL_SPEED),
    )

    companion object {
        const val DEFAULT_KEEP_SCREEN_ON_MINUTES = 10

        const val DEFAULT_DAILY_GOAL_MINUTES = 20
        const val MAX_DAILY_GOAL_MINUTES = 240
        val DAILY_GOAL_PRESETS = listOf(0, 10, 15, 20, 30, 45, 60)
        const val MAX_KEEP_SCREEN_ON_MINUTES = 60

        const val MIN_AUTO_SCROLL_SPEED = 0.25f
        const val MAX_AUTO_SCROLL_SPEED = 4f
        const val DEFAULT_AUTO_SCROLL_SPEED = 1f

        /** The overlay never goes fully opaque; at 1.0 the page is clearly amber, still readable. */
        const val WARM_TINT_MAX_ALPHA = 0.32f

        val Defaults = ReadingSettings()
    }
}
