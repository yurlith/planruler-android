package com.planruler.feature.pipecalculator

import com.planruler.fabrication3d.ChainCommand3D
import com.planruler.fabrication3d.ChainPath3D
import com.planruler.fabrication3d.ChainPlan3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.Fabrication3DResult
import com.planruler.fabrication3d.catalog.toAssemblyProfile3D
import com.planruler.fabrication3d.engine.DefaultFabrication3DEngine
import com.planruler.pipecalculator.FlangedOffsetAssemblyInput
import com.planruler.pipecalculator.calculateFlangedOffsetAssembly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectManipulation3DTest {
    private val engine = DefaultFabrication3DEngine()
    private val profile = calculateFlangedOffsetAssembly(
        FlangedOffsetAssemblyInput(dn = 50, pn = 16, targetOffsetMm = 500.0, overallFaceToFaceMm = 1_600.0),
    ).toAssemblyProfile3D()

    @Test
    fun `pipe endpoint drag produces a snapped replacement`() {
        val editor = editor(ChainPlan3D(steps = listOf(ChainStep3D.Pipe(300.0))))
        val spec = directHandleSpecs(editor, requireNotNull(editor.partIdAt(ChainPath3D(listOf(0))))).single()
        val projector = projector(editor)
        val drag = projector.project(spec.anchorWorld + spec.dragAxisWorld * 100.0).screen -
            projector.project(spec.anchorWorld).screen

        val edit = requireNotNull(directEditForDrag(spec, projector, drag))

        assertEquals(
            ChainCommand3D.Replace(ChainPath3D(listOf(0)), ChainStep3D.Pipe(400.0)),
            edit.command,
        )
        assertEquals("L 400.0 mm", edit.valueLabel)
    }

    @Test
    fun `elbow exposes angle and roll handles and both produce replacements`() {
        val editor = editor(
            ChainPlan3D(steps = listOf(ChainStep3D.Pipe(300.0), ChainStep3D.Elbow(45.0, 0.0))),
        )
        val elbowPath = ChainPath3D(listOf(1))
        val specs = directHandleSpecs(editor, requireNotNull(editor.partIdAt(elbowPath)))
        val projector = projector(editor)
        assertEquals(setOf(DirectHandleKind3D.ANGLE, DirectHandleKind3D.ROLL), specs.map { it.kind }.toSet())

        fun edit(kind: DirectHandleKind3D, degrees: Double): DirectEdit3D {
            val spec = specs.single { it.kind == kind }
            val arc = spec.radiusMm * Math.toRadians(degrees)
            val drag = projector.project(spec.anchorWorld + spec.dragAxisWorld * arc).screen -
                projector.project(spec.anchorWorld).screen
            return requireNotNull(directEditForDrag(spec, projector, drag))
        }

        assertEquals(
            ChainCommand3D.Replace(elbowPath, ChainStep3D.Elbow(60.0, 0.0)),
            edit(DirectHandleKind3D.ANGLE, 15.0).command,
        )
        assertEquals(
            ChainCommand3D.Replace(elbowPath, ChainStep3D.Elbow(45.0, 30.0)),
            edit(DirectHandleKind3D.ROLL, 30.0).command,
        )
    }

    @Test
    fun `camera fallback chooses a preset where an edge-on axis is readable`() {
        val editor = editor(ChainPlan3D(steps = listOf(ChainStep3D.Pipe(300.0))))
        val mesh = engine.mesh.build(editor.assembly).value()

        val preset = bestControllablePreset(
            mesh = mesh,
            width = 1080f,
            height = 1400f,
            zoom = 1.15f,
            perspective = false,
            origin = editor.plan.start.position,
            axis = com.planruler.fabrication3d.Vec3.UNIT_Z,
        )
        val projector = SceneProjector3D(mesh, 1080f, 1400f, preset.yaw, preset.pitch, 1.15f, false)

        assertTrue(
            requireNotNull(
                DragProjection3D.axisPixelsPerMm(projector, editor.plan.start.position, com.planruler.fabrication3d.Vec3.UNIT_Z),
            ) >= DragProjection3D.MIN_PIXELS_PER_MM,
        )
    }

    @Test
    fun `selecting the start flange exposes three position axes`() {
        val editor = editor(ChainPlan3D(steps = listOf(ChainStep3D.Pipe(300.0))))
        val startPartId = editor.assembly.parts.first().id
        val specs = directHandleSpecs(editor, startPartId)
        val x = specs.single { it.kind == DirectHandleKind3D.START_X }
        val projector = projector(editor)
        val drag = projector.project(x.anchorWorld + x.dragAxisWorld * 100.0).screen -
            projector.project(x.anchorWorld).screen

        val edit = requireNotNull(directEditForDrag(x, projector, drag))

        assertEquals(
            ChainCommand3D.MoveStart(editor.plan.start.copy(position = com.planruler.fabrication3d.Vec3(100.0, 0.0, 0.0))),
            edit.command,
        )
        assertEquals(3, specs.size)
    }

    private fun editor(plan: ChainPlan3D) = engine.chains.fromPlan(profile, plan).value()

    private fun projector(editor: com.planruler.fabrication3d.ChainEditorState3D): SceneProjector3D {
        val mesh = engine.mesh.build(editor.assembly).value()
        return SceneProjector3D(mesh, 1080f, 1400f, -32f, 24f, 1.15f, false)
    }

    private fun <T> Fabrication3DResult<T>.value(): T = when (this) {
        is Fabrication3DResult.Ok -> value
        is Fabrication3DResult.Failure -> error("fixture failed: $error")
    }
}
