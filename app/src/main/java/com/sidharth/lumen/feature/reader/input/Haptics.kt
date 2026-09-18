package com.sidharth.lumen.feature.reader.input

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * The two ticks the reader uses. Routed through [View.performHapticFeedback] rather than
 * a Vibrator so it honours the system's "touch feedback" setting and needs no permission.
 */
object Haptics {

    /** A light tick: selection handles appeared. */
    fun light(view: View?, enabled: Boolean) {
        if (!enabled || view == null) return
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** A firmer tick: something was committed — a highlight saved. */
    fun firm(view: View?, enabled: Boolean) {
        if (!enabled || view == null) return
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}
