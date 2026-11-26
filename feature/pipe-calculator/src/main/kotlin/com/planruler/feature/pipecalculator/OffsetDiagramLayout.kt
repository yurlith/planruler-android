package com.planruler.feature.pipecalculator

import com.planruler.pipecalculator.PipeOffsetResult
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal data class DiagramPoint(val x: Float, val y: Float) {
    operator fun plus(other: DiagramPoint) = DiagramPoint(x + other.x, y + other.y)
    operator fun minus(other: DiagramPoint) = DiagramPoint(x - other.x, y - other.y)
    operator fun times(k: Float) = DiagramPoint(x * k, y * k)
}

internal data class DiagramSegment(val a: DiagramPoint, val b: DiagramPoint)

internal enum class DiagramLabelKind { CUT, OFFSET, TRAVEL, ADVANCE, ANGLE }

internal data class DiagramLabel(
    val kind: DiagramLabelKind,
    val text: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    fun overlaps(other: DiagramLabel, gap: Float): Boolean =
        left < other.right + gap && other.left < right + gap && top < other.bottom + gap && other.top < bottom + gap
}

/** A dimension: the line with arrows and the two extension lines that tie it to the geometry. */
internal data class DiagramDimension(val line: DiagramSegment, val extensions: List<DiagramSegment>)

/**
 * Screen geometry of the offset sketch, drawn to scale: incoming run, sloped leg and
 * outgoing run, the piece to cut, and H/X/L dimensions whose values never overlap each
 * other or any line of the sketch.
 */
internal data class OffsetDiagram(
    val runIn: DiagramSegment,
    val sloped: DiagramSegment,
    val runOut: DiagramSegment,
    /** The straight piece between the cut marks; null when the elbows do not fit. */
    val cut: DiagramSegment?,
    val pipeWidthPx: Float,
    val dimensions: List<DiagramDimension>,
    val labels: List<DiagramLabel>,
    val angleCenter: DiagramPoint,
    val angleRadiusPx: Float,
    /** Slope of the sloped leg in screen degrees, measured from +x towards -y. */
    val angleDeg: Float,
)

internal data class OffsetDiagramTexts(
    val cut: String,
    val offset: String,
    val travel: String,
    val advance: String,
    val angle: String,
)

/**
 * Lays the sketch out in a [widthPx] × [heightPx] box. [textWidth] measures one label in
 * pixels at [labelHeightPx] line height; [unitPx] is one density-independent pixel.
 */
