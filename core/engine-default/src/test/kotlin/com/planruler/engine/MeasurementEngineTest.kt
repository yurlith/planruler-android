package com.planruler.engine

import com.planruler.engine.api.EngineResult
import com.planruler.engine.default.DefaultMeasurementEngine
import com.planruler.engine.default.Geometry
import com.planruler.model.*
import org.junit.Assert.*
import org.junit.Test

class MeasurementEngineTest {
    private fun engine() = DefaultMeasurementEngine(clock = { 1L }, idGenerator = { "id" })

    @Test fun `manual calibration and distance`() {
        val engine = engine()
        engine.calibrateByReference(DocPoint(0.0, 0.0), DocPoint(100.0, 0.0), 2.0, LengthUnit.METER)
        engine.beginMeasurement(MeasurementType.DISTANCE, DocPoint(0.0, 0.0))
        engine.addPoint(DocPoint(25.0, 0.0))
        val measurement = (engine.commitMeasurement() as EngineResult.Ok).value
        assertEquals(0.5, ((engine.evaluate(measurement) as EngineResult.Ok).value as MeasureValue.Length).meters, 1e-12)
    }

    @Test fun `pdf point precision at one to fifty`() {
        val engine = engine()
        engine.calibratePdfRatio(50.0)
        engine.beginMeasurement(MeasurementType.DISTANCE, DocPoint(0.0, 0.0))
        engine.addPoint(DocPoint(72.0, 0.0))
        val measurement = (engine.commitMeasurement() as EngineResult.Ok).value
        val meters = ((engine.evaluate(measurement) as EngineResult.Ok).value as MeasureValue.Length).meters
        assertEquals(1.270, meters, 0.00000001) // substantially tighter than 0.01 mm
    }

    @Test fun `polyline area perimeter and angle`() {
        assertEquals(7.0, Geometry.polylineLength(listOf(DocPoint(0.0, 0.0), DocPoint(3.0, 0.0), DocPoint(3.0, 4.0))), 0.0)
        val rectangle = listOf(DocPoint(0.0, 0.0), DocPoint(3.0, 0.0), DocPoint(3.0, 4.0), DocPoint(0.0, 4.0))
        assertEquals(12.0, Geometry.polygonArea(rectangle), 0.0)
        assertEquals(14.0, Geometry.polygonPerimeter(rectangle), 0.0)
        assertEquals(90.0, Geometry.angleDegrees(DocPoint(1.0, 0.0), DocPoint(0.0, 0.0), DocPoint(0.0, 1.0)), 1e-12)
    }

    @Test fun `zero calibration is rejected`() {
        val result = engine().calibrateByReference(DocPoint(1.0, 1.0), DocPoint(1.0, 1.0), 1.0, LengthUnit.METER)
        assertTrue(result is EngineResult.Error)
    }

    @Test fun `calibration audit and independent verification are retained`() {
        val engine = engine()
        val audit = CalibrationAudit(
            calibratedAtEpochMs = 77L,
            calibratedBy = "Alex",
            pageIndex = 2,
            referenceDocumentLength = 100.0,
            enteredLength = 2.0,
            enteredUnit = LengthUnit.METER,
        )
        val calibrated = engine.calibrateByReference(
            DocPoint(0.0, 0.0),
            DocPoint(100.0, 0.0),
            2.0,
            LengthUnit.METER,
            audit,
        ) as EngineResult.Ok
        assertEquals(audit, calibrated.value.audit)

        val verified = engine.verifyCalibration(
            DocPoint(0.0, 0.0),
            DocPoint(50.0, 0.0),
            expectedLength = 1.01,
            unit = LengthUnit.METER,
            pageIndex = 2,
        ) as EngineResult.Ok
        assertEquals(1.0, verified.value.measuredLength, 1e-12)
        assertEquals(0.01 / 1.01, verified.value.relativeError, 1e-12)
        assertEquals(1L, verified.value.verifiedAtEpochMs)
        assertEquals(verified.value, engine.state.value.calibration?.audit?.verification)
    }

    @Test fun `verification requires a calibrated scale`() {
        val result = engine().verifyCalibration(
            DocPoint(0.0, 0.0),
            DocPoint(10.0, 0.0),
            1.0,
            LengthUnit.METER,
        )
        assertTrue(result is EngineResult.Error)
    }

    @Test fun `self intersection is detected`() {
        assertTrue(Geometry.selfIntersects(listOf(
            DocPoint(0.0, 0.0), DocPoint(10.0, 10.0), DocPoint(0.0, 10.0), DocPoint(10.0, 0.0),
        )))
    }

