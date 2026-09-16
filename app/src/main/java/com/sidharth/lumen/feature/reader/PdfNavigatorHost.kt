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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sidharth.lumen.feature.reader.gesture.ReaderGestureLayout
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.adapter.pdfium.navigator.PdfiumSettings
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

private const val NAVIGATOR_TAG = "lumen-pdf-navigator"

/** The concrete PDF navigator type, spelled once so callers don't repeat the generics. */
typealias LumenPdfNavigator = PdfNavigatorFragment<PdfiumSettings, PdfiumPreferences>

/**
 * Hosts Readium's [PdfNavigatorFragment] inside Compose, mirroring [EpubNavigatorHost].
 *
 * The two hosts stay separate rather than being merged behind one interface, because the
 * navigators genuinely differ in kind: the EPUB one is `Decorable` and `Selectable` and
 * takes typography preferences, and the PDF one is none of those things (§M8 limits).
 * Pretending otherwise would mean a shared abstraction whose PDF half is mostly
 * `UnsupportedOperationException`, which is exactly the silent-stub failure §7 forbids.
 *
 * What this host does provide, verified against the AAR: page turns via
 * `OverflowableNavigator`, position through `currentLocator`, and restore through `go()`.
 */
@Composable
fun PdfNavigatorHost(
    publication: Publication,
    initialLocator: Locator?,
    onNavigatorReady: (LumenPdfNavigator) -> Unit,
    modifier: Modifier = Modifier,
    onGestureLayoutReady: (ReaderGestureLayout) -> Unit = {},
) {
    val activity = androidx.activity.compose.LocalActivity.current as FragmentActivity
    val containerId = remember { View.generateViewId() }
    val engineProvider = remember { PdfiumEngineProvider() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            // Same View-level gesture seam as the EPUB host: brightness and font-size
            // edge drags are resolved by this ViewGroup before the page view sees them.
            ReaderGestureLayout(context).apply {
                addView(FragmentContainerView(context).apply { id = containerId })
                onGestureLayoutReady(this)
            }
        },
    )

    DisposableEffect(publication, containerId) {
        val fragmentManager = activity.supportFragmentManager

        fragmentManager.fragmentFactory =
            PdfNavigatorFactory(publication, engineProvider).createFragmentFactory(
                initialLocator = initialLocator,
                // Defaults: the reader's own gesture layer owns page turns and brightness,
                // so nothing is overridden here.
                initialPreferences = PdfiumPreferences(),
                listener = null,
            )

        fragmentManager.commitNow {
            replace(containerId, PdfNavigatorFragment::class.java, null, NAVIGATOR_TAG)
        }

        @Suppress("UNCHECKED_CAST")
        val fragment =
            fragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? LumenPdfNavigator
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
 * Mirrors `TrackLocator` for PDFs: every page change is persisted, so closing on page 40
 * of 300 reopens on page 40.
 */
@Composable
fun TrackPdfLocator(
    navigator: LumenPdfNavigator?,
    onLocatorChanged: (Locator) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(navigator, lifecycleOwner) {
        val current = navigator ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            current.currentLocator.collect { onLocatorChanged(it) }
        }
    }
}
