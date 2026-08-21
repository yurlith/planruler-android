package com.planruler.feature.pipecalculator

import com.planruler.fabrication3d.FabricationPartKind
import com.planruler.fabrication3d.RouteRequest3D
import com.planruler.fabrication3d.RouteTerminal3D
import com.planruler.fabrication3d.StraightPipeGeometry3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.catalog.toAssemblyProfile3D
import com.planruler.fabrication3d.engine.DefaultFabrication3DEngine
import com.planruler.fabrication3d.getOrNull
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobId
import com.planruler.model.InstallationJobInput
import com.planruler.model.InstallationTaskType
import com.planruler.model.AppLanguage
import com.planruler.pipecalculator.FlangedOffsetAssemblyInput
import com.planruler.pipecalculator.calculateFlangedOffsetAssembly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssemblyDrawingProjectionTest {
    private val engine = DefaultFabrication3DEngine()
    private val profile = calculateFlangedOffsetAssembly(
        FlangedOffsetAssemblyInput(50, 16, 500.0, 4_000.0),
    ).toAssemblyProfile3D()
    private val assembly = requireNotNull(
        engine.router.solve(
            RouteRequest3D(
                profile = profile,
                target = RouteTerminal3D(Vec3(4_000.0, 500.0, 300.0), Vec3.UNIT_X),
                preferredElbowAngleDeg = 45.0,
                minimumStraightMm = 100.0,
            ),
        ).getOrNull(),
    ).assembly

    @Test fun `every required working view is generated from one assembly`() {
        assertEquals(5, AssemblyDrawingView.entries.size)
        AssemblyDrawingView.entries.forEach { view ->
            val drawing = AssemblyDrawingGenerator.generate(assembly, view, AssemblyDrawingLayer.ALL)
            assertTrue(view.name, drawing.strokes.isNotEmpty())
            assertTrue(view.name, drawing.bounds.width > 0.0)
            assertTrue(view.name, drawing.bounds.height > 0.0)
            assertTrue(view.name, drawing.dimensions.all { it.label.isNotBlank() && it.lane > 0 })
        }
    }

    @Test fun `drawing layers expose installation cuts and fittings without changing geometry`() {
        val all = AssemblyDrawingGenerator.generate(assembly, AssemblyDrawingView.ISOMETRIC, AssemblyDrawingLayer.ALL)
        val installation = AssemblyDrawingGenerator.generate(
            assembly,
            AssemblyDrawingView.ISOMETRIC,
            AssemblyDrawingLayer.INSTALLATION,
        )
        val cutting = AssemblyDrawingGenerator.generate(assembly, AssemblyDrawingView.ISOMETRIC, AssemblyDrawingLayer.CUTTING)
        val details = AssemblyDrawingGenerator.generate(assembly, AssemblyDrawingView.ISOMETRIC, AssemblyDrawingLayer.DETAILS)

        assertEquals(assembly.parts.map { it.id }.toSet(), all.strokes.map { it.partId }.toSet())
        assertTrue(cutting.strokes.all { it.kind == FabricationPartKind.PIPE })
        assertTrue(cutting.dimensions.all { it.label.contains("CUT") })
        assertTrue(details.strokes.all { it.kind != FabricationPartKind.PIPE })
        assertFalse(installation.dimensions.any { it.partId != null })
        assertTrue(installation.welds.isNotEmpty())
    }

    @Test fun `spatial route receives independent X Y and Z dimensions`() {
        val drawing = AssemblyDrawingGenerator.generate(assembly, AssemblyDrawingView.ISOMETRIC, AssemblyDrawingLayer.ALL)
        val labels = drawing.dimensions.map { it.label }
        assertTrue(labels.any { it.startsWith("X ") })
        assertTrue(labels.any { it.startsWith("Y ") })
        assertTrue(labels.any { it.startsWith("Z ") })
    }

    @Test fun `end view produces real circular end geometry`() {
        val drawing = AssemblyDrawingGenerator.generate(assembly, AssemblyDrawingView.END, AssemblyDrawingLayer.ALL)
        assertTrue(drawing.circles.isNotEmpty())
        assertTrue(drawing.circles.all { it.radiusMm > 0.0 })
    }

    @Test fun `the same part can be picked and kept selected between views`() {
        val drawing = AssemblyDrawingGenerator.generate(assembly, AssemblyDrawingView.ISOMETRIC, AssemblyDrawingLayer.ALL)
        val viewport = DrawingViewport2D(drawing.bounds, 0.0, 0.0, 900.0, 600.0, 52.0)
        val stroke = drawing.strokes.first()
        val first = viewport.map(stroke.points.first())
        val last = viewport.map(stroke.points.last())
        val tap = DrawingPoint2D((first.x + last.x) / 2.0, (first.y + last.y) / 2.0)

        assertEquals(stroke.partId, pickDrawingPart(drawing, viewport, tap))
        val sameIdInTop = AssemblyDrawingGenerator
            .generate(assembly, AssemblyDrawingView.TOP, AssemblyDrawingLayer.ALL)
            .strokes
            .any { it.partId == stroke.partId }
        assertTrue(sameIdInTop)
        assertNotNull(assembly.partOrNull(stroke.partId))
    }

    @Test fun `tee and reducer templates use the same projection pipeline`() {
        listOf(InstallationTaskType.TEE_BRANCH, InstallationTaskType.REDUCER).forEach { task ->
            val request = installerRouteRequest(
                InstallationJob(
                    id = InstallationJobId(task.name),
                    name = task.name,
                    taskType = task,
                    input = InstallationJobInput(nominalDiameter = 50, alongMm = 4_000.0, lateralOffsetMm = 600.0),
                    createdAtEpochMs = 1L,
                    modifiedAtEpochMs = 1L,
                ),
            )
            val editor = requireNotNull(engine.chains.fromPlan(profile, requireNotNull(request.manualTemplate)).getOrNull())
            val drawing = AssemblyDrawingGenerator.generate(
                editor.assembly,
                AssemblyDrawingView.ISOMETRIC,
                AssemblyDrawingLayer.ALL,
            )
            assertEquals(editor.assembly.parts.map { it.id }.toSet(), drawing.strokes.map { it.partId }.toSet())
            assertTrue(drawing.dimensions.isNotEmpty())
        }
    }

    @Test fun `field csv contains cuts materials and checked audit`() {
        val csv = AssemblyFieldCsvWriter.csvText(
            assembly = assembly,
            jobName = "=unsafe job",
            language = AppLanguage.ENGLISH,
            checkedBy = "Site foreman",
            checkedAtEpochMs = 1_700_000_000_000L,
        )

        assertTrue(csv.startsWith("Installation job;'=unsafe job"))
        assertTrue(csv.contains("Cuts CSV").not())
        assertTrue(csv.contains("Code;Type;Description;Quantity;Cut, mm"))
        assertTrue(csv.contains("MATERIALS"))
        assertTrue(csv.contains("CHECKED: Site foreman"))
        assertEquals(assembly.parts.size, csv.lineSequence().count { it.contains(";PIPE;") || it.contains(";ELBOW;") || it.contains(";FLANGE;") || it.contains(";TEE;") || it.contains(";REDUCER;") || it.contains(";CAP;") })
    }

    @Test fun `installer schedule links every part to a readable cut and material total`() {
        val rows = buildAssemblyPartFieldRows(assembly, AppLanguage.ENGLISH)
        val summary = buildAssemblyMaterialFieldSummary(assembly)
        val expectedPipeLength = assembly.parts.sumOf {
            (it.definition.geometry as? StraightPipeGeometry3D)?.lengthMm ?: 0.0
        }

        assertEquals(assembly.parts.map { it.id }, rows.map { it.partId })
        assertTrue(rows.filter { it.kind == FabricationPartKind.PIPE }.all { it.primaryValue.startsWith("CUT ") })
        assertTrue(rows.filter { assembly.connectionsOf(it.partId).isNotEmpty() }.all { it.connectedCodes.isNotEmpty() })
        assertEquals(assembly.pipeCount(), summary.pipeCount)
        assertEquals(expectedPipeLength, summary.totalPipeLengthMm, 1e-6)
        assertEquals(assembly.parts.size - assembly.pipeCount(), summary.fittingCounts.values.sum())
        assertTrue(summary.label(AppLanguage.ENGLISH).contains("Pipe:"))
    }
}
