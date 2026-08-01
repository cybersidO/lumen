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
import com.sidharth.lumen.R
import com.sidharth.lumen.core.readium.readiumPreferences
import com.sidharth.lumen.core.settings.ResolvedTheme
import org.readium.r2.navigator.epub.EpubNavigatorFragment

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

            is ReaderUiState.Ready -> {
                val preferences = remember(settings.typography, resolvedTheme) {
                    readiumPreferences(settings.typography, resolvedTheme)
                }

                EpubNavigatorHost(
                    publication = state.publication,
                    initialLocator = state.initialLocator,
                    preferences = preferences,
                    onNavigatorReady = { navigator = it },
                    onLocatorChanged = viewModel::onLocatorChanged,
                    modifier = Modifier.fillMaxSize().testTag(ReaderSurfaceTag),
                )

                TrackLocator(navigator = navigator, onLocatorChanged = viewModel::onLocatorChanged)

                // Typography changes are submitted to the live navigator rather than
                // rebuilding it — that is what keeps the reader's place when the font
                // size changes (§M2's done-when).
                ApplyPreferences(navigator = navigator, preferences = preferences)

                // Taps go through Readium's own input pipeline, NOT a Compose overlay.
                // A transparent clickable Box on top of the navigator swallows every touch
                // before the WebView sees it, which silently breaks swipe-to-turn-page.
                // The full gesture layer lands in M3; this is the minimum that leaves the
                // reader usable.
                ReaderTapListener(navigator = navigator, onTap = viewModel::toggleChrome)

                ReaderChrome(
                    visible = chrome.visible,
                    title = chrome.chapterTitle ?: state.title,
                    progressFraction = chrome.progressFraction,
                    onBack = onBack,
                    onOpenToc = { showToc = true },
                    onOpenTypography = { showTypography = true },
                )
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
private fun ReaderChrome(
    visible: Boolean,
    title: String,
    progressFraction: Float,
    onBack: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenTypography: () -> Unit,
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
                    IconButton(
                        onClick = onOpenTypography,
                        modifier = Modifier.testTag(ReaderTypographyButtonTag),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FormatSize,
                            contentDescription = stringResource(R.string.reader_typography),
                        )
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
private fun TableOfContentsSheet(
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
