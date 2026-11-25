package com.planruler.isometric

import com.planruler.model.IsoPoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Model-space vector in millimetres: X east, Y north, Z up. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Double) = Vec3(x * scale, y * scale, z * scale)
    operator fun div(scale: Double) = Vec3(x / scale, y / scale, z / scale)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(other: Vec3) = x * other.x + y * other.y + z * other.z
    fun cross(other: Vec3) = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    val length: Double get() = sqrt(x * x + y * y + z * z)
    val isFinite: Boolean get() = x.isFinite() && y.isFinite() && z.isFinite()

    fun normalized(): Vec3 {
        val l = length
        require(l > 0.0 && l.isFinite()) { "Cannot normalise a zero vector" }
        return this / l
    }

    fun distanceTo(other: Vec3) = (this - other).length

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val X = Vec3(1.0, 0.0, 0.0)
        val Y = Vec3(0.0, 1.0, 0.0)
        val Z = Vec3(0.0, 0.0, 1.0)
    }
}

fun IsoPoint.toVec() = Vec3(x, y, z)
fun Vec3.toIsoPoint() = IsoPoint(x, y, z)

/** Drawing-space point in millimetres of the projected sheet, Y pointing up. */
data class P2(val x: Double, val y: Double) {
    operator fun plus(other: P2) = P2(x + other.x, y + other.y)
    operator fun minus(other: P2) = P2(x - other.x, y - other.y)
    operator fun times(scale: Double) = P2(x * scale, y * scale)
    operator fun div(scale: Double) = P2(x / scale, y / scale)
    operator fun unaryMinus() = P2(-x, -y)

    fun dot(other: P2) = x * other.x + y * other.y
    fun cross(other: P2) = x * other.y - y * other.x
    val length: Double get() = hypot(x, y)
    fun distanceTo(other: P2) = (this - other).length

    fun normalized(): P2 {
        val l = length
        return if (l > 0.0 && l.isFinite()) this / l else P2(1.0, 0.0)
    }

    /** Counter-clockwise perpendicular. */
    fun perpendicular() = P2(-y, x)

    companion object {
        val ZERO = P2(0.0, 0.0)
    }
}

data class Rect2(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
    val center: P2 get() = P2((minX + maxX) / 2.0, (minY + maxY) / 2.0)
    val corners: List<P2> get() = listOf(P2(minX, minY), P2(maxX, minY), P2(maxX, maxY), P2(minX, maxY))

    fun expanded(margin: Double) = Rect2(minX - margin, minY - margin, maxX + margin, maxY + margin)
    fun contains(point: P2) = point.x in minX..maxX && point.y in minY..maxY
    fun union(other: Rect2) = Rect2(min(minX, other.minX), min(minY, other.minY), max(maxX, other.maxX), max(maxY, other.maxY))

    companion object {
        fun around(points: Iterable<P2>): Rect2? {
            var result: Rect2? = null
            points.forEach { point ->
                if (!point.x.isFinite() || !point.y.isFinite()) return@forEach
                result = result?.let {
                    Rect2(min(it.minX, point.x), min(it.minY, point.y), max(it.maxX, point.x), max(it.maxY, point.y))
                } ?: Rect2(point.x, point.y, point.x, point.y)
            }
            return result
        }
    }
}

/** The six principal run directions, named the way an installer reads a site. */
enum class IsoAxis(val unit: Vec3) {
    EAST(Vec3.X),
    WEST(-Vec3.X),
    NORTH(Vec3.Y),
    SOUTH(-Vec3.Y),
    UP(Vec3.Z),
    DOWN(-Vec3.Z);

    val opposite: IsoAxis
        get() = when (this) {
            EAST -> WEST
            WEST -> EAST
            NORTH -> SOUTH
            SOUTH -> NORTH
            UP -> DOWN
            DOWN -> UP
        }

    val vertical: Boolean get() = this == UP || this == DOWN

    companion object {
        /** Exact axis of a direction, or null for an offset (diagonal) run. */
        fun of(direction: Vec3, toleranceDeg: Double = 0.5): IsoAxis? {
            if (!direction.isFinite || direction.length == 0.0) return null
            val unit = direction.normalized()
            val limit = kotlin.math.cos(Math.toRadians(toleranceDeg))
            return entries.firstOrNull { it.unit.dot(unit) >= limit }
        }
    }
}

