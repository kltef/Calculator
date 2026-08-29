package com.cascalc.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cascalc.app.R
import com.cascalc.engine.AngleMode
import com.cascalc.engine.HistoryEntry

/**
 * Past calculations, newest first.
 *
 * Tapping the expression puts it back in the editor to be amended; tapping the
 * result inserts the value at the caret, which is how most follow-up
 * calculations actually start.
 */
@Composable
fun HistoryPanel(
    entries: List<HistoryEntry>,
    onRecallInput: (HistoryEntry) -> Unit,
    onRecallResult: (HistoryEntry) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleSmall,
            )
            if (entries.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text(stringResource(R.string.clear_history))
                }
            }
        }

        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(entries, key = { it.id }) { entry ->
                HistoryRow(entry, onRecallInput, onRecallResult, onDelete)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onRecallInput: (HistoryEntry) -> Unit,
    onRecallResult: (HistoryEntry) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.input,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onRecallInput(entry) },
            )
            Text(
                text = entry.result.exact,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onRecallResult(entry) },
            )
            entry.result.approximate?.let { approximate ->
                Text(
                    text = "${stringResource(R.string.approx_prefix)} $approximate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (entry.angleMode == AngleMode.DEGREES) {
            Text(
                text = "DEG",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = { onDelete(entry.id) }) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.delete_entry),
            )
        }
    }
}
