package com.planruler.engine

import com.planruler.engine.api.EngineResult
import com.planruler.engine.api.SnapContext
import com.planruler.engine.api.SnapType
import com.planruler.engine.default.DefaultMeasurementEngine
import com.planruler.engine.default.DefaultSnapEngine
import com.planruler.model.DocPoint
import com.planruler.model.LengthUnit
import com.planruler.model.MeasureValue
import com.planruler.model.Measurement
import com.planruler.model.MeasurementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Risers/drops on measured pipe runs and the 45° guide used while routing them. */
class PipeRouteEngineTest {
    private fun engineWithRun(): Pair<DefaultMeasurementEngine, Measurement> {
        val engine = DefaultMeasurementEngine(clock = { 1L }, idGenerator = { "run" })
        // 100 document units = 1 m.
        engine.calibrateByReference(DocPoint(0.0, 0.0), DocPoint(100.0, 0.0), 1.0, LengthUnit.METER)
        engine.beginMeasurement(MeasurementType.POLYLINE, DocPoint(0.0, 0.0))
        engine.addPoint(DocPoint(200.0, 0.0))
        engine.addPoint(DocPoint(200.0, 100.0))
        val run = (engine.commitMeasurement() as EngineResult.Ok).value
        return engine to run
    }

    private fun DefaultMeasurementEngine.lengthOf(id: String): MeasureValue.Length {
        val measurement = state.value.measurements.first { it.id.value == id }
        return (evaluate(measurement) as EngineResult.Ok).value as MeasureValue.Length
    }

    @Test
    fun `a riser adds its height to the run length but not to the plan segments`() {
        val (engine, run) = engineWithRun()
        assertTrue(engine.setVertexRise(run.id, 1, 2_500.0) is EngineResult.Ok)
        assertTrue(engine.setVertexRise(run.id, 2, -600.0) is EngineResult.Ok)
        val value = engine.lengthOf("run")
        assertEquals(3.0 + 3.1, value.meters, 1e-9)
        assertEquals(listOf(2.0, 1.0), value.segmentsMeters)
        assertTrue(engine.state.value.canUndo)
        engine.undo()
        assertEquals(3.0 + 2.5, engine.lengthOf("run").meters, 1e-9)
    }

    @Test
    fun `vertex edits keep risers on their joints`() {
        val (engine, run) = engineWithRun()
        engine.setVertexRise(run.id, 1, 2_500.0)
        engine.insertVertex(run.id, 0, DocPoint(100.0, 0.0))
        assertEquals(listOf(0.0, 0.0, 2_500.0), engine.state.value.measurements.single().route?.risesMm)
        engine.removeVertex(run.id, 2)
        assertEquals(listOf(0.0, 2_500.0), engine.state.value.measurements.single().route?.risesMm)
    }

    @Test
    fun `invalid riser edits are refused`() {
        val (engine, run) = engineWithRun()
        assertTrue(engine.setVertexRise(run.id, 5, 100.0) is EngineResult.Error)
        assertTrue(engine.setVertexRise(run.id, 0, Double.POSITIVE_INFINITY) is EngineResult.Error)
        assertTrue(engine.setVertexRise(run.id, 0, 250_000.0) is EngineResult.Error)
        assertNull(engine.state.value.measurements.single().route)
        assertTrue(engine.setRouteStartElevation(run.id, 300.0) is EngineResult.Ok)
        assertEquals(300.0, engine.state.value.measurements.single().route?.startElevationMm ?: 0.0, 0.0)
    }

    @Test
    fun `routing snaps to 45 degrees from the previous point`() {
        val snap = DefaultSnapEngine().resolve(
            DocPoint(50.6, 49.5),
            SnapContext(emptyList(), anchor = DocPoint(0.0, 0.0), sensitivityDocumentUnits = 1.5),
        )
        assertEquals(SnapType.DIAGONAL, snap.type)
        assertEquals(snap.point.x, snap.point.y, 1e-9)
        val disabled = DefaultSnapEngine().resolve(
            DocPoint(50.6, 49.5),
            SnapContext(
                emptyList(),
                anchor = DocPoint(0.0, 0.0),
                sensitivityDocumentUnits = 1.5,
                allowed = setOf(SnapType.HORIZONTAL, SnapType.VERTICAL),
            ),
        )
        assertEquals(SnapType.NONE, disabled.type)
        // Close to the anchor every guide converges; no diagonal is forced there.
        val near = DefaultSnapEngine().resolve(
            DocPoint(0.7, 0.9),
            SnapContext(emptyList(), anchor = DocPoint(0.0, 0.0), sensitivityDocumentUnits = 1.5, allowed = setOf(SnapType.DIAGONAL)),
        )
        assertEquals(SnapType.NONE, near.type)
    }
}
