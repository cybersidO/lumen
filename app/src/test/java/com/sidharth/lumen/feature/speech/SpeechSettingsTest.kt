package com.sidharth.lumen.feature.speech

import com.sidharth.lumen.core.settings.SpeechSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSettingsTest {

    @Test
    fun defaultsAreNeutralAndTimerOff() {
        val d = SpeechSettings.Defaults
        assertEquals(1f, d.speed); assertEquals(1f, d.pitch)
        assertNull(d.voiceId); assertEquals(0, d.sleepTimerMinutes)
    }

    @Test
    fun sanitisedClampsSpeedPitchAndTimer() {
        val s = SpeechSettings(speed = 9f, pitch = 0.01f, sleepTimerMinutes = 9999).sanitised()
        assertEquals(SpeechSettings.MAX_SPEED, s.speed)
        assertEquals(SpeechSettings.MIN_PITCH, s.pitch)
        assertEquals(SpeechSettings.MAX_SLEEP_MINUTES, s.sleepTimerMinutes)
    }

    @Test
    fun aNegativeTimerBecomesOffRatherThanNegative() {
        assertEquals(0, SpeechSettings(sleepTimerMinutes = -5).sanitised().sleepTimerMinutes)
    }

    @Test
    fun sleepPresetsStartWithOffAndAscend() {
        val p = SpeechSettings.SLEEP_PRESETS
        assertEquals(0, p.first())
        assertEquals(p.sorted(), p)
        assertTrue(p.all { it <= SpeechSettings.MAX_SLEEP_MINUTES })
    }
}
