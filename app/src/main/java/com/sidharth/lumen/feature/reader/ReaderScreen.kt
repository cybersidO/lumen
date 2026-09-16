package com.sidharth.lumen.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.widget.Toast
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.sidharth.lumen.feature.highlight.HighlightColor
import com.sidharth.lumen.feature.highlight.HighlightEditSheet
import com.sidharth.lumen.feature.highlight.SelectionToolbar
import com.sidharth.lumen.feature.dictionary.DefinitionSheet
import kotlinx.coroutines.launch
import com.sidharth.lumen.R
import com.sidharth.lumen.core.books.BookFormat
import com.sidharth.lumen.core.readium.readiumPreferences
import com.sidharth.lumen.core.settings.ResolvedTheme
import com.sidharth.lumen.feature.reader.gesture.ApplyWindowBrightness
import com.sidharth.lumen.feature.reader.gesture.DimOverlay
import com.sidharth.lumen.feature.reader.gesture.GestureGeometry
import com.sidharth.lumen.feature.reader.gesture.GestureIndicator
import com.sidharth.lumen.feature.reader.gesture.IndicatorSide
import com.sidharth.lumen.feature.reader.gesture.ReaderGestureLayout
import com.sidharth.lumen.feature.reader.gesture.TapAction
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.ReadingProgression

