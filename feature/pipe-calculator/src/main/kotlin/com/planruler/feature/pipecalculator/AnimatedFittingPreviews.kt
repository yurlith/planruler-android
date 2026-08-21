package com.planruler.feature.pipecalculator

import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planruler.pipecalculator.ElbowCatalogEntry
import com.planruler.pipecalculator.FlangeCatalogEntry
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

@Composable
internal fun AnimatedElbowPreview(elbow: ElbowCatalogEntry, description: String) {
    val transition = rememberInfiniteTransition(label = "elbow-centerline")
    val dashPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 36f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = FastOutSlowInEasing)),
        label = "elbow-dash-phase",
    )
    val pipe = MaterialTheme.colorScheme.primaryContainer
    val bore = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val dimension = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()

    PreviewCard(
        title = "DN ${elbow.dn} · ${zeroDecimal(elbow.angleDeg)}° · R ${oneDecimal(elbow.centerlineRadiusMm)} mm",
        description = description,
        details = "ØD ${oneDecimal(elbow.outsideDiameterMm)} × s ${oneDecimal(elbow.wallThicknessMm)} mm · " +
            "R ± ${oneDecimal(elbow.radiusToleranceMm)} mm · A ${oneDecimal(elbow.centerToEndMm)} mm",
        testTag = PipeCalculatorTags.ElbowAnimation,
    ) {
        Canvas(Modifier.fillMaxWidth().height(250.dp)) {
            val w = size.width
            val h = size.height
            val radius = min(w * 0.24f, h * 0.30f)
            val center = Offset(w * 0.39f, h * 0.29f)
            val angle = elbow.angleDeg.toFloat()
            val endAngle = 90f - angle
            val pipeHalf = (radius * (elbow.outsideDiameterMm / 2.0 / elbow.centerlineRadiusMm)).toFloat()
                .coerceIn(13f, 36f)
            val outerStroke = pipeHalf * 2f
            val innerStroke = (outerStroke *
                ((elbow.outsideDiameterMm - 2.0 * elbow.wallThicknessMm) / elbow.outsideDiameterMm)).toFloat()
            val start = Offset(center.x, center.y + radius)
            val endRadians = Math.toRadians(endAngle.toDouble())
            val end = Offset(
                center.x + cos(endRadians).toFloat() * radius,
                center.y + sin(endRadians).toFloat() * radius,
            )
            val outlet = Offset(cos(Math.toRadians(angle.toDouble())).toFloat(), -sin(Math.toRadians(angle.toDouble())).toFloat())
            val incomingEnd = Offset(w * 0.08f, start.y)
            val outgoingEnd = end + outlet * (w * 0.25f)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            drawLine(outline, incomingEnd, start, outerStroke + 5f, StrokeCap.Butt)
            drawArc(outline, 90f, -angle, false, arcTopLeft, arcSize, style = Stroke(outerStroke + 5f, cap = StrokeCap.Butt))
            drawLine(outline, end, outgoingEnd, outerStroke + 5f, StrokeCap.Butt)
            drawLine(pipe, incomingEnd, start, outerStroke, StrokeCap.Butt)
            drawArc(pipe, 90f, -angle, false, arcTopLeft, arcSize, style = Stroke(outerStroke, cap = StrokeCap.Butt))
            drawLine(pipe, end, outgoingEnd, outerStroke, StrokeCap.Butt)
            drawLine(bore, incomingEnd, start, innerStroke, StrokeCap.Butt)
            drawArc(bore, 90f, -angle, false, arcTopLeft, arcSize, style = Stroke(innerStroke, cap = StrokeCap.Butt))
            drawLine(bore, end, outgoingEnd, innerStroke, StrokeCap.Butt)

            drawLine(
                outline,
                incomingEnd,
                start,
                2f,
                StrokeCap.Round,
                PathEffect.dashPathEffect(floatArrayOf(10f, 7f), dashPhase),
            )
            drawArc(
                outline,
                90f,
                -angle,
                false,
                arcTopLeft,
                arcSize,
                style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), dashPhase)),
            )
            drawLine(
                outline,
                end,
                outgoingEnd,
                2f,
                StrokeCap.Round,
                PathEffect.dashPathEffect(floatArrayOf(10f, 7f), dashPhase),
            )

            val inletNormal = Offset(0f, pipeHalf)
            drawLine(outline, incomingEnd - inletNormal, incomingEnd + inletNormal, 3f)
            val outletNormal = Offset(-outlet.y, outlet.x) * pipeHalf
            drawLine(outline, outgoingEnd - outletNormal, outgoingEnd + outletNormal, 3f)

            val midRadians = Math.toRadians((90f - angle / 2f).toDouble())
            val radiusPoint = Offset(
                center.x + cos(midRadians).toFloat() * radius,
                center.y + sin(midRadians).toFloat() * radius,
            )
            technicalDimension(center, radiusPoint, dimension)
            drawCircle(dimension, 4.5f, center)

            val tangentA = (radius * tan(Math.toRadians((angle / 2f).toDouble()))).toFloat()
            val tangentIntersection = Offset(start.x + tangentA, start.y)
            val aY = start.y + pipeHalf + 28f
            technicalDimension(Offset(start.x, aY), Offset(tangentIntersection.x, aY), dimension)
            drawLine(dimension, Offset(start.x, start.y), Offset(start.x, aY + 6f), 2f)
            drawLine(dimension, tangentIntersection, Offset(tangentIntersection.x, aY + 6f), 2f)

            val diameterX = incomingEnd.x + w * 0.055f
            technicalDimension(
                Offset(diameterX, start.y - pipeHalf),
                Offset(diameterX, start.y + pipeHalf),
                dimension,
            )

            val paint = technicalPaint(labelColor, 11.sp.toPx())
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    "R ${oneDecimal(elbow.centerlineRadiusMm)}",
                    (center.x + radiusPoint.x) / 2f + 28f,
                    (center.y + radiusPoint.y) / 2f,
                    paint,
                )
                canvas.nativeCanvas.drawText(
                    "A ${oneDecimal(elbow.centerToEndMm)}",
                    (start.x + tangentIntersection.x) / 2f,
                    aY + 25f,
                    paint,
                )
                canvas.nativeCanvas.save()
                canvas.nativeCanvas.rotate(-90f, diameterX - 18f, start.y)
                canvas.nativeCanvas.drawText(
                    "ØD ${oneDecimal(elbow.outsideDiameterMm)} · s ${oneDecimal(elbow.wallThicknessMm)}",
                    diameterX - 18f,
                    start.y,
                    paint,
                )
                canvas.nativeCanvas.restore()
                canvas.nativeCanvas.drawText(
                    "α ${zeroDecimal(elbow.angleDeg)}°",
                    tangentIntersection.x + 42f,
                    tangentIntersection.y - 18f,
                    paint,
                )
            }
        }
    }
}

