package com.planruler.feature.workspace

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.text.KeyboardOptions
import com.planruler.designsystem.PlanRulerTestTags
import com.planruler.designsystem.component.SectionHeader
import com.planruler.designsystem.component.SheetHeader
import com.planruler.designsystem.icon.PlanRulerIcons
import com.planruler.designsystem.theme.MeasurementPalette
import com.planruler.designsystem.theme.Space
import com.planruler.document.api.RenderedPage
import com.planruler.engine.api.MeasurementPropertiesUpdate
import com.planruler.export.api.ExportFormat
import com.planruler.export.api.ExportPageSelection
import com.planruler.model.AppSettings
import com.planruler.model.Calibration
import com.planruler.model.DocPoint
import com.planruler.model.Layer
import com.planruler.model.LayerId
import com.planruler.model.LengthUnit
import com.planruler.model.Measurement
import com.planruler.model.MeasurementReviewStatus
import com.planruler.model.MeasurementType
import com.planruler.model.PageRevision
import com.planruler.model.PageMetadata
import com.planruler.model.RevisionControlPoint
import com.planruler.model.TakeoffTemplate
import com.planruler.model.TradeCategory
import com.planruler.model.calculateTakeoffTotals
import java.util.Locale
import kotlin.math.roundToInt

enum class CalibrationStep { CHOICE, LENGTH, RATIO, VERIFY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationSheet(
    step: CalibrationStep,
    documentLength: Double,
    pageWidth: Double,
    coordinateUnit: PageMetadata.CoordinateUnit?,
    initialUnit: LengthUnit,
    calibration: Calibration?,
    allowRatio: Boolean,
    text: Wt,
    onStep: (CalibrationStep) -> Unit,
    onPickPoints: () -> Unit,
    onApplyLength: (Double, LengthUnit, String) -> Unit,
    onApplyRatio: (Double, Boolean, String) -> Unit,
    onPickVerification: () -> Unit,
    onApplyVerification: (Double, LengthUnit) -> Unit,
    onSkipVerification: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(PlanRulerTestTags.CalibrationSheet),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.x4),
        ) {
            SheetHeader(text.calibrate)
            when (step) {
                CalibrationStep.CHOICE -> {
                    MethodCard(text.calibrationByLength, text.calibrationByLengthHint) {
                        onStep(CalibrationStep.LENGTH)
                        onPickPoints()
                    }
                    if (allowRatio) {
                        MethodCard(text.calibrationByRatio, text.calibrationByRatioHint) {
                            onStep(CalibrationStep.RATIO)
                        }
                    } else {
                        Text(
                            text.imageCalibration,
                            Modifier.padding(horizontal = Space.x5, vertical = Space.x2),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                CalibrationStep.LENGTH -> LengthCalibration(
                    documentLength = documentLength,
                    pageWidth = pageWidth,
                    coordinateUnit = coordinateUnit,
                    initialUnit = initialUnit,
                    text = text,
                    onRedraw = onPickPoints,
                    onApply = onApplyLength,
                )
                CalibrationStep.RATIO -> RatioCalibration(text, onApplyRatio)
                CalibrationStep.VERIFY -> VerificationCalibration(
                    documentLength = documentLength,
                    calibration = calibration,
                    initialUnit = initialUnit,
                    text = text,
                    onPickPoints = onPickVerification,
                    onApply = onApplyVerification,
                    onSkip = onSkipVerification,
                )
            }
        }
    }
}

@Composable
private fun MethodCard(title: String, body: String, onClick: () -> Unit) {
    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x5, vertical = Space.x1)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(Space.x4)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LengthCalibration(
    documentLength: Double,
    pageWidth: Double,
    coordinateUnit: PageMetadata.CoordinateUnit?,
    initialUnit: LengthUnit,
    text: Wt,
    onRedraw: () -> Unit,
    onApply: (Double, LengthUnit, String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    var calibratedBy by remember { mutableStateOf("") }
    // A known length is normally entered in the project's display unit. Defaulting
    // every project to millimetres made an entry such as "2" wrong by 1000x in a
    // metre-based project, even though the measurement engine itself was correct.
    var unit by remember(initialUnit) { mutableStateOf(initialUnit) }
    val parsed = value.replace(',', '.').toDoubleOrNull()
    val share = if (pageWidth > 0.0) (documentLength / pageWidth * 100).roundToInt() else 0
    val preview = parsed
        ?.takeIf { it > 0.0 && documentLength > 0.0 }
        ?.let { unit.toMeters(it) / documentLength }
    val precision = when {
        share >= 30 -> text.precisionHigh
        share >= 10 -> text.precisionMedium
        else -> text.precisionLow
    }
    Column(Modifier.padding(horizontal = Space.x5), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
        Text(
            "${text.referenceSegment}: ${"%.1f".format(documentLength)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value,
            { value = it.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(12) },
            label = { Text(text.knownLength) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            calibratedBy,
            { calibratedBy = it.take(60) },
            label = { Text(text.calibratedBy) },
            placeholder = { Text(text.localUser) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            LengthUnit.entries.forEach { candidate ->
                FilterChip(
                    selected = unit == candidate,
                    onClick = { unit = candidate },
                    label = { Text(candidate.symbol) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
            Text("${text.precision}:", style = MaterialTheme.typography.labelMedium)
            Text(precision, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            text.referenceShare(share),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (share in 1..9) {
            Text(
                text.shortReferenceWarning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (coordinateUnit == PageMetadata.CoordinateUnit.IMAGE_PIXEL) {
            Text(
                text.scanQualityWarning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Text(
                text.scanQualityHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        preview?.let { metersPerDocumentUnit ->
            Text(
                text.calibrationPreview(
                    formatCalibrationNumber(metersPerDocumentUnit),
                    coordinateUnitLabel(coordinateUnit, text),
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            OutlinedButton(onRedraw) { Text(text.redrawPoints) }
            Button(
                onClick = { parsed?.let { onApply(it, unit, calibratedBy) } },
                enabled = parsed != null && parsed > 0.0 && documentLength > 0.0,
                modifier = Modifier.testTag(PlanRulerTestTags.CalibrationApply),
            ) { Text(text.apply) }
        }
    }
}

@Composable
private fun RatioCalibration(text: Wt, onApply: (Double, Boolean, String) -> Unit) {
    var ratio by remember { mutableStateOf("50") }
    var sure by remember { mutableStateOf(true) }
    var calibratedBy by remember { mutableStateOf("") }
    val parsed = ratio.toDoubleOrNull()
    Column(Modifier.padding(horizontal = Space.x5), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            Text("1 :", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                ratio,
                { ratio = it.filter(Char::isDigit).take(5) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(140.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            listOf(20, 50, 100, 200).forEach { preset ->
                AssistChip(onClick = { ratio = preset.toString() }, label = { Text("1:$preset") })
            }
        }
        OutlinedTextField(
            calibratedBy,
            { calibratedBy = it.take(60) },
            label = { Text(text.calibratedBy) },
            placeholder = { Text(text.localUser) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        parsed?.takeIf { it > 0.0 }?.let {
            Text(
                text.ratioPreview(formatCalibrationNumber(0.0254 / 72.0 * it)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(text.printedScaleSure, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            FilterChip(sure, { sure = true }, { Text(text.yes) })
            FilterChip(!sure, { sure = false }, { Text(text.notSure) })
        }
        Button(
            onClick = { parsed?.let { onApply(it, sure, calibratedBy) } },
            enabled = parsed != null && parsed > 0.0,
            modifier = Modifier.testTag(PlanRulerTestTags.CalibrationApply),
        ) { Text(text.apply) }
    }
}

@Composable
private fun VerificationCalibration(
    documentLength: Double,
    calibration: Calibration?,
    initialUnit: LengthUnit,
    text: Wt,
    onPickPoints: () -> Unit,
    onApply: (Double, LengthUnit) -> Unit,
    onSkip: () -> Unit,
) {
    var expected by remember { mutableStateOf("") }
    var unit by remember(initialUnit) { mutableStateOf(initialUnit) }
    val parsed = expected.replace(',', '.').toDoubleOrNull()
    val measured = calibration?.let {
        unit.fromMeters(documentLength * it.metersPerDocumentUnit)
    }?.takeIf { documentLength > 0.0 }
    val errorPercent = if (parsed != null && parsed > 0.0 && measured != null) {
        kotlin.math.abs(measured - parsed) / parsed * 100.0
    } else {
        null
    }

    Column(Modifier.padding(horizontal = Space.x5), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
        Text(text.verificationHint, style = MaterialTheme.typography.bodyMedium)
        if (documentLength <= 0.0) {
            Button(onPickPoints, Modifier.fillMaxWidth()) { Text(text.pickVerificationSegment) }
        } else {
            OutlinedTextField(
                expected,
                { expected = it.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(12) },
                label = { Text(text.expectedLength) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                LengthUnit.entries.forEach { candidate ->
                    FilterChip(
                        selected = unit == candidate,
                        onClick = { unit = candidate },
                        label = { Text(candidate.symbol) },
                    )
                }
            }
            measured?.let {
                Text(
                    text.measuredControl(formatCalibrationNumber(it), unit.symbol),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            errorPercent?.let {
                Text(
                    text.controlDifference(formatCalibrationNumber(it)),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (it <= 1.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                OutlinedButton(onPickPoints) { Text(text.redrawPoints) }
                Button(
                    onClick = { parsed?.let { onApply(it, unit) } },
                    enabled = parsed != null && parsed > 0.0,
                    modifier = Modifier.testTag(PlanRulerTestTags.CalibrationVerify),
                ) { Text(text.saveVerification) }
            }
        }
        TextButton(onSkip) { Text(text.skipVerification) }
    }
}

private fun coordinateUnitLabel(unit: PageMetadata.CoordinateUnit?, text: Wt) = when (unit) {
    PageMetadata.CoordinateUnit.PDF_POINT -> text.pdfPoint
    PageMetadata.CoordinateUnit.IMAGE_PIXEL -> text.imagePixel
    null -> text.documentUnit
}

private fun formatCalibrationNumber(value: Double): String = when {
    value >= 100.0 -> "%.1f".format(java.util.Locale.US, value)
    value >= 1.0 -> "%.3f".format(java.util.Locale.US, value)
    else -> "%.6f".format(java.util.Locale.US, value)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PropertiesSheet(
    measurement: Measurement,
    value: String,
    text: Wt,
    layers: List<Layer>,
    onDismiss: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMarkReviewed: () -> Unit,
    onApply: (MeasurementPropertiesUpdate) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember(measurement.id) { mutableStateOf(measurement.label.orEmpty()) }
    var material by remember(measurement.id) { mutableStateOf(measurement.takeoff.material.orEmpty()) }
    var subcategory by remember(measurement.id) { mutableStateOf(measurement.takeoff.subcategory.orEmpty()) }
    var diameter by remember(measurement.id) { mutableStateOf(measurement.takeoff.diameter.orEmpty()) }
    var size by remember(measurement.id) { mutableStateOf(measurement.takeoff.size.orEmpty()) }
    var layerId by remember(measurement.id) { mutableStateOf(measurement.layerId) }
    var comment by remember(measurement.id) { mutableStateOf(measurement.takeoff.comment.orEmpty()) }
    var quantity by remember(measurement.id) { mutableStateOf(measurement.takeoff.quantity.toString()) }
    var waste by remember(measurement.id) {
        mutableStateOf(((measurement.takeoff.wasteFactor - 1.0) * 100).roundToInt().toString())
    }
    var category by remember(measurement.id) { mutableStateOf(measurement.takeoff.category) }
    var stroke by remember(measurement.id) { mutableStateOf(measurement.style.strokeWidth) }
    var color by remember(measurement.id) { mutableStateOf(measurement.style.colorArgb) }
    var showLabel by remember(measurement.id) { mutableStateOf(measurement.showLabel) }
    var unit by remember(measurement.id) { mutableStateOf(measurement.displayUnit) }
    val quantityValue = quantity.replace(',', '.').toDoubleOrNull()
    val wasteValue = waste.toIntOrNull()
    val valid = quantityValue != null && quantityValue > 0.0 && wasteValue != null && wasteValue in 0..300

    fun apply() {
        onApply(
            MeasurementPropertiesUpdate(
                label = label,
                takeoff = measurement.takeoff.copy(
                    category = category,
                    subcategory = subcategory.trim().takeIf(String::isNotEmpty),
                    material = material.trim().takeIf(String::isNotEmpty),
                    diameter = diameter.trim().takeIf(String::isNotEmpty),
                    size = size.trim().takeIf(String::isNotEmpty),
                    quantity = quantityValue ?: 1.0,
                    wasteFactor = 1.0 + (wasteValue ?: 0) / 100.0,
                    comment = comment.trim().takeIf(String::isNotEmpty),
                ),
                style = measurement.style.copy(strokeWidth = stroke, colorArgb = color),
                displayUnit = unit,
                showLabel = showLabel,
                layerId = layerId,
            ),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(PlanRulerTestTags.PropertiesSheet),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.x6),
        ) {
            SheetHeader(text.measurement(measurement.type), subtitle = value) {
                Row {
                    TextButton(onDuplicate) { Text(text.duplicate) }
                    TextButton(onDelete) {
                        Text(text.delete, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Column(
                Modifier.padding(horizontal = Space.x5),
                verticalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                if (measurement.reviewStatus == MeasurementReviewStatus.NEEDS_REVIEW) {
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(Space.x3), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                            Text(text.needsReview, style = MaterialTheme.typography.titleSmall)
                            Text(text.carriedReviewWarning, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = onMarkReviewed) { Text(text.markReviewed) }
                        }
                    }
                }
                OutlinedTextField(
                    label,
                    { label = it.take(200) },
                    label = { Text(text.label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionHeader(text.category)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    TradeCategory.entries.forEach { candidate ->
                        FilterChip(
                            selected = category == candidate,
                            onClick = { category = candidate },
                            label = { Text(categoryLabel(candidate, text)) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    OutlinedTextField(
                        material,
                        { material = it.take(120) },
                        label = { Text(text.material) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        quantity,
                        { quantity = it.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(10) },
                        label = { Text(text.quantity) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    OutlinedTextField(
                        subcategory,
                        { subcategory = it.take(120) },
                        label = { Text(text.subcategory) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        waste,
                        { waste = it.filter(Char::isDigit).take(3) },
                        label = { Text(text.waste) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
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
                if (layers.size > 1) {
                    SectionHeader(text.moveToLayer)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                        layers.forEach { layer ->
                            FilterChip(
                                selected = layerId == layer.id,
                                onClick = { layerId = layer.id },
                                label = { Text(layer.name) },
                                enabled = !layer.locked,
                            )
                        }
                    }
                }
                SectionHeader(text.color)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.x2),
                ) {
                    MeasurementPalette.forEach { candidate ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(candidate))
                                .border(
                                    if (color == candidate) 3.dp else 1.dp,
                                    if (color == candidate) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    CircleShape,
                                )
                                .clickable { color = candidate },
                        )
                    }
                }
                Text("${text.strokeWidth}: ${"%.1f".format(stroke)}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = stroke,
                    onValueChange = { stroke = it },
                    valueRange = 0.5f..12f,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text.showLabel)
                    Switch(showLabel, { showLabel = it })
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text.units, style = MaterialTheme.typography.labelMedium)
                    FilterChip(unit == null, { unit = null }, { Text(text.automatic) })
                    LengthUnit.entries.forEach { candidate ->
                        FilterChip(unit == candidate, { unit = candidate }, { Text(candidate.symbol) })
                    }
                }
                OutlinedTextField(
                    comment,
                    { comment = it.take(2_000) },
                    label = { Text(text.comment) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    OutlinedButton(onDismiss, Modifier.weight(1f)) { Text(text.close) }
                    Button(
                        { apply() },
                        Modifier.weight(1f),
                        enabled = valid,
                    ) { Text(text.apply) }
                }
            }
        }
    }
}

fun categoryLabel(category: TradeCategory, text: Wt): String = text.category(category)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesSheet(
    pages: List<PageMetadata>,
    selected: Int,
    thumbnails: Map<Int, PageThumbnail>,
    measurementsPerPage: Map<Int, Int>,
    revisionsPerPage: Map<Int, Int>,
    text: Wt,
    onSelect: (Int) -> Unit,
    onNewRevision: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Space.x5)) {
            SheetHeader(text.pages, subtitle = "${pages.size}")
            LazyRow(
                Modifier.fillMaxWidth().height(150.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.x5),
                horizontalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                itemsIndexed(pages) { index, _ ->
                    val thumbnail = thumbnails[index]
                    Column(
                        Modifier
                            .width(78.dp)
                            .testTag("pr:page:$index")
                            .clickable { onSelect(index) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(68.dp, 88.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    if (index == selected) 2.dp else 1.dp,
                                    if (index == selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    MaterialTheme.shapes.extraSmall,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (thumbnail != null) {
                                val bitmap = remember(thumbnail) {
                                    Bitmap.createBitmap(thumbnail.width, thumbnail.height, Bitmap.Config.ARGB_8888)
                                        .apply {
                                            setPixels(
                                                thumbnail.argb,
                                                0,
                                                thumbnail.width,
                                                0,
                                                0,
                                                thumbnail.width,
                                                thumbnail.height,
                                            )
                                        }
                                        .asImageBitmap()
                                }
                                Image(bitmap, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                            } else {
                                Text("${index + 1}", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (index == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            "${measurementsPerPage[index] ?: 0}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val revisions = revisionsPerPage[index] ?: 0
                        if (revisions > 0) {
                            Text(
                                text.revisionNumber(revisions),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onNewRevision,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.x5, vertical = Space.x3)
                    .testTag("pr:revision:new"),
            ) { Text(text.replaceCurrentPage) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevisionAlignmentSheet(
    previousPage: RenderedPage,
    pending: PendingPageRevision,
    text: Wt,
    onSelectPage: (Int) -> Unit,
    onConfirm: (List<RevisionControlPoint>, String?) -> Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pairs by remember(pending.sourcePageIndex) { mutableStateOf(emptyList<RevisionControlPoint>()) }
    var previousPoint by remember(pending.sourcePageIndex) { mutableStateOf<DocPoint?>(null) }
    var note by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val canAddPrevious = previousPoint == null && pairs.size < 3 && !pending.rendering
    val canAddCurrent = previousPoint != null && pairs.size < 3 && !pending.rendering

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x5, vertical = Space.x3),
            verticalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            SheetHeader(text.alignment, subtitle = text.controlPoints)
            Text(text.alignmentHint, style = MaterialTheme.typography.bodyMedium)
            if (pending.document.pages.size > 1) {
                Text(text.selectRevisionPage, style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        { onSelectPage(pending.sourcePageIndex - 1) },
                        enabled = pending.sourcePageIndex > 0 && !pending.rendering,
                    ) { Text("‹") }
                    Text("${pending.sourcePageIndex + 1} / ${pending.document.pages.size}")
                    TextButton(
                        { onSelectPage(pending.sourcePageIndex + 1) },
                        enabled = pending.sourcePageIndex < pending.document.pages.lastIndex && !pending.rendering,
                    ) { Text("›") }
                }
            }
            Text(
                if (previousPoint == null) text.pickPreviousPoint else text.pickCurrentPoint,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text.previousPlan, style = MaterialTheme.typography.labelLarge)
            RevisionPointPicker(
                page = previousPage,
                points = pairs.map { it.previous } + listOfNotNull(previousPoint),
                enabled = canAddPrevious,
                onPoint = {
                    previousPoint = it
                    invalid = false
                },
                modifier = Modifier.testTag("pr:revision:previous"),
            )
            Text(text.newPlan, style = MaterialTheme.typography.labelLarge)
            RevisionPointPicker(
                page = pending.renderedPage,
                points = pairs.map { it.current },
                enabled = canAddCurrent,
                onPoint = { current ->
                    previousPoint?.let { previous ->
                        pairs = pairs + RevisionControlPoint(previous, current)
                        previousPoint = null
                        invalid = false
                    }
                },
                modifier = Modifier.testTag("pr:revision:current"),
            )
            Text("${text.controlPoints}: ${pairs.size} / 3", style = MaterialTheme.typography.labelMedium)
            if (invalid) Text(text.invalidAlignment, color = MaterialTheme.colorScheme.error)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(240) },
                label = { Text("${text.revisionNote} (${text.optional})") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                TextButton(
                    onClick = {
                        if (previousPoint != null) previousPoint = null else if (pairs.isNotEmpty()) pairs = pairs.dropLast(1)
                        invalid = false
                    },
                    enabled = previousPoint != null || pairs.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text(text.undoPoint) }
                OutlinedButton(onDismiss, Modifier.weight(1f)) { Text(text.cancel) }
                Button(
                    onClick = {
                        if (!onConfirm(pairs, note.takeIf(String::isNotBlank))) invalid = true
                    },
                    enabled = pairs.size in 2..3 && !pending.rendering,
                    modifier = Modifier.weight(1f).testTag("pr:revision:confirm"),
                ) { Text(text.apply) }
            }
        }
    }
}

@Composable
private fun RevisionPointPicker(
    page: RenderedPage,
    points: List<DocPoint>,
    enabled: Boolean,
    onPoint: (DocPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = remember(page) {
        Bitmap.createBitmap(page.pixelWidth, page.pixelHeight, Bitmap.Config.ARGB_8888).apply {
            setPixels(page.argb, 0, page.pixelWidth, 0, 0, page.pixelWidth, page.pixelHeight)
        }.asImageBitmap()
    }
    val palette = listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047))
    val activeBorder = MaterialTheme.colorScheme.primary
    val idleBorder = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(page, enabled) {
                if (enabled) {
                    detectTapGestures { offset ->
                        val scale = minOf(size.width / page.source.width, size.height / page.source.height)
                        if (scale <= 0.0 || !scale.isFinite()) return@detectTapGestures
                        val left = (size.width - page.source.width * scale) / 2.0
                        val top = (size.height - page.source.height * scale) / 2.0
                        val x = ((offset.x - left) / scale).coerceIn(0.0, page.source.width)
                        val y = ((offset.y - top) / scale).coerceIn(0.0, page.source.height)
                        onPoint(DocPoint(x, y))
                    }
                }
            },
    ) {
        val scale = minOf(size.width / page.source.width, size.height / page.source.height)
        val width = (page.source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (page.source.height * scale).roundToInt().coerceAtLeast(1)
        val left = ((size.width - width) / 2f).roundToInt()
        val top = ((size.height - height) / 2f).roundToInt()
        drawImage(image, dstOffset = IntOffset(left, top), dstSize = IntSize(width, height))
        points.forEachIndexed { index, point ->
            val center = androidx.compose.ui.geometry.Offset(
                (left + point.x * scale).toFloat(),
                (top + point.y * scale).toFloat(),
            )
            val color = palette[index.coerceAtMost(palette.lastIndex)]
            drawCircle(Color.White, 10.dp.toPx(), center)
            drawCircle(color, 10.dp.toPx(), center, style = Stroke(4.dp.toPx()))
        }
        drawRect(
            if (enabled) activeBorder else idleBorder,
            style = Stroke(if (enabled) 3.dp.toPx() else 1.dp.toPx()),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RevisionControlsBar(
    revision: PageRevision,
    opacity: Float,
    filter: RevisionMeasurementFilter,
    needsReviewCount: Int,
    text: Wt,
    onOpacity: (Float) -> Unit,
    onFilter: (RevisionMeasurementFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.x3, vertical = Space.x2),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 5.dp,
    ) {
        Column(Modifier.padding(horizontal = Space.x3, vertical = Space.x2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text.revisionNumber(revision.revisionNumber),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text("${text.needsReview}: $needsReviewCount", style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text.previousOpacity, style = MaterialTheme.typography.labelSmall)
                Slider(opacity, onOpacity, valueRange = 0f..1f, modifier = Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                listOf(
                    RevisionMeasurementFilter.ALL to text.allMeasurements,
                    RevisionMeasurementFilter.REVISION to text.changedMeasurements,
                    RevisionMeasurementFilter.NEEDS_REVIEW to text.needsReview,
                ).forEach { (candidate, label) ->
                    FilterChip(filter == candidate, { onFilter(candidate) }, { Text(label) })
                }
            }
        }
    }
}

enum class ScheduleGrouping { TEMPLATE, MATERIAL, LAYER, PAGE, PROJECT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSheet(
    measurements: List<Measurement>,
    calibration: Calibration?,
    unit: LengthUnit,
    templates: List<TakeoffTemplate>,
    layers: List<Layer>,
    valueOf: (Measurement) -> String,
    text: Wt,
    onSelect: (Measurement) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var grouping by remember { mutableStateOf(ScheduleGrouping.TEMPLATE) }
    val templatesById = templates.associateBy { it.id }
    val layersById = layers.associateBy { it.id }
    val filtered = measurements.filter {
        query.isBlank() ||
            it.label.orEmpty().contains(query, true) ||
            it.takeoff.material.orEmpty().contains(query, true) ||
            templatesById[it.templateId]?.name.orEmpty().contains(query, true) ||
            text.measurement(it.type).contains(query, true)
    }
    val groups: Map<String, List<Measurement>> = when (grouping) {
        ScheduleGrouping.TEMPLATE -> filtered.groupBy {
            templatesById[it.templateId]?.name ?: it.label?.takeIf(String::isNotBlank) ?: text.noTemplate
        }
        ScheduleGrouping.MATERIAL -> filtered.groupBy {
            it.takeoff.material?.takeIf(String::isNotBlank) ?: text.noMaterial
        }
        ScheduleGrouping.LAYER -> filtered.groupBy {
            layersById[it.layerId]?.name ?: text.layer
        }
        ScheduleGrouping.PAGE -> filtered.groupBy { "${text.page} ${it.pageIndex + 1}" }
        ScheduleGrouping.PROJECT -> mapOf(text.projectTotal to filtered)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().heightIn(max = 640.dp)) {
            SheetHeader(text.schedule, subtitle = "${filtered.size}")
            OutlinedTextField(
                query,
                { query = it.take(80) },
                label = { Text(text.searchHint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.x5),
            )
            if (filtered.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.x5, vertical = Space.x2),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(Space.x3)) {
                        Text(text.projectTotal, style = MaterialTheme.typography.labelLarge)
                        Text(
                            takeoffSummary(filtered, calibration, unit, text),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(Space.x3),
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                FilterChip(
                    selected = grouping == ScheduleGrouping.TEMPLATE,
                    onClick = { grouping = ScheduleGrouping.TEMPLATE },
                    label = { Text(text.groupByTemplate) },
                )
                FilterChip(
                    selected = grouping == ScheduleGrouping.MATERIAL,
                    onClick = { grouping = ScheduleGrouping.MATERIAL },
                    label = { Text(text.groupByMaterial) },
                )
                FilterChip(
                    selected = grouping == ScheduleGrouping.LAYER,
                    onClick = { grouping = ScheduleGrouping.LAYER },
                    label = { Text(text.groupByLayer) },
                )
                FilterChip(
                    selected = grouping == ScheduleGrouping.PAGE,
                    onClick = { grouping = ScheduleGrouping.PAGE },
                    label = { Text(text.groupByPage) },
                )
                FilterChip(
                    selected = grouping == ScheduleGrouping.PROJECT,
                    onClick = { grouping = ScheduleGrouping.PROJECT },
                    label = { Text(text.projectTotal) },
                )
            }
            if (filtered.isEmpty()) {
                Text(
                    text.scheduleEmpty,
                    Modifier.fillMaxWidth().padding(Space.x6),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                groups.forEach { (title, rows) ->
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = Space.x5, vertical = Space.x2),
                        ) {
                            Text(title, style = MaterialTheme.typography.labelLarge)
                            Text(
                                takeoffSummary(rows, calibration, unit, text),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(rows, key = { it.id.value }) { measurement ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(measurement) }
                                .testTag(PlanRulerTestTags.scheduleRow(measurement.id.value))
                                .padding(horizontal = Space.x5, vertical = Space.x3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                toolIcon(toolOf(measurement.type)),
                                null,
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(Modifier.weight(1f).padding(horizontal = Space.x3)) {
                                Text(
                                    measurement.label?.takeIf { it.isNotBlank() } ?: text.measurement(measurement.type),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${text.page} ${measurement.pageIndex + 1} · " +
                                        listOfNotNull(
                                            templatesById[measurement.templateId]?.name,
                                            measurement.takeoff.material,
                                        ).joinToString(" · ").ifBlank {
                                            categoryLabel(measurement.takeoff.category, text)
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(valueOf(measurement), style = MaterialTheme.typography.titleSmall)
                            Icon(
                                PlanRulerIcons.Target,
                                text.showOnPlan,
                                Modifier.padding(start = Space.x3).size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun takeoffSummary(
    measurements: List<Measurement>,
    calibration: Calibration?,
    unit: LengthUnit,
    text: Wt,
): String {
    val totals = calculateTakeoffTotals(measurements, calibration)
    fun number(value: Double) = String.format(Locale.US, "%.2f", value)
    fun result(label: String, base: Double, adjusted: Double, suffix: String): String {
        val baseValue = number(base)
        val adjustedValue = number(adjusted)
        return if (kotlin.math.abs(base - adjusted) < 0.000_001) {
            "$label $baseValue $suffix"
        } else {
            "$label $baseValue → $adjustedValue $suffix"
        }
    }
    val parts = mutableListOf<String>()
    if (totals.baseLengthMeters > 0.0) {
        parts += result(
            text.lengthTotal,
            unit.fromMeters(totals.baseLengthMeters),
            unit.fromMeters(totals.adjustedLengthMeters),
            unit.symbol,
        )
    }
    if (totals.baseAreaSquareMeters > 0.0) {
        val square = unit.metersPerUnit * unit.metersPerUnit
        parts += result(
            text.areaTotal,
            totals.baseAreaSquareMeters / square,
            totals.adjustedAreaSquareMeters / square,
            "${unit.symbol}²",
        )
    }
    if (totals.baseCount > 0.0) {
        parts += result(text.countTotal, totals.baseCount, totals.adjustedCount, text.pieces)
    }
    if (parts.isEmpty()) parts += "${totals.itemCount} ${text.pieces}"
    return parts.joinToString(" · ")
}

fun toolOf(type: MeasurementType): WorkspaceTool = when (type) {
    MeasurementType.DISTANCE -> WorkspaceTool.DISTANCE
    MeasurementType.POLYLINE -> WorkspaceTool.POLYLINE
    MeasurementType.AREA -> WorkspaceTool.AREA
    MeasurementType.ANGLE -> WorkspaceTool.ANGLE
    MeasurementType.ANNOTATION -> WorkspaceTool.ANNOTATION
    MeasurementType.COUNTER -> WorkspaceTool.COUNTER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreToolsSheet(
    text: Wt,
    focusMode: Boolean,
    onTool: (WorkspaceTool) -> Unit,
    onSchedule: () -> Unit,
    onExport: () -> Unit,
    onFocus: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Space.x6)) {
            SheetHeader(text.more)
            SecondaryTools.forEach { tool ->
                SheetRow(toolIconFor(tool), text.tool(tool)) { onTool(tool); onDismiss() }
            }
            HorizontalDivider(Modifier.padding(vertical = Space.x2))
            SheetRow(PlanRulerIcons.Schedule, text.schedule) { onSchedule() }
            SheetRow(PlanRulerIcons.Export, text.export) { onExport() }
            SheetRow(PlanRulerIcons.Focus, text.focusMode + if (focusMode) " ✓" else "") { onFocus() }
        }
    }
}

private fun toolIconFor(tool: WorkspaceTool) = toolIcon(tool)

@Composable
private fun SheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.x5, vertical = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * O4. Visibility and lock live on the project, not on the engine: they are a view
 * concern and must never change what a measurement is worth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersSheet(
    layers: List<Layer>,
    counts: Map<LayerId, Int>,
    text: Wt,
    onDismiss: () -> Unit,
    onVisible: (LayerId, Boolean) -> Unit,
    onLocked: (LayerId, Boolean) -> Unit,
    onRename: (LayerId, String) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (LayerId) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newLayer by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<LayerId?>(null) }
    var renameText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(PlanRulerTestTags.LayersSheet),
    ) {
        Column(Modifier.navigationBarsPadding().heightIn(max = 560.dp)) {
            SheetHeader(text.layers, subtitle = "${layers.size}")
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(layers, key = { it.id.value }) { layer ->
                    val used = counts[layer.id] ?: 0
                    Column(Modifier.padding(horizontal = Space.x5, vertical = Space.x2)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f).clickable {
                                renaming = layer.id
                                renameText = layer.name
                            }) {
                                Text(layer.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "$used ${text.pieces}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { onDelete(layer.id) },
                                enabled = layers.size > 1 && used == 0,
                            ) { Text(text.delete, color = MaterialTheme.colorScheme.error) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
                            Row(
                                Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text.layerVisible, style = MaterialTheme.typography.bodyMedium)
                                Switch(layer.visible, { onVisible(layer.id, it) })
                            }
                            Row(
                                Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text.layerLocked, style = MaterialTheme.typography.bodyMedium)
                                Switch(layer.locked, { onLocked(layer.id, it) })
                            }
                        }
                        HorizontalDivider(Modifier.padding(top = Space.x2))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(Space.x5),
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    newLayer,
                    { newLayer = it.take(60) },
                    label = { Text(text.layerName) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onAdd(newLayer); newLayer = "" },
                    enabled = newLayer.isNotBlank(),
                ) { Text(text.addLayer) }
            }
        }
    }

    renaming?.let { id ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(text.layerName) },
            text = {
                OutlinedTextField(renameText, { renameText = it.take(60) }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = { onRename(id, renameText); renaming = null },
                ) { Text(text.apply) }
            },
            dismissButton = { TextButton({ renaming = null }) { Text(text.cancel) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportWizard(
    pageCount: Int,
    currentPage: Int,
    text: Wt,
    settings: AppSettings,
    onSettings: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
    onExport: (ExportFormat, ExportPageSelection, Int, Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by remember { mutableStateOf(1) }
    var format by remember { mutableStateOf(ExportFormat.ANNOTATED_PDF) }
    var selection by remember { mutableStateOf(ExportPageSelection.CURRENT) }
    var first by remember { mutableStateOf("1") }
    var last by remember { mutableStateOf(pageCount.toString()) }
    val firstIndex = (first.toIntOrNull() ?: 1) - 1
    val lastIndex = (last.toIntOrNull() ?: pageCount) - 1
    val rangeValid = firstIndex in 0 until pageCount && lastIndex in firstIndex until pageCount

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = Space.x6)
                .testTag(PlanRulerTestTags.ExportStep + ":" + step),
        ) {
            SheetHeader(text.export, subtitle = "$step / 3")
            Column(
                Modifier.padding(horizontal = Space.x5),
                verticalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                when (step) {
                    1 -> {
                        SectionHeader(text.exportFormat)
                        FormatCard(text.exportPdf, format == ExportFormat.ANNOTATED_PDF) {
                            format = ExportFormat.ANNOTATED_PDF
                        }
                        FormatCard(text.exportCsv, format == ExportFormat.CSV) { format = ExportFormat.CSV }
                        FormatCard(text.exportJson, format == ExportFormat.JSON) { format = ExportFormat.JSON }
                    }
                    2 -> {
                        SectionHeader(text.exportContent)
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                            FilterChip(
                                selected = selection == ExportPageSelection.CURRENT,
                                onClick = { selection = ExportPageSelection.CURRENT },
                                label = { Text(text.exportCurrentPage) },
                            )
                            FilterChip(
                                selected = selection == ExportPageSelection.ALL,
                                onClick = { selection = ExportPageSelection.ALL },
                                label = { Text(text.exportAllPages) },
                            )
                            FilterChip(
                                selected = selection == ExportPageSelection.RANGE,
                                onClick = { selection = ExportPageSelection.RANGE },
                                label = { Text(text.exportRange) },
                            )
                        }
                        if (selection == ExportPageSelection.RANGE) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                                OutlinedTextField(
                                    first,
                                    { first = it.filter(Char::isDigit).take(4) },
                                    label = { Text(text.firstPage) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    last,
                                    { last = it.filter(Char::isDigit).take(4) },
                                    label = { Text(text.lastPage) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        // Bound to the persisted settings, so the wizard and the settings
                        // screen cannot disagree about what the export will contain.
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text.includeLegend)
                            Switch(
                                settings.exportIncludeLegend,
                                { onSettings(settings.copy(exportIncludeLegend = it)) },
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text.includeScale)
                            Switch(
                                settings.exportIncludeScale,
                                { onSettings(settings.copy(exportIncludeScale = it)) },
                            )
                        }
                    }
                    else -> {
                        SectionHeader(text.exportPreview)
                        Card(colors = CardDefaults.cardColors()) {
                            Column(Modifier.padding(Space.x4)) {
                                Text(formatLabel(format, text), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when (selection) {
                                        ExportPageSelection.CURRENT -> "${text.exportCurrentPage} (${currentPage + 1})"
                                        ExportPageSelection.ALL -> "${text.exportAllPages} ($pageCount)"
                                        ExportPageSelection.RANGE -> "${text.exportRange} $first–$last"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text.exportSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    OutlinedButton({ if (step == 1) onDismiss() else step-- }, Modifier.weight(1f)) {
                        Text(if (step == 1) text.cancel else text.backPoint)
                    }
                    Button(
                        {
                            if (step < 3) {
                                step++
                            } else {
                                onExport(
                                    format,
                                    selection,
                                    if (selection == ExportPageSelection.RANGE) firstIndex else currentPage,
                                    if (selection == ExportPageSelection.RANGE) lastIndex else currentPage,
                                )
                            }
                        },
                        Modifier.weight(1f).testTag(PlanRulerTestTags.ExportRun),
                        enabled = step < 3 || selection != ExportPageSelection.RANGE || rangeValid,
                    ) { Text(if (step < 3) text.next else text.export) }
                }
            }
        }
    }
}

private fun formatLabel(format: ExportFormat, text: Wt) = when (format) {
    ExportFormat.ANNOTATED_PDF -> text.exportPdf
    ExportFormat.CSV -> text.exportCsv
    ExportFormat.JSON -> text.exportJson
}

@Composable
private fun FormatCard(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(Modifier.padding(Space.x4), verticalAlignment = Alignment.CenterVertically) {
            Icon(PlanRulerIcons.Export, null, Modifier.size(24.dp))
            Text(title, Modifier.padding(start = Space.x3), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun AnnotationDialog(
    initial: String,
    editing: Boolean,
    text: Wt,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) text.editAnnotation else text.addAnnotation) },
        text = {
            OutlinedTextField(
                value,
                { value = it.take(2_000) },
                label = { Text(text.annotationText) },
                minLines = 3,
                maxLines = 8,
                supportingText = { Text("${value.length}/2000") },
            )
        },
        confirmButton = {
            TextButton(
                { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
                modifier = Modifier.testTag(PlanRulerTestTags.TextEntrySave),
            ) { Text(text.apply) }
        },
        dismissButton = { TextButton(onDismiss) { Text(text.cancel) } },
    )
}
