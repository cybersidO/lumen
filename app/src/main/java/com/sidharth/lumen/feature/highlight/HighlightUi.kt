package com.sidharth.lumen.feature.highlight

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sidharth.lumen.R

const val SelectionToolbarTag = "selection_toolbar"
const val HighlightEditSheetTag = "highlight_edit_sheet"

fun colorSwatchTag(color: HighlightColor) = "swatch_${color.name.lowercase()}"

/**
 * §2.5's selection toolbar: five colours plus note / copy / look up / share. Floats above
 * the bottom of the reader while a selection is active — deliberately clear of the text
 * so the selection handles stay reachable.
 */
@Composable
fun SelectionToolbar(
    onColor: (HighlightColor) -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    onLookUp: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(SelectionToolbarTag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HighlightColor.entries.forEach { color ->
                ColorSwatch(color = color, onClick = { onColor(color) })
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarAction(Icons.Outlined.NoteAdd, R.string.selection_note, onNote)
            ToolbarAction(Icons.Outlined.ContentCopy, R.string.selection_copy, onCopy)
            ToolbarAction(Icons.Outlined.Search, R.string.selection_lookup, onLookUp)
            ToolbarAction(Icons.Outlined.Share, R.string.selection_share, onShare)
        }
    }
}

@Composable
private fun ColorSwatch(
    color: HighlightColor,
    onClick: () -> Unit,
    selected: Boolean = false,
    sizeDp: Int = 30,
) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(color.swatch)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                },
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Highlight " + color.displayName }
            .testTag(colorSwatchTag(color)),
    )
}

@Composable
private fun ToolbarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(labelRes),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Edit sheet for an existing highlight (§2.5: tap → edit/delete/re-colour), plus the
 * note and tags §2.5 says a highlight can carry.
 */
@Composable
fun HighlightEditSheet(
    highlight: Highlight,
    onRecolor: (HighlightColor) -> Unit,
    onSaveNote: (String) -> Unit,
    onSaveTags: (List<String>) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var noteText by remember(highlight.id) { mutableStateOf(highlight.note.orEmpty()) }
    var tagsText by remember(highlight.id) { mutableStateOf(highlight.tags.joinToString(", ")) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
            .testTag(HighlightEditSheetTag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The quote being edited, so the sheet is self-identifying.
        Text(
            text = "“${highlight.quoteExact}”",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HighlightColor.entries.forEach { color ->
                ColorSwatch(
                    color = color,
                    onClick = { onRecolor(color) },
                    selected = color == highlight.color,
                )
            }
        }

        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            label = { Text(stringResource(R.string.highlight_note_label)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("highlight_note_field"),
        )

        OutlinedTextField(
            value = tagsText,
            onValueChange = { tagsText = it },
            label = { Text(stringResource(R.string.highlight_tags_label)) },
            supportingText = { Text(stringResource(R.string.highlight_tags_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("highlight_tags_field"),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDelete, modifier = Modifier.testTag("highlight_delete")) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.highlight_delete),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            AssistChip(
                onClick = {
                    onSaveNote(noteText)
                    onSaveTags(tagsText.split(','))
                },
                label = { Text(stringResource(R.string.highlight_save)) },
                modifier = Modifier.testTag("highlight_save"),
            )
        }
    }
}
