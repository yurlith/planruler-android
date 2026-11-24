package com.planruler.isometric

import com.planruler.model.IsoComponentKind
import com.planruler.model.IsoPipeSpec
import com.planruler.model.IsoPipeSystem
import com.planruler.model.IsoProjectionStyle
import com.planruler.model.IsometricSheet
import com.planruler.model.ViewportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsometricEngineTest {
    private var ids = 0
    private var now = 1_000L
    private val supply20 = IsoPipeSpec(IsoPipeSystem.HEATING_SUPPLY, 20)

    private fun engine(sheet: IsometricSheet = IsometricEngine.newSheet("sheet", "Test", 0L)) =
        IsometricEngine(sheet, clock = { now++ }, idGenerator = { "id-${ids++}" })

    private fun <T> IsoResult<T>.ok(): T = when (this) {
        is IsoResult.Ok -> value
        is IsoResult.Error -> throw AssertionError("Expected success, got $error")
    }

    private fun IsometricEngine.length(pipeIndex: Int = 0): Double {
        val network = IsoNetwork(sheet)
        return network.length(sheet.pipes[pipeIndex])
    }

    @Test
    fun `a first run creates two joints and one pipe that undo removes`() {
        val engine = engine()
        val run = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(1_500.0, 0.0, 0.0), supply20).ok()
        assertEquals(2, engine.sheet.nodes.size)
        assertEquals(1, engine.sheet.pipes.size)
        assertEquals(1_500.0, engine.length(), 1e-9)
        assertFalse(run.joinedExisting)
        assertTrue(engine.state.value.canUndo)

        engine.undo().ok()
        assertTrue(engine.sheet.pipes.isEmpty())
        assertTrue(engine.state.value.canRedo)
        engine.redo().ok()
        assertEquals(1, engine.sheet.pipes.size)
    }

    @Test
    fun `chained runs make an elbow at the corner`() {
        val engine = engine()
        val first = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(1_000.0, 0.0, 0.0), supply20).ok()
        engine.addRun(RunStart.Node(first.endNodeId), Vec3(0.0, 0.0, 2_700.0), supply20).ok()
        val fitting = requireNotNull(IsoNetwork(engine.sheet).fittingAt(first.endNodeId))
        assertEquals(IsoFittingKind.ELBOW, fitting.kind)
        assertEquals(90, fitting.standardAngleDeg)
        assertEquals(20, fitting.nominalDiameter)
    }

    @Test
    fun `a run ending on an existing joint closes onto it`() {
        val engine = engine()
        val a = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(1_000.0, 0.0, 0.0), supply20).ok()
        val b = engine.addRun(RunStart.Node(a.endNodeId), Vec3(0.0, 1_000.0, 0.0), supply20).ok()
        val c = engine.addRun(RunStart.Node(b.endNodeId), Vec3(-1_000.0, 0.0, 0.0), supply20).ok()
        val closing = engine.addRun(RunStart.Node(c.endNodeId), Vec3(0.0, -1_000.0, 0.0), supply20).ok()
        assertTrue(closing.joinedExisting)
        assertEquals(a.startNodeId, closing.endNodeId)
        assertEquals(4, engine.sheet.nodes.size)
        assertEquals(4, engine.sheet.pipes.size)
    }

    @Test
    fun `a run ending inside another run makes a tee`() {
        val engine = engine()
        engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(2_000.0, 0.0, 0.0), IsoPipeSpec(IsoPipeSystem.HEATING_SUPPLY, 25)).ok()
        val branch = engine.addRun(RunStart.Point(Vec3(800.0, 0.0, 1_000.0)), Vec3(0.0, 0.0, -1_000.0), supply20).ok()
        assertTrue(branch.joinedExisting)
        assertEquals(3, engine.sheet.pipes.size)
        val tee = requireNotNull(IsoNetwork(engine.sheet).fittingAt(branch.endNodeId))
        assertEquals(IsoFittingKind.TEE, tee.kind)
        assertEquals(25, tee.nominalDiameter)
        assertEquals(20, tee.branchDiameter)
    }

    @Test
    fun `a branch can start from the middle of a run`() {
        val engine = engine()
        val main = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(3_000.0, 0.0, 0.0), supply20).ok()
        val branch = engine.addRun(RunStart.OnPipe(main.pipeId, 1_200.0), Vec3(0.0, 0.0, 600.0), supply20).ok()
        val joint = engine.sheet.nodes.first { it.id == branch.startNodeId }
        assertEquals(1_200.0, joint.position.x, 1e-9)
        assertEquals(IsoFittingKind.TEE, IsoNetwork(engine.sheet).fittingAt(joint.id)?.kind)
    }

    @Test
    fun `a run passing over an existing joint is split there`() {
        val engine = engine()
        val riser = engine.addRun(RunStart.Point(Vec3(1_000.0, 0.0, 0.0)), Vec3(0.0, 0.0, 500.0), supply20).ok()
        val main = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(2_000.0, 0.0, 0.0), supply20).ok()
        assertEquals(2, main.pipeIds.size)
        assertTrue(main.joinedExisting)
        assertEquals(IsoFittingKind.TEE, IsoNetwork(engine.sheet).fittingAt(riser.startNodeId)?.kind)
    }

    @Test
    fun `doubling back over an existing run is refused`() {
        val engine = engine()
        val run = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(1_000.0, 0.0, 0.0), supply20).ok()
        val result = engine.addRun(RunStart.Node(run.startNodeId), Vec3(400.0, 0.0, 0.0), supply20)
        assertEquals(IsoResult.Error(IsoError.Overlap), result)
        assertEquals(1, engine.sheet.pipes.size)
    }

    @Test
    fun `changing a length moves everything beyond the run`() {
        val engine = engine()
        val first = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(1_000.0, 0.0, 0.0), supply20).ok()
        val second = engine.addRun(RunStart.Node(first.endNodeId), Vec3(0.0, 0.0, 2_000.0), supply20).ok()
        engine.setPipeLength(first.pipeId, 1_750.0).ok()
        val network = IsoNetwork(engine.sheet)
        assertEquals(1_750.0, network.length(network.pipesById.getValue(first.pipeId)), 1e-9)
        assertEquals(2_000.0, network.length(network.pipesById.getValue(second.pipeId)), 1e-9)
        assertEquals(Vec3(1_750.0, 0.0, 2_000.0), network.position(second.endNodeId))

        engine.setPipeLength(first.pipeId, 1_000.0, keep = first.endNodeId).ok()
        assertEquals(Vec3(750.0, 0.0, 0.0), IsoNetwork(engine.sheet).position(first.startNodeId))
    }

    @Test
    fun `a closed loop refuses a one-sided length change`() {
        val engine = engine()
        val a = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(1_000.0, 0.0, 0.0), supply20).ok()
        val b = engine.addRun(RunStart.Node(a.endNodeId), Vec3(0.0, 1_000.0, 0.0), supply20).ok()
        val c = engine.addRun(RunStart.Node(b.endNodeId), Vec3(-1_000.0, 0.0, 0.0), supply20).ok()
        engine.addRun(RunStart.Node(c.endNodeId), Vec3(0.0, -1_000.0, 0.0), supply20).ok()
        assertEquals(IsoResult.Error(IsoError.Loop), engine.setPipeLength(a.pipeId, 1_200.0))
    }

    @Test
    fun `splitting keeps inline components on the correct half`() {
        val engine = engine()
        val run = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(1_000.0, 0.0, 0.0), supply20).ok()
        val near = engine.addComponent(IsoComponentKind.BALL_VALVE, run.pipeId, 0.2).ok()
        val far = engine.addComponent(IsoComponentKind.CHECK_VALVE, run.pipeId, 0.8).ok()
        val joint = engine.splitPipe(run.pipeId, 500.0).ok()
        val sheet = engine.sheet
        val nearComponent = sheet.components.first { it.id == near }
        val farComponent = sheet.components.first { it.id == far }
        assertEquals(run.pipeId, nearComponent.pipeId)
        assertEquals(0.4, nearComponent.offset, 1e-9)
        val second = sheet.pipes.first { it.from == joint }
        assertEquals(second.id, farComponent.pipeId)
        assertEquals(0.6, farComponent.offset, 1e-9)
    }

    @Test
    fun `deleting a straight joint heals the run and keeps components in place`() {
        val engine = engine()
        val run = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(1_000.0, 0.0, 0.0), supply20).ok()
        val valve = engine.addComponent(IsoComponentKind.GATE_VALVE, run.pipeId, 0.5).ok()
        val joint = engine.splitPipe(run.pipeId, 200.0).ok()
        engine.deleteNode(joint).ok()
        val sheet = engine.sheet
        assertEquals(1, sheet.pipes.size)
        assertEquals(1_000.0, IsoNetwork(sheet).length(sheet.pipes.single()), 1e-9)
        assertEquals(0.5, sheet.components.first { it.id == valve }.offset, 1e-9)
    }

    @Test
    fun `deleting a tee joint takes its runs and orphaned joints`() {
        val engine = engine()
        val main = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(2_000.0, 0.0, 0.0), supply20).ok()
        val branch = engine.addRun(RunStart.OnPipe(main.pipeId, 1_000.0), Vec3(0.0, 1_000.0, 0.0), supply20).ok()
        engine.deleteNode(branch.startNodeId).ok()
        assertTrue(engine.sheet.pipes.isEmpty())
        assertTrue(engine.sheet.nodes.isEmpty())
    }

    @Test
    fun `different sizes on a straight joint read as a reducer`() {
        val engine = engine()
        val first = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(1_000.0, 0.0, 0.0), IsoPipeSpec(IsoPipeSystem.COLD_WATER, 32)).ok()
        val second = engine.addRun(RunStart.Node(first.endNodeId), Vec3(1_000.0, 0.0, 0.0), IsoPipeSpec(IsoPipeSystem.COLD_WATER, 32)).ok()
        assertEquals(IsoFittingKind.COUPLING, IsoNetwork(engine.sheet).fittingAt(first.endNodeId)?.kind)
        engine.setPipeSpec(listOf(second.pipeId), IsoPipeSpec(IsoPipeSystem.COLD_WATER, 25)).ok()
        val reducer = requireNotNull(IsoNetwork(engine.sheet).fittingAt(first.endNodeId))
        assertEquals(IsoFittingKind.REDUCER, reducer.kind)
        assertEquals(32, reducer.nominalDiameter)
        assertEquals(25, reducer.branchDiameter)
    }

    @Test
    fun `a radiator closes the open end`() {
        val engine = engine()
        val run = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(0.0, 0.0, 600.0), supply20).ok()
        assertEquals(IsoFittingKind.END, IsoNetwork(engine.sheet).fittingAt(run.endNodeId)?.kind)
        engine.addComponentAtNode(IsoComponentKind.RADIATOR, run.endNodeId).ok()
        assertEquals(null, IsoNetwork(engine.sheet).fittingAt(run.endNodeId))
    }

    @Test
    fun `view changes are not edits`() {
        val engine = engine()
        engine.setViewport(ViewportState(2.0, 10.0, 20.0))
        assertFalse(engine.state.value.canUndo)
        engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(100.0, 0.0, 0.0), supply20).ok()
        engine.undo().ok()
        assertEquals(ViewportState(2.0, 10.0, 20.0), engine.sheet.viewport)
    }

    @Test
    fun `sheet settings are validated`() {
        val engine = engine()
        assertTrue(engine.setGridStep(0.1) is IsoResult.Error)
        engine.setGridStep(50.0).ok()
        engine.setProjection(IsoProjectionStyle.FRONTAL)
        assertEquals(IsoProjectionStyle.FRONTAL, engine.sheet.projection)
        assertTrue(engine.rename("  ") is IsoResult.Error)
        engine.rename("Стояк Ст-1").ok()
        assertEquals("Стояк Ст-1", engine.sheet.name)
        assertNotNull(engine.sheet.modifiedAtEpochMs)
    }

    @Test
    fun `zero and invalid runs are refused`() {
        val engine = engine()
        assertEquals(IsoResult.Error(IsoError.ZeroLength), engine.addRun(RunStart.Point(Vec3.ZERO), Vec3.ZERO, supply20))
        assertTrue(engine.addRun(RunStart.Point(Vec3(Double.NaN, 0.0, 0.0)), Vec3.X, supply20) is IsoResult.Error)
        assertTrue(engine.sheet.nodes.isEmpty())
    }
}