internal fun layoutOffsetDiagram(
    result: PipeOffsetResult,
    pipeOutsideDiameterMm: Double?,
    widthPx: Float,
    heightPx: Float,
    unitPx: Float,
    labelHeightPx: Float,
    textWidth: (String) -> Float,
    texts: OffsetDiagramTexts,
): OffsetDiagram {
    val pad = labelHeightPx * 0.25f
    fun box(text: String) = Pair(textWidth(text) + 2 * pad, labelHeightPx + 2 * pad)

    val offsetBox = box(texts.offset)
    val advanceBox = box(texts.advance)
    val margin = 6f * unitPx
    val dimGap = 14f * unitPx
    val overhang = 4f * unitPx
    val labelGap = 3f * unitPx
    // A value clears the end of an extension line: overhang + line half-width + gap.
    val clear = overhang + 1f * unitPx + labelGap + 1f * unitPx
    val rightReserve = dimGap + clear + 12f * unitPx + offsetBox.first + margin
    val bottomReserve = if (result.advanceMm > 0.0) dimGap + clear + 12f * unitPx + advanceBox.second + margin else margin
    val topReserve = labelHeightPx + 2 * pad + margin

    val offset = result.offsetMm
    val advance = result.advanceMm
    val lead = max(advance, offset) * 0.32
    val modelWidth = lead + advance + lead
    val availableWidth = max(1f, widthPx - margin - rightReserve)
    val availableHeight = max(1f, heightPx - topReserve - bottomReserve)
    val scale = min(availableWidth / modelWidth, availableHeight / offset).toFloat()

    val drawnWidth = (modelWidth * scale).toFloat()
    val x0 = margin + (availableWidth - drawnWidth) / 2f
    val yLow = topReserve + (availableHeight + (offset * scale).toFloat()) / 2f
    val p0 = DiagramPoint(x0, yLow)
    val p1 = DiagramPoint(x0 + (lead * scale).toFloat(), yLow)
    val p2 = DiagramPoint(p1.x + (advance * scale).toFloat(), yLow - (offset * scale).toFloat())
    val p3 = DiagramPoint(p2.x + (lead * scale).toFloat(), p2.y)

    val slopeLength = hypot((p2.x - p1.x).toDouble(), (p2.y - p1.y).toDouble()).toFloat()
    val along = DiagramPoint((p2.x - p1.x) / slopeLength, (p2.y - p1.y) / slopeLength)
    // Normal pointing to the upper left of the sloped leg (screen y grows downwards).
    val upperLeft = DiagramPoint(along.y, -along.x)

    val pipeWidth = ((pipeOutsideDiameterMm ?: 0.0) * scale).toFloat().coerceIn(4f * unitPx, 12f * unitPx)
    val deduction = ((result.takeoutMm + result.jointGapMm) * scale).toFloat()
    val cut = if (result.fits && slopeLength > 2 * deduction) {
        DiagramSegment(p1 + along * deduction, p2 - along * deduction)
    } else {
        null
    }

    val dimensions = mutableListOf<DiagramDimension>()
    val obstacles = mutableListOf(
        DiagramSegment(p0, p1) to pipeWidth / 2f,
        DiagramSegment(p1, p2) to pipeWidth / 2f,
        DiagramSegment(p2, p3) to pipeWidth / 2f,
    )
    fun addDimension(dimension: DiagramDimension) {
        dimensions += dimension
        obstacles += dimension.line to 1.5f * unitPx
        dimension.extensions.forEach { obstacles += it to 1f * unitPx }
    }

    // H on the right, between the axis levels of the two runs.
    val hx = p3.x + dimGap
    val offsetLine = DiagramSegment(DiagramPoint(hx, yLow), DiagramPoint(hx, p2.y))
    addDimension(
        DiagramDimension(
            offsetLine,
            listOf(
                DiagramSegment(DiagramPoint(p1.x + pipeWidth, yLow), DiagramPoint(hx + overhang, yLow)),
                DiagramSegment(DiagramPoint(p3.x + 2f * unitPx, p2.y), DiagramPoint(hx + overhang, p2.y)),
            ),
        ),
    )
    // X under the incoming run, from elbow centre to elbow centre; none for a right angle.
    val advanceLine = if (advance > 0.0 && p2.x - p1.x > 2f * unitPx) {
        val y = yLow + dimGap
        DiagramSegment(DiagramPoint(p1.x, y), DiagramPoint(p2.x, y)).also { line ->
            addDimension(
                DiagramDimension(
                    line,
                    listOf(
                        DiagramSegment(DiagramPoint(p1.x, yLow + pipeWidth), DiagramPoint(p1.x, y + overhang)),
                        DiagramSegment(DiagramPoint(p2.x, p2.y + pipeWidth), DiagramPoint(p2.x, y + overhang)),
                    ),
                ),
            )
        }
    } else {
        null
    }
    // L along the sloped leg on its upper-left side. At 90° it equals H, so it is not repeated.
    val travelLine = if (abs(result.angleDeg - 90.0) > 1e-6) {
        val shift = upperLeft * (pipeWidth / 2f + dimGap)
        DiagramSegment(p1 + shift, p2 + shift).also { line ->
            val tail = upperLeft * overhang
            addDimension(
                DiagramDimension(
                    line,
                    listOf(
                        DiagramSegment(p1 + upperLeft * pipeWidth, line.a + tail),
                        DiagramSegment(p2 + upperLeft * pipeWidth, line.b + tail),
                    ),
                ),
            )
        }
    } else {
        null
    }

    val placed = mutableListOf<DiagramLabel>()
    fun fits(label: DiagramLabel): Boolean =
        label.left >= 0f && label.top >= 0f && label.right <= widthPx && label.bottom <= heightPx &&
            placed.none { it.overlaps(label, labelGap) } &&
            obstacles.none { (segment, halfWidth) -> segmentHitsBox(segment, label, halfWidth + labelGap) }

    fun place(kind: DiagramLabelKind, text: String, centers: List<DiagramPoint>) {
        val (w, h) = box(text)
        centers.asSequence()
            .map { DiagramLabel(kind, text, it.x - w / 2f, it.y - h / 2f, w, h) }
            .firstOrNull(::fits)
            ?.let { placed += it }
    }

    /** Candidate centres beside a line, stepping outwards along [normal] and along the line. */
    fun besideLine(line: DiagramSegment, normal: DiagramPoint, text: String, minOffset: Float): List<DiagramPoint> {
        val (w, h) = box(text)
        val halfExtent = abs(normal.x) * w / 2f + abs(normal.y) * h / 2f
        return buildList {
            listOf(0.5f, 0.38f, 0.62f, 0.26f, 0.74f).forEach { t ->
                val base = line.a + (line.b - line.a) * t
                listOf(0f, 4f, 10f, 18f).forEach { extra ->
                    add(base + normal * (minOffset + halfExtent + extra * unitPx))
                }
            }
        }
    }

    // Dimension values have one natural place each, so they go first; the cut length can sit
    // on either side of the sloped leg and takes what is left.
    place(DiagramLabelKind.OFFSET, texts.offset, besideLine(offsetLine, DiagramPoint(1f, 0f), texts.offset, clear))
    advanceLine?.let { line ->
        place(DiagramLabelKind.ADVANCE, texts.advance, besideLine(line, DiagramPoint(0f, 1f), texts.advance, clear))
    }
    cut?.let { piece ->
        val lowerRight = upperLeft * -1f
        // Beyond the L dimension when there is one; right beside the leg when there is not.
        val farSide = if (travelLine != null) pipeWidth / 2f + dimGap + clear else pipeWidth / 2f + labelGap + unitPx
        place(
            DiagramLabelKind.CUT,
            texts.cut,
            besideLine(piece, lowerRight, texts.cut, pipeWidth / 2f + labelGap + unitPx) +
                besideLine(piece, upperLeft, texts.cut, farSide),
        )
    }
    travelLine?.let { line ->
        place(DiagramLabelKind.TRAVEL, texts.travel, besideLine(line, upperLeft, texts.travel, clear))
    }
    val arcRadius = min(slopeLength * 0.28f, 30f * unitPx)
    val halfAngle = Math.toRadians(result.angleDeg / 2.0)
    val bisector = DiagramPoint(cos(halfAngle).toFloat(), -sin(halfAngle).toFloat())
    place(
        DiagramLabelKind.ANGLE,
        texts.angle,
        listOf(1.0f, 1.25f, 1.6f).map { k ->
            val (w, h) = box(texts.angle)
            p1 + bisector * (arcRadius * k + max(w, h) / 2f)
        },
    )

    return OffsetDiagram(
        runIn = DiagramSegment(p0, p1),
        sloped = DiagramSegment(p1, p2),
        runOut = DiagramSegment(p2, p3),
        cut = cut,
        pipeWidthPx = pipeWidth,
        dimensions = dimensions,
        labels = placed,
        angleCenter = p1,
        angleRadiusPx = arcRadius,
        angleDeg = result.angleDeg.toFloat(),
    )
}

/** True when a segment widened by [halfWidth] touches the label box. */
internal fun segmentHitsBox(segment: DiagramSegment, box: DiagramLabel, halfWidth: Float): Boolean {
    val left = box.left - halfWidth
    val top = box.top - halfWidth
    val right = box.right + halfWidth
    val bottom = box.bottom + halfWidth
    var t0 = 0f
    var t1 = 1f
    val dx = segment.b.x - segment.a.x
    val dy = segment.b.y - segment.a.y
    // Liang–Barsky clipping against the widened box.
    val checks = listOf(-dx to segment.a.x - left, dx to right - segment.a.x, -dy to segment.a.y - top, dy to bottom - segment.a.y)
    for ((p, q) in checks) {
        if (p == 0f) {
            if (q < 0f) return false
        } else {
            val r = q / p
            if (p < 0f) {
                if (r > t1) return false
                if (r > t0) t0 = r
            } else {
                if (r < t0) return false
                if (r < t1) t1 = r
            }
        }
    }
    return t0 <= t1
}
