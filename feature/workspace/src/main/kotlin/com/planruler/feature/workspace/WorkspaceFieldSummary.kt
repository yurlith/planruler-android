package com.planruler.feature.workspace

import com.planruler.model.Calibration
import com.planruler.model.DocPoint
import com.planruler.model.Measurement
import com.planruler.model.MeasurementType
import com.planruler.model.TakeoffTotals
import com.planruler.model.TradeCategory
import com.planruler.model.calculateTakeoffTotals
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.roundToInt

internal data class FieldSummary(
    val lastMeasurement: Measurement?,
    val material: String?,
    val totals: TakeoffTotals,
    val assemblySource: Measurement?,
    /** Elbows the pipe runs of the group need: plan corners plus the bends of risers and drops. */
    val elbows: Int = 0,
)

internal fun fieldSummary(
    measurements: List<Measurement>,
    pageIndex: Int,
    calibration: Calibration?,
    selected: Measurement?,
): FieldSummary {
    val onPage = measurements.filter { it.pageIndex == pageIndex }
    val last = onPage.lastOrNull()
    val anchor = selected ?: last
    val material = anchor?.takeoff?.material?.trim()?.takeIf(String::isNotEmpty)
    val group = if (material == null) onPage else onPage.filter { it.takeoff.material?.trim() == material }
    val candidate = anchor?.takeIf {
        calibration != null && it.type in setOf(MeasurementType.DISTANCE, MeasurementType.POLYLINE) && it.points.size >= 2
    }
    return FieldSummary(last, material, calculateTakeoffTotals(group, calibration), candidate, pipeElbows(group))
}

private val PIPE_CATEGORIES = setOf(TradeCategory.HEATING, TradeCategory.PLUMBING)

/**
 * Elbows a pipe run needs. A plan corner of 5° or more is one elbow; a riser or drop costs
 * two elbows inside a run (down-up) and one at either end, where it replaces the corner.
 */
internal fun pipeElbows(measurements: List<Measurement>): Int = measurements.sumOf(::elbowsOf)

private fun elbowsOf(measurement: Measurement): Int {
    val run = measurement.type == MeasurementType.POLYLINE || measurement.type == MeasurementType.DISTANCE
    if (!run || (measurement.route == null && measurement.takeoff.category !in PIPE_CATEGORIES)) return 0
    val points = measurement.points
    var elbows = 0
    points.indices.forEach { index ->
        val vertical = abs(measurement.route?.riseAt(index) ?: 0.0) > 0.5
        val end = index == 0 || index == points.lastIndex
        elbows += when {
            vertical && end -> 1
            vertical -> 2
            end -> 0
            bendDegrees(points[index - 1], points[index], points[index + 1]) >= MIN_BEND_DEG -> 1
            else -> 0
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

internal fun fieldTotalValue(totals: TakeoffTotals, pieces: String = "pcs"): String = buildList {
    if (abs(totals.adjustedLengthMeters) > 1e-9) add(formatFieldNumber(totals.adjustedLengthMeters, "m"))
    if (abs(totals.adjustedAreaSquareMeters) > 1e-9) add(formatFieldNumber(totals.adjustedAreaSquareMeters, "m²"))
    if (abs(totals.adjustedCount) > 1e-9) add(formatFieldNumber(totals.adjustedCount, pieces))
}.joinToString(" · ").ifBlank { "—" }

/** Strip total with the elbow count a fitter needs for the same runs. */
internal fun fieldTotalWithElbows(total: String, elbows: Int, elbowsShort: String): String = when {
    elbows <= 0 -> total
    total == "—" || total.isBlank() -> "$elbows $elbowsShort"
    else -> "$total · $elbows $elbowsShort"
}

/** Plan label of a vertical leg: arrow and height in millimetres. */
internal fun riseLabel(riseMm: Double): String =
    "${if (riseMm > 0.0) "↑" else "↓"} ${abs(riseMm).roundToInt()} mm"

private fun formatFieldNumber(value: Double, unit: String): String =
    String.format(Locale.US, if (abs(value) >= 100.0) "%.1f %s" else "%.2f %s", value, unit)