/** Plane of a free (offset) run, named by the two axes it contains. */
enum class IsoPlane(val first: Vec3, val second: Vec3) {
    /** Floor plane: X and Y. */
    HORIZONTAL(Vec3.X, Vec3.Y),

    /** Wall facing south: X and Z. */
    FRONT(Vec3.X, Vec3.Z),

    /** Wall facing east: Y and Z. */
    SIDE(Vec3.Y, Vec3.Z),
}

internal fun distanceToSegment(point: P2, start: P2, end: P2): Pair<Double, Double> {
    val d = end - start
    val lengthSquared = d.dot(d)
    if (lengthSquared <= 0.0) return point.distanceTo(start) to 0.0
    val t = ((point - start).dot(d) / lengthSquared).coerceIn(0.0, 1.0)
    return point.distanceTo(start + d * t) to t
}

internal fun distanceToSegment(point: Vec3, start: Vec3, end: Vec3): Pair<Double, Double> {
    val d = end - start
    val lengthSquared = d.dot(d)
    if (lengthSquared <= 0.0) return point.distanceTo(start) to 0.0
    val t = ((point - start).dot(d) / lengthSquared).coerceIn(0.0, 1.0)
    return point.distanceTo(start + d * t) to t
}

internal fun nearlyEqual(a: Double, b: Double, tolerance: Double = 1e-6) = abs(a - b) <= tolerance

/** True when the two segments cross at a single point inside both (touching ends do not count). */
internal fun segmentsCross(a: P2, b: P2, c: P2, d: P2): Boolean {
    val ab = b - a
    val cd = d - c
    val d1 = ab.cross(c - a)
    val d2 = ab.cross(d - a)
    val d3 = cd.cross(a - c)
    val d4 = cd.cross(b - c)
    return ((d1 > 0.0 && d2 < 0.0) || (d1 < 0.0 && d2 > 0.0)) && ((d3 > 0.0 && d4 < 0.0) || (d3 < 0.0 && d4 > 0.0))
}

/** Gap between two segments; zero when they cross. */
internal fun segmentDistance(a: P2, b: P2, c: P2, d: P2): Double {
    if (segmentsCross(a, b, c, d)) return 0.0
    return minOf(
        distanceToSegment(a, c, d).first,
        distanceToSegment(b, c, d).first,
        distanceToSegment(c, a, b).first,
        distanceToSegment(d, a, b).first,
    )
}

/** Even-odd test; the polygon is closed implicitly. */
internal fun polygonContains(polygon: List<P2>, point: P2): Boolean {
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        if ((pi.y > point.y) != (pj.y > point.y) &&
            point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

/** Gap between a closed polygon and a segment; zero when they touch or overlap. */
internal fun polygonSegmentDistance(polygon: List<P2>, a: P2, b: P2): Double {
    if (polygon.size < 2) return polygon.firstOrNull()?.let { distanceToSegment(it, a, b).first } ?: Double.MAX_VALUE
    if (polygon.size > 2 && (polygonContains(polygon, a) || polygonContains(polygon, b))) return 0.0
    var best = Double.MAX_VALUE
    for (i in polygon.indices) {
        val p = polygon[i]
        val q = polygon[(i + 1) % polygon.size]
        if (segmentsCross(p, q, a, b)) return 0.0
        best = minOf(best, distanceToSegment(a, p, q).first, distanceToSegment(b, p, q).first, distanceToSegment(p, a, b).first)
    }
    return best
}

/** Gap between two closed polygons; zero when they touch, cross or one contains the other. */
internal fun polygonDistance(first: List<P2>, second: List<P2>): Double {
    if (second.size > 2 && first.any { polygonContains(second, it) }) return 0.0
    if (first.size > 2 && second.any { polygonContains(first, it) }) return 0.0
    var best = Double.MAX_VALUE
    for (i in first.indices) {
        best = minOf(best, polygonSegmentDistance(second, first[i], first[(i + 1) % first.size]))
        if (best == 0.0) return 0.0
    }
    return best
}