@Composable
internal fun AnimatedFlangePreview(
    flange: FlangeCatalogEntry,
    description: String,
    frontViewLabel: String,
    profileLabel: String,
) {
    val transition = rememberInfiniteTransition(label = "flange-inspection")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2_800, easing = FastOutSlowInEasing)),
        label = "flange-marker",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flange-pulse",
    )
    val flangeFill = MaterialTheme.colorScheme.surfaceVariant
    val background = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val boltCircle = MaterialTheme.colorScheme.primary
    val dimension = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()

    PreviewCard(
        title = "DN ${flange.dn} · PN ${flange.pn}",
        description = description,
        details = "D ${zeroDecimal(flange.outsideDiameterMm)} · k ${zeroDecimal(flange.boltCircleDiameterMm)} mm · " +
            "${flange.boltHoleCount} × Ød₂ ${zeroDecimal(flange.boltHoleDiameterMm)} mm",
        testTag = PipeCalculatorTags.FlangeAnimation,
    ) {
        Canvas(Modifier.fillMaxWidth().height(300.dp)) {
            val w = size.width
            val h = size.height
            val center = Offset(w * 0.34f, h * 0.47f)
            val outerRadius = min(w * 0.26f, h * 0.31f)
            val boltRadius = outerRadius *
                (flange.boltCircleDiameterMm / flange.outsideDiameterMm).toFloat()
            val holeRadius = outerRadius *
                (flange.boltHoleDiameterMm / flange.outsideDiameterMm).toFloat()
            val boreRadius = (outerRadius * (flange.dn.toDouble() / flange.outsideDiameterMm).toFloat())
                .coerceIn(outerRadius * 0.18f, outerRadius * 0.43f)

            drawCircle(flangeFill, outerRadius, center)
            drawCircle(outline, outerRadius, center, style = Stroke(3f))
            drawCircle(
                boltCircle.copy(alpha = 0.65f),
                boltRadius,
                center,
                style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f), rotation / 10f)),
            )
            drawCircle(background, boreRadius, center)
            drawCircle(outline, boreRadius, center, style = Stroke(3f))

            repeat(flange.boltHoleCount) { index ->
                val angle = (2.0 * PI * index / flange.boltHoleCount) - PI / 2.0
                val holeCenter = Offset(
                    center.x + cos(angle).toFloat() * boltRadius,
                    center.y + sin(angle).toFloat() * boltRadius,
                )
                drawCircle(background, holeRadius, holeCenter)
                drawCircle(outline, holeRadius, holeCenter, style = Stroke(2f))
            }

            rotate(rotation, center) {
                drawArc(
                    dimension.copy(alpha = pulse),
                    -20f,
                    40f,
                    false,
                    Offset(center.x - boltRadius, center.y - boltRadius),
                    Size(boltRadius * 2f, boltRadius * 2f),
                    style = Stroke(7f, cap = StrokeCap.Round),
                )
            }

            val dY = center.y + outerRadius + 30f
            technicalDimension(
                Offset(center.x - outerRadius, dY),
                Offset(center.x + outerRadius, dY),
                dimension,
            )
            drawLine(dimension, Offset(center.x - outerRadius, center.y), Offset(center.x - outerRadius, dY + 6f), 2f)
            drawLine(dimension, Offset(center.x + outerRadius, center.y), Offset(center.x + outerRadius, dY + 6f), 2f)

            val kY = center.y - boltRadius
            technicalDimension(
                Offset(center.x - boltRadius, kY),
                Offset(center.x + boltRadius, kY),
                boltCircle,
            )

            val sideX = w * 0.76f
            val sideOuter = outerRadius * 0.82f
            val plateHalfThickness = w * 0.025f
            drawRect(
                flangeFill,
                topLeft = Offset(sideX - plateHalfThickness, center.y - sideOuter),
                size = Size(plateHalfThickness * 2f, sideOuter * 2f),
            )
            drawRect(
                outline,
                topLeft = Offset(sideX - plateHalfThickness, center.y - sideOuter),
                size = Size(plateHalfThickness * 2f, sideOuter * 2f),
                style = Stroke(3f),
            )
            val pipeHalf = boreRadius * 0.86f
            drawRect(
                flangeFill,
                topLeft = Offset(sideX + plateHalfThickness, center.y - pipeHalf),
                size = Size(w * 0.18f, pipeHalf * 2f),
            )
            drawLine(outline, Offset(sideX + plateHalfThickness, center.y - pipeHalf), Offset(w * 0.96f, center.y - pipeHalf), 3f)
            drawLine(outline, Offset(sideX + plateHalfThickness, center.y + pipeHalf), Offset(w * 0.96f, center.y + pipeHalf), 3f)
            val hub = Path().apply {
                moveTo(sideX + plateHalfThickness, center.y - pipeHalf * 1.65f)
                lineTo(sideX + w * 0.09f, center.y - pipeHalf)
                lineTo(sideX + w * 0.09f, center.y + pipeHalf)
                lineTo(sideX + plateHalfThickness, center.y + pipeHalf * 1.65f)
                close()
            }
            drawPath(hub, flangeFill)
            drawPath(hub, outline, style = Stroke(3f))
            drawLine(outline, Offset(sideX - plateHalfThickness, center.y), Offset(w * 0.96f, center.y), 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)))

            val firstHole = Offset(center.x, center.y - boltRadius)
            drawLine(dimension, firstHole + Offset(holeRadius, 0f), Offset(center.x + outerRadius * 0.82f, center.y - outerRadius * 0.92f), 2f)

            val paint = technicalPaint(labelColor, 11.sp.toPx())
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    "D ${zeroDecimal(flange.outsideDiameterMm)} mm",
                    center.x,
                    dY + 27f,
                    paint,
                )
                canvas.nativeCanvas.drawText(
                    "k ${zeroDecimal(flange.boltCircleDiameterMm)} mm",
                    center.x,
                    kY - 14f,
                    paint,
                )
                canvas.nativeCanvas.drawText(
                    "${flange.boltHoleCount} × Ød₂ ${zeroDecimal(flange.boltHoleDiameterMm)}",
                    center.x + outerRadius * 0.83f,
                    center.y - outerRadius * 0.98f,
                    paint,
                )
                canvas.nativeCanvas.drawText(frontViewLabel, center.x, h * 0.97f, paint)
                canvas.nativeCanvas.drawText(profileLabel, sideX + w * 0.07f, center.y + sideOuter + 28f, paint)
            }
        }
    }
}

@Composable
private fun PreviewCard(
    title: String,
    description: String,
    details: String,
    testTag: String,
    drawing: @Composable () -> Unit,
) {
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics { contentDescription = description },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(details, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            drawing()
        }
    }
}

private fun DrawScope.technicalDimension(start: Offset, end: Offset, color: Color) {
    drawLine(color, start, end, 2f)
    val vector = end - start
    val length = kotlin.math.sqrt(vector.x * vector.x + vector.y * vector.y).coerceAtLeast(1f)
    val normal = Offset(-vector.y / length, vector.x / length) * 7f
    drawLine(color, start - normal, start + normal, 2f)
    drawLine(color, end - normal, end + normal, 2f)
}

private fun technicalPaint(color: Int, sizePx: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    textSize = sizePx
    textAlign = Paint.Align.CENTER
}

private operator fun Offset.times(scale: Float) = Offset(x * scale, y * scale)
private fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)
private fun zeroDecimal(value: Double): String = String.format(Locale.US, "%.0f", value)
