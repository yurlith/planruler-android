package com.planruler.isometric

import com.planruler.model.IsoNodeId
import com.planruler.model.IsoPipeSpec
import com.planruler.model.IsoPipeSystem
import com.planruler.model.IsoProjectionStyle
import org.junit.Assert.*
import org.junit.Test

class IsoEditorCompletionTest {
    @Test fun `drag snaps all six axes in both projections without changing the grab offset`() {
        val origin = Vec3(125.0, -250.0, 2700.0)
        IsoProjectionStyle.entries.forEach { style ->
            val projection = IsoProjection.of(style)
            val pointer = projection.project(origin) + P2(15.0, -8.0)
            val drag = IsoNodeDrag(IsoNodeId("node"), origin, pointer)
            IsoAxis.entries.forEach { axis ->
                val target = drag.target(pointer + projection.project(axis.unit * 349.0), projection, 100.0)
                assertTrue("$style $axis $target", target.distanceTo(origin + axis.unit * 300.0) < 1e-8)
            }
            assertEquals(origin, drag.target(pointer, projection, 100.0))
            assertEquals(origin, drag.target(P2(Double.NaN, 0.0), projection, 100.0))
        }
    }

    @Test fun `drag preview is transient and a collapsed pipe is refused without an undo entry`() {
        val engine = IsometricEngine(IsometricEngine.newSheet("sheet", "Test", 0))
        val run = (engine.addRun(RunStart.Point(Vec3.ZERO), Vec3.X * 1000.0, IsoPipeSpec()) as IsoResult.Ok).value
        val original = engine.sheet
        val moving = IsoNodeDrag(run.endNodeId, Vec3.X * 1000.0, P2.ZERO)
        assertNotEquals(original, moving.preview(original, Vec3.X * 1500.0))
        assertEquals(original, engine.sheet)
        assertTrue(engine.moveNode(run.endNodeId, Vec3.ZERO) is IsoResult.Error)
        assertEquals(original, engine.sheet)
        assertTrue(engine.moveNode(run.endNodeId, Vec3.X * 1500.0) is IsoResult.Ok)
        assertEquals(1500.0, IsoTakeoff.of(engine.sheet).totalLengthMm, 0.0)
        engine.undo()
        assertEquals(original, engine.sheet)
        engine.redo()
        assertEquals(1500.0, IsoTakeoff.of(engine.sheet).totalLengthMm, 0.0)
    }

    @Test fun `paper layout includes all annotations and keeps scale on a large network`() {
        IsoProjectionStyle.entries.forEach { projection ->
            val engine = IsometricEngine(IsometricEngine.newSheet("sheet", "Котельная", 0, projection))
            val spec = IsoPipeSpec(IsoPipeSystem.HEATING_RETURN, 50)
            val run = (engine.addRun(RunStart.Point(Vec3.ZERO), Vec3.X * 80000.0, spec) as IsoResult.Ok).value
            engine.addRun(RunStart.Node(run.endNodeId), Vec3.Z * 2700.0, spec)
            val paper = IsoPaperDrawing.build(engine.sheet)
            assertTrue(paper.scene.texts.isNotEmpty())
            assertTrue(paper.bounds.width / paper.scale <= paper.widthMm - 20.0 + 1e-7)
            assertTrue(paper.bounds.height / paper.scale <= paper.heightMm - 35.0 + 1e-7)
            assertEquals(2.5, paper.style.textHeightMm / paper.scale, 1e-9)
            paper.scene.strokes.flatMap { it.points }.forEach { assertTrue(paper.bounds.contains(it)) }
        }
    }
}
