package com.planruler.feature.pipecalculator

import com.planruler.fabrication3d.ChainCommand3D
import com.planruler.fabrication3d.ChainPath3D
import com.planruler.fabrication3d.ChainPlan3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.MeshQuality3D
import com.planruler.fabrication3d.engine.DefaultFabrication3DEngine
import com.planruler.pipecalculator.FlangedOffsetAssemblyInput
import com.planruler.pipecalculator.calculateFlangedOffsetAssembly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The workshop dispatches every engine call onto a background dispatcher, so commands that
 * arrive faster than results come back are the interesting case. A controlled dispatcher
 * makes that window deterministic: nothing runs until the scheduler is advanced, so a burst
 * of taps is guaranteed to overlap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Assembly3DViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val engine = DefaultFabrication3DEngine()

    private val calculation = calculateFlangedOffsetAssembly(
        FlangedOffsetAssemblyInput(dn = 50, pn = 16, targetOffsetMm = 500.0, overallFaceToFaceMm = 1_600.0),
    )

    @Before
    fun installTestDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun restoreDispatcher() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = Assembly3DViewModel(engine, dispatcher)

    @Test
    fun `a burst of commands issued before any result lands keeps every edit`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.bind(calculation, ChainPlan3D())
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.editor)

        // Six commands with nothing advanced in between: each one is queued while the
        // previous is still in flight, which is exactly the lost-update window.
        repeat(3) {
            viewModel.execute(ChainCommand3D.Append(ChainStep3D.Pipe(200.0)))
            viewModel.execute(ChainCommand3D.Append(ChainStep3D.Elbow(45.0, 0.0)))
        }
        advanceUntilIdle()

        val editor = requireNotNull(viewModel.state.value.editor)
        assertEquals(6, editor.plan.steps.size)
        assertEquals(3, editor.pipeCount)
        assertEquals(3, editor.elbowCount)
        // The start flange plus the six appended elements.
        assertEquals(7, editor.assembly.parts.size)
    }

    @Test
    fun `an overlapping undo and redo burst ends on the state the taps asked for`() =
        runTest(dispatcher) {
            val viewModel = newViewModel()
            viewModel.bind(calculation, ChainPlan3D())
            advanceUntilIdle()

            repeat(4) { viewModel.execute(ChainCommand3D.Append(ChainStep3D.Pipe(200.0))) }
            advanceUntilIdle()
            assertEquals(4, requireNotNull(viewModel.state.value.editor).plan.steps.size)

            repeat(3) { viewModel.undo() }
            advanceUntilIdle()
            assertEquals(1, requireNotNull(viewModel.state.value.editor).plan.steps.size)

            repeat(3) { viewModel.redo() }
            advanceUntilIdle()
            assertEquals(4, requireNotNull(viewModel.state.value.editor).plan.steps.size)
        }

    @Test
    fun `overlapping edits to an existing element are not lost either`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.bind(calculation, ChainPlan3D())
        advanceUntilIdle()

        viewModel.execute(ChainCommand3D.Append(ChainStep3D.Pipe(200.0)))
        viewModel.execute(ChainCommand3D.Append(ChainStep3D.Pipe(300.0)))
        advanceUntilIdle()

        val first = ChainPath3D(listOf(0))
        val second = ChainPath3D(listOf(1))
        viewModel.execute(ChainCommand3D.Replace(first, ChainStep3D.Pipe(450.0)))
        viewModel.execute(ChainCommand3D.Replace(second, ChainStep3D.Pipe(650.0)))
        advanceUntilIdle()

        val steps = requireNotNull(viewModel.state.value.editor).plan.steps
        assertEquals(ChainStep3D.Pipe(450.0), steps[0])
        assertEquals(ChainStep3D.Pipe(650.0), steps[1])
    }

    @Test
    fun `the mesh is rebuilt for whatever the screen shows`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.bind(calculation, ChainPlan3D())
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.mesh)

        viewModel.setMode(AssemblyWorkspaceMode3D.MANUAL)
        viewModel.execute(ChainCommand3D.Append(ChainStep3D.Pipe(200.0)))
        advanceUntilIdle()

        val mesh = requireNotNull(viewModel.state.value.mesh)
        val shown = requireNotNull(viewModel.state.value.shownAssembly)
        assertEquals(shown.parts.map { it.id }.toSet(), mesh.labels.map { it.partId }.toSet())
    }

    @Test
    fun `part selection is shared by renderers and remains editor aware`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.bind(calculation, ChainPlan3D())
        advanceUntilIdle()
        viewModel.setMode(AssemblyWorkspaceMode3D.MANUAL)
        val partId = requireNotNull(viewModel.state.value.editor).assembly.parts.first().id

        viewModel.selectPart(partId)
        advanceUntilIdle()

        assertEquals(partId, viewModel.state.value.selectedPartId)
        assertEquals(partId, requireNotNull(viewModel.state.value.editor).selectedPartId)
        viewModel.setMode(AssemblyWorkspaceMode3D.VERIFIED)
        viewModel.selectPart(null)
        assertNull(viewModel.state.value.selectedPartId)
    }

    @Test
    fun `many preview frames commit as one undo and restore mesh quality`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.bind(calculation, ChainPlan3D(steps = listOf(ChainStep3D.Pipe(300.0))))
        advanceUntilIdle()
        viewModel.setMode(AssemblyWorkspaceMode3D.MANUAL)
        val path = ChainPath3D(listOf(0))

        viewModel.preview(ChainCommand3D.Replace(path, ChainStep3D.Pipe(325.0)))
        viewModel.preview(ChainCommand3D.Replace(path, ChainStep3D.Pipe(350.0)))
        viewModel.preview(ChainCommand3D.Replace(path, ChainStep3D.Pipe(400.0)))
        assertEquals(MeshQuality3D.DRAFT, viewModel.state.value.quality)
        viewModel.commitPreview()
        advanceUntilIdle()

        var editor = requireNotNull(viewModel.state.value.editor)
        assertEquals(ChainStep3D.Pipe(400.0), editor.plan.steps.single())
        assertEquals(1, editor.undoStack.size)
        assertEquals(MeshQuality3D.NORMAL, viewModel.state.value.quality)

        viewModel.undo()
        advanceUntilIdle()
        editor = requireNotNull(viewModel.state.value.editor)
        assertEquals(ChainStep3D.Pipe(300.0), editor.plan.steps.single())
    }
}