const val ReaderTocButtonTag = "reader_toc_button"
const val ReaderTypographyButtonTag = "reader_typography_button"
const val ReaderSurfaceTag = "reader_surface"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    resolvedTheme: ResolvedTheme,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chrome by viewModel.chrome.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val toc by viewModel.tableOfContents.collectAsStateWithLifecycle()

    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var showToc by remember { mutableStateOf(false) }
    var showTypography by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = uiState) {
            ReaderUiState.Loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )

            is ReaderUiState.Failed -> ReaderError(state.message, onBack)

            // PDFs render through a different navigator with a genuinely smaller feature
            // set (no text layer, no decorations — see PdfReaderContent), so they branch
            // here rather than being forced through the EPUB path.
            is ReaderUiState.Ready if state.format == BookFormat.PDF -> {
                PdfReaderContent(
                    state = state,
                    settings = settings,
                    chrome = chrome,
                    viewModel = viewModel,
                    onBack = onBack,
                )
            }

            is ReaderUiState.Ready -> {
                val preferences = remember(settings.typography, resolvedTheme) {
                    readiumPreferences(settings.typography, resolvedTheme)
                }

                val activity = LocalActivity.current
                val liveBrightness by viewModel.liveBrightness.collectAsStateWithLifecycle()
                val indicator by viewModel.indicator.collectAsStateWithLifecycle()
                val isRtl = remember(state.publication) {
                    state.publication.metadata.readingProgression ==
                        ReadingProgression.RTL
                }

                var gestureLayout by remember { mutableStateOf<ReaderGestureLayout?>(null) }

                val highlights by viewModel.highlights.collectAsStateWithLifecycle()
                val activeSelection by viewModel.activeSelection.collectAsStateWithLifecycle()
                val editingHighlight by viewModel.editingHighlight.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()

                // Suppresses the system selection menu and reports selection lifecycle.
                // The navigator reference is read through a holder because the callback
                // must exist before the fragment does.
                val navigatorHolder = rememberUpdatedState(navigator)
                val selectionCallback = remember {
                    object : ActionMode.Callback {
                        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                            menu.clear()
                            scope.launch {
                                val selection =
                                    navigatorHolder.value?.currentSelection()?.locator
                                viewModel.onSelectionChanged(selection)
                            }
                            return true
                        }

                        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                            menu.clear()
                            return true
                        }

                        override fun onActionItemClicked(
                            mode: ActionMode,
                            item: MenuItem,
                        ): Boolean = false

                        override fun onDestroyActionMode(mode: ActionMode) {
                            viewModel.clearSelection()
                        }
                    }
                }

                EpubNavigatorHost(
                    publication = state.publication,
                    initialLocator = state.initialLocator,
                    preferences = preferences,
                    onNavigatorReady = { navigator = it },
                    onLocatorChanged = viewModel::onLocatorChanged,
                    modifier = Modifier.fillMaxSize().testTag(ReaderSurfaceTag),
                    onGestureLayoutReady = { gestureLayout = it },
                    selectionActionModeCallback = selectionCallback,
                )

                HighlightDecorations(
                    navigator = navigator,
                    highlights = highlights,
                    isDark = resolvedTheme.isDark,
                    onHighlightTapped = viewModel::openHighlightEditor,
                )

                // The gesture layout is a plain View; its callbacks are re-pointed at the
                // current ViewModel state on every relevant change.
                LaunchedEffect(gestureLayout, settings.gestures, isRtl) {
                    gestureLayout?.apply {
                        this.settings = settings.gestures.sanitised()
                        this.isRtl = isRtl
                        currentBrightness = { viewModel.brightnessSliderValue() }
                        onBrightnessChange = viewModel::onBrightnessDrag
                        onBrightnessDragEnd = viewModel::commitBrightness
                        onFontSizeStep = viewModel::nudgeFontSize
                        onFontSizeDragEnd = viewModel::onFontSizeDragEnd
                        onTwoFingerTap = viewModel::toggleThemeFromGesture
                    }
                }

                TrackLocator(navigator = navigator, onLocatorChanged = viewModel::onLocatorChanged)

                // Typography changes are submitted to the live navigator rather than
                // rebuilding it — that is what keeps the reader's place when the font
                // size changes (§M2's done-when).
                ApplyPreferences(navigator = navigator, preferences = preferences)

                // §2.3's tap zones, resolved from Readium's TapEvent so link taps stay
                // link taps. Page turns are consumed; chrome toggles are not.
                ReaderTapListener(
                    navigator = navigator,
                    resolveAction = { x, width ->
                        GestureGeometry.tapActionFor(
                            zone = GestureGeometry.zoneFor(
                                x = x,
                                width = width.toFloat(),
                                settings = settings.gestures,
                            ),
                            settings = settings.gestures,
                            isRtl = isRtl,
                        )
                    },
                    onAction = { action ->
                        when (action) {
                            TapAction.NEXT_PAGE -> navigator?.goForward(animated = true)
                            TapAction.PREVIOUS_PAGE -> navigator?.goBackward(animated = true)
                            TapAction.TOGGLE_CHROME -> viewModel.toggleChrome()
                            TapAction.NONE -> Unit
                        }
                    },
                )

                // Window-local brightness (§2.3): live value during a drag, saved value
                // otherwise. The dim overlay carries the range below the OS floor.
                val effectiveBrightness = liveBrightness ?: settings.brightness
                ApplyWindowBrightness(activity = activity, brightness = effectiveBrightness)
                DimOverlay(alpha = effectiveBrightness.extraDim)

                indicator?.let { ind ->
                    GestureIndicator(
                        visible = true,
                        side = when (ind.kind) {
                            IndicatorKind.BRIGHTNESS -> IndicatorSide.LEFT
                            IndicatorKind.FONT_SIZE -> IndicatorSide.RIGHT
                        },
                        label = when (ind.kind) {
                            IndicatorKind.BRIGHTNESS ->
                                "${(ind.fraction * 100).toInt()}%"
                            IndicatorKind.FONT_SIZE ->
                                "${settings.typography.fontSizePercent}%"
                        },
                        fraction = ind.fraction,
                    )
                }

                ReaderChrome(
                    visible = chrome.visible,
                    title = chrome.chapterTitle ?: state.title,
                    progressFraction = chrome.progressFraction,
                    onBack = onBack,
                    onOpenToc = { showToc = true },
                    onOpenTypography = { showTypography = true },
                )

                val clipboard = LocalClipboardManager.current
                val context = LocalContext.current
                val copiedMessage = stringResource(R.string.selection_copied)
                var transientMessage by remember { mutableStateOf<String?>(null) }

                if (activeSelection != null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        SelectionToolbar(
                            onColor = { color ->
                                viewModel.createHighlight(color)
                                navigator?.clearSelection()
                            },
                            onNote = {
                                viewModel.createHighlight(HighlightColor.YELLOW, thenEdit = true)
                                navigator?.clearSelection()
                            },
                            onCopy = {
                                activeSelection?.text?.highlight?.let { text ->
                                    clipboard.setText(AnnotatedString(text))
                                    transientMessage = copiedMessage
                                }
                                viewModel.clearSelection()
                                navigator?.clearSelection()
                            },
                            onLookUp = {
                                viewModel.lookUpSelection()
                                navigator?.clearSelection()
                            },
                            onShare = {
                                activeSelection?.text?.highlight?.let { text ->
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(send, null),
                                    )
                                }
                                viewModel.clearSelection()
                                navigator?.clearSelection()
                            },
                        )
                    }
                }

                transientMessage?.let { message ->
                    LaunchedEffect(message) {
                        kotlinx.coroutines.delay(2000)
                        transientMessage = null
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(bottom = 90.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

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

    (viewModel.editingHighlight.collectAsStateWithLifecycle().value)?.let { editing ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::closeHighlightEditor,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            HighlightEditSheet(
                highlight = editing,
                onRecolor = viewModel::recolorEditingHighlight,
                onSaveNote = viewModel::saveEditingNote,
                onSaveTags = { tags ->
                    viewModel.saveEditingTags(tags)
                    viewModel.closeHighlightEditor()
                },
                onDelete = viewModel::deleteEditingHighlight,
            )
        }
    }

    (viewModel.lookup.collectAsStateWithLifecycle().value)?.let { lookupState ->
        val sheetState = rememberModalBottomSheetState()
        val context = LocalContext.current
        val noBrowser = stringResource(R.string.dictionary_no_browser)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissLookup,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            DefinitionSheet(
                state = lookupState,
                onSearchWeb = { word ->
                    // ACTION_VIEW to whatever browser the user has. Lumen declares no
                    // INTERNET permission and makes no request of its own.
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        ("https://www.google.com/search?q=" + Uri.encode("define $word")).toUri(),
                    )
                    runCatching { context.startActivity(intent) }
                        .onFailure { Toast.makeText(context, noBrowser, Toast.LENGTH_SHORT).show() }
                },
            )
        }
    }

    if (showTypography) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showTypography = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            TypographySheet(
                typography = settings.typography,
                onChange = { updated -> viewModel.updateTypography { updated } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderChrome(
    visible: Boolean,
    title: String,
    progressFraction: Float,
    onBack: () -> Unit,
    onOpenToc: () -> Unit,
    /** Null on PDFs: a fixed-layout page does not reflow, so there is nothing to set. */
    onOpenTypography: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = {
                    Text(text = title, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenToc,
                        modifier = Modifier.testTag(ReaderTocButtonTag),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.List,
                            contentDescription = stringResource(R.string.reader_toc),
                        )
                    }
                    onOpenTypography?.let { openTypography ->
                        IconButton(
                            onClick = openTypography,
                            modifier = Modifier.testTag(ReaderTypographyButtonTag),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FormatSize,
                                contentDescription = stringResource(R.string.reader_typography),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.reader_progress,
                        (progressFraction * 100).toInt().coerceIn(0, 100),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ReaderError(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.reader_back_to_library))
        }
    }
}

@Composable
private fun ApplyPreferences(
    navigator: EpubNavigatorFragment?,
    preferences: org.readium.r2.navigator.epub.EpubPreferences,
) {
    androidx.compose.runtime.LaunchedEffect(navigator, preferences) {
        navigator?.submitPreferences(preferences)
    }
}

@Composable
internal fun TableOfContentsSheet(
    entries: List<TocEntry>,
    onSelect: (TocEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        Text(
            text = stringResource(R.string.reader_toc_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
        return
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
    ) {
        item {
            Text(
                text = stringResource(R.string.reader_toc),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
        }
        items(entries.size) { index ->
            val entry = entries[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(entry) }
                    .padding(
                        start = 24.dp + (entry.depth * 16).dp,
                        end = 24.dp,
                        top = 12.dp,
                        bottom = 12.dp,
                    ),
            ) {
                Text(
                    text = entry.title,
                    style = if (entry.depth == 0) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = if (entry.depth == 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
