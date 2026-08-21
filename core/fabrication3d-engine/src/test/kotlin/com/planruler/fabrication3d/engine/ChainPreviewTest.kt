package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.ChainCommand3D
import com.planruler.fabrication3d.ChainPath3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.EditorAction3D
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.StraightPipeGeometry3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A drag issues one edit per frame. Without a preview path the undo stack would take an
 * entry per frame, so "undo" would step back a fraction of a millimetre rather than undo
 * the drag. These tests pin that contract.
 */
class ChainPreviewTest {
    private val profile = workshopProfile()

    private fun editorWithPipe() = engine.chains.create(profile).unwrap()
        .appendAll(ChainStep3D.Pipe(300.0), ChainStep3D.Elbow(45.0, 0.0), ChainStep3D.Pipe(240.0))

    private val firstPipe = ChainPath3D(listOf(0))

    private fun pipeLengthOf(state: com.planruler.fabrication3d.ChainEditorState3D, path: ChainPath3D): Double {
        val id = requireNotNull(state.partIdAt(path))
        return (state.assembly.part(id).definition.geometry as StraightPipeGeometry3D).lengthMm
    }

    @Test
    fun `a whole drag becomes a single undo entry`() {
        val editor = editorWithPipe()
        val historyBefore = editor.undoStack.size

        // Sixty frames of a drag, as a real gesture would produce.
        var dragging = editor
        (1..60).forEach { frame ->
            dragging = engine.chains
                .preview(dragging, ChainCommand3D.Replace(firstPipe, ChainStep3D.Pipe(300.0 + frame)))
                .unwrap()
            assertTrue("frame $frame left no preview open", dragging.isPreviewing)
            assertEquals("frame $frame recorded history", historyBefore, dragging.undoStack.size)
        }

        val committed = engine.chains.commitPreview(dragging).unwrap()

        assertFalse(committed.isPreviewing)
        assertEquals(historyBefore + 1, committed.undoStack.size)
        assertEquals(360.0, pipeLengthOf(committed, firstPipe), 1e-9)

        // One undo returns the pipe to where the drag began, not to the previous frame.
        val undone = engine.chains.undo(committed).unwrap()
        assertEquals(300.0, pipeLengthOf(undone, firstPipe), 1e-9)
        assertValid(undone.assembly)
    }

    @Test
    fun `cancelling a drag restores the recipe it started from`() {
        val editor = editorWithPipe()
        val historyBefore = editor.undoStack.size

        var dragging = editor
        listOf(340.0, 380.0, 420.0).forEach { length ->
            dragging = engine.chains
                .preview(dragging, ChainCommand3D.Replace(firstPipe, ChainStep3D.Pipe(length)))
                .unwrap()
        }

        val cancelled = engine.chains.cancelPreview(dragging).unwrap()

        assertFalse(cancelled.isPreviewing)
        assertEquals("cancelling wrote history", historyBefore, cancelled.undoStack.size)
        assertEquals(300.0, pipeLengthOf(cancelled, firstPipe), 1e-9)
        assertEquals(editor.plan, cancelled.plan)
        assertValid(cancelled.assembly)
    }

    @Test
    fun `a drag that ends where it began costs no history`() {
        val editor = editorWithPipe()
        val historyBefore = editor.undoStack.size

        val dragged = engine.chains
            .preview(editor, ChainCommand3D.Replace(firstPipe, ChainStep3D.Pipe(420.0)))
            .unwrap()
        val backAgain = engine.chains
            .preview(dragged, ChainCommand3D.Replace(firstPipe, ChainStep3D.Pipe(300.0)))
            .unwrap()

        val committed = engine.chains.commitPreview(backAgain).unwrap()

        assertFalse(committed.isPreviewing)
        assertEquals(historyBefore, committed.undoStack.size)
    }

    /** A handle must stop at the rule, not tear the model down. */
    @Test
    fun `a preview refused by the rules leaves the last accepted one standing`() {
        val editor = editorWithPipe()
        val dragged = engine.chains
            .preview(editor, ChainCommand3D.Replace(firstPipe, ChainStep3D.Pipe(400.0)))
            .unwrap()

        val refused = engine.chains
            .preview(dragged, ChainCommand3D.Replace(firstPipe, ChainStep3D.Pipe(0.5)))
            .failure()

        assertTrue(refused is Fabrication3DError.InvalidParameter)
        assertEquals(400.0, pipeLengthOf(dragged, firstPipe), 1e-9)
        assertTrue(dragged.isPreviewing)
    }

    @Test
    fun `committing or cancelling without a gesture is refused, not silently accepted`() {
        val editor = editorWithPipe()

        assertEquals(
            Fabrication3DError.NothingToDo(EditorAction3D.UPDATE),
            engine.chains.commitPreview(editor).failure(),
        )
        assertEquals(
            Fabrication3DError.NothingToDo(EditorAction3D.UPDATE),
            engine.chains.cancelPreview(editor).failure(),
        )
    }

    /** A discrete edit during a gesture must settle it, never discard it. */
    @Test
    fun `executing during a drag keeps the drag as its own history entry`() {
        val editor = editorWithPipe()
        val historyBefore = editor.undoStack.size

        val dragging = engine.chains
            .preview(editor, ChainCommand3D.Replace(firstPipe, ChainStep3D.Pipe(500.0)))
            .unwrap()
        val afterAppend = engine.chains
            .execute(dragging, ChainCommand3D.Append(ChainStep3D.Pipe(120.0)))
            .unwrap()

        assertFalse(afterAppend.isPreviewing)
        // One entry for the drag, one for the append.
        assertEquals(historyBefore + 2, afterAppend.undoStack.size)
        assertEquals(500.0, pipeLengthOf(afterAppend, firstPipe), 1e-9)

        // Undoing the append leaves the dragged length in place.
        val undone = engine.chains.undo(afterAppend).unwrap()
        assertEquals(500.0, pipeLengthOf(undone, firstPipe), 1e-9)
        assertEquals(editor.pipeCount, undone.pipeCount)
    }

    @Test
    fun `undo during a drag settles it and then steps back over it`() {
        val editor = editorWithPipe()

        val dragging = engine.chains
            .preview(editor, ChainCommand3D.Replace(firstPipe, ChainStep3D.Pipe(500.0)))
            .unwrap()
        val undone = engine.chains.undo(dragging).unwrap()

        assertFalse(undone.isPreviewing)
        assertEquals(300.0, pipeLengthOf(undone, firstPipe), 1e-9)
    }

    @Test
    fun `a long drag never grows history beyond the undo quota`() {
        var dragging = editorWithPipe()
        repeat(500) { frame ->
            dragging = engine.chains
                .preview(dragging, ChainCommand3D.Replace(firstPipe, ChainStep3D.Pipe(300.0 + frame % 50)))
                .unwrap()
        }
        val committed = engine.chains.commitPreview(dragging).unwrap()

        assertTrue(
            "500 frames grew the undo stack to ${committed.undoStack.size}",
            committed.undoStack.size <= engine.limits.maxUndoDepth,
        )
    }
}
