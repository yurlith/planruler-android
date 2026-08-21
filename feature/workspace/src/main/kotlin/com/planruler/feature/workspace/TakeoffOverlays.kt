package com.planruler.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.planruler.designsystem.PlanRulerTestTags
import com.planruler.designsystem.component.SheetHeader
import com.planruler.designsystem.theme.MeasurementPalette
import com.planruler.designsystem.theme.Space
import com.planruler.model.DistanceConstraint
import com.planruler.model.Layer
import com.planruler.model.LengthUnit
import com.planruler.model.MeasurementStyle
import com.planruler.model.MeasurementType
import com.planruler.model.TakeoffProperties
import com.planruler.model.TakeoffTemplate
import com.planruler.model.TradeCategory

@Composable
fun ActiveTemplateBar(
    template: TakeoffTemplate?,
    canRepeat: Boolean,
    text: Wt,
    onTemplates: () -> Unit,
    onRepeat: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 6.dp,
        modifier = Modifier.padding(horizontal = Space.x2, vertical = Space.x1),
    ) {
        Row(
            Modifier.padding(horizontal = Space.x2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(template?.style?.colorArgb ?: 0xFF7A7A7A)),
            )
            TextButton(onTemplates, Modifier.testTag(PlanRulerTestTags.TemplatesOpen)) {
                Text(
                    template?.name ?: text.noActiveTemplate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onRepeat, enabled = canRepeat, modifier = Modifier.testTag(PlanRulerTestTags.RepeatLast)) {
                Text(text.repeatLast)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesSheet(
    templates: List<TakeoffTemplate>,
    activeId: String?,
    text: Wt,
    onSelect: (TakeoffTemplate) -> Unit,
    onEdit: (TakeoffTemplate) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(PlanRulerTestTags.TemplatesSheet),
    ) {
        Column(Modifier.navigationBarsPadding().heightIn(max = 680.dp)) {
            SheetHeader(text.templates, subtitle = templates.size.toString()) {
                TextButton(onAdd) { Text(text.addTemplate) }
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                TradeCategory.entries.forEach { category ->
                    val rows = templates.filter { it.takeoff.category == category }
                    if (rows.isNotEmpty()) {
                        item(key = "header-${category.name}") {
                            Text(
                                categoryLabel(category, text),
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = Space.x5, vertical = Space.x2),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        items(rows, key = { it.id }) { template ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(template) }
                                    .padding(horizontal = Space.x5, vertical = Space.x3),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(template.style.colorArgb))
                                        .border(
                                            if (template.id == activeId) 3.dp else 1.dp,
                                            if (template.id == activeId) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape,
                                        ),
                                )
                                Column(Modifier.weight(1f).padding(horizontal = Space.x3)) {
                                    Text(template.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        listOfNotNull(
                                            text.measurement(template.measurementType),
                                            template.takeoff.material,
                                            template.takeoff.diameter ?: template.takeoff.size,
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (template.id == activeId) Text(text.active, color = MaterialTheme.colorScheme.primary)
                                TextButton({ onEdit(template) }) { Text(text.edit) }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateEditorDialog(
    initial: TakeoffTemplate,
    layers: List<Layer>,
    usedCount: Int,
    text: Wt,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (TakeoffTemplate, Boolean) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var type by remember(initial.id) { mutableStateOf(initial.measurementType) }
    var category by remember(initial.id) { mutableStateOf(initial.takeoff.category) }
    var material by remember(initial.id) { mutableStateOf(initial.takeoff.material.orEmpty()) }
    var diameter by remember(initial.id) { mutableStateOf(initial.takeoff.diameter.orEmpty()) }
    var size by remember(initial.id) { mutableStateOf(initial.takeoff.size.orEmpty()) }
    var quantity by remember(initial.id) { mutableStateOf(initial.takeoff.quantity.toString()) }
    var waste by remember(initial.id) {
        mutableStateOf(((initial.takeoff.wasteFactor - 1.0) * 100.0).toInt().toString())
    }
    var unit by remember(initial.id) { mutableStateOf(initial.displayUnit) }
    var layerId by remember(initial.id) { mutableStateOf(initial.layerId) }
    var color by remember(initial.id) { mutableStateOf(initial.style.colorArgb) }
    var stroke by remember(initial.id) { mutableStateOf(initial.style.strokeWidth) }
    var updateExisting by remember(initial.id) { mutableStateOf(usedCount > 0) }
    val quantityValue = quantity.replace(',', '.').toDoubleOrNull()
    val wasteValue = waste.toIntOrNull()
    val valid = name.isNotBlank() && quantityValue != null && quantityValue >= 0.0 &&
        wasteValue != null && wasteValue in 0..300

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (onDelete != null) text.editTemplate else text.addTemplate) },
        text = {
            Column(
                Modifier.heightIn(max = 590.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                OutlinedTextField(
                    name,
                    { name = it.take(120) },
                    label = { Text(text.templateName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text.measurementKind, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    listOf(
                        MeasurementType.DISTANCE,
                        MeasurementType.POLYLINE,
                        MeasurementType.AREA,
                        MeasurementType.COUNTER,
                    ).forEach { candidate ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = { type = candidate },
                            label = { Text(text.measurement(candidate)) },
                            enabled = usedCount == 0,
                        )
                    }
                }
                Text(text.category, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    TradeCategory.entries.forEach { candidate ->
                        FilterChip(
                            category == candidate,
                            { category = candidate },
                            { Text(categoryLabel(candidate, text)) },
                        )
                    }
                }
                OutlinedTextField(
                    material,
                    { material = it.take(120) },
                    label = { Text(text.material) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    OutlinedTextField(
                        diameter,
                        { diameter = it.take(40) },
                        label = { Text(text.diameter) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        size,
                        { size = it.take(40) },
                        label = { Text(text.size) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    OutlinedTextField(
                        quantity,
                        { quantity = it.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(10) },
                        label = { Text(text.quantityMultiplier) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        waste,
                        { waste = it.filter(Char::isDigit).take(3) },
                        label = { Text(text.waste) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(text.units, style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    FilterChip(unit == null, { unit = null }, { Text("auto") })
                    LengthUnit.entries.forEach { candidate ->
                        FilterChip(unit == candidate, { unit = candidate }, { Text(candidate.symbol) })
                    }
                }
                if (layers.isNotEmpty()) {
                    Text(text.layers, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                        layers.forEach { layer ->
                            FilterChip(
                                layerId == layer.id,
                                { layerId = layer.id },
                                { Text(layer.name) },
                                enabled = !layer.locked,
                            )
                        }
                    }
                }
                Text(text.color, style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    MeasurementPalette.forEach { candidate ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(candidate))
                                .border(
                                    if (color == candidate) 3.dp else 1.dp,
                                    if (color == candidate) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                )
                                .clickable { color = candidate },
                        )
                    }
                }
                Text("${text.strokeWidth}: ${"%.1f".format(stroke)}")
                Slider(stroke, { stroke = it }, valueRange = 0.5f..12f)
                if (usedCount > 0) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(updateExisting, { updateExisting = it })
                        Text(text.updateExisting(usedCount), Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            measurementType = type,
                            style = MeasurementStyle(color, stroke, initial.style.textSize),
                            displayUnit = unit,
                            layerId = layerId,
                            takeoff = initial.takeoff.copy(
                                category = category,
                                material = material.trim().takeIf(String::isNotEmpty),
                                diameter = diameter.trim().takeIf(String::isNotEmpty),
                                size = size.trim().takeIf(String::isNotEmpty),
                                quantity = quantityValue ?: 1.0,
                                wasteFactor = 1.0 + (wasteValue ?: 0) / 100.0,
                            ),
                        ),
                        updateExisting,
                    )
                },
                enabled = valid,
                modifier = Modifier.testTag(PlanRulerTestTags.TemplateSave),
            ) { Text(text.save) }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(it) { Text(text.delete, color = MaterialTheme.colorScheme.error) } }
                TextButton(onDismiss) { Text(text.cancel) }
            }
        },
    )
}

@Composable
fun ExactLengthDialog(
    currentLength: String,
    initialUnit: LengthUnit,
    text: Wt,
    onDismiss: () -> Unit,
    onApply: (Double, LengthUnit, DistanceConstraint) -> Unit,
) {
    val numeric = currentLength.substringBefore(' ').replace(',', '.').toDoubleOrNull()
    var value by remember(currentLength) { mutableStateOf(numeric?.toString().orEmpty()) }
    var unit by remember(initialUnit) { mutableStateOf(initialUnit) }
    var constraint by remember { mutableStateOf(DistanceConstraint.FREE) }
    val parsed = value.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.exactLength) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                OutlinedTextField(
                    value,
                    { value = it.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(12) },
                    label = { Text(text.lengthValue) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    LengthUnit.entries.forEach { candidate ->
                        FilterChip(unit == candidate, { unit = candidate }, { Text(candidate.symbol) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    FilterChip(
                        constraint == DistanceConstraint.FREE,
                        { constraint = DistanceConstraint.FREE },
                        { Text(text.keepDirection) },
                    )
                    FilterChip(
                        constraint == DistanceConstraint.HORIZONTAL,
                        { constraint = DistanceConstraint.HORIZONTAL },
                        { Text(text.horizontal) },
                    )
                    FilterChip(
                        constraint == DistanceConstraint.VERTICAL,
                        { constraint = DistanceConstraint.VERTICAL },
                        { Text(text.vertical) },
                    )
                }
                Text(text.exactLengthHint, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                { parsed?.let { onApply(it, unit, constraint) } },
                enabled = parsed != null && parsed > 0.0,
                modifier = Modifier.testTag(PlanRulerTestTags.ExactLengthApply),
            ) { Text(text.apply) }
        },
        dismissButton = { TextButton(onDismiss) { Text(text.cancel) } },
    )
}

fun blankTakeoffTemplate(id: String) = TakeoffTemplate(
    id = id,
    name = "",
    measurementType = MeasurementType.POLYLINE,
    style = MeasurementStyle(colorArgb = 0xFF1976D2, strokeWidth = 2.5f),
    displayUnit = LengthUnit.METER,
    takeoff = TakeoffProperties(),
)
