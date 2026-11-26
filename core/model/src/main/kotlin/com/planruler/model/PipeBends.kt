package com.planruler.model

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.roundToInt

private val PIPE_CATEGORIES = setOf(TradeCategory.HEATING, TradeCategory.PLUMBING)

/** Plan turns of at least 5 degrees and the 90-degree bends at rises and drops. */
fun pipeBendAngles(measurement: Measurement): List<Int> {
    val run = measurement.type == MeasurementType.POLYLINE || measurement.type == MeasurementType.DISTANCE
    if (!run || (measurement.route == null && measurement.takeoff.category !in PIPE_CATEGORIES)) return emptyList()
    val points = measurement.points
    val elbows = mutableListOf<Int>()
    points.indices.forEach { index ->
        val vertical = abs(measurement.route?.riseAt(index) ?: 0.0) > 0.5
        val end = index == 0 || index == points.lastIndex
        elbows += when {
            vertical && end -> listOf(90)
            vertical -> listOf(90, 90)
            end -> emptyList()
            else -> bendDegrees(points[index - 1], points[index], points[index + 1])
                .takeIf { it >= MIN_BEND_DEG }?.let { listOf(it.roundToInt()) } ?: emptyList()
        }
    }
    return elbows
}

private fun bendDegrees(a: DocPoint, b: DocPoint, c: DocPoint): Double {
    val ux = b.x - a.x
    val uy = b.y - a.y
    val vx = c.x - b.x
    val vy = c.y - b.y
    val lengths = hypot(ux, uy) * hypot(vx, vy)
    if (lengths <= 0.0) return 0.0
    return Math.toDegrees(acos(((ux * vx + uy * vy) / lengths).coerceIn(-1.0, 1.0)))
}

private const val MIN_BEND_DEG = 5.0

