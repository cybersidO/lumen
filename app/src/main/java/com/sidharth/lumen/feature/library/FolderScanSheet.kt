package com.sidharth.lumen.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sidharth.lumen.R
import com.sidharth.lumen.core.books.FoundBook

const val FolderScanSheetTag = "folder_scan_sheet"
const val FolderScanImportButtonTag = "folder_scan_import"
const val ScanGrantButtonTag = "scan_grant_all_files"

/**
 * The results of scanning a folder (M10): every EPUB found, pre-ticked, with one button
 * to bring them all in. States flow Scanning → Found → Importing → Finished and the sheet
 * stays up throughout so the user can watch the count climb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScanSheet(
    state: ScanUiState,
    onToggle: (android.net.Uri) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onImport: () -> Unit,
    onAddFolder: () -> Unit,
    onGrantAllFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.testTag(FolderScanSheetTag),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            when (state) {
                ScanUiState.Idle -> Unit

                ScanUiState.NeedsAllFilesAccess -> {
                    Text(
                        text = stringResource(R.string.scan_all_files_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.scan_all_files_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onGrantAllFiles,
                        modifier = Modifier.fillMaxWidth().testTag(ScanGrantButtonTag),
                    ) {
                        Text(stringResource(R.string.scan_all_files_grant))
                    }
                    TextButton(onClick = onAddFolder, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.scan_all_files_folder_instead))
                    }
                }

                ScanUiState.NeedsFolder -> {
                    Text(
                        text = stringResource(R.string.scan_needs_folder_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.scan_needs_folder_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onAddFolder, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.scan_choose_folder))
                    }
                }

                is ScanUiState.Scanning -> {
                    Text(
                        text = pluralStringResource(R.plurals.scan_scanning, state.foldersSeen, state.foldersSeen),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                is ScanUiState.Found -> {
                    if (state.books.isEmpty()) {
                        Text(
                            text = stringResource(R.string.scan_none_found),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.scan_none_found_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onAddFolder, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.scan_add_another_folder))
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                            Text(stringResource(R.string.nav_close))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.scan_found_count, state.books.size, state.books.size,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            val allSelected = state.selected.size == state.books.size
                            TextButton(onClick = { onSelectAll(!allSelected) }) {
                                Text(
                                    stringResource(
                                        if (allSelected) R.string.scan_select_none else R.string.scan_select_all,
                                    ),
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        ) {
                            items(state.books, key = { it.uri.toString() }) { book ->
                                FoundRow(
                                    book = book,
                                    checked = book.uri in state.selected,
                                    onToggle = { onToggle(book.uri) },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onAddFolder, modifier = Modifier.align(Alignment.Start)) {
                            Text(stringResource(R.string.scan_add_another_folder))
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onImport,
                            enabled = state.selected.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().testTag(FolderScanImportButtonTag),
                        ) {
                            Text(
                                pluralStringResource(
                                    R.plurals.scan_import_count, state.selected.size, state.selected.size,
                                ),
                            )
                        }
                    }
                }

                is ScanUiState.Importing -> {
                    Text(
                        text = stringResource(R.string.scan_importing, state.done + 1, state.total),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = state.currentName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { state.done.toFloat() / state.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is ScanUiState.Finished -> {
                    Text(
                        text = stringResource(R.string.scan_finished_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.library_msg_batch, state.imported, state.alreadyPresent, state.failed,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.nav_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun FoundRow(book: FoundBook, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = book.displayName.removeSuffix(".epub").removeSuffix(".EPUB"),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sizeText = book.sizeBytes?.let { stringResource(R.string.scan_size_mb, it / 1_048_576.0) }
            val subtitle = listOfNotNull(
                book.relativePath.trimEnd('/').takeIf { it.isNotEmpty() },
                sizeText,
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
