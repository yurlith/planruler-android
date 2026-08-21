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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planruler.pipecalculator.TwoElbowAssemblyResult
import java.util.Locale
import kotlin.math.atan2

@Composable
internal fun DimensionedOffsetPreview(
    result: TwoElbowAssemblyResult,
    dn: Int,
    outsideDiameterMm: Double,
    wallThicknessMm: Double,
    description: String,
    cutPipeLabel: String,
    betweenCutMarksLabel: String,
    centerToCenterLabel: String,
    faceToFaceLabel: String,
    pointLegend: String,
    face1Mark: String,
    face2Mark: String,
) {
    val transition = rememberInfiniteTransition(label = "insert-cut-highlight")
    val dashPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 42f,
        animationSpec = infiniteRepeatable(tween(1_500, easing = FastOutSlowInEasing)),
        label = "insert-centerline",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.52f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "insert-pulse",
    )
    val pipeColor = MaterialTheme.colorScheme.primaryContainer
    val insertColor = MaterialTheme.colorScheme.primary
    val boreColor = MaterialTheme.colorScheme.surface
    val centerlineColor = MaterialTheme.colorScheme.outline
    val dimensionColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()

    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .testTag(PipeCalculatorTags.OffsetDiagram)
            .semantics { contentDescription = description },
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "DN $dn · Ø ${oneDecimal(outsideDiameterMm)} × ${oneDecimal(wallThicknessMm)} mm · ${oneDecimal(result.angleDeg)}°",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "$cutPipeLabel: C = ${oneDecimal(result.insertCutLengthMm)} mm",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "$betweenCutMarksLabel · $faceToFaceLabel = ${oneDecimal(result.fittingFaceDistanceMm)} mm · 2 × g ${oneDecimal(result.weldGapEachMm)} mm",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Canvas(Modifier.fillMaxWidth().height(280.dp)) {
                val w = size.width
                val h = size.height
                val lowerY = h * 0.68f
                val upperY = h * 0.25f
                val lowerElbow = Offset(w * 0.29f, lowerY)
                val upperElbow = Offset(w * 0.71f, upperY)
                val diagonal = upperElbow - lowerElbow
                val diagonalPx = kotlin.math.sqrt(diagonal.x * diagonal.x + diagonal.y * diagonal.y)
                val unit = Offset(diagonal.x / diagonalPx, diagonal.y / diagonalPx)
                val takeoutPx = (diagonalPx * result.elbowTakeoutEachMm / result.centerTravelMm)
                    .toFloat().coerceIn(13f, diagonalPx * 0.22f)
                val gapPx = (diagonalPx * result.weldGapEachMm / result.centerTravelMm)
                    .toFloat().coerceAtLeast(1.5f)
                val lowerArcStart = Offset(lowerElbow.x - takeoutPx, lowerElbow.y)
                val lowerWeld = lowerElbow + Offset(unit.x * takeoutPx, unit.y * takeoutPx)
                val upperWeld = upperElbow - Offset(unit.x * takeoutPx, unit.y * takeoutPx)
                val lowerCut = lowerWeld + Offset(unit.x * gapPx, unit.y * gapPx)
                val upperCut = upperWeld - Offset(unit.x * gapPx, unit.y * gapPx)
                val upperArcEnd = Offset(upperElbow.x + takeoutPx, upperElbow.y)
                val lowerInlet = Offset(w * 0.05f, lowerY)
                val upperOutlet = Offset(w * 0.95f, upperY)
                val outerWidth = 31f
                val innerWidth = (outerWidth *
                    ((outsideDiameterMm - 2.0 * wallThicknessMm) / outsideDiameterMm)).toFloat()
                    .coerceAtMost(outerWidth - 4f)

                val lowerFitting = Path().apply {
                    moveTo(lowerInlet.x, lowerInlet.y)
                    lineTo(lowerArcStart.x, lowerArcStart.y)
                    quadraticTo(lowerElbow.x, lowerElbow.y, lowerWeld.x, lowerWeld.y)
                }
                val insertPath = Path().apply {
                    moveTo(lowerCut.x, lowerCut.y)
                    lineTo(upperCut.x, upperCut.y)
                }
                val upperFitting = Path().apply {
                    moveTo(upperWeld.x, upperWeld.y)
                    quadraticTo(upperElbow.x, upperElbow.y, upperArcEnd.x, upperArcEnd.y)
                    lineTo(upperOutlet.x, upperOutlet.y)
                }
                listOf(lowerFitting, upperFitting).forEach { fitting ->
                    drawPath(fitting, centerlineColor, style = Stroke(outerWidth + 5f, cap = StrokeCap.Butt))
                    drawPath(fitting, pipeColor, style = Stroke(outerWidth, cap = StrokeCap.Butt))
                    drawPath(fitting, boreColor, style = Stroke(innerWidth, cap = StrokeCap.Butt))
                }
                drawPath(insertPath, centerlineColor, style = Stroke(outerWidth + 5f, cap = StrokeCap.Butt))
                drawPath(insertPath, insertColor.copy(alpha = pulse), style = Stroke(outerWidth, cap = StrokeCap.Butt))
                drawPath(insertPath, boreColor, style = Stroke(innerWidth, cap = StrokeCap.Butt))

                val assemblyPath = Path().apply {
                    moveTo(lowerInlet.x, lowerInlet.y)
                    lineTo(lowerArcStart.x, lowerArcStart.y)
                    quadraticTo(lowerElbow.x, lowerElbow.y, lowerWeld.x, lowerWeld.y)
                    lineTo(upperWeld.x, upperWeld.y)
                    quadraticTo(upperElbow.x, upperElbow.y, upperArcEnd.x, upperArcEnd.y)
                    lineTo(upperOutlet.x, upperOutlet.y)
                }
                drawPath(
                    assemblyPath,
                    centerlineColor,
                    style = Stroke(
                        width = 2.5f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(11f, 7f), dashPhase),
                    ),
                )
                val weldNormal = Offset(-unit.y, unit.x) * (outerWidth / 2f)
                drawLine(centerlineColor, lowerWeld - weldNormal, lowerWeld + weldNormal, 3f)
                drawLine(centerlineColor, upperWeld - weldNormal, upperWeld + weldNormal, 3f)
                val cutNormal = Offset(-unit.y, unit.x) * (outerWidth * 0.70f)
                drawLine(dimensionColor, lowerCut - cutNormal, lowerCut + cutNormal, 4f)
                drawLine(dimensionColor, upperCut - cutNormal, upperCut + cutNormal, 4f)
                drawLine(
                    dimensionColor,
                    lowerInlet + Offset(0f, -outerWidth * 0.65f),
                    lowerInlet + Offset(0f, outerWidth * 0.65f),
                    3f,
                )
                drawLine(
                    dimensionColor,
                    upperOutlet + Offset(0f, -outerWidth * 0.65f),
                    upperOutlet + Offset(0f, outerWidth * 0.65f),
                    3f,
                )
                drawCircle(dimensionColor, 5f, lowerElbow)
                drawCircle(dimensionColor, 5f, upperElbow)

                val angleRadius = 38f
                drawArc(
                    dimensionColor,
                    startAngle = 0f,
                    sweepAngle = -result.angleDeg.toFloat(),
                    useCenter = false,
                    topLeft = Offset(lowerElbow.x - angleRadius, lowerElbow.y - angleRadius),
                    size = androidx.compose.ui.geometry.Size(angleRadius * 2f, angleRadius * 2f),
                    style = Stroke(2.5f),
                )

                val heightX = w * 0.14f
                dimensionLine(Offset(heightX, upperY), Offset(heightX, lowerY), dimensionColor)
                val advanceY = h * 0.84f
                dimensionLine(Offset(lowerElbow.x, advanceY), Offset(upperElbow.x, advanceY), dimensionColor)
                drawLine(dimensionColor, Offset(lowerElbow.x, lowerY), Offset(lowerElbow.x, advanceY + 5f), 2f)
                drawLine(dimensionColor, Offset(upperElbow.x, upperY), Offset(upperElbow.x, advanceY + 5f), 2f)

                val normal = Offset(-unit.y, unit.x)
                val centerDimensionOffset = normal * -49f
                val centerDimensionStart = lowerElbow + centerDimensionOffset
                val centerDimensionEnd = upperElbow + centerDimensionOffset
                drawLine(centerlineColor, lowerElbow, centerDimensionStart, 1.5f)
                drawLine(centerlineColor, upperElbow, centerDimensionEnd, 1.5f)
                alignedDimensionLine(centerDimensionStart, centerDimensionEnd, normal, centerlineColor, 2f)

                val cutDimensionOffset = normal * 53f
                val cutDimensionStart = lowerCut + cutDimensionOffset
                val cutDimensionEnd = upperCut + cutDimensionOffset
                drawLine(dimensionColor, lowerCut, cutDimensionStart, 1.8f)
                drawLine(dimensionColor, upperCut, cutDimensionEnd, 1.8f)
                alignedDimensionLine(cutDimensionStart, cutDimensionEnd, normal, dimensionColor, 3.5f)

                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = labelColor
                    textSize = 12.sp.toPx()
                    textAlign = Paint.Align.CENTER
                }
                val dimensionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = labelColor
                    textSize = 9.sp.toPx()
                    textAlign = Paint.Align.CENTER
                }
                val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dimensionColor.toArgb()
                    textSize = 11.sp.toPx()
                    textAlign = Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.White.toArgb()
                    textSize = 9.sp.toPx()
                    textAlign = Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val badge1 = lowerInlet + Offset(0f, -31f)
                val badge2 = lowerCut + normal * 27f
                val badge3 = upperCut + normal * 27f
                val badge4 = upperOutlet + Offset(0f, 31f)
                listOf(badge1, badge2, badge3, badge4).forEach { badge ->
                    drawCircle(dimensionColor, 12f, badge)
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.rotate(-90f, heightX - 18f, (upperY + lowerY) / 2f)
                    canvas.nativeCanvas.drawText(
                        "H = ${oneDecimal(result.targetHeightMm)} mm",
                        heightX - 18f,
                        (upperY + lowerY) / 2f,
                        paint,
                    )
                    canvas.nativeCanvas.restore()
                    canvas.nativeCanvas.drawText(
                        "α ${oneDecimal(result.angleDeg)}° · A ${oneDecimal(result.elbowTakeoutEachMm)} · g ${oneDecimal(result.weldGapEachMm)} mm",
                        lowerElbow.x + 95f,
                        lowerElbow.y + 38f,
                        paint,
                    )
                    canvas.nativeCanvas.drawText(
                        "X = ${oneDecimal(result.horizontalAdvanceMm)} mm",
                        (lowerElbow.x + upperElbow.x) / 2f,
                        advanceY + 30f,
                        paint,
                    )
                    val angle = Math.toDegrees(
                        atan2(
                            (upperElbow.y - lowerElbow.y).toDouble(),
                            (upperElbow.x - lowerElbow.x).toDouble(),
                        ),
                    ).toFloat()
                    val centerDimensionMid = (centerDimensionStart + centerDimensionEnd) * 0.5f
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.rotate(angle, centerDimensionMid.x, centerDimensionMid.y)
                    canvas.nativeCanvas.drawText(
                        "L = ${oneDecimal(result.centerTravelMm)} mm · $centerToCenterLabel",
                        centerDimensionMid.x,
                        centerDimensionMid.y - 9f,
                        dimensionPaint,
                    )
                    canvas.nativeCanvas.restore()

                    val cutDimensionMid = (cutDimensionStart + cutDimensionEnd) * 0.5f
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.rotate(angle, cutDimensionMid.x, cutDimensionMid.y)
                    canvas.nativeCanvas.drawText(
                        "C = ${oneDecimal(result.insertCutLengthMm)} mm · $cutPipeLabel",
                        cutDimensionMid.x,
                        cutDimensionMid.y - 10f,
                        cutPaint,
                    )
                    canvas.nativeCanvas.restore()

                    canvas.nativeCanvas.drawText(face1Mark, lowerWeld.x - 17f, lowerWeld.y - 22f, dimensionPaint)
                    canvas.nativeCanvas.drawText(face2Mark, upperWeld.x + 17f, upperWeld.y - 22f, dimensionPaint)
                    listOf(badge1, badge2, badge3, badge4).forEachIndexed { index, badge ->
                        canvas.nativeCanvas.drawText((index + 1).toString(), badge.x, badge.y + 9f, badgePaint)
                    }
                }
            }
            Text(
                pointLegend,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DrawScope.dimensionLine(start: Offset, end: Offset, color: Color) {
    drawLine(color, start, end, 2f)
    if (start.x == end.x) {
        drawLine(color, Offset(start.x - 7f, start.y), Offset(start.x + 7f, start.y), 2f)
        drawLine(color, Offset(end.x - 7f, end.y), Offset(end.x + 7f, end.y), 2f)
    } else {
        drawLine(color, Offset(start.x, start.y - 7f), Offset(start.x, start.y + 7f), 2f)
        drawLine(color, Offset(end.x, end.y - 7f), Offset(end.x, end.y + 7f), 2f)
    }
}

private fun DrawScope.alignedDimensionLine(
    start: Offset,
    end: Offset,
    normal: Offset,
    color: Color,
    strokeWidth: Float,
) {
    drawLine(color, start, end, strokeWidth)
    val tick = normal * 8f
    drawLine(color, start - tick, start + tick, strokeWidth)
    drawLine(color, end - tick, end + tick, strokeWidth)
}

private fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

private operator fun Offset.times(scale: Float) = Offset(x * scale, y * scale)