    @Test fun `snap honors threshold`() {
        val candidate = DocPoint(10.0, 10.0)
        assertEquals(candidate, Geometry.snap(DocPoint(10.4, 10.3), listOf(candidate), 1.0))
        assertEquals(DocPoint(20.0, 20.0), Geometry.snap(DocPoint(20.0, 20.0), listOf(candidate), 1.0))
    }

    @Test fun `coordinate transform round trips and preserves focus`() {
        val transform = ViewportTransform(1000.0, 800.0, ViewportState(2.0, 100.0, 200.0))
        val original = DocPoint(42.25, 81.75)
        val screen = transform.documentToScreen(original)
        val roundTrip = transform.screenToDocument(screen)
        assertEquals(original.x, roundTrip.x, 1e-12)
        assertEquals(original.y, roundTrip.y, 1e-12)
        val focus = ScreenPoint(310.0, 270.0)
        val before = transform.screenToDocument(focus)
        val zoomed = ViewportTransform(1000.0, 800.0, transform.zoomAt(3.0, focus))
        val after = zoomed.screenToDocument(focus)
        assertEquals(before.x, after.x, 1e-12)
        assertEquals(before.y, after.y, 1e-12)
    }

    @Test fun `invalid gesture samples cannot poison viewport`() {
        val transform = ViewportTransform(1000.0, 800.0, ViewportState(2.0, 100.0, 200.0))
        val zoomed = transform.zoomAt(Double.NaN, ScreenPoint(Double.NaN, Double.POSITIVE_INFINITY))
        val panned = ViewportTransform(1000.0, 800.0, zoomed)
            .panBy(Double.NaN, Double.NEGATIVE_INFINITY)
        assertEquals(2.0, panned.zoom, 0.0)
        assertEquals(100.0, panned.centerX, 0.0)
        assertEquals(200.0, panned.centerY, 0.0)
        assertTrue(panned.zoom.isFinite() && panned.centerX.isFinite() && panned.centerY.isFinite())
    }

    @Test fun `nearby zoom frames reuse one sharp render bucket`() {
        assertEquals(2.0, quantizedRenderScale(1.01), 0.0)
        assertEquals(2.0, quantizedRenderScale(1.37), 0.0)
        assertEquals(2.0, quantizedRenderScale(2.0), 0.0)
        listOf(0.13, 0.75, 1.01, 3.2, 31.9).forEach { zoom ->
            val renderScale = quantizedRenderScale(zoom)
            assertTrue(renderScale >= zoom)
            assertTrue(renderScale < zoom * 2.0)
        }
        assertEquals(1.0, quantizedRenderScale(Double.NaN), 0.0)
    }

    @Test fun `recalibration recomputes and undo restores`() {
        val engine = engine()
        engine.calibrateByReference(DocPoint(0.0, 0.0), DocPoint(100.0, 0.0), 1.0, LengthUnit.METER)
        engine.beginMeasurement(MeasurementType.DISTANCE, DocPoint(0.0, 0.0))
        engine.addPoint(DocPoint(50.0, 0.0))
        val measurement = (engine.commitMeasurement() as EngineResult.Ok).value
        assertEquals(0.5, ((engine.evaluate(measurement) as EngineResult.Ok).value as MeasureValue.Length).meters, 0.0)
        engine.calibrateByReference(DocPoint(0.0, 0.0), DocPoint(100.0, 0.0), 2.0, LengthUnit.METER)
        assertEquals(1.0, ((engine.evaluate(measurement) as EngineResult.Ok).value as MeasureValue.Length).meters, 0.0)
        engine.undo()
        assertEquals(0.5, ((engine.evaluate(measurement) as EngineResult.Ok).value as MeasureValue.Length).meters, 0.0)
        engine.redo()
        assertEquals(1.0, ((engine.evaluate(measurement) as EngineResult.Ok).value as MeasureValue.Length).meters, 0.0)
    }

    @Test fun `all units convert through SI`() {
        LengthUnit.entries.forEach { unit ->
            assertEquals(1.0, unit.fromMeters(unit.toMeters(1.0)), 1e-12)
        }
    }

    @Test fun `completed vertex drag creates one undo step and supports redo`() {
        val engine = engineWithLine()
        val id = MeasurementId("line")
        assertTrue(engine.beginEdit(id) is EngineResult.Ok)
        engine.previewVertex(1, DocPoint(12.0, 2.0))
        engine.previewVertex(1, DocPoint(15.0, 5.0))
        assertTrue(engine.commitEdit() is EngineResult.Ok)
        assertEquals(DocPoint(15.0, 5.0), engine.state.value.measurements.single().points[1])

        engine.undo()
        assertEquals(DocPoint(10.0, 0.0), engine.state.value.measurements.single().points[1])
        engine.redo()
        assertEquals(DocPoint(15.0, 5.0), engine.state.value.measurements.single().points[1])
    }

