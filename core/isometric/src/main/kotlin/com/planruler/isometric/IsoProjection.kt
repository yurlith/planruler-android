package com.planruler.isometric

import com.planruler.model.IsoPoint
import com.planruler.model.IsoProjectionStyle
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A run direction the pen can produce, with its projected (not normalised) screen vector. */
data class RunDirection(
    /** Unit model direction. */
    val direction: Vec3,
    /** Principal axis, or null for a 45° offset inside one of the three planes. */
    val axis: IsoAxis?,
    /** Projection of [direction]; its length is the drawing scale factor along that direction. */
    val screen: P2,
) {
    val screenAngleDeg: Double get() = Math.toDegrees(atan2(screen.y, screen.x)).let { if (it < 0) it + 360.0 else it }
}

/** Result of reading a pen stroke on the sheet as a run of the model. */
data class RunMatch(
    val run: RunDirection,
    /** Model length implied by the stroke, before any snapping. */
    val modelLengthMm: Double,
    /** Angle between the stroke and the chosen direction on the sheet. */
    val deviationDeg: Double,
)

/**
 * Parallel axonometric projection with true length on all three axes (reduced coefficients
 * of 1 as allowed by GOST 2.317 / ISO 5456-3). Drawing units are millimetres, Y up.
 */
class IsoProjection private constructor(
    val style: IsoProjectionStyle,
    val ex: P2,
    val ey: P2,
    val ez: P2,
) {
    fun project(point: Vec3): P2 = ex * point.x + ey * point.y + ez * point.z
    fun project(point: IsoPoint): P2 = project(point.toVec())

    /** Projected screen vector of a model direction; linear, so identical to [project]. */
    fun screenOf(direction: Vec3): P2 = project(direction)

    /** Runs started on empty paper lie on this plane, so a lattice node maps to one model point. */
    val basePlane: IsoPlane
        get() = when (style) {
            IsoProjectionStyle.ISOMETRIC -> IsoPlane.HORIZONTAL
            IsoProjectionStyle.FRONTAL -> IsoPlane.FRONT
        }

    /** Model point on [plane] through [origin] whose projection is [target]. */
    fun unprojectOnPlane(target: P2, origin: Vec3, plane: IsoPlane): Vec3? {
        val a = project(plane.first)
        val b = project(plane.second)
        val determinant = a.cross(b)
        if (abs(determinant) < 1e-9) return null
        val rhs = target - project(origin)
        val s = rhs.cross(b) / determinant
        val t = a.cross(rhs) / determinant
        return origin + plane.first * s + plane.second * t
    }

    /** The six axes followed by the 45° plane offsets that read unambiguously on this sheet. */
    val runDirections: List<RunDirection> by lazy {
        val candidates = mutableListOf<RunDirection>()
        IsoAxis.entries.forEach { axis -> candidates += RunDirection(axis.unit, axis, project(axis.unit)) }
        val diagonal = 1.0 / sqrt(2.0)
        IsoPlane.entries.forEach { plane ->
            listOf(1.0 to 1.0, 1.0 to -1.0, -1.0 to 1.0, -1.0 to -1.0).forEach { (a, b) ->
                val direction = (plane.first * a + plane.second * b) * diagonal
                candidates += RunDirection(direction, null, project(direction))
            }
        }
        // A diagonal that projects onto an already listed direction would be read as that
        // direction anyway; drawing it requires numeric input with an explicit plane.
        candidates.fold(mutableListOf<RunDirection>()) { kept, candidate ->
            val duplicate = kept.any { existing ->
                val a = existing.screen.normalized()
                val b = candidate.screen.normalized()
                a.dot(b) > cos(Math.toRadians(0.5))
            }
            if (!duplicate && candidate.screen.length > 1e-9) kept += candidate
            kept
        }.toList()
    }

    /**
     * Reads a stroke of [delta] drawing millimetres as a model run. With [allowOffsets] false
     * only the six axes are considered, which is the normal mode for heating and water lines.
     */
    fun matchRun(delta: P2, allowOffsets: Boolean = true): RunMatch? {
        if (!delta.x.isFinite() || !delta.y.isFinite() || delta.length < 1e-9) return null
        val unit = delta.normalized()
        val best = runDirections
            .asSequence()
            .filter { allowOffsets || it.axis != null }
            .maxByOrNull { it.screen.normalized().dot(unit) }
            ?: return null
        val screenUnit = best.screen.normalized()
        val deviation = Math.toDegrees(kotlin.math.acos(screenUnit.dot(unit).coerceIn(-1.0, 1.0)))
        val modelLength = delta.dot(best.screen) / best.screen.dot(best.screen)
        return RunMatch(best, modelLength, deviation)
    }

    companion object {
        private val ISO = IsoProjection(
            IsoProjectionStyle.ISOMETRIC,
            ex = P2(cos(Math.toRadians(30.0)), sin(Math.toRadians(30.0))),
            ey = P2(-cos(Math.toRadians(30.0)), sin(Math.toRadians(30.0))),
            ez = P2(0.0, 1.0),
        )
        private val FRONTAL = IsoProjection(
            IsoProjectionStyle.FRONTAL,
            ex = P2(1.0, 0.0),
            ey = P2(cos(Math.toRadians(45.0)), sin(Math.toRadians(45.0))),
            ez = P2(0.0, 1.0),
        )

        fun of(style: IsoProjectionStyle): IsoProjection = when (style) {
            IsoProjectionStyle.ISOMETRIC -> ISO
            IsoProjectionStyle.FRONTAL -> FRONTAL
        }
    }
}
