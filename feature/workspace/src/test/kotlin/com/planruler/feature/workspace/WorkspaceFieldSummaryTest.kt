package com.planruler.feature.workspace

import com.planruler.model.Calibration
import com.planruler.model.DocPoint
import com.planruler.model.LengthUnit
import com.planruler.model.Measurement
import com.planruler.model.MeasurementId
import com.planruler.model.MeasurementType
import com.planruler.model.TakeoffProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceFieldSummaryTest {
    private val calibration = Calibration.reference(10.0, 1.0, LengthUnit.METER)

    @Test fun `summary follows selected material and keeps latest field result`() {
        val steel = line("steel", "Сталь", 10.0, created = 1L)
        val copper = line("copper", "Медь", 20.0, created = 2L)
        val anotherSteel = line("steel-2", "Сталь", 5.0, created = 3L)

        val summary = fieldSummary(listOf(steel, copper, anotherSteel), 0, calibration, steel)

        assertEquals(anotherSteel, summary.lastMeasurement)
        assertEquals("Сталь", summary.material)
        assertEquals(1.5, summary.totals.adjustedLengthMeters, 0.0001)
        assertEquals(steel, summary.assemblySource)
        assertEquals("1.50 m", fieldTotalValue(summary.totals))
    }

    @Test fun `assembly source needs calibration`() {
        val line = line("line", null, 10.0, created = 1L)
        assertNull(fieldSummary(listOf(line), 0, null, line).assemblySource)
    }

    private fun line(id: String, material: String?, length: Double, created: Long) = Measurement(
        id = MeasurementId(id),
        type = MeasurementType.DISTANCE,
        points = listOf(DocPoint(0.0, 0.0), DocPoint(length, 0.0)),
        takeoff = TakeoffProperties(material = material),
        pageIndex = 0,
        createdAtEpochMs = created,
    )
}
