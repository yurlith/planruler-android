package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.EngineLimits3D
import com.planruler.fabrication3d.EngineQuota3D
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.MeshMaterial3D
import com.planruler.fabrication3d.MeshQuality3D
import com.planruler.fabrication3d.catalog.toParametricAssembly3D
import com.planruler.pipecalculator.FlangedOffsetAssemblyInput
import com.planruler.pipecalculator.calculateFlangedOffsetAssembly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshTessellationTest {
    private val result = calculateFlangedOffsetAssembly(FlangedOffsetAssemblyInput(50, 16, 500.0, 1_600.0))
    private val assembly = result.toParametricAssembly3D(engine.parts).unwrap()

    @Test
    fun `workshop mesh contains finite hollow solids labels and weld rings`() {
        val mesh = engine.mesh.build(assembly).unwrap()

        assertTrue(mesh.triangles.size > 1_000)
        assertEquals(7, mesh.labels.size)
        assertEquals(6, mesh.polylines.count { it.material == MeshMaterial3D.WELD })
        assertTrue(mesh.triangles.any { it.material == MeshMaterial3D.INNER_BORE })
        assertTrue(mesh.triangles.all { it.a.isFinite() && it.b.isFinite() && it.c.isFinite() })
        assertTrue(mesh.bounds.minimum.x <= 0.0)
        assertTrue(mesh.bounds.maximum.x >= result.input.overallFaceToFaceMm)
        assertTrue(mesh.bounds.minimum.y < 0.0)
        assertTrue(mesh.bounds.maximum.y > result.input.targetOffsetMm)
        assertTrue(mesh.bounds.size.z >= result.flange.outsideDiameterMm * 0.99)
    }

    @Test
    fun `mesh quality trades triangles for frame budget`() {
        val draft = engine.mesh.build(assembly, MeshQuality3D.DRAFT).unwrap()
        val normal = engine.mesh.build(assembly, MeshQuality3D.NORMAL).unwrap()
        val fine = engine.mesh.build(assembly, MeshQuality3D.FINE).unwrap()

        assertTrue(draft.triangles.size < normal.triangles.size)
        assertTrue(normal.triangles.size < fine.triangles.size)
        assertEquals(7, draft.labels.size)
        assertEquals(7, fine.labels.size)
    }

    @Test
    fun `an oversized scene reports a triangle quota instead of exhausting memory`() {
        val tiny = DefaultFabrication3DEngine(EngineLimits3D(maxTriangles = 1_000))

        val error = tiny.mesh.build(assembly, MeshQuality3D.FINE).failure()

        assertTrue(error is Fabrication3DError.QuotaExceeded)
        assertEquals(EngineQuota3D.TRIANGLES, (error as Fabrication3DError.QuotaExceeded).quota)
    }

    @Test
    fun `reducer and cap tessellate into closed solids`() {
        val editor = engine.chains.create(workshopProfile()).unwrap().appendAll(
            com.planruler.fabrication3d.ChainStep3D.Pipe(300.0),
            com.planruler.fabrication3d.ChainStep3D.Cap(),
        )

        val mesh = engine.mesh.build(editor.assembly).unwrap()

        assertTrue(mesh.triangles.any { it.material == MeshMaterial3D.CAP })
        assertTrue(mesh.triangles.all { it.a.isFinite() && it.b.isFinite() && it.c.isFinite() })
    }
}
