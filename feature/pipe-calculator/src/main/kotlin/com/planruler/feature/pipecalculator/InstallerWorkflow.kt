package com.planruler.feature.pipecalculator

import com.planruler.fabrication3d.ChainPlan3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.Vec3
import com.planruler.model.InstallationEndDirection
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobInput
import com.planruler.model.InstallationLateralDirection
import com.planruler.model.InstallationMeasurementReference
import com.planruler.model.InstallationTaskType
import com.planruler.model.InstallationVerticalDirection
import com.planruler.pipecalculator.PIPE_INSTALLATION_SERIES
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

internal enum class InstallerCalculationPath { AUTO_ROUTE, MANUAL_TEMPLATE }

/** Installer terms translated once into the coordinate frame understood by the 3D engine. */
internal data class InstallerRouteRequest(
    val taskType: InstallationTaskType,
    val calculationPath: InstallerCalculationPath,
    val target: Vec3,
    val endDirection: Vec3,
    val preferredAngleDeg: Double?,
    val minimumStraightMm: Double,
    val manualTemplate: ChainPlan3D? = null,
    val referenceCorrectionMm: Double = 0.0,
)

internal fun installerRouteRequest(job: InstallationJob): InstallerRouteRequest {
    val input = job.input
    val pipe = PIPE_INSTALLATION_SERIES.firstOrNull { it.dn == input.nominalDiameter }
    val radius = (pipe?.outsideDiameterMm ?: 0.0) / 2.0
    val correction = radialReferenceOffset(input.endReference, radius) -
        radialReferenceOffset(input.startReference, radius)
    val lateralMagnitude = (input.lateralOffsetMm + correction).coerceAtLeast(0.0)
    val lateral = when (input.lateralDirection) {
        InstallationLateralDirection.LEFT -> -lateralMagnitude
        InstallationLateralDirection.RIGHT -> lateralMagnitude
    }
    val vertical = when (input.verticalDirection) {
        InstallationVerticalDirection.UP -> input.verticalOffsetMm
        InstallationVerticalDirection.DOWN -> -input.verticalOffsetMm
    }
    val taskTarget = when (job.taskType) {
        InstallationTaskType.STRAIGHT_INSERT -> Vec3(input.alongMm, 0.0, 0.0)
        InstallationTaskType.FLAT_OFFSET -> Vec3(input.alongMm, lateral, 0.0)
        else -> Vec3(input.alongMm, lateral, vertical)
    }
    val path = when (job.taskType) {
        InstallationTaskType.OBSTACLE_BYPASS,
        InstallationTaskType.TEE_BRANCH,
        InstallationTaskType.REDUCER,
        InstallationTaskType.MANUAL,
        -> InstallerCalculationPath.MANUAL_TEMPLATE
        else -> InstallerCalculationPath.AUTO_ROUTE
    }
    return InstallerRouteRequest(
        taskType = job.taskType,
        calculationPath = path,
        target = taskTarget,
        endDirection = input.endDirection.vector(),
        preferredAngleDeg = input.angleDeg.takeIf { job.taskType != InstallationTaskType.STRAIGHT_INSERT },
        minimumStraightMm = input.minimumStraightMm,
        manualTemplate = if (path == InstallerCalculationPath.MANUAL_TEMPLATE) {
            manualTemplate(job.taskType, input, lateral, vertical)
        } else {
            null
        },
        referenceCorrectionMm = correction,
    )
}

private fun radialReferenceOffset(reference: InstallationMeasurementReference, radiusMm: Double): Double =
    when (reference) {
        InstallationMeasurementReference.PIPE_OUTER_EDGE -> radiusMm
        InstallationMeasurementReference.PIPE_INNER_EDGE -> -radiusMm
        else -> 0.0
    }

private fun InstallationEndDirection.vector(): Vec3 = when (this) {
    InstallationEndDirection.FORWARD -> Vec3.UNIT_X
    InstallationEndDirection.BACK -> -Vec3.UNIT_X
    InstallationEndDirection.LEFT -> -Vec3.UNIT_Y
    InstallationEndDirection.RIGHT -> Vec3.UNIT_Y
    InstallationEndDirection.UP -> Vec3.UNIT_Z
    InstallationEndDirection.DOWN -> -Vec3.UNIT_Z
}

private fun manualTemplate(
    task: InstallationTaskType,
    input: InstallationJobInput,
    lateral: Double,
    vertical: Double,
): ChainPlan3D {
    val straight = max(input.minimumStraightMm, input.alongMm / 4.0)
    val transfer = max(input.minimumStraightMm, hypot(lateral, vertical))
    val roll = Math.toDegrees(atan2(vertical, lateral.takeIf { it != 0.0 } ?: 1.0))
    val angle = input.angleDeg.coerceIn(1.0, 179.0)
    return when (task) {
        InstallationTaskType.OBSTACLE_BYPASS -> ChainPlan3D(
            steps = listOf(
                ChainStep3D.Pipe(straight),
                ChainStep3D.Elbow(angle, roll),
                ChainStep3D.Pipe(transfer),
                ChainStep3D.Elbow(-angle, 0.0),
                ChainStep3D.Pipe(straight),
                ChainStep3D.Flange(),
            ),
        )
        InstallationTaskType.TEE_BRANCH -> ChainPlan3D(
            steps = listOf(
                ChainStep3D.Pipe(straight),
                ChainStep3D.Tee(
                    rollDeg = roll,
                    branch = listOf(ChainStep3D.Pipe(transfer), ChainStep3D.Flange()),
                ),
                ChainStep3D.Pipe(straight),
                ChainStep3D.Flange(),
            ),
        )
        InstallationTaskType.REDUCER -> {
            val series = PIPE_INSTALLATION_SERIES
            val currentIndex = series.indexOfFirst { it.dn == input.nominalDiameter }
            val smaller = series.getOrNull((currentIndex - 1).coerceAtLeast(0)) ?: series.first()
            ChainPlan3D(
                steps = listOf(
                    ChainStep3D.Pipe(straight),
                    ChainStep3D.Reducer(
                        lengthMm = max(60.0, smaller.outsideDiameterMm * 1.5),
                        smallNominalDiameter = smaller.dn,
                        smallOutsideDiameterMm = smaller.outsideDiameterMm,
                        smallWallThicknessMm = smaller.wallThicknessMm,
                    ),
                    ChainStep3D.Pipe(straight),
                    ChainStep3D.Flange(),
                ),
            )
        }
        InstallationTaskType.MANUAL -> ChainPlan3D()
        else -> ChainPlan3D()
    }
}
