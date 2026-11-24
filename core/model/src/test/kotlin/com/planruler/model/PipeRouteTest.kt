package com.planruler.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipeRouteTest {
    private fun pipe(route: PipeRoute?, type: MeasurementType = MeasurementType.POLYLINE) = Measurement(
        id = MeasurementId("p"),
        type = type,
        points = listOf(DocPoint(0.0, 0.0), DocPoint(100.0, 0.0), DocPoint(100.0, 50.0)),
        createdAtEpochMs = 0,
        takeoff = TakeoffProperties(quantity = 1.0, wasteFactor = 1.1),
        route = route,
    )

    @Test
    fun `risers and drops count towards the installed length`() {
        // 1 document unit = 10 mm, plan length 150 units = 1.5 m.
        val calibration = Calibration.reference(100.0, 1.0, LengthUnit.METER)
        val route = PipeRoute(startElevationMm = 300.0, risesMm = listOf(0.0, 2_400.0, -600.0))
        val totals = calculateTakeoffTotals(listOf(pipe(route)), calibration)
        assertEquals(1.5 + 3.0, totals.baseLengthMeters, 1e-9)
        assertEquals((1.5 + 3.0) * 1.1, totals.adjustedLengthMeters, 1e-9)
        assertEquals(1.5, calculateTakeoffTotals(listOf(pipe(null)), calibration).baseLengthMeters, 1e-9)
        assertEquals(3_000.0, pipe(route).verticalLengthMm, 1e-9)
        assertEquals(0.0, pipe(route, MeasurementType.AREA).verticalLengthMm, 1e-9)
    }

    @Test
    fun `rises stay aligned when vertices are inserted`() {
        val route = PipeRoute(risesMm = listOf(0.0, 2_400.0, -600.0)).afterInsert(afterIndex = 0, pointCountBefore = 3)
        assertEquals(listOf(0.0, 0.0, 2_400.0, -600.0), route.risesMm)
    }

    @Test
    fun `removing a vertex keeps every downstream level`() {
        val route = PipeRoute(risesMm = listOf(100.0, 2_400.0, -600.0))
        assertEquals(listOf(2_500.0, -600.0), route.afterRemove(index = 1, pointCountBefore = 3).risesMm)
        assertEquals(listOf(2_500.0, -600.0), route.afterRemove(index = 0, pointCountBefore = 3).risesMm)
        assertEquals(listOf(100.0, 1_800.0), route.afterRemove(index = 2, pointCountBefore = 3).risesMm)
        assertEquals(route, route.afterRemove(index = 7, pointCountBefore = 3))
    }

    @Test
    fun `setting a rise validates the vertex and trims trailing zeros`() {
        val route = PipeRoute().withRise(1, 1_200.0, pointCount = 3)
        assertEquals(listOf(0.0, 1_200.0), route.risesMm)
        assertTrue(route.hasRises)
        assertEquals(0.0, route.riseAt(2), 0.0)
        assertFalse(route.withRise(1, 0.0, pointCount = 3).hasRises)
        assertTrue(runCatching { route.withRise(3, 10.0, pointCount = 3) }.isFailure)
        assertTrue(runCatching { route.withRise(0, Double.NaN, pointCount = 3) }.isFailure)
    }
}
