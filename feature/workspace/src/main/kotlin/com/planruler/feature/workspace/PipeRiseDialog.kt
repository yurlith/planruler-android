package com.planruler.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import com.planruler.designsystem.theme.Space
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MAX_RISE_INPUT_MM = 100_000.0

/**
 * Vertical leg at a vertex of a pipe run on the plan: a riser goes up, a drop goes down.
 * The height counts towards the run length and becomes a vertical pipe in the isometric.
 */
@Composable
internal fun PipeRiseDialog(
    pointNumber: Int,
    currentRiseMm: Double,
    text: Wt,
    onDismiss: () -> Unit,
    onApply: (Double) -> Unit,
) {
    var up by remember { mutableStateOf(currentRiseMm >= 0.0) }
    var value by remember {
        mutableStateOf(if (currentRiseMm == 0.0) "" else abs(currentRiseMm).roundToInt().toString())
    }
    val height = value.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 && it <= MAX_RISE_INPUT_MM }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.riseTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                Text("${text.risePoint} $pointNumber", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    FilterChip(up, { up = true }, { Text("↑ ${text.riseUp}") })
                    FilterChip(!up, { up = false }, { Text("↓ ${text.riseDown}") })
                }
                OutlinedTextField(
                    value,
                    { next -> value = next.filter { it.isDigit() || it == '.' || it == ',' }.take(8) },
                    label = { Text(text.riseHeight) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text.riseHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button({ height?.let { onApply(if (up) it else -it) } }, enabled = height != null) { Text(text.apply) }
        },
        dismissButton = { TextButton(onDismiss) { Text(text.cancel) } },
    )
}
