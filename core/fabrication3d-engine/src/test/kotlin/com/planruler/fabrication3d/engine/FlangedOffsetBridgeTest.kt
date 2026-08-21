package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.ChainCommand3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.ElbowGeometry3D
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.FabricationPartKind
import com.planruler.fabrication3d.PortReference3D
import com.planruler.fabrication3d.StraightPipeGeometry3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.catalog.toAssemblyProfile3D
import com.planruler.fabrication3d.catalog.toChainPlan3D
import com.planruler.fabrication3d.catalog.toParametricAssembly3D
import com.planruler.pipecalculator.FlangedOffsetAssemblyInput
import com.planruler.pipecalculator.PIPE_INSTALLATION_SERIES
import com.planruler.pipecalculator.calculateFlangedOffsetAssembly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlangedOffsetBridgeTest {
    /**
     * The verified spool is a template until it can be edited. Replaying it as a recipe has
     * to land on the same geometry, otherwise "open in the editor" would silently move pipe.
     */
    @Test
    fun `the verified calculation reopens as an editable recipe with the same geometry`() {
        val result = calculateFlangedOffsetAssembly(
            FlangedOffsetAssemblyInput(dn = 50, pn = 16, targetOffsetMm = 500.0, overallFaceToFaceMm = 1_600.0),
        )
        val verified = result.toParametricAssembly3D(engine.parts).unwrap()

        val editor = engine.chains.fromPlan(result.toAssemblyProfile3D(), result.toChainPlan3D()).unwrap()

        assertEquals(verified.parts.size, editor.assembly.parts.size)
        assertEquals(2, editor.elbowCount)
        assertEquals(3, editor.pipeCount)
        assertValid(editor.assembly)

        val verifiedEnd = verified.worldPort(PortReference3D("F2", "face")).frame.position
        val editableEnd = editor.assembly.worldPort(PortReference3D("F2", "face")).frame.position
        assertVec(verifiedEnd, editableEnd, 1e-6)
    }

    @Test
    fun `every element of the reopened spool can then be changed`() {
        val result = calculateFlangedOffsetAssembly(
            FlangedOffsetAssemblyInput(dn = 50, pn = 16, targetOffsetMm = 500.0, overallFaceToFaceMm = 1_600.0),
        )
        val editor = engine.chains.fromPlan(result.toAssemblyProfile3D(), result.toChainPlan3D()).unwrap()

        val elbowPath = requireNotNull(editor.pathForPart("E1"))
        val retuned = engine.chains
            .execute(editor, ChainCommand3D.Replace(elbowPath, ChainStep3D.Elbow(30.0, 15.0)))
            .unwrap()

        val geometry = retuned.assembly.part("E1").definition.geometry as ElbowGeometry3D
        assertEquals(30.0, geometry.angleDeg, 1e-9)
        assertValid(retuned.assembly)

        val extended = engine.chains
            .execute(retuned, ChainCommand3D.Append(ChainStep3D.Pipe(200.0)))
            .failure()
        // The spool is closed by its second flange, so appending is refused by rule.
        assertTrue(extended is Fabrication3DError.InvalidParameter)
    }

    @Test
    fun `default workshop assembly closes as seven parametric parts and six welds`() {
        val result = calculateFlangedOffsetAssembly(FlangedOffsetAssemblyInput(50, 16, 500.0, 1_600.0))

        val assembly = result.toParametricAssembly3D(engine.parts).unwrap()

        assertEquals(7, assembly.parts.size)
        assertEquals(6, assembly.connections.size)
        assertEquals(2, assembly.freePorts().size)
        assertValid(assembly)
        assertVec(Vec3.ZERO, assembly.worldPort(PortReference3D("F1", "face")).frame.position)
        assertVec(
            Vec3(result.input.overallFaceToFaceMm, result.input.targetOffsetMm, 0.0),
            assembly.worldPort(PortReference3D("F2", "face")).frame.position,
        )
        val pipeLengths = assembly.parts
            .filter { it.definition.kind == FabricationPartKind.PIPE }
            .associate { it.code to (it.definition.geometry as StraightPipeGeometry3D).lengthMm }
        assertEquals(result.inletPipeCutMm, pipeLengths.getValue("P1"), 1e-7)
        assertEquals(result.diagonalPipeCutMm, pipeLengths.getValue("P2"), 1e-7)
        assertEquals(result.outletPipeCutMm, pipeLengths.getValue("P3"), 1e-7)
    }

    @Test
    fun `adapter closes for every catalog diameter and workshop angle`() {
        PIPE_INSTALLATION_SERIES.forEach { pipe ->
            listOf(30.0, 45.0, 60.0, 90.0).forEach { angle ->
                val result = calculateFlangedOffsetAssembly(
                    FlangedOffsetAssemblyInput(
                        dn = pipe.dn,
                        pn = 16,
                        targetOffsetMm = 1_500.0,
                        overallFaceToFaceMm = 8_000.0,
                        angleDeg = angle,
                        stockLengthMm = 12_000.0,
                    ),
                )
                val assembly = result.toParametricAssembly3D(engine.parts).unwrap()
                assertValid(assembly)
                assertVec(
                    Vec3(8_000.0, 1_500.0, 0.0),
                    assembly.worldPort(PortReference3D("F2", "face")).frame.position,
                    2e-6,
                )
            }
        }
    }
}
