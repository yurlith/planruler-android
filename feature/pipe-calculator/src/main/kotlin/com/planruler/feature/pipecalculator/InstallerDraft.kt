package com.planruler.feature.pipecalculator

import com.planruler.model.InstallationEndDirection
import com.planruler.model.InstallationInputMode
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobInput
import com.planruler.model.InstallationLateralDirection
import com.planruler.model.InstallationMeasurementReference
import com.planruler.model.InstallationPipeMaterial
import com.planruler.model.InstallationTaskType
import com.planruler.model.InstallationVerticalDirection
import kotlin.math.hypot

/** The installer's form as typed: text fields stay text until a calculation needs numbers. */
internal data class InstallerDraft(
    val taskType: InstallationTaskType,
    val inputMode: InstallationInputMode,
    val nominalDiameter: Int,
    val pressureClass: Int,
    val material: InstallationPipeMaterial,
    val materialName: String?,
    val startReference: InstallationMeasurementReference,
    val endReference: InstallationMeasurementReference,
    val along: String,
    val lateral: String,
    val vertical: String,
    val lateralDirection: InstallationLateralDirection,
    val verticalDirection: InstallationVerticalDirection,
    val endDirection: InstallationEndDirection,
    val angle: String,
    val minimumStraight: String,
    val weldGap: String,
    val quantity: String,
    val sawKerf: String,
    val stockLengthMm: Int,
) {
    fun toInputOrNull(): InstallationJobInput? = runCatching {
        val alongMm = along.installerNumber().also { require(it > 0.0) }
        val lateralMm = lateral.installerNumber().also { require(it >= 0.0) }
        val verticalMm = vertical.installerNumber().also { require(it >= 0.0) }
        InstallationJobInput(
            nominalDiameter = nominalDiameter,
            pressureClass = pressureClass,
            material = material,
            materialName = materialName,
            inputMode = inputMode,
            startReference = startReference,
            endReference = endReference,
            alongMm = alongMm,
            lateralOffsetMm = lateralMm,
            verticalOffsetMm = verticalMm,
            lateralDirection = lateralDirection,
            verticalDirection = verticalDirection,
            endDirection = endDirection,
            angleDeg = angle.installerNumber().also { require(it > 0.0 && it < 180.0) },
            targetOffsetMm = hypot(lateralMm, verticalMm),
            overallFaceToFaceMm = alongMm,
            minimumStraightMm = minimumStraight.installerNumber().also { require(it >= 0.0) },
            weldGapMm = weldGap.installerNumber().also { require(it >= 0.0) },
            quantity = quantity.installerWholeNumber().also { require(it > 0) },
            sawKerfMm = sawKerf.installerNumber().also { require(it >= 0.0) },
            stockLengthMm = stockLengthMm,
        )
    }.getOrNull()

    fun selectTask(task: InstallationTaskType): InstallerDraft = copy(
        taskType = task,
        nominalDiameter = if (task == InstallationTaskType.REDUCER && nominalDiameter == 15) 20 else nominalDiameter,
        lateral = when (task) {
            InstallationTaskType.STRAIGHT_INSERT, InstallationTaskType.REDUCER -> "0"
            else -> lateral.takeUnless { it == "0" } ?: "500"
        },
        vertical = when (task) {
            InstallationTaskType.ROLLING_OFFSET,
            InstallationTaskType.TURN_WITH_OFFSET,
            InstallationTaskType.OBSTACLE_BYPASS,
            InstallationTaskType.TEE_BRANCH,
            -> vertical.takeUnless { it == "0" } ?: "300"
            else -> "0"
        },
        endDirection = when (task) {
            InstallationTaskType.TURN_WITH_OFFSET -> InstallationEndDirection.RIGHT
            InstallationTaskType.U_TURN -> InstallationEndDirection.BACK
            else -> InstallationEndDirection.FORWARD
        },
        startReference = referenceFor(task, startReference),
        endReference = referenceFor(task, endReference),
    )

    /**
     * An offset ("Etage") is usually welded between existing pipe ends, while the untouched
     * default is two flange faces; the flange-to-flange spool is flanged by definition.
     * A reference the fitter picked on purpose is kept.
     */
    private fun referenceFor(task: InstallationTaskType, current: InstallationMeasurementReference) = when {
        task == InstallationTaskType.FLANGED_OFFSET -> InstallationMeasurementReference.FLANGE_FACE
        task in offsetTasks &&
            startReference == InstallationMeasurementReference.FLANGE_FACE &&
            endReference == InstallationMeasurementReference.FLANGE_FACE -> InstallationMeasurementReference.PIPE_FACE
        else -> current
    }

    companion object {
        fun from(job: InstallationJob): InstallerDraft = InstallerDraft(
            taskType = job.taskType,
            inputMode = job.input.inputMode,
            nominalDiameter = job.input.nominalDiameter,
            pressureClass = job.input.pressureClass,
            material = job.input.material,
            materialName = job.input.materialName,
            startReference = job.input.startReference,
            endReference = job.input.endReference,
            along = editableInstallerNumber(job.input.alongMm),
            lateral = editableInstallerNumber(job.input.lateralOffsetMm),
            vertical = editableInstallerNumber(job.input.verticalOffsetMm),
            lateralDirection = job.input.lateralDirection,
            verticalDirection = job.input.verticalDirection,
            endDirection = job.input.endDirection,
            angle = editableInstallerNumber(job.input.angleDeg),
            minimumStraight = editableInstallerNumber(job.input.minimumStraightMm),
            weldGap = editableInstallerNumber(job.input.weldGapMm),
            quantity = job.input.quantity.toString(),
            sawKerf = editableInstallerNumber(job.input.sawKerfMm),
            stockLengthMm = job.input.stockLengthMm,
        )
    }
}

private val offsetTasks = setOf(InstallationTaskType.FLAT_OFFSET, InstallationTaskType.ROLLING_OFFSET)

internal fun InstallationTaskType.usesAutoRoute() = this !in setOf(
    InstallationTaskType.OBSTACLE_BYPASS,
    InstallationTaskType.TEE_BRANCH,
    InstallationTaskType.REDUCER,
    InstallationTaskType.MANUAL,
)

/** Tasks that read the side offset, the rise and the way the far end faces. */
internal fun InstallationTaskType.usesLateral() =
    this != InstallationTaskType.STRAIGHT_INSERT && this != InstallationTaskType.REDUCER

internal fun InstallationTaskType.usesVertical() = usesLateral() && this != InstallationTaskType.FLAT_OFFSET

internal fun InstallationTaskType.usesEndDirection() = this !in setOf(
    InstallationTaskType.STRAIGHT_INSERT,
    InstallationTaskType.FLAT_OFFSET,
    InstallationTaskType.ROLLING_OFFSET,
    InstallationTaskType.FLANGED_OFFSET,
    InstallationTaskType.REDUCER,
)

internal fun String.installerNumber(): Double = trim().replace(',', '.').toDouble()

internal fun String.installerWholeNumber(): Int {
    val value = installerNumber()
    require(value % 1.0 == 0.0)
    return value.toInt()
}

internal fun editableInstallerNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
