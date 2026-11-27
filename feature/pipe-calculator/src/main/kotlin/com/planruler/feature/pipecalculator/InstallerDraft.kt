package com.planruler.feature.pipecalculator

import com.planruler.model.InstallationEndDirection
import com.planruler.model.InstallationInputMode
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobInput
import com.planruler.model.InstallationLateralDirection
import com.planruler.model.InstallationMeasurementReference
import com.planruler.model.InstallationOffsetPlacement
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
    val offsetPlacement: InstallationOffsetPlacement = InstallationOffsetPlacement.CENTER,
    val offsetLead: String = "300",
    /** A flat offset climbs or drops instead of stepping sideways; its size then sits in [vertical]. */
    val flatVertical: Boolean = false,
) {
    fun toInputOrNull(): InstallationJobInput? = runCatching {
        val alongMm = along.installerNumber().also { require(it > 0.0) }
        val lateralMm = lateral.installerNumber().also { require(it >= 0.0) }
        val verticalMm = vertical.installerNumber().also { require(it >= 0.0) }
        val leadMm = runCatching { offsetLead.installerNumber() }.getOrNull()?.takeIf { it > 0.0 }
        require(offsetPlacement != InstallationOffsetPlacement.EXACT || leadMm != null)
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
            offsetPlacement = offsetPlacement,
            offsetLeadMm = leadMm ?: 300.0,
        )
    }.getOrNull()

    /** The one size a flat offset asks for, wherever it points. */
    val flatOffset: String get() = if (flatVertical) vertical else lateral

    fun withFlatOffset(value: String): InstallerDraft =
        if (flatVertical) copy(vertical = value, lateral = "0") else copy(lateral = value, vertical = "0")

    /** Points a flat offset sideways or up/down, carrying the typed size over to the new plane. */
    fun withFlatDirection(direction: FlatOffsetDirection): InstallerDraft {
        val size = flatOffset
        return when (direction) {
            FlatOffsetDirection.LEFT, FlatOffsetDirection.RIGHT -> copy(
                flatVertical = false,
                lateral = size,
                vertical = "0",
                lateralDirection = if (direction == FlatOffsetDirection.LEFT) {
                    InstallationLateralDirection.LEFT
                } else {
                    InstallationLateralDirection.RIGHT
                },
            )
            FlatOffsetDirection.UP, FlatOffsetDirection.DOWN -> copy(
                flatVertical = true,
                vertical = size,
                lateral = "0",
                verticalDirection = if (direction == FlatOffsetDirection.UP) {
                    InstallationVerticalDirection.UP
                } else {
                    InstallationVerticalDirection.DOWN
                },
            )
        }
    }

    val flatDirection: FlatOffsetDirection get() = when {
        flatVertical && verticalDirection == InstallationVerticalDirection.UP -> FlatOffsetDirection.UP
        flatVertical -> FlatOffsetDirection.DOWN
        lateralDirection == InstallationLateralDirection.LEFT -> FlatOffsetDirection.LEFT
        else -> FlatOffsetDirection.RIGHT
    }

    fun selectTask(task: InstallationTaskType): InstallerDraft {
        if (task == taskType) return this
        val switched = switchTo(task)
        // A pure rise or drop stays one: the flat offset then climbs instead of stepping sideways.
        val onlyRises = lateral.isZeroSize() && !vertical.isZeroSize()
        return if (task == InstallationTaskType.FLAT_OFFSET && onlyRises) {
            switched.copy(flatVertical = true, lateral = "0", vertical = vertical)
        } else {
            switched
        }
    }

    private fun switchTo(task: InstallationTaskType): InstallerDraft = copy(
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
        flatVertical = false,
    )

    /**
     * An offset ("Etage") is usually welded between existing pipe ends, while the untouched
     * default is two flange faces; the flange-to-flange spool is flanged by definition.
     * A reference the fitter picked on purpose is kept.
     */
    private fun referenceFor(task: InstallationTaskType, current: InstallationMeasurementReference) = when {
        task == InstallationTaskType.FLANGED_OFFSET -> InstallationMeasurementReference.FLANGE_FACE
        task in siteWeldedOffsetTasks &&
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
            offsetPlacement = job.input.offsetPlacement,
            offsetLead = editableInstallerNumber(job.input.offsetLeadMm),
            flatVertical = job.taskType == InstallationTaskType.FLAT_OFFSET &&
                job.input.lateralOffsetMm == 0.0 && job.input.verticalOffsetMm > 0.0,
        )
    }
}

/** The four ways a flat offset can step: sideways in plan, or up and down in elevation. */
internal enum class FlatOffsetDirection { LEFT, RIGHT, UP, DOWN }

/** Offsets that are usually welded in between existing pipe ends on site. */
private val siteWeldedOffsetTasks = setOf(InstallationTaskType.FLAT_OFFSET, InstallationTaskType.ROLLING_OFFSET)

/** The same two-elbow offset as a prefabricated spool with a flange at each end. */
private val offsetTasks = siteWeldedOffsetTasks + InstallationTaskType.FLANGED_OFFSET

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

/** Two-elbow offsets between parallel ends: these can sit anywhere along the measured run. */
internal fun InstallationTaskType.isOffset() = this in offsetTasks

internal fun InstallationTaskType.usesEndDirection() = this !in setOf(
    InstallationTaskType.STRAIGHT_INSERT,
    InstallationTaskType.FLAT_OFFSET,
    InstallationTaskType.ROLLING_OFFSET,
    InstallationTaskType.FLANGED_OFFSET,
    InstallationTaskType.REDUCER,
)

internal fun String.installerNumber(): Double = trim().replace(',', '.').toDouble()

private fun String.isZeroSize(): Boolean = runCatching { installerNumber() == 0.0 }.getOrDefault(true)

internal fun String.installerWholeNumber(): Int {
    val value = installerNumber()
    require(value % 1.0 == 0.0)
    return value.toInt()
}

internal fun editableInstallerNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
