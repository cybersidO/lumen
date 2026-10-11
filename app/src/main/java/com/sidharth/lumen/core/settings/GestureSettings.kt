package com.sidharth.lumen.core.settings

/**
 * §2.3 requires every zone size and every gesture's enable/disable to be a setting.
 *
 * Zones are stored as fractions of screen width so they survive rotation and different
 * devices; the spec's defaults (15% edge strips, 25% tap zones, 50% centre) are the
 * starting values.
 */
data class GestureSettings(
    val edgeStripFraction: Float = DEFAULT_EDGE_STRIP,
    val tapZoneFraction: Float = DEFAULT_TAP_ZONE,

    /**
     * All three off by default since 1.6.0.
     *
     * The owner's verdict after living with them: a swipe up or down should scroll and do
     * nothing else. Each of these took over a vertical drag — brightness on the left
     * fifteen percent, font size on the right — or turned the page from a tap near an
     * edge, and all three fired by accident far more often than on purpose. They remain
     * switchable in Settings for anyone who wants them back; they are simply no longer
     * what the app does out of the box.
     */
    val brightnessDragEnabled: Boolean = false,
    val fontSizeDragEnabled: Boolean = false,
    val tapToTurnPageEnabled: Boolean = false,
    val twoFingerTapThemeEnabled: Boolean = false,
) {
    companion object {
        const val MIN_EDGE_STRIP = 0.05f
        const val MAX_EDGE_STRIP = 0.30f
        const val DEFAULT_EDGE_STRIP = 0.15f

        const val MIN_TAP_ZONE = 0.10f
        const val MAX_TAP_ZONE = 0.40f
        const val DEFAULT_TAP_ZONE = 0.25f

        val Defaults = GestureSettings()
    }

    /**
     * Tap zones and edge strips overlap along the screen edge, and the drag gesture wins
     * there — a vertical drag in the left strip must never turn a page (§2.3). Clamping
     * keeps the centre (chrome) zone from vanishing entirely when both are widened.
     */
    val centreTapFraction: Float get() = (1f - 2f * tapZoneFraction).coerceAtLeast(0f)

    fun sanitised(): GestureSettings = copy(
        edgeStripFraction = edgeStripFraction.coerceIn(MIN_EDGE_STRIP, MAX_EDGE_STRIP),
        tapZoneFraction = tapZoneFraction.coerceIn(MIN_TAP_ZONE, MAX_TAP_ZONE),
    )
}

/** Screen brightness while reading, independent of the system setting (§2.3). */
data class BrightnessSettings(
    /**
     * `null` means "follow system" — the sentinel the spec asks for. Otherwise 0f..1f,
     * applied to the activity window only, which is why no WRITE_SETTINGS permission is
     * needed and why leaving the app restores the system brightness automatically.
     */
    val windowBrightness: Float? = null,

    /**
     * Extra dimming *below* the OS minimum, drawn as a black overlay (§2.3). 0f = none,
     * 1f would be fully black; capped well short of that so the screen can never be made
     * unrecoverably dark.
     */
    val extraDim: Float = 0f,
) {
    companion object {
        const val MAX_EXTRA_DIM = 0.85f
        const val FOLLOW_SYSTEM = -1f

        /**
         * The bottom slice of the drag's travel is spent going *below* the OS minimum,
         * where `screenBrightness` has nothing left to give and only a black overlay can
         * darken things further (§2.3 — "stock minimum is too bright").
         */
        const val SUB_MINIMUM_TRAVEL = 0.25f

        val Defaults = BrightnessSettings()

        /**
         * One continuous 0..1 drag maps onto two mechanisms: window brightness above the
         * floor, extra dimming below it. Continuous by construction, so the drag never
         * feels steppy (§2.3).
         */
        fun fromSlider(value: Float): BrightnessSettings {
            val v = value.coerceIn(0f, 1f)
            return if (v >= SUB_MINIMUM_TRAVEL) {
                BrightnessSettings(
                    windowBrightness = (v - SUB_MINIMUM_TRAVEL) / (1f - SUB_MINIMUM_TRAVEL),
                    extraDim = 0f,
                )
            } else {
                BrightnessSettings(
                    windowBrightness = 0f,
                    extraDim = (1f - v / SUB_MINIMUM_TRAVEL) * MAX_EXTRA_DIM,
                )
            }
        }
    }

    /** The inverse of [fromSlider], so a drag resumes from where the last one left off. */
    fun toSlider(): Float = when {
        extraDim > 0f -> (1f - extraDim / MAX_EXTRA_DIM) * SUB_MINIMUM_TRAVEL
        else -> SUB_MINIMUM_TRAVEL + (windowBrightness ?: 0.5f) * (1f - SUB_MINIMUM_TRAVEL)
    }.coerceIn(0f, 1f)

    fun sanitised(): BrightnessSettings = copy(
        windowBrightness = windowBrightness?.coerceIn(0f, 1f),
        extraDim = extraDim.coerceIn(0f, MAX_EXTRA_DIM),
    )
}
