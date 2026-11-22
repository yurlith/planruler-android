package com.planruler.feature.pipecalculator

import com.planruler.fabrication3d.AssemblyMesh3D
import com.planruler.fabrication3d.Bounds3D
import com.planruler.fabrication3d.MeshMaterial3D
import com.planruler.fabrication3d.MeshTriangle3D
import com.planruler.fabrication3d.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class SceneShading3DTest {
    private fun mesh(vararg triangles: MeshTriangle3D) = AssemblyMesh3D(
        triangles = triangles.toList(),
        polylines = emptyList(),
        labels = emptyList(),
        bounds = Bounds3D(Vec3(-10.0, -10.0, -10.0), Vec3(10.0, 10.0, 10.0)),
    )

    private fun tri(part: String, a: Vec3, b: Vec3, c: Vec3) = MeshTriangle3D(part, MeshMaterial3D.PIPE, a, b, c)

    /** Two facets hinged on the Y axis, the second tilted by [degrees]. */
    private fun hinge(degrees: Double, flipSecond: Boolean = false, secondPart: String = "P1"): AssemblyMesh3D {
        val angle = Math.toRadians(degrees)
        val far = Vec3(-cos(angle), 0.0, sin(angle))
        val first = tri("P1", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(1.0, 0.0, 0.0))
        val second = if (flipSecond) {
            tri(secondPart, Vec3(0.0, 0.0, 0.0), far, Vec3(0.0, 1.0, 0.0))
        } else {
            tri(secondPart, Vec3(0.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), far)
        }
        return mesh(first, second)
    }

    @Test
    fun `gentle bend is smoothed into one shared normal`() {
        val normals = SmoothNormals3D(hinge(160.0))
        val shared = normals.normal(0, 0)
        val same = normals.normal(1, 0)
        assertEquals(1.0, kotlin.math.abs(shared.dot(same)), 1e-9)
        // The blend differs from the flat face normal.
        assertTrue(kotlin.math.abs(shared.dot(Vec3.UNIT_Z)) < 0.999)
    }

    @Test
    fun `sharp crease keeps each face normal`() {
        val mesh = hinge(90.0)
        val normals = SmoothNormals3D(mesh)
        assertEquals(1.0, kotlin.math.abs(normals.normal(0, 0).dot(mesh.triangles[0].normal)), 1e-9)
        assertEquals(1.0, kotlin.math.abs(normals.normal(1, 0).dot(mesh.triangles[1].normal)), 1e-9)
    }

    @Test
    fun `inconsistent winding is aligned before averaging`() {
        val consistent = SmoothNormals3D(hinge(160.0)).normal(0, 0)
        val flipped = SmoothNormals3D(hinge(160.0, flipSecond = true)).normal(0, 0)
        assertEquals(1.0, kotlin.math.abs(consistent.dot(flipped)), 1e-9)
    }

    @Test
    fun `different parts never share a smoothed edge`() {
        val mesh = hinge(160.0, secondPart = "P2")
        val normals = SmoothNormals3D(mesh)
        assertEquals(1.0, kotlin.math.abs(normals.normal(0, 0).dot(mesh.triangles[0].normal)), 1e-9)
    }

    @Test
    fun `lighting is two sided and keeps the theme floor`() {
        val front = SceneLighting3D.shade(Vec3(0.0, 0.0, 1.0), 0.4f)
        val back = SceneLighting3D.shade(Vec3(0.0, 0.0, -1.0), 0.4f)
        assertEquals(front, back)
        val grazing = SceneLighting3D.shade(Vec3(0.0, -1.0, 0.0), 0.4f)
        assertTrue(grazing.diffuse >= 0.4f)
        assertTrue(front.diffuse > grazing.diffuse)
    }

    @Test
    fun `highlight whitens without exceeding one`() {
        val light = SceneLighting3D.Light(diffuse = 1.1f, specular = 1f)
        assertEquals(1f, litChannel(0.8f, light), 1e-6f)
        assertEquals(0.5f, litChannel(0.5f, SceneLighting3D.Light(1f, 0f)), 1e-6f)
    }
}
