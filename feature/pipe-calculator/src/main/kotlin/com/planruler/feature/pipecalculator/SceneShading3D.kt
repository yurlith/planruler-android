package com.planruler.feature.pipecalculator

import com.planruler.fabrication3d.AssemblyMesh3D
import com.planruler.fabrication3d.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Per-vertex normals for smooth (Gouraud) shading, computed once per mesh.
 *
 * The tessellator emits flat triangles whose winding is not guaranteed, so neighbours are
 * sign-aligned before averaging, and only faces within [CREASE_COS] of each other are
 * blended: a pipe wall turns round, while the rim between a flange face and its edge
 * stays a crisp crease.
 */
internal class SmoothNormals3D(mesh: AssemblyMesh3D) {
    /** Three normals per triangle, flattened as x, y, z. */
    val vertexNormals: DoubleArray

    init {
        val triangles = mesh.triangles
        val faceNormals = triangles.map { it.normal }
        val corners = HashMap<CornerKey, MutableList<Int>>(triangles.size * 2)
        triangles.forEachIndexed { index, triangle ->
            corners.getOrPut(CornerKey.of(triangle.partId, triangle.a)) { ArrayList(6) }.add(index)
            corners.getOrPut(CornerKey.of(triangle.partId, triangle.b)) { ArrayList(6) }.add(index)
            corners.getOrPut(CornerKey.of(triangle.partId, triangle.c)) { ArrayList(6) }.add(index)
        }
        vertexNormals = DoubleArray(triangles.size * 9)
        triangles.forEachIndexed { index, triangle ->
            val own = faceNormals[index]
            listOf(triangle.a, triangle.b, triangle.c).forEachIndexed { corner, point ->
                var x = 0.0
                var y = 0.0
                var z = 0.0
                corners[CornerKey.of(triangle.partId, point)].orEmpty().forEach { other ->
                    val candidate = faceNormals[other]
                    val dot = own.dot(candidate)
                    if (abs(dot) >= CREASE_COS) {
                        val sign = if (dot < 0.0) -1.0 else 1.0
                        x += candidate.x * sign
                        y += candidate.y * sign
                        z += candidate.z * sign
                    }
                }
                val length = sqrt(x * x + y * y + z * z)
                val offset = index * 9 + corner * 3
                if (length < 1e-9) {
                    vertexNormals[offset] = own.x
                    vertexNormals[offset + 1] = own.y
                    vertexNormals[offset + 2] = own.z
                } else {
                    vertexNormals[offset] = x / length
                    vertexNormals[offset + 1] = y / length
                    vertexNormals[offset + 2] = z / length
                }
            }
        }
    }

    fun normal(triangle: Int, corner: Int): Vec3 {
        val offset = triangle * 9 + corner * 3
        return Vec3(vertexNormals[offset], vertexNormals[offset + 1], vertexNormals[offset + 2])
    }

    private data class CornerKey(val partId: String, val x: Long, val y: Long, val z: Long) {
        companion object {
            fun of(partId: String, point: Vec3) = CornerKey(
                partId,
                (point.x * QUANTUM).roundToLong(),
                (point.y * QUANTUM).roundToLong(),
                (point.z * QUANTUM).roundToLong(),
            )
        }
    }

    companion object {
        /** cos(40°): faces meeting at a sharper angle keep a hard edge. */
        const val CREASE_COS = 0.766
        private const val QUANTUM = 100.0
    }
}

/**
 * Studio lighting in camera space: a warm key light from the upper left, a cool fill from
 * the right, a sky/ground ambient and a Blinn-Phong highlight that makes steel read as
 * metal. Every surface is lit two-sided because bores are seen from inside.
 */
internal object SceneLighting3D {
    private val key = Vec3(-0.42, 0.62, 0.66).normalized()
    private val fill = Vec3(0.75, -0.1, 0.65).normalized()
    private val halfway = (key + Vec3.UNIT_Z).normalized()

    /** Diffuse brightness in 0..~1.1 and a separate additive white highlight in 0..1. */
    data class Light(val diffuse: Float, val specular: Float)

    fun shade(viewNormal: Vec3, ambientFloor: Float): Light {
        // Face the viewer: the camera looks down -Z, so a normal pointing away is flipped.
        val n = if (viewNormal.z < 0.0) viewNormal * -1.0 else viewNormal
        val sky = 0.5 + 0.5 * n.y
        val ambient = ambientFloor + 0.14 * sky
        val diffuse = ambient + 0.62 * max(0.0, n.dot(key)) + 0.2 * max(0.0, n.dot(fill))
        val specular = 0.42 * max(0.0, n.dot(halfway)).pow(28.0)
        return Light(diffuse.toFloat().coerceIn(0f, 1.15f), specular.toFloat().coerceIn(0f, 1f))
    }
}

/** Mixes a lit base colour with the white highlight, all channels in 0..1. */
internal fun litChannel(base: Float, light: SceneLighting3D.Light): Float =
    (base * light.diffuse + (1f - base * light.diffuse) * light.specular).coerceIn(0f, 1f)
