package com.sidharth.lumen.feature.reader

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sidharth.lumen.core.settings.ReadingSettings
import com.sidharth.lumen.feature.reader.input.VolumeKeyAction
import com.sidharth.lumen.feature.reader.input.VolumeKeyDispatcher
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

const val WarmTintTag = "reader_warm_tint"

/**
 * M10's small comforts, each a self-contained effect so the reader screen stays readable.
 * Every one of them restores what it changed on dispose — leaving the reader must never
 * leave the phone locked in landscape or unable to sleep.
 */

/**
 * Keeps the screen awake while reading, and lets it sleep again after [minutes] of no
 * page turns (0 = never). [interactionKey] is anything that changes on a page turn.
 */
@Composable
fun KeepScreenOn(activity: Activity?, enabled: Boolean, minutes: Int, interactionKey: Any?) {
    val window = activity?.window ?: return
    DisposableEffect(window, enabled) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    // Re-arm on every turn; the delay only completes when the reader has gone quiet.
    LaunchedEffect(window, enabled, minutes, interactionKey) {
        if (!enabled) return@LaunchedEffect
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (minutes <= 0) return@LaunchedEffect
        delay(TimeUnit.MINUTES.toMillis(minutes.toLong()))
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

/** Pins the orientation to whatever it is when reading starts. */
@Composable
fun LockOrientation(activity: Activity?, locked: Boolean) {
    DisposableEffect(activity, locked) {
        val target = activity ?: return@DisposableEffect onDispose { }
        val previous = target.requestedOrientation
        if (locked) target.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose { target.requestedOrientation = previous }
    }
}

/**
 * A warm amber wash for late reading. It sits above the page and below the chrome, and
 * its ceiling is deliberately low so the text stays readable at full strength. This is
 * an overlay rather than a Readium theme because the point is to *combine* it with
 * whichever theme is on.
 */
@Composable
fun WarmTintOverlay(strength: Float) {
    if (strength <= 0f) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WarmTintTag)
            .background(WarmTintColor.copy(alpha = strength * ReadingSettings.WARM_TINT_MAX_ALPHA)),
    )
}

private val WarmTintColor = Color(0xFFFF9E3D)

/**
 * Claims the volume keys for page turning while this composable is on screen, and only
 * while the app is resumed — a press on the lock screen must still be a volume press.
 */
@Composable
fun VolumeKeyPaging(
    dispatcher: VolumeKeyDispatcher,
    enabled: Boolean,
    onAction: (VolumeKeyAction) -> Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(dispatcher, enabled, lifecycleOwner, onAction) {
        if (!enabled) return@DisposableEffect onDispose { }
        val handler: (VolumeKeyAction) -> Boolean = { onAction(it) }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> dispatcher.register(handler)
                Lifecycle.Event.ON_PAUSE -> dispatcher.unregister(handler)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            dispatcher.register(handler)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            dispatcher.unregister(handler)
        }
    }
}

/** Tells the ViewModel when the reader is genuinely in front of the user, for the stats. */
@Composable
fun ReaderVisibility(onVisible: () -> Unit, onHidden: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, onVisible, onHidden) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> onVisible()
                Lifecycle.Event.ON_STOP -> onHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onVisible()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onHidden()
        }
    }
}
