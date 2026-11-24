package com.planruler.isometric

import com.planruler.model.IsoComponentKind
import com.planruler.model.IsoPipeSpec
import com.planruler.model.IsoPipeSystem
import com.planruler.model.IsoProjectionStyle
import com.planruler.model.IsometricSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Takeoff, drawing, picking and export of one small heating riser. */
class IsoDrawingTest {
    private var ids = 0
    private val dn25 = IsoPipeSpec(IsoPipeSystem.HEATING_SUPPLY, 25, "Steel")
    private val dn20 = IsoPipeSpec(IsoPipeSystem.HEATING_SUPPLY, 20, "Steel")

    private fun <T> IsoResult<T>.ok(): T = (this as? IsoResult.Ok)?.value ?: throw AssertionError("Expected success: $this")

    /**
     * Main DN25 along X (4 m), a 45° offset, a DN20 riser of 2.7 m from its middle with a
     * radiator branch and a valve on the main.
     */
    private fun riser(): Pair<IsometricEngine, RunAdded> {
        val engine = IsometricEngine(
            IsometricEngine.newSheet("s", "Riser", 0L),
            clock = { 1L },
            idGenerator = { "n${ids++}" },
        )
        val main = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(4_000.0, 0.0, 0.0), dn25).ok()
        engine.addComponent(IsoComponentKind.BALL_VALVE, main.pipeId, 0.15).ok()
        val offset = engine.addRun(RunStart.Node(main.endNodeId), Vec3(500.0, 500.0, 0.0), dn25).ok()
        engine.addRun(RunStart.Node(offset.endNodeId), Vec3(1_000.0, 0.0, 0.0), dn25).ok()
        val riser = engine.addRun(RunStart.OnPipe(main.pipeId, 2_000.0), Vec3(0.0, 0.0, 2_700.0), dn20).ok()
        val branch = engine.addRun(RunStart.Node(riser.endNodeId), Vec3(0.0, 600.0, 0.0), dn20).ok()
        engine.addComponentAtNode(IsoComponentKind.RADIATOR, branch.endNodeId).ok()
        return engine to riser
    }

    @Test
    fun `bill of materials counts runs, fittings and components`() {
        val (engine, _) = riser()
        val bom = IsoTakeoff.of(engine.sheet)
        val main = bom.pipes.first { it.nominalDiameter == 25 }
        val riser = bom.pipes.first { it.nominalDiameter == 20 }
        assertEquals(4_000.0 + 707.1068 + 1_000.0, main.lengthMm, 1e-3)
        assertEquals(0.0, main.verticalMm, 1e-9)
        assertEquals(2_700.0 + 600.0, riser.lengthMm, 1e-9)
        assertEquals(2_700.0, riser.verticalMm, 1e-9)

        val elbows = bom.fittings.filter { it.kind == IsoFittingKind.ELBOW }
        assertEquals(2, elbows.filter { it.standardAngleDeg == 45 }.sumOf { it.count })
        assertEquals(1, elbows.filter { it.standardAngleDeg == 90 }.sumOf { it.count })
        val tee = bom.fittings.single { it.kind == IsoFittingKind.TEE }
        assertEquals(25, tee.nominalDiameter)
        assertEquals(20, tee.branchDiameter)
        assertEquals(2, bom.fittings.filter { it.kind == IsoFittingKind.END }.sumOf { it.count })
        assertEquals(1, bom.components.single { it.kind == IsoComponentKind.BALL_VALVE }.count)
        assertEquals(25, bom.components.single { it.kind == IsoComponentKind.BALL_VALVE }.nominalDiameter)
        assertEquals(1, bom.components.single { it.kind == IsoComponentKind.RADIATOR }.count)
        assertEquals(3, bom.elbowCount)
        assertEquals(1, bom.teeCount)
    }

    @Test
    fun `scene draws each run once with a true-length dimension`() {
        val (engine, _) = riser()
        val scene = IsoSceneBuilder.build(engine.sheet, IsoSceneStyle.forPaper(20.0))
        assertEquals(engine.sheet.pipes.size, scene.strokes.count { it.role == IsoRole.PIPE })
        val dimensions = scene.texts.filter { it.role == IsoRole.DIMENSION }.map { it.text }
        assertTrue("2700" in dimensions)
        assertTrue("707" in dimensions)
        assertTrue(scene.texts.any { it.role == IsoRole.ELEVATION && it.text == "+2.700" })
        assertTrue(scene.dots.any { it.role == IsoRole.FITTING && it.filled })
        scene.texts.forEach { assertTrue(it.angleDeg > -90.0 && it.angleDeg <= 90.0) }
        assertNotNull(scene.bounds)
        assertTrue(scene.strokes.any { it.role == IsoRole.SYMBOL })
    }

    @Test
    fun `hidden annotations are not drawn`() {
        val (engine, _) = riser()
        engine.setDisplay(dimensions = false, elevations = false, systemCodes = false)
        val scene = IsoSceneBuilder.build(engine.sheet, IsoSceneStyle.forPaper(20.0))
        assertFalse(scene.texts.any { it.role == IsoRole.DIMENSION || it.role == IsoRole.ELEVATION || it.role == IsoRole.TAG })
        assertFalse(scene.strokes.any { it.role == IsoRole.DIMENSION })
    }

    @Test
    fun `a selected run and the preview are highlighted`() {
        val (engine, riser) = riser()
        val style = IsoSceneStyle.forPaper(20.0)
        val preview = RunPlan(Vec3.ZERO, IsoProjection.of(IsoProjectionStyle.ISOMETRIC).runDirections.first(), 300.0, Vec3(300.0, 0.0, 0.0))
        val scene = IsoSceneBuilder.build(engine.sheet, style, selection = IsoSelection(pipeIds = setOf(riser.pipeId)), preview = preview)
        assertEquals(style.selectionArgb, scene.strokes.first { it.role == IsoRole.PIPE && it.pipeId == riser.pipeId }.colorArgb)
        assertTrue(scene.strokes.any { it.role == IsoRole.PREVIEW && it.dashed })
        assertTrue(scene.texts.any { it.role == IsoRole.PREVIEW && it.text == "300" })
    }

    @Test
    fun `readable angles never turn text upside down`() {
        assertEquals(0.0, IsoSceneBuilder.readableAngle(P2(-1.0, 0.0)), 1e-9)
        assertEquals(30.0, IsoSceneBuilder.readableAngle(P2(-0.866, -0.5)), 0.01)
        assertEquals(90.0, IsoSceneBuilder.readableAngle(P2(0.0, 1.0)), 1e-9)
        assertEquals(90.0, IsoSceneBuilder.readableAngle(P2(0.0, -1.0)), 1e-9)
    }

    @Test
    fun `a touch picks a joint before the run under it`() {
        val (engine, riser) = riser()
        val projection = IsoProjection.of(engine.sheet.projection)
        val top = engine.sheet.nodes.first { it.id == riser.endNodeId }
        val onJoint = IsoHitTester.hit(engine.sheet, projection.project(top.position) + P2(5.0, 5.0), 40.0) as IsoHit.Node
        assertEquals(riser.endNodeId, onJoint.id)
        assertEquals(kotlin.math.hypot(5.0, 5.0), onJoint.screenDistanceMm, 1e-9)
        val middle = projection.project(Vec3(2_000.0, 0.0, 1_000.0))
        val onRiser = IsoHitTester.hit(engine.sheet, middle + P2(10.0, 0.0), 40.0) as IsoHit.Pipe
        assertEquals(riser.pipeId, onRiser.id)
        assertEquals(1_000.0, onRiser.distanceAlongMm, 1e-6)
        assertNull(IsoHitTester.hit(engine.sheet, P2(50_000.0, 50_000.0), 40.0))
    }

    @Test
    fun `a stroke rounds its length and snaps onto reachable joints and runs`() {
        val (engine, riser) = riser()
        val projection = IsoProjection.of(engine.sheet.projection)
        val start = Vec3(0.0, 2_000.0, 0.0)
        val free = requireNotNull(
            IsoRunPlanner.plan(engine.sheet, projection, start, projection.project(start + Vec3(1_234.0, 0.0, 0.0)), RunPlanOptions(lengthStepMm = 50.0)),
        )
        assertEquals(1_250.0, free.lengthMm, 1e-9)
        assertNull(free.joinNodeId)

        // From above the main, a stroke down near the main tees into it exactly.
        val above = Vec3(3_000.0, 0.0, 1_500.0)
        val tee = requireNotNull(
            IsoRunPlanner.plan(
                engine.sheet,
                projection,
                above,
                projection.project(Vec3(3_000.0, 0.0, 30.0)),
                RunPlanOptions(lengthStepMm = 100.0, joinToleranceMm = 80.0),
            ),
        )
        assertEquals(1_500.0, tee.lengthMm, 1e-6)
        assertNotNull(tee.joinPipeId)

        // A stroke towards the riser top ends exactly on that joint.
        val beside = Vec3(2_000.0, -900.0, 2_700.0)
        val join = requireNotNull(
            IsoRunPlanner.plan(
                engine.sheet,
                projection,
                beside,
                projection.project(Vec3(2_000.0, -40.0, 2_700.0)),
                RunPlanOptions(lengthStepMm = 100.0, joinToleranceMm = 80.0),
            ),
        )
        assertEquals(riser.endNodeId, join.joinNodeId)
        assertEquals(900.0, join.lengthMm, 1e-6)
    }

    @Test
    fun `svg and dxf exports carry the drawing`() {
        val (engine, _) = riser()
        engine.rename("Стояк & ветка").ok()
        val style = IsoSceneStyle.forPaper(IsoExport.paperScaleFor(engine.sheet))
        val scene = IsoSceneBuilder.build(engine.sheet, style)
        val svg = IsoExport.svg(scene, style, engine.sheet.name)
        assertTrue(svg.startsWith("<?xml"))
        assertTrue("<svg" in svg && "</svg>" in svg)
        assertTrue("Стояк &amp; ветка" in svg)
        assertTrue(Regex("<polyline ").findAll(svg).count() >= engine.sheet.pipes.size)
        assertTrue(">2700<" in svg)

        val dxf = IsoExport.dxf(scene, style)
        assertTrue(dxf.startsWith("0\nSECTION\n2\nHEADER"))
        assertTrue(dxf.trimEnd().endsWith("0\nEOF"))
        assertTrue("\nLINE\n" in dxf && "\nTEXT\n" in dxf && "\nCIRCLE\n" in dxf)
        assertFalse(dxf.contains(','))
    }

    @Test
    fun `cyrillic text is escaped for R12 DXF`() {
        val style = IsoSceneStyle.forPaper(10.0)
        val scene = IsoScene(emptyList(), listOf(IsoText("Т1", P2.ZERO, 0.0, 25.0, 0xFF000000, IsoRole.TAG)), emptyList(), Rect2(0.0, 0.0, 1.0, 1.0))
        assertTrue("\\U+0422" + "1" in IsoExport.dxf(scene, style))
    }

    @Test
    fun `paper scale is a standard scale that fits A3`() {
        val (engine, _) = riser()
        val scale = IsoExport.paperScaleFor(engine.sheet)
        assertTrue(scale in listOf(10.0, 20.0, 25.0, 50.0))
        assertEquals(20.0, IsoExport.paperScaleFor(IsometricSheet(com.planruler.model.IsometricSheetId("e"), "e", createdAtEpochMs = 0, modifiedAtEpochMs = 0)), 1e-9)
    }
}
