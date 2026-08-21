package com.planruler.fabrication3d

import kotlin.math.max
import kotlin.math.min

enum class MeshMaterial3D { PIPE, ELBOW, FLANGE, TEE, REDUCER, CAP, INNER_BORE, WELD, GUIDE }

data class MeshTriangle3D(
    val partId: String,
    val material: MeshMaterial3D,
    val a: Vec3,
    val b: Vec3,
    val c: Vec3,
) {
    val center: Vec3 get() = (a + b + c) / 3.0
    val normal: Vec3
        get() {
            val cross = (b - a).cross(c - a)
            return if (cross.length <= GEOMETRY_EPSILON) Vec3.UNIT_Z else cross.normalized()
        }
}

data class MeshPolyline3D(
    val ownerId: String,
    val material: MeshMaterial3D,
    val points: List<Vec3>,
    val closed: Boolean = false,
)

data class MeshLabel3D(
    val partId: String,
    val text: String,
    val position: Vec3,
)

data class Bounds3D(
    val minimum: Vec3,
    val maximum: Vec3,
) {
    val center: Vec3 get() = (minimum + maximum) / 2.0
    val size: Vec3 get() = maximum - minimum
    val radius: Double get() = max(size.x, max(size.y, size.z)) / 2.0

    fun contains(point: Vec3, marginMm: Double = 0.0): Boolean =
        point.x >= minimum.x - marginMm && point.x <= maximum.x + marginMm &&
            point.y >= minimum.y - marginMm && point.y <= maximum.y + marginMm &&
            point.z >= minimum.z - marginMm && point.z <= maximum.z + marginMm

    companion object {
        fun from(points: List<Vec3>): Bounds3D {
            require(points.isNotEmpty()) { "Bounds need at least one point" }
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var minZ = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            var maxZ = Double.NEGATIVE_INFINITY
            points.forEach { point ->
                require(point.isFinite()) { "A mesh cannot contain non-finite coordinates" }
                minX = min(minX, point.x)
                minY = min(minY, point.y)
                minZ = min(minZ, point.z)
                maxX = max(maxX, point.x)
                maxY = max(maxY, point.y)
                maxZ = max(maxZ, point.z)
            }
            return Bounds3D(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ))
        }

        fun around(first: Vec3, second: Vec3, marginMm: Double = 0.0): Bounds3D = Bounds3D(
            Vec3(
                min(first.x, second.x) - marginMm,
                min(first.y, second.y) - marginMm,
                min(first.z, second.z) - marginMm,
            ),
            Vec3(
                max(first.x, second.x) + marginMm,
                max(first.y, second.y) + marginMm,
                max(first.z, second.z) + marginMm,
            ),
        )
    }
}

data class AssemblyMesh3D(
    val triangles: List<MeshTriangle3D>,
    val polylines: List<MeshPolyline3D>,
    val labels: List<MeshLabel3D>,
    val bounds: Bounds3D,
)

/**
 * Tessellation density. The renderer picks this per frame budget; the engine only
 * needs to know how many segments a full revolution and a 90 degree bend get.
 */
enum class MeshQuality3D(val radialSegments: Int, val curveSegmentsPerQuarter: Int) {
    DRAFT(12, 8),
    NORMAL(24, 24),
    FINE(48, 40),
    ;

    companion object {
        /** The density the shipped workshop viewport used before quality became selectable. */
        val DEFAULT = NORMAL
    }
}
