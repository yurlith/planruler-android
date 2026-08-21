package com.planruler.engine.default

import com.planruler.engine.api.*
import com.planruler.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToLong

class DefaultMeasurementEngine(
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val historyLimit: Int = 100,
) : MeasurementEngineApi {
    private val mutableState = MutableStateFlow(EngineState())
    override val state = mutableState.asStateFlow()
    private val undo = ArrayDeque<EngineState>()
    private val redo = ArrayDeque<EngineState>()
    private var activeEdit: EditSession? = null

    override fun restore(calibration: Calibration?, unit: LengthUnit, measurements: List<Measurement>) {
        undo.clear(); redo.clear(); activeEdit = null
        mutableState.value = EngineState(
            calibration = calibration,
            displayUnit = unit,
            measurements = measurements,
            decimals = state.value.decimals,
            showUnits = state.value.showUnits,
        )
    }

    override fun calibrateByReference(
        start: DocPoint,
        end: DocPoint,
        length: Double,
        unit: LengthUnit,
        audit: CalibrationAudit?,
    ) =
        runCatching { Calibration.reference(start.distanceTo(end), length, unit, audit) }
            .fold({ calibration -> mutate { it.copy(calibration = calibration, draft = null) }; EngineResult.Ok(calibration) },
                { EngineResult.Error(MeasurementError.InvalidGeometry(it.message ?: "Invalid calibration")) })

    override fun calibratePdfRatio(ratio: Double, audit: CalibrationAudit?) =
        runCatching { Calibration.pdfRatio(ratio, audit) }
            .fold({ calibration -> mutate { it.copy(calibration = calibration) }; EngineResult.Ok(calibration) },
                { EngineResult.Error(MeasurementError.InvalidGeometry("Scale ratio must be positive")) })

    override fun verifyCalibration(
        start: DocPoint,
        end: DocPoint,
        expectedLength: Double,
        unit: LengthUnit,
        pageIndex: Int,
    ): EngineResult<CalibrationVerification> {
        val calibration = state.value.calibration ?: return EngineResult.Error(MeasurementError.NotCalibrated)
        val documentLength = start.distanceTo(end)
        if (!documentLength.isFinite() || documentLength <= 0.0) {
            return EngineResult.Error(MeasurementError.InvalidGeometry("Reference segment must not be zero"))
        }
        if (!expectedLength.isFinite() || expectedLength <= 0.0) {
            return EngineResult.Error(MeasurementError.InvalidGeometry("Known length must be positive"))
        }
        val verification = CalibrationVerification(
            verifiedAtEpochMs = clock(),
            pageIndex = pageIndex,
            documentLength = documentLength,
            expectedLength = expectedLength,
            measuredLength = unit.fromMeters(documentLength * calibration.metersPerDocumentUnit),
            unit = unit,
        )
        val audit = calibration.audit ?: CalibrationAudit(
            calibratedAtEpochMs = verification.verifiedAtEpochMs,
            calibratedBy = "Local user",
            pageIndex = pageIndex,
        )
        mutate { it.copy(calibration = calibration.copy(audit = audit.copy(verification = verification))) }
        return EngineResult.Ok(verification)
    }

    override fun beginMeasurement(
        type: MeasurementType,
        first: DocPoint,
        label: String?,
        pageIndex: Int,
        style: MeasurementStyle,
        layerId: LayerId,
        takeoff: TakeoffProperties,
        displayUnit: LengthUnit?,
        templateId: String?,
        revisionId: String?,
    ): EngineResult<Unit> {
        if (type !in setOf(MeasurementType.ANNOTATION, MeasurementType.COUNTER) && state.value.calibration == null)
            return EngineResult.Error(MeasurementError.NotCalibrated)
        mutableState.value = state.value.copy(
            draft = Measurement(
                MeasurementId(idGenerator()),
                type,
                listOf(first),
                style = style.copy(strokeWidth = style.strokeWidth.coerceIn(0.5f, 24f)),
                label = label,
                layerId = layerId,
                takeoff = takeoff,
                displayUnit = displayUnit,
                templateId = templateId,
                pageIndex = pageIndex,
                createdAtEpochMs = clock(),
                revisionId = revisionId,
            ),
        )
        return EngineResult.Ok(Unit)
    }

    override fun addPoint(point: DocPoint): EngineResult<Unit> {
        val draft = state.value.draft ?: return EngineResult.Error(MeasurementError.NoActiveDraft)
        mutableState.value = state.value.copy(draft = draft.copy(points = draft.points + point))
        return EngineResult.Ok(Unit)
    }

    override fun updateLastPoint(point: DocPoint): EngineResult<Unit> {
        val draft = state.value.draft ?: return EngineResult.Error(MeasurementError.NoActiveDraft)
        val points = if (draft.points.size == 1) draft.points + point else draft.points.dropLast(1) + point
        mutableState.value = state.value.copy(draft = draft.copy(points = points))
        return EngineResult.Ok(Unit)
    }

    override fun removeLastPoint(): EngineResult<Unit> {
        val draft = state.value.draft ?: return EngineResult.Error(MeasurementError.NoActiveDraft)
        if (draft.points.size <= 1) return EngineResult.Error(MeasurementError.InvalidGeometry("The draft has a single point"))
        mutableState.value = state.value.copy(draft = draft.copy(points = draft.points.dropLast(1)))
        return EngineResult.Ok(Unit)
    }

    override fun commitMeasurement(): EngineResult<Measurement> {
        val draft = state.value.draft ?: return EngineResult.Error(MeasurementError.NoActiveDraft)
        val required = when (draft.type) {
            MeasurementType.DISTANCE -> 2
            MeasurementType.POLYLINE -> 2
            MeasurementType.AREA -> 3
            MeasurementType.ANGLE -> 3
            MeasurementType.ANNOTATION, MeasurementType.COUNTER -> 1
        }
        if (draft.points.size < required)
            return EngineResult.Error(MeasurementError.InvalidGeometry("At least $required points required"))
        if (draft.type == MeasurementType.AREA && Geometry.selfIntersects(draft.points))
            return EngineResult.Error(MeasurementError.InvalidGeometry("Polygon is self-intersecting"))
        mutate { it.copy(measurements = it.measurements + draft, draft = null) }
        return EngineResult.Ok(draft)
    }

    override fun cancelMeasurement() { mutableState.value = state.value.copy(draft = null) }

    override fun updateVertex(id: MeasurementId, index: Int, point: DocPoint): EngineResult<Unit> {
        val measurement = state.value.measurements.find { it.id == id }
            ?: return EngineResult.Error(MeasurementError.NotFound(id))
        if (index !in measurement.points.indices) return EngineResult.Error(MeasurementError.InvalidGeometry("Vertex out of range"))
        val updated = measurement.copy(points = measurement.points.toMutableList().also { it[index] = point })
        if (updated.type == MeasurementType.AREA && Geometry.selfIntersects(updated.points))
            return EngineResult.Error(MeasurementError.InvalidGeometry("Polygon is self-intersecting"))
        mutate { s -> s.copy(measurements = s.measurements.map { if (it.id == id) updated else it }) }
        return EngineResult.Ok(Unit)
    }

    override fun moveMeasurement(id: MeasurementId, delta: DocPoint): EngineResult<Unit> {
        val measurement = measurement(id) ?: return EngineResult.Error(MeasurementError.NotFound(id))
        val updated = measurement.copy(points = measurement.points.map { DocPoint(it.x + delta.x, it.y + delta.y) })
        replace(updated)
        return EngineResult.Ok(Unit)
    }

    override fun insertVertex(id: MeasurementId, afterIndex: Int, point: DocPoint): EngineResult<Unit> {
        val measurement = measurement(id) ?: return EngineResult.Error(MeasurementError.NotFound(id))
        if (measurement.type !in setOf(MeasurementType.POLYLINE, MeasurementType.AREA) ||
            afterIndex !in measurement.points.indices
        ) {
            return EngineResult.Error(MeasurementError.InvalidGeometry("A vertex can only be inserted into a polyline or area"))
        }
        val points = measurement.points.toMutableList().apply { add(afterIndex + 1, point) }
        val updated = measurement.copy(points = points)
        if (updated.type == MeasurementType.AREA && Geometry.selfIntersects(points)) {
            return EngineResult.Error(MeasurementError.InvalidGeometry("Polygon is self-intersecting"))
        }
        replace(updated)
        return EngineResult.Ok(Unit)
    }

    override fun removeVertex(id: MeasurementId, index: Int): EngineResult<Unit> {
        val measurement = measurement(id) ?: return EngineResult.Error(MeasurementError.NotFound(id))
        if (index !in measurement.points.indices) {
            return EngineResult.Error(MeasurementError.InvalidGeometry("Vertex out of range"))
        }
        val minimum = when (measurement.type) {
            MeasurementType.AREA, MeasurementType.ANGLE -> 3
            MeasurementType.DISTANCE, MeasurementType.POLYLINE -> 2
            MeasurementType.ANNOTATION, MeasurementType.COUNTER -> 1
        }
        if (measurement.points.size <= minimum) {
            return EngineResult.Error(MeasurementError.InvalidGeometry("The measurement requires at least $minimum points"))
        }
        replace(measurement.copy(points = measurement.points.filterIndexed { pointIndex, _ -> pointIndex != index }))
        return EngineResult.Ok(Unit)
    }

    override fun duplicateMeasurement(id: MeasurementId, offset: DocPoint): EngineResult<Measurement> {
        val measurement = measurement(id) ?: return EngineResult.Error(MeasurementError.NotFound(id))
        val existing = state.value.measurements.mapTo(mutableSetOf()) { it.id }
        var duplicateId = MeasurementId(idGenerator())
        var attempts = 0
        while (duplicateId in existing && attempts++ < 100) duplicateId = MeasurementId(idGenerator())
        if (duplicateId in existing) {
            return EngineResult.Error(MeasurementError.InvalidGeometry("Could not allocate a unique measurement ID"))
        }
        val duplicate = measurement.copy(
            id = duplicateId,
            points = measurement.points.map { DocPoint(it.x + offset.x, it.y + offset.y) },
            createdAtEpochMs = clock(),
        )
        mutate { it.copy(measurements = it.measurements + duplicate) }
        return EngineResult.Ok(duplicate)
    }

    override fun setExactLength(
        id: MeasurementId,
        length: Double,
        unit: LengthUnit,
        constraint: DistanceConstraint,
    ): EngineResult<Unit> {
        val measurement = measurement(id) ?: return EngineResult.Error(MeasurementError.NotFound(id))
        if (measurement.type != MeasurementType.DISTANCE || measurement.points.size != 2) {
            return EngineResult.Error(MeasurementError.InvalidGeometry("Exact length requires a two-point distance"))
        }
        if (!length.isFinite() || length <= 0.0) {
            return EngineResult.Error(MeasurementError.InvalidGeometry("Known length must be positive"))
        }
        val calibration = state.value.calibration ?: return EngineResult.Error(MeasurementError.NotCalibrated)
        val documentLength = unit.toMeters(length) / calibration.metersPerDocumentUnit
        val start = measurement.points[0]
        val currentEnd = measurement.points[1]
        val dx = currentEnd.x - start.x
        val dy = currentEnd.y - start.y
        val currentLength = kotlin.math.hypot(dx, dy)
        val end = when (constraint) {
            DistanceConstraint.HORIZONTAL -> DocPoint(
                start.x + documentLength * if (dx < 0.0) -1.0 else 1.0,
                start.y,
            )
            DistanceConstraint.VERTICAL -> DocPoint(
                start.x,
                start.y + documentLength * if (dy < 0.0) -1.0 else 1.0,
            )
            DistanceConstraint.FREE -> {
                if (currentLength <= 0.0) {
                    return EngineResult.Error(MeasurementError.InvalidGeometry("Distance points must not overlap"))
                }
                DocPoint(
                    start.x + dx / currentLength * documentLength,
                    start.y + dy / currentLength * documentLength,
                )
            }
        }
        replace(measurement.copy(points = listOf(start, end)))
        return EngineResult.Ok(Unit)
    }

    override fun applyTemplateToMeasurements(template: TakeoffTemplate): EngineResult<Int> {
        val count = state.value.measurements.count { it.templateId == template.id }
        if (count == 0) return EngineResult.Ok(0)
        mutate { snapshot ->
            snapshot.copy(
                measurements = snapshot.measurements.map { measurement ->
                    if (measurement.templateId == template.id) {
                        measurement.copy(
                            label = template.name,
                            style = template.style.copy(strokeWidth = template.style.strokeWidth.coerceIn(0.5f, 24f)),
                            layerId = template.layerId,
                            takeoff = template.takeoff,
                            displayUnit = template.displayUnit,
                        )
                    } else {
                        measurement
                    }
                },
            )
        }
        return EngineResult.Ok(count)
    }

    override fun updateProperties(id: MeasurementId, update: MeasurementPropertiesUpdate): EngineResult<Unit> {
        val measurement = measurement(id) ?: return EngineResult.Error(MeasurementError.NotFound(id))
        val normalizedLabel = update.label?.trim()?.takeIf(String::isNotEmpty)
        replace(
            measurement.copy(
                label = normalizedLabel,
                takeoff = update.takeoff,
                style = update.style.copy(strokeWidth = update.style.strokeWidth.coerceIn(0.5f, 24f)),
                displayUnit = update.displayUnit,
                showLabel = update.showLabel,
                layerId = update.layerId,
            ),
        )
        return EngineResult.Ok(Unit)
    }

    override fun setReviewStatus(
        id: MeasurementId,
        status: MeasurementReviewStatus,
        reviewedAtEpochMs: Long?,
    ): EngineResult<Unit> {
        val measurement = measurement(id) ?: return EngineResult.Error(MeasurementError.NotFound(id))
        replace(
            measurement.copy(
                reviewStatus = status,
                reviewedAtEpochMs = if (status == MeasurementReviewStatus.VERIFIED) {
                    reviewedAtEpochMs ?: clock()
                } else {
                    null
                },
            ),
        )
        return EngineResult.Ok(Unit)
    }

    override fun beginEdit(id: MeasurementId): EngineResult<Unit> {
        if (activeEdit != null) cancelEdit()
        if (measurement(id) == null) return EngineResult.Error(MeasurementError.NotFound(id))
        activeEdit = EditSession(state.value.copy(draft = null), id)
        return EngineResult.Ok(Unit)
    }

    override fun previewVertex(index: Int, point: DocPoint): EngineResult<Unit> {
        val session = activeEdit ?: return EngineResult.Error(MeasurementError.InvalidGeometry("No edit is active"))
        val current = measurement(session.id) ?: return EngineResult.Error(MeasurementError.NotFound(session.id))
        if (index !in current.points.indices) {
            return EngineResult.Error(MeasurementError.InvalidGeometry("Vertex out of range"))
        }
        val points = current.points.toMutableList().also { it[index] = point }
        if (current.type == MeasurementType.AREA && Geometry.selfIntersects(points)) {
            return EngineResult.Error(MeasurementError.InvalidGeometry("Polygon is self-intersecting"))
        }
        mutableState.value = state.value.copy(
            measurements = state.value.measurements.map { if (it.id == current.id) current.copy(points = points) else it },
        )
        return EngineResult.Ok(Unit)
    }

    override fun previewMove(deltaFromStart: DocPoint): EngineResult<Unit> {
        val session = activeEdit ?: return EngineResult.Error(MeasurementError.InvalidGeometry("No edit is active"))
        val original = session.original.measurements.first { it.id == session.id }
        val moved = original.copy(points = original.points.map {
            DocPoint(it.x + deltaFromStart.x, it.y + deltaFromStart.y)
        })
        mutableState.value = state.value.copy(
            measurements = state.value.measurements.map { if (it.id == session.id) moved else it },
        )
        return EngineResult.Ok(Unit)
    }

    override fun commitEdit(): EngineResult<Unit> {
        val session = activeEdit ?: return EngineResult.Error(MeasurementError.InvalidGeometry("No edit is active"))
        activeEdit = null
        if (state.value.measurements == session.original.measurements) return EngineResult.Ok(Unit)
        undo.addLast(session.original)
        while (undo.size > historyLimit) undo.removeFirst()
        redo.clear()
        mutableState.value = state.value.copy(canUndo = true, canRedo = false)
        return EngineResult.Ok(Unit)
    }

    override fun cancelEdit() {
        activeEdit?.let { mutableState.value = it.original }
        activeEdit = null
    }

    override fun updateAnnotation(id: MeasurementId, text: String): EngineResult<Unit> {
        if (state.value.measurements.none { it.id == id }) return EngineResult.Error(MeasurementError.NotFound(id))
        val normalized = text.trim().take(2_000)
        if (normalized.isEmpty()) return EngineResult.Error(MeasurementError.InvalidGeometry("Annotation text cannot be empty"))
        mutate { s -> s.copy(measurements = s.measurements.map { if (it.id == id) it.copy(label = normalized) else it }) }
        return EngineResult.Ok(Unit)
    }

    override fun deleteMeasurement(id: MeasurementId): EngineResult<Unit> {
        if (state.value.measurements.none { it.id == id }) return EngineResult.Error(MeasurementError.NotFound(id))
        mutate { it.copy(measurements = it.measurements.filterNot { measurement -> measurement.id == id }) }
        return EngineResult.Ok(Unit)
    }

    override fun setDisplayUnit(unit: LengthUnit) = mutate { it.copy(displayUnit = unit) }

    /** Display formatting is a view concern: it must not create an undo record. */
    override fun setDisplayFormat(decimals: Int, showUnits: Boolean) {
        mutableState.value = state.value.copy(decimals = decimals.coerceIn(0, 4), showUnits = showUnits)
    }

    override fun undo(): EngineResult<Unit> {
        val previous = undo.removeLastOrNull() ?: return EngineResult.Error(MeasurementError.NothingToUndo)
        redo.addLast(state.value.copy(draft = null))
        mutableState.value = previous.copy(
            draft = null,
            canUndo = undo.isNotEmpty(),
            canRedo = true,
            decimals = state.value.decimals,
            showUnits = state.value.showUnits,
        )
        return EngineResult.Ok(Unit)
    }
    override fun redo(): EngineResult<Unit> {
        val next = redo.removeLastOrNull() ?: return EngineResult.Error(MeasurementError.NothingToUndo)
        undo.addLast(state.value.copy(draft = null))
        mutableState.value = next.copy(
            draft = null,
            canUndo = true,
            canRedo = redo.isNotEmpty(),
            decimals = state.value.decimals,
            showUnits = state.value.showUnits,
        )
        return EngineResult.Ok(Unit)
    }

    override fun evaluate(measurement: Measurement): EngineResult<MeasureValue> {
        val calibration = state.value.calibration
        if (measurement.type !in setOf(MeasurementType.ANNOTATION, MeasurementType.COUNTER) && calibration == null)
            return EngineResult.Error(MeasurementError.NotCalibrated)
        val factor = calibration?.metersPerDocumentUnit ?: 1.0
        val value = try {
            when (measurement.type) {
                MeasurementType.DISTANCE, MeasurementType.POLYLINE -> {
                    val segments = measurement.points.zipWithNext().map { (a, b) -> a.distanceTo(b) * factor }
                    MeasureValue.Length(segments.sum(), segments)
                }
                MeasurementType.AREA -> MeasureValue.Area(
                    Geometry.polygonArea(measurement.points) * factor * factor,
                    Geometry.polygonPerimeter(measurement.points) * factor,
                )
                MeasurementType.ANGLE -> MeasureValue.Angle(Geometry.angleDegrees(measurement.points[0], measurement.points[1], measurement.points[2]))
                MeasurementType.COUNTER -> MeasureValue.Count(1)
                MeasurementType.ANNOTATION -> MeasureValue.None
            }
        } catch (_: RuntimeException) {
            return EngineResult.Error(MeasurementError.InvalidGeometry("Invalid measurement geometry"))
        }
        return EngineResult.Ok(value)
    }

    override fun format(value: MeasureValue, unit: LengthUnit?): String {
        val snapshot = state.value
        val resolvedUnit = unit ?: snapshot.displayUnit
        val factor = 10.0.pow(snapshot.decimals)
        fun clean(raw: Double): String {
            val rounded = round(raw * factor) / factor
            return if (snapshot.decimals == 0) {
                rounded.roundToLong().toString()
            } else {
                String.format(Locale.US, "%.${snapshot.decimals}f", rounded)
            }
        }
        fun suffix(symbol: String) = if (snapshot.showUnits) " $symbol" else ""
        return when (value) {
            is MeasureValue.Length -> clean(resolvedUnit.fromMeters(value.meters)) + suffix(resolvedUnit.symbol)
            is MeasureValue.Area ->
                clean(value.squareMeters / (resolvedUnit.metersPerUnit * resolvedUnit.metersPerUnit)) +
                    suffix("${resolvedUnit.symbol}²")
            is MeasureValue.Angle -> clean(value.degrees) + "°"
            is MeasureValue.Count -> value.count.toString()
            MeasureValue.None -> ""
        }
    }

    private fun mutate(block: (EngineState) -> EngineState) {
        activeEdit = null
        undo.addLast(state.value.copy(draft = null))
        while (undo.size > historyLimit) undo.removeFirst()
        redo.clear()
        mutableState.value = block(state.value).copy(canUndo = true, canRedo = false)
    }

    private fun measurement(id: MeasurementId) = state.value.measurements.find { it.id == id }
    private fun replace(measurement: Measurement) =
        mutate { state -> state.copy(measurements = state.measurements.map { if (it.id == measurement.id) measurement else it }) }
    private data class EditSession(val original: EngineState, val id: MeasurementId)
}
