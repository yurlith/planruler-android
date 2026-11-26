package com.planruler.feature.pipecalculator

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planruler.designsystem.localization.localizedUi
import com.planruler.model.AppLanguage
import com.planruler.pipecalculator.PIPE_INSTALLATION_SERIES
import com.planruler.pipecalculator.PipeOffsetResult
import com.planruler.pipecalculator.STANDARD_OFFSET_ANGLES
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The quick "Etage": offset in, cut length out, recalculated on every keystroke. No project,
 * no job and no 3D model in the way — the workshop's full fabrication flow stays one tile away.
 */
@Composable
internal fun OffsetCalculatorPage(language: AppLanguage) {
    val text = OffsetText(language)
    var mode by rememberSaveable { mutableStateOf(OffsetMode.PLAIN) }
    var offset by rememberSaveable { mutableStateOf("500") }
    var second by rememberSaveable { mutableStateOf("300") }
    var advance by rememberSaveable { mutableStateOf("800") }
    var angle by rememberSaveable { mutableStateOf("45") }
    var customAngle by rememberSaveable { mutableStateOf(false) }
    var fittings by rememberSaveable { mutableStateOf(OffsetFittings.WELDED_3D) }
    var dn by rememberSaveable { mutableStateOf(50) }
    var takeout by rememberSaveable { mutableStateOf("40") }
    var gap by rememberSaveable { mutableStateOf(OFFSET_WELD_GAP_MM) }
    val form = OffsetForm(mode, offset, second, advance, angle, fittings, dn, takeout, gap)
    val outcome = remember(form) { form.calculate() }
    val invalid = (outcome as? OffsetOutcome.Invalid)?.field

    LazyColumn(
        Modifier.fillMaxSize().testTag(PipeCalculatorTags.OffsetPage),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(OffsetMode.entries) { candidate ->
                    FilterChip(
                        selected = mode == candidate,
                        onClick = { mode = candidate },
                        label = { Text(text.mode(candidate)) },
                        modifier = Modifier.testTag(PipeCalculatorTags.offsetMode(candidate.name)),
                    )
                }
            }
        }
        item {
            Text(text.modeHint(mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OffsetNumberField(
                    value = offset,
                    onValue = { offset = it },
                    label = if (mode == OffsetMode.ROLLING) text.rise else text.offset,
                    error = invalid == OffsetField.OFFSET,
                    tag = PipeCalculatorTags.OffsetHeight,
                    modifier = Modifier.weight(1f),
                )
                when (mode) {
                    OffsetMode.ROLLING -> OffsetNumberField(
                        value = second,
                        onValue = { second = it },
                        label = text.side,
                        error = invalid == OffsetField.SECOND,
                        tag = PipeCalculatorTags.OffsetSecond,
                        modifier = Modifier.weight(1f),
                    )
                    OffsetMode.BY_ADVANCE -> OffsetNumberField(
                        value = advance,
                        onValue = { advance = it },
                        label = text.availableAdvance,
                        error = invalid == OffsetField.ADVANCE,
                        tag = PipeCalculatorTags.OffsetAdvance,
                        modifier = Modifier.weight(1f),
                    )
                    OffsetMode.PLAIN -> Unit
                }
            }
        }
        if (mode != OffsetMode.BY_ADVANCE) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text.elbowAngle, style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(STANDARD_OFFSET_ANGLES) { value ->
                            val label = angleText(value)
                            FilterChip(
                                selected = !customAngle && angle == label,
                                onClick = { customAngle = false; angle = label },
                                label = { Text("$label°") },
                                modifier = Modifier.testTag(PipeCalculatorTags.offsetAngle(label)),
                            )
                        }
                        item {
                            FilterChip(
                                selected = customAngle,
                                onClick = { customAngle = true },
                                label = { Text(text.customAngle) },
                            )
                        }
                    }
                    if (customAngle) {
                        OffsetNumberField(
                            value = angle,
                            onValue = { angle = it },
                            label = text.angleDegrees,
                            error = invalid == OffsetField.ANGLE,
                            tag = PipeCalculatorTags.OffsetCustomAngle,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text.fittings, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OffsetFittings.entries.forEach { kind ->
                        FilterChip(
                            selected = fittings == kind,
                            onClick = {
                                val next = form.withFittings(kind)
                                fittings = next.fittings
                                gap = next.gap
                            },
                            label = { Text(text.fittings(kind)) },
                        )
                    }
                }
                if (fittings == OffsetFittings.WELDED_3D) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(OFFSET_DIAMETERS) { value ->
                            FilterChip(
                                selected = dn == value,
                                onClick = { dn = value },
                                label = { Text("DN $value") },
                                modifier = Modifier.testTag(PipeCalculatorTags.offsetDn(value)),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (fittings == OffsetFittings.CUSTOM) {
                        OffsetNumberField(
                            value = takeout,
                            onValue = { takeout = it },
                            label = text.takeout,
                            error = invalid == OffsetField.TAKEOUT,
                            tag = PipeCalculatorTags.OffsetTakeout,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OffsetNumberField(
                        value = gap,
                        onValue = { gap = it },
                        label = text.gap,
                        error = invalid == OffsetField.GAP,
                        tag = PipeCalculatorTags.OffsetGap,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        when (outcome) {
            is OffsetOutcome.Invalid -> item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Text(text.invalid(outcome.field), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                }
            }
            is OffsetOutcome.Ready -> {
                item {
                    OffsetResultCard(
                        outcome = outcome,
                        mode = mode,
                        text = text,
                        onUseStockAngle = { stock ->
                            mode = OffsetMode.PLAIN
                            customAngle = false
                            angle = angleText(stock)
                        },
                    )
                }
                item {
                    OffsetSketch(
                        result = outcome.result,
                        pipeOutsideDiameterMm = PIPE_INSTALLATION_SERIES.firstOrNull { it.dn == dn }
                            ?.outsideDiameterMm
                            ?.takeIf { fittings == OffsetFittings.WELDED_3D },
                        text = text,
                    )
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text(text.formula, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun OffsetNumberField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    error: Boolean,
    tag: String,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValue(next.filter { it.isDigit() || it == '.' || it == ',' }.take(9)) },
        label = { Text(label) },
        isError = error,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.testTag(tag),
    )
}

@Composable
private fun OffsetResultCard(
    outcome: OffsetOutcome.Ready,
    mode: OffsetMode,
    text: OffsetText,
    onUseStockAngle: (Double) -> Unit,
) {
    val result = outcome.result
    ElevatedCard(
        Modifier.fillMaxWidth().testTag(PipeCalculatorTags.OffsetResult),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (result.fits) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (result.fits) {
                Text(text.cutPiece, style = MaterialTheme.typography.labelLarge)
                Text(
                    "${mm(result.cutLengthMm)} mm",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.testTag(PipeCalculatorTags.OffsetCutLength),
                )
            } else {
                Text(
                    text.tooSmall(mm(result.minimumOffsetMm)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (mode == OffsetMode.ROLLING) {
                OffsetMetric(text.trueOffset, "${mm(result.offsetMm)} mm")
                OffsetMetric(text.roll, "${degrees(result.rollDeg)}°")
            }
            if (mode == OffsetMode.BY_ADVANCE) {
                OffsetMetric(text.exactAngle, "${degrees(result.angleDeg)}°")
            }
            OffsetMetric(text.travel, "${mm(result.travelMm)} mm")
            OffsetMetric(text.advance, "${mm(result.advanceMm)} mm")
            OffsetMetric(text.faceToFace, "${mm(result.faceToFaceMm)} mm")
            OffsetMetric(
                text.takeoutEach,
                "${mm(result.takeoutMm)} mm" + (outcome.radiusMm?.let { " (R ${mm(it)})" } ?: ""),
            )
            OffsetMetric(text.gapEach, "${mm(result.jointGapMm)} mm")
            OffsetMetric(text.minimum, "${mm(result.minimumOffsetMm)} mm")
            outcome.nearestStockAngle?.let { stock ->
                if (abs(stock - result.angleDeg) > 0.05) {
                    TextButton(onClick = { onUseStockAngle(stock) }) {
                        Text(text.useStockAngle(angleText(stock)))
                    }
                }
            }
        }
    }
}

@Composable
private fun OffsetMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OffsetSketch(result: PipeOffsetResult, pipeOutsideDiameterMm: Double?, text: OffsetText) {
    val pipeColor = MaterialTheme.colorScheme.outline
    val cutColor = MaterialTheme.colorScheme.primary
    val dimensionColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurface
    val labelBackdrop = MaterialTheme.colorScheme.surface
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .testTag(PipeCalculatorTags.OffsetSketch)
            .semantics { contentDescription = text.sketchDescription },
    ) {
        Canvas(Modifier.fillMaxWidth().height(260.dp).padding(8.dp)) {
            val paint = Paint().apply {
                isAntiAlias = true
                textSize = 13.sp.toPx()
                color = labelColor.toArgb()
            }
            val labelHeight = paint.fontMetrics.let { it.descent - it.ascent }
            val diagram = layoutOffsetDiagram(
                result = result,
                pipeOutsideDiameterMm = pipeOutsideDiameterMm,
                widthPx = size.width,
                heightPx = size.height,
                unitPx = 1.dp.toPx(),
                labelHeightPx = labelHeight,
                textWidth = paint::measureText,
                texts = OffsetDiagramTexts(
                    cut = "C = ${mm(result.cutLengthMm)}",
                    offset = "H = ${mm(result.offsetMm)}",
                    travel = "L = ${mm(result.travelMm)}",
                    advance = "X = ${mm(result.advanceMm)}",
                    angle = "${degrees(result.angleDeg)}°",
                ),
            )
            drawOffsetDiagram(diagram, paint, pipeColor, cutColor, dimensionColor, labelBackdrop)
        }
    }
}

private fun DrawScope.drawOffsetDiagram(
    diagram: OffsetDiagram,
    paint: Paint,
    pipeColor: Color,
    cutColor: Color,
    dimensionColor: Color,
    labelBackdrop: Color,
) {
    fun DiagramPoint.offset() = Offset(x, y)
    val pipe = diagram.pipeWidthPx
    listOf(diagram.runIn, diagram.sloped, diagram.runOut).forEach { segment ->
        drawLine(pipeColor.copy(alpha = 0.35f), segment.a.offset(), segment.b.offset(), strokeWidth = pipe, cap = StrokeCap.Round)
        drawLine(
            pipeColor,
            segment.a.offset(),
            segment.b.offset(),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
        )
    }
    diagram.cut?.let { piece ->
        drawLine(cutColor, piece.a.offset(), piece.b.offset(), strokeWidth = pipe * 0.8f, cap = StrokeCap.Butt)
        val dx = piece.b.x - piece.a.x
        val dy = piece.b.y - piece.a.y
        val length = hypot(dx, dy).coerceAtLeast(1f)
        val normal = Offset(-dy / length, dx / length) * pipe
        listOf(piece.a, piece.b).forEach { mark ->
            drawLine(cutColor, mark.offset() - normal, mark.offset() + normal, strokeWidth = 2.dp.toPx())
        }
    }
    val sweep = -diagram.angleDeg
    drawArc(
        dimensionColor,
        startAngle = 0f,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(diagram.angleCenter.x - diagram.angleRadiusPx, diagram.angleCenter.y - diagram.angleRadiusPx),
        size = Size(diagram.angleRadiusPx * 2, diagram.angleRadiusPx * 2),
        style = Stroke(1.5.dp.toPx()),
    )
    diagram.dimensions.forEach { dimension ->
        dimension.extensions.forEach { drawLine(dimensionColor.copy(alpha = 0.7f), it.a.offset(), it.b.offset(), strokeWidth = 1.dp.toPx()) }
        val a = dimension.line.a.offset()
        val b = dimension.line.b.offset()
        drawLine(dimensionColor, a, b, strokeWidth = 1.5.dp.toPx())
        drawArrowHead(a, b, dimensionColor)
        drawArrowHead(b, a, dimensionColor)
    }
    diagram.labels.forEach { label ->
        drawRoundRect(
            labelBackdrop.copy(alpha = 0.92f),
            topLeft = Offset(label.left, label.top),
            size = Size(label.width, label.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
        )
        val baseline = label.top + (label.height - (paint.fontMetrics.descent - paint.fontMetrics.ascent)) / 2f - paint.fontMetrics.ascent
        paint.isFakeBoldText = label.kind == DiagramLabelKind.CUT
        drawContext.canvas.nativeCanvas.drawText(
            label.text,
            label.left + (label.width - paint.measureText(label.text)) / 2f,
            baseline,
            paint,
        )
    }
    paint.isFakeBoldText = false
}

private fun DrawScope.drawArrowHead(tip: Offset, from: Offset, color: Color) {
    val angle = atan2(tip.y - from.y, tip.x - from.x)
    val length = 7.dp.toPx()
    listOf(angle + 2.7f, angle - 2.7f).forEach { side ->
        drawLine(
            color,
            tip,
            Offset(tip.x + kotlin.math.cos(side) * length, tip.y + kotlin.math.sin(side) * length),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

private fun mm(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

private fun degrees(value: Double): String =
    if (abs(value - Math.round(value)) < 0.05) Math.round(value).toString()
    else String.format(Locale.getDefault(), "%.1f", value)

private class OffsetText(private val language: AppLanguage) {
    private fun t(russian: String, english: String) = localizedUi(language, russian, english)

    fun mode(mode: OffsetMode) = when (mode) {
        OffsetMode.PLAIN -> t("Этаж", "Offset")
        OffsetMode.ROLLING -> t("В двух плоскостях", "Rolling offset")
        OffsetMode.BY_ADVANCE -> t("Угол по месту", "Angle from the run")
    }

    fun modeHint(mode: OffsetMode) = when (mode) {
        OffsetMode.PLAIN -> t(
            "Смещение H между осями двух параллельных труб. Для труб одного диаметра можно мерить по верху или по низу.",
            "Offset H between the axes of two parallel pipes. For pipes of one size you can measure top to top or bottom to bottom.",
        )
        OffsetMode.ROLLING -> t(
            "Смещение сразу вверх и в сторону: приложение сложит их в истинное смещение и покажет поворот отводов.",
            "The pipe moves up and sideways at once: both offsets add up to the true offset, and the roll of the elbows is shown.",
        )
        OffsetMode.BY_ADVANCE -> t(
            "Известны смещение и место вдоль трассы: приложение подберёт угол отводов.",
            "The offset and the room along the run are known: the elbow angle is worked out.",
        )
    }

    val offset get() = t("Смещение H, мм", "Offset H, mm")
    val rise get() = t("Вверх / вниз, мм", "Up / down, mm")
    val side get() = t("В сторону, мм", "Sideways, mm")
    val availableAdvance get() = t("Место вдоль трассы X, мм", "Room along the run X, mm")
    val elbowAngle get() = t("Угол отводов", "Elbow angle")
    val customAngle get() = t("Свой угол", "Other angle")
    val angleDegrees get() = t("Угол, °", "Angle, °")
    val fittings get() = t("Тип отводов", "Elbow type")

    fun fittings(kind: OffsetFittings) = when (kind) {
        OffsetFittings.WELDED_3D -> t("Сварные 3D", "Welded 3D")
        OffsetFittings.CUSTOM -> t("Свой размер A", "Own take-out A")
    }

    val takeout get() = t("Размер отвода A (Z), мм", "Elbow take-out A (Z), mm")
    val gap get() = t("Зазор на стык, мм", "Gap per joint, mm")
    val cutPiece get() = t("Отрезать вставку C", "Cut the piece C")
    val trueOffset get() = t("Истинное смещение T", "True offset T")
    val roll get() = t("Поворот плоскости от вертикали", "Roll of the plane from vertical")
    val exactAngle get() = t("Нужный угол отводов", "Required elbow angle")
    val travel get() = t("Между центрами отводов L", "Elbow centre to centre L")
    val advance get() = t("Сдвиг вдоль трассы X", "Advance along the run X")
    val faceToFace get() = t("Между торцами отводов F", "Between elbow ends F")
    val takeoutEach get() = t("Размер одного отвода A", "Take-out of one elbow A")
    val gapEach get() = t("Зазор на стык g", "Gap per joint g")
    val minimum get() = t("Минимальный этаж с этими отводами", "Smallest offset with these elbows")
    val sketchDescription get() = t(
        "Схема этажа в масштабе: вставка C, смещение H, между центрами L и сдвиг X.",
        "Offset sketch to scale: piece C, offset H, centre to centre L and advance X.",
    )
    val formula get() = t(
        "L = T / sin α; X = T / tan α; C = L − 2A − 2g; для сварных 3D-отводов A = R × tan(α/2). Перед резкой сверьте размер A с вашими отводами.",
        "L = T / sin α; X = T / tan α; C = L − 2A − 2g; for welded 3D elbows A = R × tan(α/2). Check A against your actual elbows before cutting.",
    )

    fun tooSmall(minimum: String) = t(
        "Этаж слишком мал для этих отводов — минимум %s мм. Возьмите угол поменьше или отводы меньшего размера.",
        "The offset is too small for these elbows — at least %s mm. Use a smaller angle or smaller elbows.",
    ).format(minimum)

    fun useStockAngle(angle: String) = t("Взять стандартные отводы %s°", "Use stock %s° elbows").format(angle)

    fun invalid(field: OffsetField) = when (field) {
        OffsetField.OFFSET -> t("Введите смещение больше нуля.", "Enter an offset greater than zero.")
        OffsetField.SECOND -> t("Введите смещение в сторону.", "Enter the sideways offset.")
        OffsetField.ADVANCE -> t(
            "Введите место вдоль трассы X. При очень большом X угол получается меньше 1°.",
            "Enter the room along the run X. A very long X needs an angle under 1°.",
        )
        OffsetField.ANGLE -> t("Угол отводов — от 1 до 90°.", "The elbow angle must be 1 to 90°.")
        OffsetField.TAKEOUT -> t("Введите размер отвода A.", "Enter the elbow take-out A.")
        OffsetField.GAP -> t("Введите зазор на стык (0, если зазора нет).", "Enter the gap per joint (0 if there is none).")
    }
}
