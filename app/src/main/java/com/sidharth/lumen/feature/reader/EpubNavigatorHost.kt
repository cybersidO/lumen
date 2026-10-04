package com.sidharth.lumen.feature.reader

import android.view.ActionMode
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
import com.sidharth.lumen.core.database.HighlightEntity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sidharth.lumen.core.settings.HighlightStyle
import com.sidharth.lumen.feature.highlight.HighlightColor
import com.sidharth.lumen.feature.reader.gesture.ReaderGestureLayout
import com.sidharth.lumen.feature.reader.gesture.TapAction
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.DecorableNavigator
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
    onGestureLayoutReady: (ReaderGestureLayout) -> Unit = {},
    selectionActionModeCallback: ActionMode.Callback? = null,
) {
    val activity = androidx.activity.compose.LocalActivity.current as FragmentActivity
    val containerId = remember { View.generateViewId() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            // The gesture layer must be a *View* wrapping the fragment container — the
            // M2 lesson: a Compose overlay above the WebView eats its touches. As a
            // parent ViewGroup it can observe every event and steal only vertical
            // edge-strip drags, leaving taps, swipes and selection for the WebView.
            ReaderGestureLayout(context).apply {
                addView(
                    FragmentContainerView(context).apply { id = containerId },
                )
                onGestureLayoutReady(this)
            }
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
                configuration = EpubNavigatorFragment.Configuration().apply {
                    // Replaces the system text-selection menu with Lumen's own toolbar.
                    selectionActionModeCallback?.let { this.selectionActionModeCallback = it }
                },
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
 * Routes taps through Readium's own input pipeline and resolves them into zone actions.
 *
 * Readium's `TapEvent` only fires for genuine content taps — link clicks and selection
 * taps are filtered at the JS level — so resolving §2.3's tap zones here means a tap on a
 * footnote link follows the link instead of also turning a page. The View-level gesture
 * layout deliberately does not resolve single-finger taps for exactly this reason.
 *
 * Page-turn taps return true (consumed); Readium's default edge-tap handling would
 * otherwise also fire and double-turn.
 */
@Composable
fun ReaderTapListener(
    navigator: EpubNavigatorFragment?,
    resolveAction: (x: Float, viewWidth: Int) -> TapAction,
    onAction: (TapAction) -> Unit,
) {
    DisposableEffect(navigator) {
        val current = navigator
        val listener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val width = current?.view?.width ?: 0
                val action = resolveAction(event.point.x, width)
                if (action == TapAction.NONE) return false
                onAction(action)
                return action != TapAction.TOGGLE_CHROME
            }
        }
        current?.addInputListener(listener)
        onDispose { current?.removeInputListener(listener) }
    }
}

/**
 * Keeps the rendered highlight decorations in step with the database and the theme.
 *
 * The decoration id carries the row id (`hl-<id>`), which is how a tap on a rendered
 * highlight finds its way back to the entity for editing.
 */
@Composable
fun HighlightDecorations(
    navigator: EpubNavigatorFragment?,
    highlights: List<HighlightEntity>,
    isDark: Boolean,
    style: HighlightStyle,
    onHighlightTapped: (Long) -> Unit,
) {
    LaunchedEffect(navigator, highlights, isDark, style) {
        val decorable = navigator as? DecorableNavigator ?: return@LaunchedEffect
        val decorations = highlights.mapNotNull { entity ->
            val locator = entity.locatorJson?.let { json ->
                runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
            } ?: return@mapNotNull null
            val tint = HighlightColor.fromId(entity.colorId).tintFor(isDark)
            Decoration(
                id = "hl-${entity.id}",
                locator = locator,
                // Same colour, two renderings (M10). Both are Readium-native styles, so
                // the rows are untouched and switching is instant.
                style = when (style) {
                    HighlightStyle.FILL -> Decoration.Style.Highlight(tint = tint, isActive = false)
                    HighlightStyle.UNDERLINE -> Decoration.Style.Underline(tint = tint, isActive = false)
                },
            )
        }
        decorable.applyDecorations(decorations, group = DECORATION_GROUP)
    }

    DisposableEffect(navigator) {
        val decorable = navigator as? DecorableNavigator
        val listener = object : DecorableNavigator.Listener {
            override fun onDecorationActivated(
                event: DecorableNavigator.OnActivatedEvent,
            ): Boolean {
                val id = event.decoration.id.removePrefix("hl-").toLongOrNull() ?: return false
                onHighlightTapped(id)
                return true
            }
        }
        decorable?.addDecorationListener(DECORATION_GROUP, listener)
        onDispose { decorable?.removeDecorationListener(listener) }
    }
}

private const val DECORATION_GROUP = "lumen-highlights"
private const val SPEECH_GROUP = "lumen-speech"

/**
 * Tints the sentence being read aloud (M11), in its own decoration group so it layers
 * over the user's highlights instead of competing with them for the same group.
 */
@Composable
fun SpeechDecoration(navigator: EpubNavigatorFragment?, locator: Locator?, tint: Color) {
    val argb = tint.toArgb()
    LaunchedEffect(navigator, locator, argb) {
        val decorable = navigator as? DecorableNavigator ?: return@LaunchedEffect
        val decorations = locator?.let {
            listOf(
                Decoration(
                    id = "speech-current",
                    locator = it,
                    style = Decoration.Style.Highlight(tint = argb, isActive = false),
                ),
            )
        }.orEmpty()
        decorable.applyDecorations(decorations, group = SPEECH_GROUP)
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
