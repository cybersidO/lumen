package com.sidharth.lumen.feature.reader.gesture

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.sidharth.lumen.core.settings.BrightnessSettings

/**
 * Brightness for the reader window only.
 *
 * `WindowManager.LayoutParams.screenBrightness` applies to this window and nothing else,
 * so it needs no `WRITE_SETTINGS`, never touches the user's system setting, and is undone
 * automatically the moment they leave the app. `BRIGHTNESS_OVERRIDE_NONE` (-1f) is the
 * "follow system" sentinel §2.3 asks for.
 *
 * Below the OS minimum there is nothing left to give, so further dimming is drawn as a
 * black overlay instead — see `DimOverlay`.
 */
@Composable
fun ApplyWindowBrightness(activity: Activity?, brightness: BrightnessSettings) {
    DisposableEffect(activity, brightness.windowBrightness) {
        val window = activity?.window
        if (window != null) {
            window.attributes = window.attributes.apply {
                screenBrightness = brightness.windowBrightness
                    ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        onDispose {
            // Leaving the reader hands brightness back to the system, so the library and
            // settings screens are never stuck at the dim level chosen for night reading.
            val w = activity?.window ?: return@onDispose
            w.attributes = w.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}