    @Test fun `cancelled drag restores document without an undo record`() {
        val engine = engineWithLine()
        engine.beginEdit(MeasurementId("line"))
        engine.previewMove(DocPoint(20.0, 30.0))
        engine.cancelEdit()
        assertEquals(
            listOf(DocPoint(0.0, 0.0), DocPoint(10.0, 0.0)),
            engine.state.value.measurements.single().points,
        )
        assertFalse(engine.state.value.canUndo)
    }

    @Test fun `move duplicate insert remove and delete are reversible commands`() {
        var nextId = 0
        val engine = DefaultMeasurementEngine(clock = { 2L }, idGenerator = { "copy-${nextId++}" })
        val original = Measurement(
            MeasurementId("polyline"),
            MeasurementType.POLYLINE,
            listOf(DocPoint(0.0, 0.0), DocPoint(10.0, 0.0)),
            createdAtEpochMs = 1L,
        )
        engine.restore(Calibration.reference(10.0, 1.0, LengthUnit.METER), LengthUnit.METER, listOf(original))

        engine.moveMeasurement(original.id, DocPoint(2.0, 3.0))
        assertEquals(DocPoint(2.0, 3.0), engine.state.value.measurements.single().points.first())
        engine.insertVertex(original.id, 0, DocPoint(7.0, 3.0))
        assertEquals(3, engine.state.value.measurements.single().points.size)
        engine.removeVertex(original.id, 1)
        assertEquals(2, engine.state.value.measurements.single().points.size)

        val duplicate = (engine.duplicateMeasurement(original.id) as EngineResult.Ok).value
        assertNotEquals(original.id, duplicate.id)
        assertEquals(2, engine.state.value.measurements.size)
        engine.deleteMeasurement(duplicate.id)
        assertEquals(1, engine.state.value.measurements.size)
        engine.undo()
        assertEquals(2, engine.state.value.measurements.size)
    }

    @Test fun `properties and unicode annotation participate in undo redo`() {
        val annotation = Measurement(
            MeasurementId("note"),
            MeasurementType.ANNOTATION,
            listOf(DocPoint(4.0, 5.0)),
            label = "Old",
            createdAtEpochMs = 1L,
        )
        val engine = engine()
        engine.restore(null, LengthUnit.METER, listOf(annotation))
        engine.updateAnnotation(annotation.id, "  Примечание\n第二行  ")
        assertEquals("Примечание\n第二行", engine.state.value.measurements.single().label)
        engine.undo()
        assertEquals("Old", engine.state.value.measurements.single().label)
        engine.redo()
        assertEquals("Примечание\n第二行", engine.state.value.measurements.single().label)
    }

    @Test fun `active template properties are stored on a new measurement`() {
        val engine = engine()
        engine.calibrateByReference(DocPoint(0.0, 0.0), DocPoint(100.0, 0.0), 1.0, LengthUnit.METER)
        val takeoff = TakeoffProperties(
            category = TradeCategory.ELECTRICAL,
            material = "Кабель",
            quantity = 2.0,
            wasteFactor = 1.1,
        )
        engine.beginMeasurement(
            type = MeasurementType.POLYLINE,
            first = DocPoint(0.0, 0.0),
            label = "Кабельная трасса",
            style = MeasurementStyle(0xFF123456, 4f),
            layerId = LayerId("electric"),
            takeoff = takeoff,
            displayUnit = LengthUnit.METER,
            templateId = "electrical-cable",
        )
        engine.addPoint(DocPoint(20.0, 0.0))
        val measurement = (engine.commitMeasurement() as EngineResult.Ok).value
        assertEquals("electrical-cable", measurement.templateId)
        assertEquals("Кабельная трасса", measurement.label)
        assertEquals(LayerId("electric"), measurement.layerId)
        assertEquals(takeoff, measurement.takeoff)
        assertEquals(0xFF123456, measurement.style.colorArgb)
    }

