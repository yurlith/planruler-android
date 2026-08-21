package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.PortReference3D
import com.planruler.fabrication3d.RouteRequest3D
import com.planruler.fabrication3d.RouteTerminal3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.catalog.toParametricAssembly3D
import com.planruler.pipecalculator.FlangedOffsetAssemblyInput
import com.planruler.pipecalculator.calculateFlangedOffsetAssembly
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization values captured from the engine before the v2 refactor. These
 * numbers are fabrication output, so a change here means the shop floor would cut
 * different pipe; any deviation must be a deliberate, reviewed decision.
 */
class Fabrication3DGoldenBaselineTest {
    @Test
    fun `reference DN50 spatial route keeps its captured cut lengths`() {
        val solution = engine.router.solve(
            RouteRequest3D(
                profile = installationProfile(50),
                target = RouteTerminal3D(Vec3(1_600.0, 500.0, 300.0)),
                preferredElbowAngleDeg = 45.0,
            ),
        ).unwrap()

        assertEquals(listOf("P1", "P2", "P3"), solution.pipeCuts.map { it.code })
        assertEquals(427.972174517380, solution.pipeCuts[0].lengthMm, 1e-9)
        assertEquals(757.660663642822, solution.pipeCuts[1].lengthMm, 1e-9)
        assertEquals(427.972174517380, solution.pipeCuts[2].lengthMm, 1e-9)
        assertEquals(45.0, solution.elbowAnglesDeg.first(), 1e-12)
        val roll = (solution.plan.steps.first { it is ChainStep3D.Elbow } as ChainStep3D.Elbow).rollDeg
        assertEquals(30.963756532074, roll, 1e-9)
        assertValid(solution.assembly)
    }

    @Test
    fun `every installation diameter keeps its captured cut lengths`() {
        GOLDEN_DIAMETER_CUTS.forEach { (dn, expected) ->
            val solution = engine.router.solve(
                RouteRequest3D(
                    profile = installationProfile(dn),
                    target = RouteTerminal3D(Vec3(8_000.0, 1_500.0, 700.0)),
                    preferredElbowAngleDeg = 45.0,
                    minimumStraightMm = 20.0,
                ),
            ).unwrap()

            val actual = solution.pipeCuts.map { it.lengthMm }
            assertEquals("DN $dn cut count", expected.size, actual.size)
            expected.forEachIndexed { index, value ->
                assertEquals("DN $dn cut $index", value, actual[index], 1e-6)
            }
        }
    }

    @Test
    fun `verified workshop assembly keeps its captured tessellation`() {
        val assembly = calculateFlangedOffsetAssembly(FlangedOffsetAssemblyInput(50, 16, 500.0, 1_600.0))
            .toParametricAssembly3D(engine.parts)
            .unwrap()

        val mesh = engine.mesh.build(assembly).unwrap()

        assertEquals(3_648, mesh.triangles.size)
        assertEquals(14, mesh.polylines.size)
        assertEquals(7, mesh.labels.size)
        assertEquals(-0.8, mesh.bounds.minimum.x, 1e-9)
        assertEquals(-82.5, mesh.bounds.minimum.y, 1e-9)
        assertEquals(-82.5, mesh.bounds.minimum.z, 1e-9)
        assertEquals(1_600.8, mesh.bounds.maximum.x, 1e-9)
        assertEquals(582.5, mesh.bounds.maximum.y, 1e-9)
        assertEquals(82.5, mesh.bounds.maximum.z, 1e-9)
    }

    @Test
    fun `manual command sequence keeps its captured terminal face`() {
        val editor = engine.chains.create(installationProfile(50)).unwrap().appendAll(
            ChainStep3D.Pipe(300.0),
            ChainStep3D.Elbow(45.0, 30.0),
            ChainStep3D.Pipe(250.0),
            ChainStep3D.Elbow(-45.0, 0.0),
            ChainStep3D.Flange(),
        )

        val face = editor.assembly.worldPort(PortReference3D("F2", "face")).frame
        assertEquals(683.085353161738, face.position.x, 1e-9)
        assertEquals(194.097849816206, face.position.y, 1e-9)
        assertEquals(112.062445840514, face.position.z, 1e-9)
        assertEquals(1.0, face.forward.x, 1e-12)
        assertEquals(0.0, face.forward.y, 1e-12)
        assertEquals(0.0, face.forward.z, 1e-12)
        assertValid(editor.assembly)
    }

    private companion object {
        val GOLDEN_DIAMETER_CUTS = mapOf(
            15 to listOf(3_121.754752, 2_313.744023, 3_121.754752),
            20 to listOf(3_118.340539, 2_312.915596, 3_118.340539),
            25 to listOf(3_114.612617, 2_305.459751, 3_114.612617),
            32 to listOf(3_108.677588, 2_297.589694, 3_108.677588),
            40 to listOf(3_102.742559, 2_289.719636, 3_102.742559),
            50 to listOf(3_091.872501, 2_273.979521, 3_091.872501),
            65 to listOf(3_084.002444, 2_258.239405, 3_084.002444),
            80 to listOf(3_070.925279, 2_242.085076, 3_070.925279),
            100 to listOf(3_053.185164, 2_210.604846, 3_053.185164),
            125 to listOf(3_034.445049, 2_179.124615, 3_034.445049),
            150 to listOf(3_018.912040, 2_148.058598, 3_018.912040),
            200 to listOf(2_980.017596, 2_084.269709, 2_980.017596),
            250 to listOf(2_940.537365, 2_021.309248, 2_940.537365),
            300 to listOf(2_901.057134, 1_958.348786, 2_901.057134),
        )
    }
}
