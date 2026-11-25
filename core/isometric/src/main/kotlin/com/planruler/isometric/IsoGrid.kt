package com.planruler.isometric

import com.planruler.model.IsoProjectionStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

data class GridLine(val start: P2, val end: P2, val major: Boolean)

/** Lines of one family are `offset * k * step + t * direction` for every integer k. */
data class GridFamily(val direction: P2, val offset: P2)

/** The step actually drawn at the current zoom and how often a line is emphasised. */
data class GridSpec(val minorStepMm: Double, val majorEvery: Int) {
    val majorStepMm: Double get() = minorStepMm * majorEvery
}

/**
 * Isometric graph paper. For the 30° isometric the three families meet in a triangular
 * lattice which is exactly the projection of the model grid, so a lattice node is a model
 * point. The frontal sheet uses square paper with 45° guides, the classic oblique paper.
 */
object IsoGrid {
    fun families(projection: IsoProjection): List<GridFamily> = when (projection.style) {
        IsoProjectionStyle.ISOMETRIC -> listOf(
            GridFamily(direction = projection.ex, offset = projection.ey),
            GridFamily(direction = projection.ey, offset = projection.ex),
            GridFamily(direction = projection.ez, offset = projection.ex),
        )
        IsoProjectionStyle.FRONTAL -> listOf(
            GridFamily(direction = projection.ex, offset = projection.ez),
            GridFamily(direction = projection.ez, offset = projection.ex),
            GridFamily(direction = P2(1.0, 1.0) / sqrt(2.0), offset = projection.ex),
        )
    }

    /**
     * Never finer than [baseStepMm]; coarsened in 1-2-5 steps until neighbouring lines are at
     * least [minSpacingPx] apart on screen, so a zoomed-out sheet never turns grey.
     */
    fun spec(baseStepMm: Double, pxPerMm: Double, minSpacingPx: Double = 12.0): GridSpec {
        val base = baseStepMm.takeIf { it.isFinite() && it > 0.0 } ?: 100.0
        val scale = pxPerMm.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        var decade = 1.0
        repeat(12) {
            listOf(1 to 10, 2 to 5, 5 to 2).forEach { (mantissa, majorEvery) ->
                val step = base * mantissa * decade
                // sin 45° is the tightest family spacing of both sheets.
                if (step * scale * FAMILY_SPACING_FACTOR >= minSpacingPx) return GridSpec(step, majorEvery)
            }
            decade *= 10.0
        }
        return GridSpec(base * decade, 10)
    }

    fun lines(
        projection: IsoProjection,
        bounds: Rect2,
        spec: GridSpec,
        maxLines: Int = 1_200,
    ): List<GridLine> {
        val step = spec.minorStepMm
        if (!step.isFinite() || step <= 0.0 || bounds.width <= 0.0 || bounds.height <= 0.0) return emptyList()
        val result = mutableListOf<GridLine>()
        families(projection).forEach { family ->
            val normal = family.direction.perpendicular().normalized()
            val spacing = family.offset.dot(normal) * step
            if (abs(spacing) < 1e-12) return@forEach
            val distances = bounds.corners.map { it.dot(normal) }
            val low = distances.min() / spacing
            val high = distances.max() / spacing
            val first = ceil(min(low, high)).toLong()
            val last = floor(max(low, high)).toLong()
            if (last - first > maxLines) return@forEach
            for (k in first..last) {
                val origin = family.offset * (k * step)
                clip(origin, family.direction, bounds)?.let { (a, b) ->
                    result += GridLine(a, b, major = k % spec.majorEvery == 0L)
                }
            }
        }
        return result
    }

    /** Nearest lattice node of the drawn paper. */
    fun nearestNode(projection: IsoProjection, point: P2, stepMm: Double): P2 {
        if (!stepMm.isFinite() || stepMm <= 0.0) return point
        return when (projection.style) {
            IsoProjectionStyle.FRONTAL -> P2(round(point.x / stepMm) * stepMm, round(point.y / stepMm) * stepMm)
            IsoProjectionStyle.ISOMETRIC -> {
                val a = projection.ex
                val b = projection.ey
                val determinant = a.cross(b)
                val i = point.cross(b) / determinant / stepMm
                val j = a.cross(point) / determinant / stepMm
                val i0 = floor(i)
                val j0 = floor(j)
                // The lattice cell is two equilateral triangles: the nearest node is a corner.
                listOf(i0 to j0, i0 + 1 to j0, i0 to j0 + 1, i0 + 1 to j0 + 1)
                    .map { (ci, cj) -> a * (ci * stepMm) + b * (cj * stepMm) }
                    .minBy { it.distanceTo(point) }
            }
        }
    }

    /**
     * Model point for a tap on empty paper: on the floor at [levelMm] for the isometric sheet,
     * on the front wall for the frontal sheet, with the free coordinates snapped to [stepMm].
     */
    fun modelPointOnBase(projection: IsoProjection, point: P2, stepMm: Double, levelMm: Double): Vec3? {
        val plane = projection.basePlane
        val origin = when (plane) {
            IsoPlane.HORIZONTAL -> Vec3(0.0, 0.0, levelMm)
            IsoPlane.FRONT, IsoPlane.SIDE -> Vec3.ZERO
        }
        val free = projection.unprojectOnPlane(point, origin, plane) ?: return null
        if (!stepMm.isFinite() || stepMm <= 0.0) return free
        fun snap(value: Double) = round(value / stepMm) * stepMm
        return when (plane) {
            IsoPlane.HORIZONTAL -> Vec3(snap(free.x), snap(free.y), levelMm)
            IsoPlane.FRONT -> Vec3(snap(free.x), 0.0, snap(free.z))
            IsoPlane.SIDE -> Vec3(0.0, snap(free.y), snap(free.z))
        }
    }

    /** Liang–Barsky clip of the infinite line `origin + t * direction` to [bounds]. */
    private fun clip(origin: P2, direction: P2, bounds: Rect2): Pair<P2, P2>? {
        var t0 = Double.NEGATIVE_INFINITY
        var t1 = Double.POSITIVE_INFINITY
        fun edge(p: Double, q: Double): Boolean {
            if (abs(p) < 1e-15) return q >= 0.0
            val r = q / p
            if (p < 0.0) {
                if (r > t1) return false
                if (r > t0) t0 = r
            } else {
                if (r < t0) return false
                if (r < t1) t1 = r
            }
            return true
        }
        if (!edge(-direction.x, origin.x - bounds.minX)) return null
        if (!edge(direction.x, bounds.maxX - origin.x)) return null
        if (!edge(-direction.y, origin.y - bounds.minY)) return null
        if (!edge(direction.y, bounds.maxY - origin.y)) return null
        if (!t0.isFinite() || !t1.isFinite() || t1 <= t0) return null
        return (origin + direction * t0) to (origin + direction * t1)
    }

    private const val FAMILY_SPACING_FACTOR = 0.7071
}
