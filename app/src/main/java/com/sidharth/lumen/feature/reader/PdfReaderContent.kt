package com.sidharth.lumen.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidharth.lumen.R
import com.sidharth.lumen.core.settings.AppSettings
import com.sidharth.lumen.feature.reader.gesture.ApplyWindowBrightness
import com.sidharth.lumen.feature.reader.gesture.DimOverlay
import com.sidharth.lumen.feature.reader.gesture.GestureGeometry
import com.sidharth.lumen.feature.reader.gesture.ReaderGestureLayout
import com.sidharth.lumen.feature.reader.gesture.TapAction

const val PdfReaderSurfaceTag = "pdf_reader_surface"
const val PdfNoTextLayerTag = "pdf_no_text_layer"

/**
 * The PDF half of the reader.
 *
 * Deliberately a smaller screen than the EPUB one, because the PDF navigator genuinely
 * supports less (verified against the 3.3.0 AARs, recorded in DECISIONS.md):
 *
 *  * **No text layer.** The PDFium Java binding exposes no text API at all, so there is no
 *    word to select, look up, or anchor a highlight to. Tapping the page says so out loud
 *    rather than silently doing nothing (§7: never a fake success).
 *  * **No decorations.** `PdfNavigatorFragment` is not `DecorableNavigator`, so highlights
 *    cannot be drawn by the navigator the way M4 does for EPUB.
 *  * **No typography.** Reflow is meaningless for a fixed-layout page, so the font-size
 *    edge drag is not wired and the typography sheet is not offered.
 *
 * What does work is the reading itself: rendering, page turns through the same §2.3 tap
 * zones, brightness, and a position that survives closing the book.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderContent(
    state: ReaderUiState.Ready,
    settings: AppSettings,
    chrome: ReaderChromeState,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var navigator by remember { mutableStateOf<LumenPdfNavigator?>(null) }
    var gestureLayout by remember { mutableStateOf<ReaderGestureLayout?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showToc by remember { mutableStateOf(false) }

    val activity = LocalActivity.current
    val liveBrightness by viewModel.liveBrightness.collectAsStateWithLifecycle()
    val toc by viewModel.tableOfContents.collectAsStateWithLifecycle()
    val noTextLayerMessage = stringResource(R.string.pdf_no_text_layer)

    Box(modifier = modifier.fillMaxSize()) {
        PdfNavigatorHost(
            publication = state.publication,
            initialLocator = state.initialLocator,
            onNavigatorReady = { navigator = it },
            modifier = Modifier.fillMaxSize().testTag(PdfReaderSurfaceTag),
            onGestureLayoutReady = { gestureLayout = it },
        )

        // Page turns and chrome come from the same zone maths as EPUB, so the reader's
        // muscle memory carries across formats. Resolved at the View layer here because
        // there is no Readium JS pipeline to ask on a PDF.
        LaunchedEffect(gestureLayout, settings.gestures) {
            gestureLayout?.apply {
                this.settings = settings.gestures.sanitised()
                this.isRtl = false
                currentBrightness = { viewModel.brightnessSliderValue() }
                onBrightnessChange = viewModel::onBrightnessDrag
                onBrightnessDragEnd = viewModel::commitBrightness
                // Font size is deliberately inert: a PDF page does not reflow, so the
                // right-edge drag is left as a no-op rather than moving a number that
                // changes nothing on screen.
                onFontSizeStep = {}
                onFontSizeDragEnd = {}
                onTwoFingerTap = viewModel::toggleThemeFromGesture
                onSingleTap = { x, width ->
                    val action = GestureGeometry.tapActionFor(
                        zone = GestureGeometry.zoneFor(
                            x = x,
                            width = width.toFloat(),
                            settings = settings.gestures,
                        ),
                        settings = settings.gestures,
                        isRtl = false,
                    )
                    when (action) {
                        TapAction.NEXT_PAGE -> navigator?.goForward(animated = true)
                        TapAction.PREVIOUS_PAGE -> navigator?.goBackward(animated = true)
                        TapAction.TOGGLE_CHROME -> viewModel.toggleChrome()
                        TapAction.NONE -> Unit
                    }
                }
                onLongPress = { notice = noTextLayerMessage }
            }
        }

        TrackPdfLocator(navigator = navigator, onLocatorChanged = viewModel::onLocatorChanged)

        val effectiveBrightness = liveBrightness ?: settings.brightness
        ApplyWindowBrightness(activity = activity, brightness = effectiveBrightness)
        DimOverlay(alpha = effectiveBrightness.extraDim)

        ReaderChrome(
            visible = chrome.visible,
            title = chrome.chapterTitle ?: state.title,
            progressFraction = chrome.progressFraction,
            onBack = onBack,
            onOpenToc = { showToc = true },
            // No typography button: see the comment on this function.
            onOpenTypography = null,
        )

        // A PDF's own outline, where it has one — Readium's PDF parser reads the bookmark
        // tree the same way the EPUB parser reads a nav document, so this is the same
        // sheet with a different source.
        if (showToc) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showToc = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                TableOfContentsSheet(
                    entries = toc,
                    onSelect = { entry ->
                        navigator?.go(entry.link, animated = false)
                        showToc = false
                        viewModel.hideChrome()
                    },
                )
            }
        }

        // The honest "not available here" state. A long press on an EPUB starts a
        // selection; on a PDF there is nothing to select, and saying nothing would read
        // as the app being broken.
        notice?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(2600)
                notice = null
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .testTag(PdfNoTextLayerTag)
                        .navigationBarsPadding()
                        .padding(bottom = 90.dp, start = 24.dp, end = 24.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
