package com.sidharth.lumen.feature.reader.input

import android.view.KeyEvent
import javax.inject.Inject
import javax.inject.Singleton

/** Which way a volume press should move the book. */
enum class VolumeKeyAction { NEXT_PAGE, PREVIOUS_PAGE }

/**
 * Routes hardware volume presses from the Activity to whichever reader is on screen.
 *
 * The Activity sees every key; the reader is a composable that comes and goes. So the
 * reader registers a handler while it is visible and unregisters on leaving, and the
 * Activity asks this object first. When no handler is registered — library, settings,
 * notes — the keys fall through and change the volume as normal.
 *
 * Both DOWN and UP are consumed for a handled press so the system volume panel never
 * flashes over the page.
 */
@Singleton
class VolumeKeyDispatcher @Inject constructor() {

    @Volatile
    private var handler: ((VolumeKeyAction) -> Boolean)? = null

    fun register(handler: (VolumeKeyAction) -> Boolean) {
        this.handler = handler
    }

    fun unregister(handler: (VolumeKeyAction) -> Boolean) {
        if (this.handler === handler) this.handler = null
    }

    /** True if the press was taken by a reader and must not reach the volume controller. */
    fun onKeyDown(keyCode: Int): Boolean {
        val action = when (keyCode) {
            // Down = forward mirrors the way a thumb rests on a phone held one-handed.
            KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeKeyAction.NEXT_PAGE
            KeyEvent.KEYCODE_VOLUME_UP -> VolumeKeyAction.PREVIOUS_PAGE
            else -> return false
        }
        return handler?.invoke(action) ?: false
    }

    fun onKeyUp(keyCode: Int): Boolean =
        handler != null &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
}
