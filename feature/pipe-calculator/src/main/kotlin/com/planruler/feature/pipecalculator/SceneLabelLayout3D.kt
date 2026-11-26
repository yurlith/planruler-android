package com.planruler.feature.pipecalculator

import kotlin.math.abs
import kotlin.math.hypot

/** A dimension line where it lands on the screen, with the size of the value written beside it. */
internal data class SceneDimensionLine(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val textWidth: Float,
    val textHeight: Float,
)

/** A round part marker on the screen. */
internal data class SceneMarker(val x: Float, val y: Float, val radius: Float)

internal data class SceneSegment(val startX: Float, val startY: Float, val endX: Float, val endY: Float)

/** Where a value is written: an axis-aligned box in screen pixels. */
internal data class SceneLabelBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun overlaps(other: SceneLabelBox): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun overlaps(marker: SceneMarker): Boolean {
        val nearestX = marker.x.coerceIn(left, right)
        val nearestY = marker.y.coerceIn(top, bottom)
        return hypot(marker.x - nearestX, marker.y - nearestY) < marker.radius
    }

    /** Whether any part of [segment] runs through the box (Liang–Barsky clipping). */
    fun crosses(segment: SceneSegment): Boolean {
        val dx = segment.endX - segment.startX
        val dy = segment.endY - segment.startY
        val p = floatArrayOf(-dx, dx, -dy, dy)
        val q = floatArrayOf(segment.startX - left, right - segment.startX, segment.startY - top, bottom - segment.startY)
        var enter = 0f
        var leave = 1f
        for (i in 0 until 4) {
            if (p[i] == 0f) {
                if (q[i] < 0f) return false
            } else {
                val r = q[i] / p[i]
                if (p[i] < 0f) {
                    if (r > leave) return false
                    if (r > enter) enter = r
                } else {
                    if (r < enter) return false
                    if (r < leave) leave = r
                }
            }
        }
        return true
    }

    fun inside(width: Float, height: Float): Boolean = left >= 0f && top >= 0f && right <= width && bottom <= height

    fun grown(by: Float): SceneLabelBox = SceneLabelBox(left - by, top - by, right + by, bottom + by)

    /** The same box slid the shortest way onto a [width] × [height] canvas. */
    fun clampedInto(width: Float, height: Float): SceneLabelBox {
        val shiftX = when {
            left < 0f -> -left
            right > width -> width - right
            else -> 0f
        }
        val shiftY = when {
            top < 0f -> -top
            bottom > height -> height - bottom
            else -> 0f
        }
        return SceneLabelBox(left + shiftX, top + shiftY, right + shiftX, bottom + shiftY)
    }
}

/**
 * Writes each dimension's value beside its own line where it covers no other value, no part
 * marker and no dimension line, and stays on the canvas. The middle of the line is tried first,
 * then points further out and the other side of the line; a value that would run off the canvas
 * is also tried slid back onto it. Values are placed in order, so the most important dimension
 * goes first. A value that could only be written over another value is left out (null); its line
 * is still drawn.
 */
internal fun layoutSceneDimensionLabels(
    dimensions: List<SceneDimensionLine>,
    markers: List<SceneMarker>,
    route: List<SceneSegment>,
    width: Float,
    height: Float,
    gapPx: Float = 4f,
): List<SceneLabelBox?> {
    val lines = dimensions.map { SceneSegment(it.startX, it.startY, it.endX, it.endY) }
    val placed = mutableListOf<SceneLabelBox>()
    return dimensions.mapIndexed { index, dimension ->
        val otherLines = lines.filterIndexed { other, _ -> other != index }
        var best: SceneLabelBox? = null
        var bestCost = Int.MAX_VALUE
        for (candidate in labelCandidates(dimension, width, height, gapPx)) {
            val cost = labelCost(candidate, placed, markers, lines[index], otherLines, route, width, height, gapPx)
            if (cost < bestCost) {
                best = candidate
                bestCost = cost
                if (cost == 0) break
            }
        }
        best?.takeIf { bestCost < COVERS_VALUE }?.also(placed::add)
    }
}

/** Fractions along the line, middle first, then alternating outwards. */
private val ALONG_LINE = floatArrayOf(0.5f, 0.38f, 0.62f, 0.26f, 0.74f, 0.14f, 0.86f)

private fun labelCandidates(
    dimension: SceneDimensionLine,
    width: Float,
    height: Float,
    gapPx: Float,
): Sequence<SceneLabelBox> = sequence {
    val dx = dimension.endX - dimension.startX
    val dy = dimension.endY - dimension.startY
    val length = hypot(dx, dy)
    // A line seen end-on is a point: its value goes above it.
    val normalX = if (length < 1e-3f) 0f else -dy / length
    val normalY = if (length < 1e-3f) -1f else dx / length
    // The side that reads as "above" the line comes first.
    val above = if (normalY < 0f || (normalY == 0f && normalX < 0f)) 1f else -1f
    val halfWidth = dimension.textWidth / 2f
    val halfHeight = dimension.textHeight / 2f
    // From the line to the centre of a box that just clears it, along the normal.
    val clearance = abs(normalX) * halfWidth + abs(normalY) * halfHeight + gapPx
    for (ring in 1..2) {
        for (t in ALONG_LINE) {
            for (side in floatArrayOf(above, -above)) {
                val distance = side * clearance * ring
                val centerX = dimension.startX + dx * t + normalX * distance
                val centerY = dimension.startY + dy * t + normalY * distance
                val box = SceneLabelBox(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
                yield(box)
                if (!box.inside(width, height)) yield(box.clampedInto(width, height))
            }
        }
    }
}

private fun labelCost(
    box: SceneLabelBox,
    placed: List<SceneLabelBox>,
    markers: List<SceneMarker>,
    ownLine: SceneSegment,
    otherLines: List<SceneSegment>,
    route: List<SceneSegment>,
    width: Float,
    height: Float,
    gapPx: Float,
): Int {
    val padded = box.grown(gapPx)
    var cost = 0
    placed.forEach { if (padded.overlaps(it)) cost += COVERS_VALUE }
    if (!box.inside(width, height)) cost += OFF_CANVAS
    markers.forEach { if (padded.overlaps(it)) cost += COVERS_MARKER }
    // Beside its own line the box keeps one gap clear by construction; only a box slid onto the
    // canvas can land on it.
    if (box.crosses(ownLine)) cost += COVERS_DIMENSION_LINE
    otherLines.forEach { if (padded.crosses(it)) cost += COVERS_DIMENSION_LINE }
    route.forEach { if (box.crosses(it)) cost += COVERS_ROUTE }
    return cost
}

private const val COVERS_VALUE = 1_000
private const val OFF_CANVAS = 400
private const val COVERS_MARKER = 100
private const val COVERS_DIMENSION_LINE = 60
private const val COVERS_ROUTE = 10
