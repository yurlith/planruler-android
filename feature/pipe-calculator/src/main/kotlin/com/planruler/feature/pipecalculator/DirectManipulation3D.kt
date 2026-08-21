package com.planruler.feature.pipecalculator

import androidx.compose.ui.geometry.Offset
import com.planruler.fabrication3d.AssemblyMesh3D
import com.planruler.fabrication3d.ChainCommand3D
import com.planruler.fabrication3d.ChainEditorState3D
import com.planruler.fabrication3d.ChainPath3D
import com.planruler.fabrication3d.ChainStart3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.ElbowGeometry3D
import com.planruler.fabrication3d.ReducerGeometry3D
import com.planruler.fabrication3d.StraightPipeGeometry3D
import com.planruler.fabrication3d.Vec3
import java.util.Locale
import kotlin.math.abs

internal enum class DirectHandleKind3D { LENGTH, ANGLE, ROLL, START_X, START_Y, START_Z }

/** Immutable gesture baseline. It deliberately survives every preview frame of one drag. */
internal data class DirectHandleSpec3D(
    val path: ChainPath3D,
    val partId: String,
    val kind: DirectHandleKind3D,
    val anchorWorld: Vec3,
    val dragAxisWorld: Vec3,
    val baselineStep: ChainStep3D? = null,
    val baselineStart: ChainStart3D? = null,
    val radiusMm: Double = 1.0,
    val minimumLengthMm: Double,
    val minimumAngleDeg: Double,
    val maximumAngleDeg: Double,
    val allowedAnglesDeg: List<Double>,
) {
    val valueLabel: String
        get() = when (kind) {
            DirectHandleKind3D.START_X -> "X ${directNumber(requireNotNull(baselineStart).position.x)} mm"
            DirectHandleKind3D.START_Y -> "Y ${directNumber(requireNotNull(baselineStart).position.y)} mm"
            DirectHandleKind3D.START_Z -> "Z ${directNumber(requireNotNull(baselineStart).position.z)} mm"
            else -> when (val step = baselineStep) {
            is ChainStep3D.Pipe -> "L ${directNumber(step.lengthMm)} mm"
            is ChainStep3D.Reducer -> "L ${directNumber(step.lengthMm)} mm"
            is ChainStep3D.Elbow -> when (kind) {
                DirectHandleKind3D.ANGLE -> "∠ ${directNumber(abs(step.angleDeg))}°"
                DirectHandleKind3D.ROLL -> "↻ ${directNumber(step.rollDeg)}°"
                else -> ""
            }
            else -> ""
            }
        }
}

internal data class DirectEdit3D(
    val command: ChainCommand3D,
    val valueLabel: String,
)

internal fun directHandleSpecs(
    editor: ChainEditorState3D,
    selectedPartId: String?,
): List<DirectHandleSpec3D> {
    val partId = selectedPartId ?: return emptyList()
    val part = editor.assembly.partOrNull(partId) ?: return emptyList()
    val path = editor.pathForPart(partId)
    if (path == null) {
        if (part != editor.assembly.parts.firstOrNull()) return emptyList()
        val start = editor.plan.start
        val offset = (part.definition.ports.maxOfOrNull { it.outsideDiameterMm } ?: 50.0).coerceAtLeast(70.0)
        fun startSpec(kind: DirectHandleKind3D, axis: Vec3) = DirectHandleSpec3D(
            path = ChainPath3D.ROOT,
            partId = partId,
            kind = kind,
            anchorWorld = start.position + axis * offset,
            dragAxisWorld = axis,
            baselineStart = start,
            minimumLengthMm = editor.profile.rules.minPipeLengthMm,
            minimumAngleDeg = editor.profile.rules.minElbowAngleDeg,
            maximumAngleDeg = editor.profile.rules.maxElbowAngleDeg,
            allowedAnglesDeg = editor.profile.rules.allowedElbowAnglesDeg,
        )
        return listOf(
            startSpec(DirectHandleKind3D.START_X, Vec3.UNIT_X),
            startSpec(DirectHandleKind3D.START_Y, Vec3.UNIT_Y),
            startSpec(DirectHandleKind3D.START_Z, Vec3.UNIT_Z),
        )
    }
    val step = editor.plan.stepAt(path) ?: return emptyList()
    val rules = editor.profile.rules

    fun spec(
        kind: DirectHandleKind3D,
        anchor: Vec3,
        axis: Vec3,
        radiusMm: Double = 1.0,
    ) = DirectHandleSpec3D(
        path = path,
        partId = partId,
        kind = kind,
        anchorWorld = anchor,
        dragAxisWorld = axis,
        baselineStep = step,
        radiusMm = radiusMm,
        minimumLengthMm = rules.minPipeLengthMm,
        minimumAngleDeg = rules.minElbowAngleDeg,
        maximumAngleDeg = rules.maxElbowAngleDeg,
        allowedAnglesDeg = rules.allowedElbowAnglesDeg,
    )

    return when (val geometry = part.definition.geometry) {
        is StraightPipeGeometry3D -> listOf(
            spec(
                kind = DirectHandleKind3D.LENGTH,
                anchor = part.worldPort("end").frame.position,
                axis = part.transform.direction(Vec3.UNIT_X),
            ),
        )
        is ReducerGeometry3D -> listOf(
            spec(
                kind = DirectHandleKind3D.LENGTH,
                anchor = part.worldPort("small").frame.position,
                axis = part.transform.direction(Vec3.UNIT_X),
            ),
        )
        is ElbowGeometry3D -> {
            val rollAxis = part.transform.direction(Vec3.UNIT_X)
            val radial = part.transform.direction(Vec3.UNIT_Z)
            // Keep the roll ring clear of the angle grip on a phone-sized viewport.
            val rollRadius = (geometry.outsideDiameterMm * 1.4).coerceAtLeast(100.0)
            listOf(
                spec(
                    kind = DirectHandleKind3D.ANGLE,
                    anchor = part.worldPort("end").frame.position,
                    axis = part.worldPort("end").frame.forward,
                    radiusMm = geometry.centerlineRadiusMm,
                ),
                spec(
                    kind = DirectHandleKind3D.ROLL,
                    anchor = part.worldPort("start").frame.position + radial * rollRadius,
                    axis = rollAxis.cross(radial),
                    radiusMm = rollRadius,
                ),
            )
        }
        else -> emptyList()
    }
}

