package com.planruler.feature.workspace

import com.planruler.model.Calibration
import com.planruler.model.Measurement
import com.planruler.model.MeasurementType
import com.planruler.model.TakeoffTotals
import com.planruler.model.calculateTakeoffTotals
import java.util.Locale
import kotlin.math.abs
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

internal fun pipeElbows(measurements: List<Measurement>): Int = measurements.sumOf { pipeBendAngles(it).size }

internal fun pipeBendAngles(measurement: Measurement): List<Int> = com.planruler.model.pipeBendAngles(measurement)

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
