package com.planruler.feature.pipecalculator

import androidx.compose.ui.geometry.Offset
import com.planruler.fabrication3d.AssemblyMesh3D
import com.planruler.fabrication3d.ChainPlan3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.catalog.toAssemblyProfile3D
import com.planruler.fabrication3d.engine.DefaultFabrication3DEngine
import com.planruler.pipecalculator.FlangedOffsetAssemblyInput
import com.planruler.pipecalculator.calculateFlangedOffsetAssembly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Turning a finger drag into millimetres is what makes a handle feel right, and it is pure
 * arithmetic over the camera, so it is checked here rather than through the rendered scene.
 */
class DragProjection3DTest {
    private val engine = DefaultFabrication3DEngine()

    private val mesh: AssemblyMesh3D = run {
        val profile = calculateFlangedOffsetAssembly(
            FlangedOffsetAssemblyInput(dn = 50, pn = 16, targetOffsetMm = 500.0, overallFaceToFaceMm = 1_600.0),
        ).toAssemblyProfile3D()
        val editor = engine.chains.fromPlan(
            profile,
            ChainPlan3D(steps = listOf(ChainStep3D.Pipe(600.0), ChainStep3D.Flange())),
        )
        val assembly = when (editor) {
            is com.planruler.fabrication3d.Fabrication3DResult.Ok -> editor.value.assembly
            is com.planruler.fabrication3d.Fabrication3DResult.Failure -> error("fixture: ${editor.error}")
        }
        when (val built = engine.mesh.build(assembly)) {
            is com.planruler.fabrication3d.Fabrication3DResult.Ok -> built.value
            is com.planruler.fabrication3d.Fabrication3DResult.Failure -> error("fixture: ${built.error}")
        }
    }

    private fun projector(
        yaw: Float,
        pitch: Float,
        perspective: Boolean = true,
        zoom: Float = 1.15f,
    ) = SceneProjector3D(
        mesh = mesh,
        width = 1080f,
        height = 1400f,
        yawDeg = yaw,
        pitchDeg = pitch,
        zoom = zoom,
        perspective = perspective,
    )

    /** The four preset viewpoints the viewport offers, plus both projections. */
    private val viewpoints = listOf(
        "isometric" to (-32f to 24f),
        "front" to (0f to 0f),
        "top" to (0f to 88f),
        "right" to (88f to 0f),
    )

    /**
     * Orthographic scale does not change with depth, so the reading there is exact. The
     * perspective case is linearised around the handle and is covered by the frame-by-frame
     * test below, which is the shape a real gesture actually has.
     */
    @Test
    fun `an orthographic drag along the axis reads back exactly`() {
        viewpoints.forEach { (name, angles) ->
            val projector = projector(angles.first, angles.second, perspective = false)
            val origin = Vec3(0.0, 0.0, 0.0)
            val axis = Vec3.UNIT_X

            val screenDelta = projector.project(origin + axis * 100.0).screen -
                projector.project(origin).screen
            val drag = DragProjection3D.alongAxis(projector, origin, axis, screenDelta)
                ?: return@forEach // Edge-on in this view; the refusal is asserted separately.

            assertEquals("$name misread a 100 mm drag", 100.0, drag.millimetres, 0.5)
        }
    }

    @Test
    fun `dragging against the axis reads back as a negative length`() {
        val projector = projector(-32f, 24f)
        val origin = Vec3.ZERO
        val forward = projector.project(origin + Vec3.UNIT_X * 100.0).screen - projector.project(origin).screen

        val drag = requireNotNull(
            DragProjection3D.alongAxis(projector, origin, Vec3.UNIT_X, -forward),
        )

        assertEquals(-100.0, drag.millimetres, 5.0)
    }

    /** Frame-sized steps are what a real gesture produces, and there the model is tight. */
    @Test
    fun `a drag applied frame by frame stays accurate under perspective`() {
        val projector = projector(-32f, 24f, perspective = true)
        val origin = Vec3.ZERO
        val fullStep = projector.project(origin + Vec3.UNIT_X * 100.0).screen - projector.project(origin).screen
        val frames = 40
        val perFrame = fullStep / frames.toFloat()

        var travelled = 0.0
        var cursor = origin
        repeat(frames) {
            val step = requireNotNull(DragProjection3D.alongAxis(projector, cursor, Vec3.UNIT_X, perFrame))
            travelled += step.millimetres
            cursor += Vec3.UNIT_X * step.millimetres
        }

        assertEquals("frame-by-frame accumulation drifted", 100.0, travelled, 0.5)
    }

