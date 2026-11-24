package com.planruler.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
@JvmInline value class IsometricSheetId(val value: String)
@Serializable
@JvmInline value class IsoNodeId(val value: String)
@Serializable
@JvmInline value class IsoPipeId(val value: String)
@Serializable
@JvmInline value class IsoComponentId(val value: String)

/**
 * Axonometric convention of a pipe isometric. Both keep true length on every axis, so a
 * dimension can be read with a ruler from the printed sheet at the sheet scale.
 */
@Serializable
enum class IsoProjectionStyle {
    /** Rectangular isometric: X at 30°, Y at 150°, Z vertical (ISO 5456-3, GOST 2.317). */
    ISOMETRIC,

    /** Frontal isometric: X horizontal, Z vertical, Y receding at 45° (GOST 2.317). */
    FRONTAL,
}

/** Service carried by a run. It drives the line style and the conventional system code. */
@Serializable
enum class IsoPipeSystem {
    HEATING_SUPPLY,
    HEATING_RETURN,
    COLD_WATER,
    HOT_WATER,
    HOT_WATER_CIRCULATION,
    SEWER,
    GAS,
    OTHER,
}

/** Model coordinates in millimetres: X east, Y north, Z up. */
@Serializable
data class IsoPoint(val x: Double, val y: Double, val z: Double)

@Serializable
data class IsoPipeSpec(
    val system: IsoPipeSystem = IsoPipeSystem.OTHER,
    /** Nominal size (DN / outside diameter for ducts); null when the source did not say. */
    val nominalDiameter: Int? = null,
    val material: String? = null,
    /** Optional display colour carried over from the 2D takeoff template. */
    val colorArgb: Long? = null,
)

@Serializable
data class IsoNode(
    val id: IsoNodeId,
    val position: IsoPoint,
    val label: String? = null,
)

@Serializable
data class IsoPipe(
    val id: IsoPipeId,
    val from: IsoNodeId,
    val to: IsoNodeId,
    val spec: IsoPipeSpec = IsoPipeSpec(),
    val label: String? = null,
    /** 2D plan measurement this run was generated from, when it was generated. */
    val sourceMeasurementId: MeasurementId? = null,
)

@Serializable
enum class IsoComponentKind {
    GATE_VALVE,
    BALL_VALVE,
    CHECK_VALVE,
    BALANCING_VALVE,
    STRAINER,
    PUMP,
    METER,
    RADIATOR,
    FIXTURE,
    AIR_VENT,
    DRAIN,
    PLUG,
}

/** Inline components sit on a pipe at [offset] (0..1 of its length); terminal ones sit on a node. */
@Serializable
data class IsoComponent(
    val id: IsoComponentId,
    val kind: IsoComponentKind,
    val pipeId: IsoPipeId? = null,
    val offset: Double = 0.5,
    val nodeId: IsoNodeId? = null,
    val label: String? = null,
)

/**
 * A persisted pipe isometric. Only the 3D network is stored: fittings, dimensions, the grid
 * and the bill of materials are derived by the isometric engine every time it is opened.
 */
@Serializable
data class IsometricSheet(
    val id: IsometricSheetId,
    val name: String,
    val projection: IsoProjectionStyle = IsoProjectionStyle.ISOMETRIC,
    val gridStepMm: Double = 100.0,
    val nodes: List<IsoNode> = emptyList(),
    val pipes: List<IsoPipe> = emptyList(),
    val components: List<IsoComponent> = emptyList(),
    val showDimensions: Boolean = true,
    val showElevations: Boolean = true,
    val showSystemCodes: Boolean = true,
    /** Elevation used for runs that are started on empty paper, millimetres. */
    val workingLevelMm: Double = 0.0,
    val viewport: ViewportState? = null,
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
)

/**
 * Vertical data of a pipe run measured on a 2D plan. The plan geometry stays in
 * [Measurement.points]; only what a plan cannot show is stored here.
 */
@Serializable
data class PipeRoute(
    /** Height of the first point above the project reference level, millimetres. */
    val startElevationMm: Double = 0.0,
    /** Signed vertical leg taken at each vertex, millimetres; index-aligned with points, missing entries are 0. */
    val risesMm: List<Double> = emptyList(),
) {
    fun riseAt(index: Int): Double = risesMm.getOrNull(index)?.takeIf(Double::isFinite) ?: 0.0

    val verticalLengthMm: Double
        get() = risesMm.sumOf { if (it.isFinite()) abs(it) else 0.0 }

    val hasRises: Boolean get() = risesMm.any { it.isFinite() && abs(it) > 1e-9 }

    /** Keeps the rise list aligned when a vertex is inserted after [afterIndex]. */
    fun afterInsert(afterIndex: Int, pointCountBefore: Int): PipeRoute {
        val aligned = List(pointCountBefore) { riseAt(it) }.toMutableList()
        aligned.add((afterIndex + 1).coerceIn(0, aligned.size), 0.0)
        return copy(risesMm = aligned.trimmed())
    }

    /**
     * Keeps total vertical length and every downstream elevation when a vertex is removed:
     * its rise moves to the previous vertex, or to the new first vertex.
     */
    fun afterRemove(index: Int, pointCountBefore: Int): PipeRoute {
        val aligned = List(pointCountBefore) { riseAt(it) }.toMutableList()
        if (index !in aligned.indices) return this
        val removed = aligned.removeAt(index)
        if (aligned.isNotEmpty() && removed != 0.0) {
            val target = (index - 1).coerceAtLeast(0)
            aligned[target] = aligned[target] + removed
        }
        return copy(risesMm = aligned.trimmed())
    }

    fun withRise(index: Int, riseMm: Double, pointCount: Int): PipeRoute {
        require(index in 0 until pointCount) { "Vertex out of range" }
        require(riseMm.isFinite()) { "Rise must be finite" }
        val aligned = List(pointCount) { riseAt(it) }.toMutableList()
        aligned[index] = riseMm
        return copy(risesMm = aligned.trimmed())
    }

    private fun List<Double>.trimmed(): List<Double> = dropLastWhile { it == 0.0 }
}

/** Vertical millimetres that a measured run adds to its plan length. */
val Measurement.verticalLengthMm: Double
    get() = if (type == MeasurementType.DISTANCE || type == MeasurementType.POLYLINE) {
        route?.verticalLengthMm ?: 0.0
    } else {
        0.0
    }
