package com.sidharth.lumen.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sidharth.lumen.R

const val DeleteDialogConfirmTag = "delete_dialog_confirm"
const val DeleteDialogKeepTag = "delete_dialog_keep_highlights"
const val DeleteDialogRemoveTag = "delete_dialog_remove_highlights"

/**
 * §2.1: deleting a book must ask what happens to its highlights, and the default is to
 * keep them. The choice is only offered when there is something to lose — for a book with
 * no highlights the dialog is a plain confirmation.
 */
@Composable
fun DeleteBookDialog(
    pending: PendingDeletion,
    onDismiss: () -> Unit,
    onConfirm: (HighlightDisposition) -> Unit,
) {
    var disposition by remember { mutableStateOf(HighlightDisposition.KEEP) }
    val hasHighlights = pending.highlightCount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title, pending.book.title)) },
        text = {
            Column {
                if (hasHighlights) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.delete_body_with_highlights,
                            pending.highlightCount,
                            pending.highlightCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Column(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .selectableGroup(),
                    ) {
                        DispositionRow(
                            label = stringResource(R.string.delete_keep_highlights),
                            hint = stringResource(R.string.delete_keep_highlights_hint),
                            selected = disposition == HighlightDisposition.KEEP,
                            testTag = DeleteDialogKeepTag,
                            onSelect = { disposition = HighlightDisposition.KEEP },
                        )
                        DispositionRow(
                            label = stringResource(R.string.delete_remove_highlights),
                            hint = null,
                            selected = disposition == HighlightDisposition.DELETE,
                            testTag = DeleteDialogRemoveTag,
                            onSelect = { disposition = HighlightDisposition.DELETE },
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.delete_body_no_highlights),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(disposition) },
                modifier = Modifier.testTag(DeleteDialogConfirmTag),
            ) {
                Text(
                    text = stringResource(R.string.delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.delete_cancel))
            }
        },
    )
}

@Composable
private fun DispositionRow(
    label: String,
    hint: String?,
    selected: Boolean,
    testTag: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(testTag)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
