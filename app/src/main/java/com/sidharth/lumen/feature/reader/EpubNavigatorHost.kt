package com.sidharth.lumen.feature.reader

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

private const val NAVIGATOR_TAG = "lumen-epub-navigator"

/**
 * Hosts Readium's [EpubNavigatorFragment] inside Compose.
 *
 * Readium's EPUB renderer is a Fragment, not a composable, so this is a genuine
 * interop seam rather than a shortcut. Two things make it work:
 *
 *  1. The navigator can only be constructed by the `FragmentFactory` Readium hands back,
 *     because it needs the already-open [Publication]. The factory is installed on the
 *     activity's FragmentManager immediately before the transaction that creates it.
 *  2. `MainActivity` drops saved fragment state on process death (see its comment), so the
 *     FragmentManager never tries to restore a navigator before a publication exists. The
 *     reading position is restored from the database instead — which is the source of
 *     truth the milestone actually requires.
 */
@Composable
fun EpubNavigatorHost(
    publication: Publication,
    initialLocator: Locator?,
    preferences: EpubPreferences,
    onNavigatorReady: (EpubNavigatorFragment) -> Unit,
    onLocatorChanged: (Locator) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = androidx.activity.compose.LocalActivity.current as FragmentActivity
    val containerId = remember { View.generateViewId() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            FragmentContainerView(context).apply { id = containerId }
        },
    )

    // Keyed on the publication: a different book means a different navigator, and the old
    // one must go. Preferences deliberately are NOT a key — changing the font size submits
    // to the live navigator instead of rebuilding it, which is what keeps the reader's
    // place across a font-size change.
    DisposableEffect(publication, containerId) {
        val fragmentManager = activity.supportFragmentManager

        fragmentManager.fragmentFactory = EpubNavigatorFactory(publication)
            .createFragmentFactory(
                initialLocator = initialLocator,
                initialPreferences = preferences,
            )

        fragmentManager.commitNow {
            replace(containerId, EpubNavigatorFragment::class.java, null, NAVIGATOR_TAG)
        }

        val fragment = fragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
        if (fragment != null) onNavigatorReady(fragment)

        onDispose {
            if (!activity.isFinishing && !fragmentManager.isDestroyed) {
                fragmentManager.findFragmentByTag(NAVIGATOR_TAG)?.let { existing ->
                    fragmentManager.commitNow { remove(existing) }
                }
            }
        }
    }
}

/**
 * Routes taps through Readium's own input pipeline.
 *
 * The navigator renders into a WebView, so anything layered over it in Compose intercepts
 * touches before the WebView gets them — which breaks swipe-to-turn-page without any
 * visible sign that it has. Registering here instead leaves Readium's own gesture handling
 * intact. Returning false marks the tap unconsumed so Readium can still act on it.
 */
@Composable
fun ReaderTapListener(
    navigator: EpubNavigatorFragment?,
    onTap: () -> Unit,
) {
    DisposableEffect(navigator) {
        val current = navigator
        val listener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                onTap()
                return false
            }
        }
        current?.addInputListener(listener)
        onDispose { current?.removeInputListener(listener) }
    }
}

/**
 * Mirrors the navigator's position into the ViewModel.
 *
 * `currentLocator` is a StateFlow that updates on every page turn *and* after a
 * repagination caused by a settings change, so this is the single place progress is
 * recorded — there is no separate page-turn callback to keep in sync.
 */
@Composable
fun TrackLocator(
    navigator: EpubNavigatorFragment?,
    onLocatorChanged: (Locator) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(navigator) {
        val current = navigator ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            current.currentLocator.collect(onLocatorChanged)
        }
    }
}