    @Test fun `exact distance supports free horizontal vertical and undo`() {
        val engine = engine()
        engine.restore(
            Calibration.reference(100.0, 1.0, LengthUnit.METER),
            LengthUnit.METER,
            listOf(
                Measurement(
                    MeasurementId("exact"),
                    MeasurementType.DISTANCE,
                    listOf(DocPoint(10.0, 20.0), DocPoint(40.0, 60.0)),
                    createdAtEpochMs = 1L,
                ),
            ),
        )
        assertTrue(engine.setExactLength(MeasurementId("exact"), 2.0, LengthUnit.METER) is EngineResult.Ok)
        val free = engine.state.value.measurements.single()
        assertEquals(200.0, free.points[0].distanceTo(free.points[1]), 1e-9)

        engine.setExactLength(MeasurementId("exact"), 3.0, LengthUnit.METER, DistanceConstraint.HORIZONTAL)
        val horizontal = engine.state.value.measurements.single()
        assertEquals(310.0, horizontal.points[1].x, 1e-9)
        assertEquals(20.0, horizontal.points[1].y, 1e-9)

        engine.setExactLength(MeasurementId("exact"), 1.5, LengthUnit.METER, DistanceConstraint.VERTICAL)
        val vertical = engine.state.value.measurements.single()
        assertEquals(10.0, vertical.points[1].x, 1e-9)
        assertEquals(170.0, vertical.points[1].y, 1e-9)
        engine.undo()
        assertEquals(horizontal.points, engine.state.value.measurements.single().points)
    }

    @Test fun `template update changes properties but preserves geometry`() {
        val points = listOf(DocPoint(0.0, 0.0), DocPoint(10.0, 0.0), DocPoint(10.0, 20.0))
        val engine = engine()
        engine.restore(
            null,
            LengthUnit.METER,
            listOf(
                Measurement(
                    MeasurementId("templated"),
                    MeasurementType.POLYLINE,
                    points,
                    templateId = "pipe",
                    createdAtEpochMs = 1L,
                ),
            ),
        )
        val template = TakeoffTemplate(
            id = "pipe",
            name = "Труба DN25",
            measurementType = MeasurementType.POLYLINE,
            style = MeasurementStyle(0xFF0066CC, 5f),
            layerId = LayerId("pipes"),
            takeoff = TakeoffProperties(material = "PP-R", diameter = "DN25", wasteFactor = 1.05),
        )
        assertEquals(1, (engine.applyTemplateToMeasurements(template) as EngineResult.Ok).value)
        val updated = engine.state.value.measurements.single()
        assertEquals(points, updated.points)
        assertEquals("Труба DN25", updated.label)
        assertEquals("PP-R", updated.takeoff.material)
        assertEquals(0xFF0066CC, updated.style.colorArgb)
        engine.undo()
        assertEquals(points, engine.state.value.measurements.single().points)
        assertNull(engine.state.value.measurements.single().takeoff.material)
    }

    @Test fun `takeoff totals apply quantity and waste consistently`() {
        val calibration = Calibration.reference(100.0, 1.0, LengthUnit.METER)
        val measurements = listOf(
            Measurement(
                MeasurementId("length"),
                MeasurementType.DISTANCE,
                listOf(DocPoint(0.0, 0.0), DocPoint(100.0, 0.0)),
                takeoff = TakeoffProperties(quantity = 2.0, wasteFactor = 1.1),
                createdAtEpochMs = 1L,
            ),
            Measurement(
                MeasurementId("area"),
                MeasurementType.AREA,
                listOf(DocPoint(0.0, 0.0), DocPoint(100.0, 0.0), DocPoint(100.0, 100.0), DocPoint(0.0, 100.0)),
                takeoff = TakeoffProperties(quantity = 3.0, wasteFactor = 1.05),
                createdAtEpochMs = 1L,
            ),
            Measurement(
                MeasurementId("count"),
                MeasurementType.COUNTER,
                listOf(DocPoint(1.0, 1.0)),
                takeoff = TakeoffProperties(quantity = 4.0, wasteFactor = 1.25),
                createdAtEpochMs = 1L,
            ),
        )
        val totals = calculateTakeoffTotals(measurements, calibration)
        assertEquals(2.0, totals.baseLengthMeters, 1e-12)
        assertEquals(2.2, totals.adjustedLengthMeters, 1e-12)
        assertEquals(3.0, totals.baseAreaSquareMeters, 1e-12)
        assertEquals(3.15, totals.adjustedAreaSquareMeters, 1e-12)
        assertEquals(4.0, totals.baseCount, 1e-12)
        assertEquals(5.0, totals.adjustedCount, 1e-12)
    }

    private fun engineWithLine(): DefaultMeasurementEngine {
        val engine = engine()
        engine.restore(
            Calibration.reference(10.0, 1.0, LengthUnit.METER),
            LengthUnit.METER,
            listOf(
                Measurement(
                    MeasurementId("line"),
                    MeasurementType.DISTANCE,
                    listOf(DocPoint(0.0, 0.0), DocPoint(10.0, 0.0)),
                    createdAtEpochMs = 1L,
                ),
            ),
        )
        return engine
    }
}
