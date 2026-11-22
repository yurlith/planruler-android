package com.planruler.feature.pipecalculator

import com.planruler.fabrication3d.AssemblyMesh3D
import com.planruler.fabrication3d.Bounds3D
import com.planruler.fabrication3d.Vec3
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.max

class SceneFit3DTest {
    private val mesh = AssemblyMesh3D(
        triangles = emptyList(),
        polylines = emptyList(),
        labels = emptyList(),
        bounds = Bounds3D(Vec3(0.0, 0.0, -30.0), Vec3(1600.0, 500.0, 30.0)),
    )

    private fun check(perspective: Boolean) {
        val width = 1000f
        val height = 1200f
        val fit = fitSceneCamera(mesh, width, height, -32f, 24f, perspective)
        val projector = SceneProjector3D(mesh, width, height, -32f, 24f, fit.zoom, perspective, fit.panX, fit.panY)
        val lo = mesh.bounds.minimum
        val hi = mesh.bounds.maximum
        val points = listOf(lo.x, hi.x).flatMap { x -> listOf(lo.y, hi.y).flatMap { y -> listOf(lo.z, hi.z).map { z -> Vec3(x, y, z) } } }
            .map { projector.project(it).screen }
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        assertEquals(width / 2f, (minX + maxX) / 2f, 6f)
        assertEquals(height / 2f, (minY + maxY) / 2f, 6f)
        assertEquals(0.8f, max((maxX - minX) / width, (maxY - minY) / height), 0.03f)
    }

    @Test fun `orthographic fit centres and fills the viewport`() = check(perspective = false)

    @Test fun `perspective fit centres and fills the viewport`() = check(perspective = true)
}