/** Converts the complete screen drag from its immutable gesture baseline into one preview. */
internal fun directEditForDrag(
    spec: DirectHandleSpec3D,
    projector: SceneProjector3D,
    dragPx: Offset,
): DirectEdit3D? {
    val projected = DragProjection3D.alongAxis(
        projector = projector,
        origin = spec.anchorWorld,
        axis = spec.dragAxisWorld,
        dragPx = dragPx,
    ) ?: return null

    val baselineStart = spec.baselineStart
    if (baselineStart != null) {
        val rawPosition = baselineStart.position + spec.dragAxisWorld * projected.millimetres
        fun snapped(value: Double) = Math.round(value / LENGTH_SNAP_MM) * LENGTH_SNAP_MM
        val position = when (spec.kind) {
            DirectHandleKind3D.START_X -> rawPosition.copy(x = snapped(rawPosition.x))
            DirectHandleKind3D.START_Y -> rawPosition.copy(y = snapped(rawPosition.y))
            DirectHandleKind3D.START_Z -> rawPosition.copy(z = snapped(rawPosition.z))
            else -> return null
        }
        val label = when (spec.kind) {
            DirectHandleKind3D.START_X -> "X ${directNumber(position.x)} mm"
            DirectHandleKind3D.START_Y -> "Y ${directNumber(position.y)} mm"
            DirectHandleKind3D.START_Z -> "Z ${directNumber(position.z)} mm"
            else -> return null
        }
        return DirectEdit3D(ChainCommand3D.MoveStart(baselineStart.copy(position = position)), label)
    }

    return when (val step = spec.baselineStep) {
        is ChainStep3D.Pipe -> {
            val length = DragProjection3D.snapLength(
                (step.lengthMm + projected.millimetres).coerceAtLeast(spec.minimumLengthMm),
                LENGTH_SNAP_MM,
            )
            DirectEdit3D(
                ChainCommand3D.Replace(spec.path, step.copy(lengthMm = length)),
                "L ${directNumber(length)} mm",
            )
        }
        is ChainStep3D.Reducer -> {
            val length = DragProjection3D.snapLength(
                (step.lengthMm + projected.millimetres).coerceAtLeast(spec.minimumLengthMm),
                LENGTH_SNAP_MM,
            )
            DirectEdit3D(
                ChainCommand3D.Replace(spec.path, step.copy(lengthMm = length)),
                "L ${directNumber(length)} mm",
            )
        }
        is ChainStep3D.Elbow -> when (spec.kind) {
            DirectHandleKind3D.ANGLE -> {
                val sign = if (step.angleDeg < 0.0) -1.0 else 1.0
                val delta = Math.toDegrees(projected.millimetres / spec.radiusMm) * sign
                val raw = step.angleDeg + delta
                val magnitude = abs(raw).coerceIn(spec.minimumAngleDeg, spec.maximumAngleDeg)
                val signed = if (raw < 0.0) -magnitude else magnitude
                val angle = DragProjection3D.snapAngle(signed, spec.allowedAnglesDeg)
                DirectEdit3D(
                    ChainCommand3D.Replace(spec.path, step.copy(angleDeg = angle)),
                    "∠ ${directNumber(abs(angle))}°",
                )
            }
            DirectHandleKind3D.ROLL -> {
                val delta = Math.toDegrees(projected.millimetres / spec.radiusMm)
                val roll = snapRoll(normalizeRoll(step.rollDeg + delta))
                DirectEdit3D(
                    ChainCommand3D.Replace(spec.path, step.copy(rollDeg = roll)),
                    "↻ ${directNumber(roll)}°",
                )
            }
            DirectHandleKind3D.LENGTH,
            DirectHandleKind3D.START_X,
            DirectHandleKind3D.START_Y,
            DirectHandleKind3D.START_Z,
            -> null
        }
        else -> null
    }
}

internal fun bestControllablePreset(
    mesh: AssemblyMesh3D,
    width: Float,
    height: Float,
    zoom: Float,
    perspective: Boolean,
    origin: Vec3,
    axis: Vec3,
): ViewPreset3D = ViewPreset3D.entries.maxBy { preset ->
    DragProjection3D.axisPixelsPerMm(
        SceneProjector3D(mesh, width, height, preset.yaw, preset.pitch, zoom, perspective),
        origin,
        axis,
    ) ?: 0f
}

private fun normalizeRoll(value: Double): Double {
    var result = value % 360.0
    if (result > 180.0) result -= 360.0
    if (result < -180.0) result += 360.0
    return result
}

private fun snapRoll(value: Double): Double = Math.round(value / ROLL_SNAP_DEG) * ROLL_SNAP_DEG

private fun directNumber(value: Double): String = String.format(Locale.US, "%.1f", value)

private const val LENGTH_SNAP_MM = 5.0
private const val ROLL_SNAP_DEG = 5.0
