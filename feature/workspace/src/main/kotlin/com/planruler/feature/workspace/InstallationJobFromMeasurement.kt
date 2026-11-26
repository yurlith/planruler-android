package com.planruler.feature.workspace

import com.planruler.model.Calibration
import com.planruler.model.InstallationEndDirection
import com.planruler.model.InstallationInputMode
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobId
import com.planruler.model.InstallationJobInput
import com.planruler.model.InstallationJobSource2D
import com.planruler.model.InstallationLateralDirection
import com.planruler.model.InstallationMeasurementReference
import com.planruler.model.InstallationPipeMaterial
import com.planruler.model.InstallationTaskType
import com.planruler.model.InstallationVerticalDirection
import com.planruler.model.Measurement
import com.planruler.model.MeasurementType
import kotlin.math.abs

private val installationDns = listOf(15, 20, 25, 32, 40, 50, 65, 80, 100, 125, 150, 200, 250, 300)

internal fun measurementToInstallationJob(
    measurement: Measurement,
    calibration: Calibration?,
    now: Long,
    id: InstallationJobId,
): InstallationJob? {
    if (calibration == null || measurement.type !in setOf(MeasurementType.DISTANCE, MeasurementType.POLYLINE)) {
        return null
    }
    if (measurement.points.size < 2) return null

    val factor = calibration.metersPerDocumentUnit * 1_000.0
    val totalLengthMm = measurement.points.zipWithNext()
        .sumOf { (start, end) -> start.distanceTo(end) } * factor
    if (!totalLengthMm.isFinite() || totalLengthMm <= 0.0) return null

    val start = measurement.points.first()
    val end = measurement.points.last()
    val dxMm = (end.x - start.x) * factor
    val dyMm = (end.y - start.y) * factor
    val xIsAlong = abs(dxMm) >= abs(dyMm)
    val terminalAlongMm = if (xIsAlong) abs(dxMm) else abs(dyMm)
    val lateralMm = if (xIsAlong) abs(dyMm) else abs(dxMm)
    val alongMm = if (measurement.points.size == 2) {
        totalLengthMm
    } else {
        terminalAlongMm.takeIf { it > 0.5 } ?: totalLengthMm
    }
    // Positive when the far end lies to the right of the direction of travel. The page's y
    // runs down, so turning the travel direction a quarter clockwise points to its right.
    val travelSign = if (xIsAlong) (if (dxMm < 0.0) -1.0 else 1.0) else (if (dyMm < 0.0) -1.0 else 1.0)
    val minorSigned = if (xIsAlong) dyMm * travelSign else -dxMm * travelSign
    val materialName = measurement.takeoff.material?.trim()?.takeIf(String::isNotEmpty)
    // A rise or drop taken between the ends lifts the far run to another level: a 3D offset.
    // Legs at the very first or last point belong to the risers beyond the job and are left out.
    val route = measurement.route
    val interiorRiseMm = if (route == null) 0.0 else (1 until measurement.points.size - 1).sumOf(route::riseAt)
    val rises = abs(interiorRiseMm) > 0.5
    val task = when {
        rises -> InstallationTaskType.ROLLING_OFFSET
        measurement.points.size == 2 || lateralMm <= 0.5 -> InstallationTaskType.STRAIGHT_INSERT
        else -> InstallationTaskType.FLAT_OFFSET
    }
    val straight = task == InstallationTaskType.STRAIGHT_INSERT

    return InstallationJob(
        id = id,
        name = listOfNotNull(measurement.label, materialName, measurement.takeoff.diameter)
            .joinToString(" · ")
            .ifBlank { "2D · ${measurement.id.value.take(8)}" }
            .take(120),
        taskType = task,
        sourceMeasurementIds = listOf(measurement.id),
        source2D = InstallationJobSource2D(
            measurementId = measurement.id,
            pageIndex = measurement.pageIndex,
            points = measurement.points,
            millimetersPerDocumentUnit = factor,
            material = materialName,
            diameter = measurement.takeoff.diameter,
        ),
        input = InstallationJobInput(
            nominalDiameter = nearestDn(measurement.takeoff.diameter),
            material = installationMaterial(materialName),
            materialName = materialName,
            inputMode = InstallationInputMode.BASIC,
            // A run measured on a plan joins pipes, not flanges: both ends are welded on site.
            startReference = InstallationMeasurementReference.PIPE_FACE,
            endReference = InstallationMeasurementReference.PIPE_FACE,
            alongMm = alongMm,
            lateralOffsetMm = if (straight || lateralMm <= 0.5) 0.0 else lateralMm,
            verticalOffsetMm = if (rises) abs(interiorRiseMm) else 0.0,
            lateralDirection = if (minorSigned < 0.0) {
                InstallationLateralDirection.LEFT
            } else {
                InstallationLateralDirection.RIGHT
            },
            verticalDirection = if (interiorRiseMm < 0.0) InstallationVerticalDirection.DOWN else InstallationVerticalDirection.UP,
            endDirection = InstallationEndDirection.FORWARD,
            targetOffsetMm = if (straight) 0.0 else kotlin.math.hypot(if (lateralMm <= 0.5) 0.0 else lateralMm, if (rises) interiorRiseMm else 0.0),
            overallFaceToFaceMm = alongMm,
            quantity = measurement.takeoff.quantity.toInt().coerceAtLeast(1),
        ),
        createdAtEpochMs = now,
        modifiedAtEpochMs = now,
        lastOpenedAtEpochMs = now,
    )
}

private fun nearestDn(value: String?): Int {
    val parsed = Regex("\\d+").find(value.orEmpty())?.value?.toIntOrNull() ?: return 50
    return installationDns.minByOrNull { abs(it - parsed) } ?: 50
}

private fun installationMaterial(value: String?): InstallationPipeMaterial {
    val normalized = value.orEmpty().lowercase()
    return when {
        "нерж" in normalized || "stainless" in normalized -> InstallationPipeMaterial.STAINLESS_STEEL
        "мед" in normalized || "copper" in normalized -> InstallationPipeMaterial.COPPER
        "ppr" in normalized || "ппр" in normalized || "полипроп" in normalized -> InstallationPipeMaterial.PPR
        "стал" in normalized || "steel" in normalized -> InstallationPipeMaterial.CARBON_STEEL
        else -> InstallationPipeMaterial.OTHER
    }
}
