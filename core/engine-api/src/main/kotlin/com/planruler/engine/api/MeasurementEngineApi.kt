package com.planruler.engine.api

import com.planruler.model.*
import kotlinx.coroutines.flow.StateFlow

data class EngineState(
    val calibration: Calibration? = null,
    val displayUnit: LengthUnit = LengthUnit.METER,
    val measurements: List<Measurement> = emptyList(),
    val draft: Measurement? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val decimals: Int = 2,
    val showUnits: Boolean = true,
)

sealed interface MeasurementError {
    data object NotCalibrated : MeasurementError
    data class InvalidGeometry(val reason: String) : MeasurementError
    data object NoActiveDraft : MeasurementError
    data object NothingToUndo : MeasurementError
    data class NotFound(val id: MeasurementId) : MeasurementError
}

data class MeasurementPropertiesUpdate(
    val label: String?,
    val takeoff: TakeoffProperties,
    val style: MeasurementStyle,
    val displayUnit: LengthUnit?,
    val showLabel: Boolean,
    val layerId: LayerId = LayerId("default"),
)

sealed interface EngineResult<out T> {
    data class Ok<T>(val value: T) : EngineResult<T>
    data class Error(val error: MeasurementError) : EngineResult<Nothing>
}

interface MeasurementEngineApi {
    val state: StateFlow<EngineState>
    fun restore(calibration: Calibration?, unit: LengthUnit, measurements: List<Measurement>)
    fun calibrateByReference(
        start: DocPoint,
        end: DocPoint,
        length: Double,
        unit: LengthUnit,
        audit: CalibrationAudit? = null,
    ): EngineResult<Calibration>
    fun calibratePdfRatio(ratio: Double, audit: CalibrationAudit? = null): EngineResult<Calibration>
    fun verifyCalibration(
        start: DocPoint,
        end: DocPoint,
        expectedLength: Double,
        unit: LengthUnit,
        pageIndex: Int = 0,
    ): EngineResult<CalibrationVerification>
    fun beginMeasurement(
        type: MeasurementType,
        first: DocPoint,
        label: String? = null,
        pageIndex: Int = 0,
        style: MeasurementStyle = MeasurementStyle(),
        layerId: LayerId = LayerId("default"),
        takeoff: TakeoffProperties = TakeoffProperties(),
        displayUnit: LengthUnit? = null,
        templateId: String? = null,
        revisionId: String? = null,
    ): EngineResult<Unit>
    fun addPoint(point: DocPoint): EngineResult<Unit>
    fun updateLastPoint(point: DocPoint): EngineResult<Unit>
    /** Drops the last drafted point; the draft itself survives so "back" is not "cancel". */
    fun removeLastPoint(): EngineResult<Unit>
    fun commitMeasurement(): EngineResult<Measurement>
    fun cancelMeasurement()
    fun updateVertex(id: MeasurementId, index: Int, point: DocPoint): EngineResult<Unit>
    fun moveMeasurement(id: MeasurementId, delta: DocPoint): EngineResult<Unit>
    fun insertVertex(id: MeasurementId, afterIndex: Int, point: DocPoint): EngineResult<Unit>
    fun removeVertex(id: MeasurementId, index: Int): EngineResult<Unit>
    fun duplicateMeasurement(id: MeasurementId, offset: DocPoint = DocPoint(12.0, 12.0)): EngineResult<Measurement>
    fun setExactLength(
        id: MeasurementId,
        length: Double,
        unit: LengthUnit,
        constraint: DistanceConstraint = DistanceConstraint.FREE,
    ): EngineResult<Unit>
    fun applyTemplateToMeasurements(template: TakeoffTemplate): EngineResult<Int>
    fun updateProperties(id: MeasurementId, update: MeasurementPropertiesUpdate): EngineResult<Unit>
    fun setReviewStatus(
        id: MeasurementId,
        status: MeasurementReviewStatus,
        reviewedAtEpochMs: Long? = null,
    ): EngineResult<Unit>
    fun beginEdit(id: MeasurementId): EngineResult<Unit>
    fun previewVertex(index: Int, point: DocPoint): EngineResult<Unit>
    fun previewMove(deltaFromStart: DocPoint): EngineResult<Unit>
    fun commitEdit(): EngineResult<Unit>
    fun cancelEdit()
    fun updateAnnotation(id: MeasurementId, text: String): EngineResult<Unit>
    fun deleteMeasurement(id: MeasurementId): EngineResult<Unit>
    fun setDisplayUnit(unit: LengthUnit)
    fun setDisplayFormat(decimals: Int, showUnits: Boolean)
    fun undo(): EngineResult<Unit>
    fun redo(): EngineResult<Unit>
    fun evaluate(measurement: Measurement): EngineResult<MeasureValue>
    fun format(value: MeasureValue, unit: LengthUnit? = null): String
}

enum class SnapType { NONE, VERTEX, HORIZONTAL, VERTICAL, SEGMENT }

data class SnapContext(
    val measurements: List<Measurement>,
    val anchor: DocPoint? = null,
    val sensitivityDocumentUnits: Double,
    val enabled: Boolean = true,
    val excludedMeasurementId: MeasurementId? = null,
    /** Restricts which snap kinds may win, so the user can ask for vertices only. */
    val allowed: Set<SnapType> = setOf(
        SnapType.VERTEX,
        SnapType.HORIZONTAL,
        SnapType.VERTICAL,
        SnapType.SEGMENT,
    ),
)

data class SnapTarget(
    val measurementId: MeasurementId,
    val vertexIndex: Int? = null,
    val segmentIndex: Int? = null,
)

data class SnapResult(
    val point: DocPoint,
    val type: SnapType,
    val target: SnapTarget? = null,
    val distance: Double = 0.0,
    val guideStart: DocPoint? = null,
    val guideEnd: DocPoint? = null,
)

interface SnapEngine {
    fun resolve(candidate: DocPoint, context: SnapContext): SnapResult
}

object SnapSensitivity {
    fun documentUnits(sensitivityDp: Double, density: Double, zoom: Double): Double {
        require(sensitivityDp >= 0.0 && density > 0.0 && zoom > 0.0)
        return sensitivityDp * density / zoom
    }
}