    @Test
    fun `movement across the axis does not change its length`() {
        val projector = projector(0f, 0f, perspective = false)
        val origin = Vec3.ZERO
        val alongScreen = projector.project(origin + Vec3.UNIT_X * 100.0).screen - projector.project(origin).screen
        val across = Offset(-alongScreen.y, alongScreen.x)

        val drag = requireNotNull(DragProjection3D.alongAxis(projector, origin, Vec3.UNIT_X, across))

        assertEquals("a sideways drag moved the length", 0.0, drag.millimetres, 1e-6)
    }

    /** An axis pointing at the camera would turn a pixel into metres; it must be refused. */
    @Test
    fun `an axis pointing at the camera is refused rather than guessed`() {
        // Front view looks down -Z, so the Z axis collapses to almost nothing on screen.
        val projector = projector(0f, 0f, perspective = false)

        val drag = DragProjection3D.alongAxis(projector, Vec3.ZERO, Vec3.UNIT_Z, Offset(120f, 0f))

        assertNull("an edge-on axis produced a length anyway", drag)
    }

    @Test
    fun `a zero length axis is refused`() {
        val projector = projector(-32f, 24f)

        assertNull(DragProjection3D.alongAxis(projector, Vec3.ZERO, Vec3.ZERO, Offset(50f, 50f)))
    }

    @Test
    fun `the reported scale says how many pixels one millimetre covers`() {
        val projector = projector(0f, 0f, perspective = false)
        val origin = Vec3.ZERO
        val hundred = projector.project(origin + Vec3.UNIT_X * 100.0).screen - projector.project(origin).screen
        val expected = kotlin.math.sqrt((hundred.x * hundred.x + hundred.y * hundred.y).toDouble()) / 100.0

        val drag = requireNotNull(DragProjection3D.alongAxis(projector, origin, Vec3.UNIT_X, hundred))

        assertEquals(expected, drag.pixelsPerMm.toDouble(), 1e-3)
    }

    @Test
    fun `zooming in means the same finger travel is fewer millimetres`() {
        val origin = Vec3.ZERO
        val near = projector(0f, 0f, perspective = false, zoom = 4f)
        val far = projector(0f, 0f, perspective = false, zoom = 1f)
        val drag = Offset(180f, 0f)

        val nearMm = requireNotNull(DragProjection3D.alongAxis(near, origin, Vec3.UNIT_X, drag)).millimetres
        val farMm = requireNotNull(DragProjection3D.alongAxis(far, origin, Vec3.UNIT_X, drag)).millimetres

        assertTrue("zooming in did not make the drag finer: $nearMm vs $farMm", abs(nearMm) < abs(farMm))
    }

    @Test
    fun `lengths snap to the step a fitter marks out`() {
        assertEquals(300.0, DragProjection3D.snapLength(298.4, 5.0), 1e-9)
        assertEquals(300.0, DragProjection3D.snapLength(301.9, 5.0), 1e-9)
        assertEquals(325.0, DragProjection3D.snapLength(323.0, 25.0), 1e-9)
        // Below one step the exact value is kept, so a short stub is still reachable.
        assertEquals(3.4, DragProjection3D.snapLength(3.4, 5.0), 1e-9)
        assertEquals(123.456, DragProjection3D.snapLength(123.456, 0.0), 1e-9)
    }

    @Test
    fun `angles snap to catalog values only when the finger lands near one`() {
        val catalog = listOf(11.25, 15.0, 22.5, 30.0, 45.0, 60.0, 90.0)

        assertEquals(45.0, DragProjection3D.snapAngle(43.2, catalog), 1e-9)
        assertEquals(90.0, DragProjection3D.snapAngle(88.5, catalog), 1e-9)
        // Sign is preserved so a bend keeps its direction.
        assertEquals(-45.0, DragProjection3D.snapAngle(-44.0, catalog), 1e-9)
        // Far from any catalog angle the exact value stands, so custom bends stay possible.
        assertEquals(37.0, DragProjection3D.snapAngle(37.0, catalog), 1e-9)
    }
}
