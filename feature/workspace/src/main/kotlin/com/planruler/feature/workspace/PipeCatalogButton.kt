package com.planruler.feature.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.planruler.pipecalculator.PIPE_INSTALLATION_SERIES
import com.planruler.pipecalculator.PipeCatalogEntry
import com.planruler.pipecalculator.catalogMatches
import com.planruler.pipecalculator.sizeLabel

/** Uses the existing manufacturer catalog; a choice fills editable fields, not a new database. */
@Composable
internal fun PipeCatalogButton(text: Wt, onSelect: (PipeCatalogEntry) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton({ open = true }) { Text(text.pipeCatalog) }
    if (!open) return
    var query by remember { mutableStateOf("") }
    val rows = remember(query) {
        PIPE_INSTALLATION_SERIES.filter { catalogMatches(query, "DN${it.dn}", it.sizeLabel, it.material) }
    }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(text.pipeCatalog) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it.take(80) }, label = { Text(text.pipeSearch) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pipe-catalog-search"))
                Text(text.pipeCatalogHint, style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 380.dp).testTag("pipe-catalog-results")) {
                    items(rows, key = { it.id }) { pipe ->
                        Column(Modifier.fillMaxWidth().clickable { onSelect(pipe); open = false }.padding(vertical = 12.dp)) {
                            Text("DN${pipe.dn} · ${pipe.sizeLabel}", style = MaterialTheme.typography.titleSmall)
                            Text(pipe.material, style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                    if (rows.isEmpty()) item { Text(text.pipeNoMatches) }
                }
                Text(PIPE_INSTALLATION_SERIES.first().source.organisation, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton({ open = false }) { Text(text.cancel) } },
    )
}
