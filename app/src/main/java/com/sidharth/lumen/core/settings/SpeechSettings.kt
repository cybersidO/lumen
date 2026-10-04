package com.sidharth.lumen.core.settings

/**
 * Read-aloud preferences (M11). Speed and pitch are Readium's own multipliers; the voice
 * is stored by its Android voice id, which is stable across launches on one device but
 * meaningless on another — so a restored backup falls back to the default voice rather
 * than to a voice that is not installed.
 */
data class SpeechSettings(
    val speed: Float = DEFAULT_SPEED,
    val pitch: Float = DEFAULT_PITCH,
    /** Android voice id, or null to let the engine pick for the book's language. */
    val voiceId: String? = null,
    /** Minutes before read-aloud stops on its own; 0 = never. */
    val sleepTimerMinutes: Int = 0,
    /** Stop at the end of the current chapter instead of a clock. */
    val stopAtChapterEnd: Boolean = false,
) {
    fun sanitised(): SpeechSettings = copy(
        speed = speed.coerceIn(MIN_SPEED, MAX_SPEED),
        pitch = pitch.coerceIn(MIN_PITCH, MAX_PITCH),
        sleepTimerMinutes = sleepTimerMinutes.coerceIn(0, MAX_SLEEP_MINUTES),
    )

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3f
        const val DEFAULT_SPEED = 1f

        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2f
        const val DEFAULT_PITCH = 1f

        const val MAX_SLEEP_MINUTES = 120
        val SLEEP_PRESETS = listOf(0, 5, 15, 30, 45, 60)

        val Defaults = SpeechSettings()
    }
}
