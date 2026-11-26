package com.planruler.feature.workspace

import com.planruler.model.Calibration
import com.planruler.model.DocPoint
import com.planruler.model.InstallationJobId
import com.planruler.model.InstallationLateralDirection
import com.planruler.model.InstallationMeasurementReference
import com.planruler.model.InstallationPipeMaterial
import com.planruler.model.InstallationTaskType
import com.planruler.model.InstallationVerticalDirection
import com.planruler.model.LengthUnit
import com.planruler.model.Measurement
import com.planruler.model.MeasurementId
import com.planruler.model.MeasurementType
import com.planruler.model.PipeRoute
import com.planruler.model.TakeoffProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class InstallationJobFromMeasurementTest {
    private val calibration = Calibration.reference(10.0, 1.0, LengthUnit.METER)

    @Test fun `distance becomes a straight insert with true measured length`() {
        val measurement = measurement(
            MeasurementType.DISTANCE,
            listOf(DocPoint(0.0, 0.0), DocPoint(3.0, 4.0)),
        )

        val job = measurementToInstallationJob(measurement, calibration, 42L, InstallationJobId("job"))!!

        assertEquals(InstallationTaskType.STRAIGHT_INSERT, job.taskType)
        assertEquals(500.0, job.input.alongMm, 0.001)
        assertEquals(0.0, job.input.lateralOffsetMm, 0.0)
        assertEquals(measurement.points, job.source2D?.points)
    }

    @Test fun `polyline transfers offset material dn and exact source`() {
        val measurement = measurement(
            MeasurementType.POLYLINE,
            listOf(DocPoint(0.0, 0.0), DocPoint(10.0, 0.0), DocPoint(10.0, -4.0)),
            TakeoffProperties(material = "Нержавеющая сталь", diameter = "DN 52", quantity = 2.0),
        )

        val job = measurementToInstallationJob(measurement, calibration, 42L, InstallationJobId("job"))!!

        assertEquals(InstallationTaskType.FLAT_OFFSET, job.taskType)
        assertEquals(1_000.0, job.input.alongMm, 0.001)
        assertEquals(400.0, job.input.lateralOffsetMm, 0.001)
        assertEquals(50, job.input.nominalDiameter)
        assertEquals(InstallationPipeMaterial.STAINLESS_STEEL, job.input.material)
        assertEquals("Нержавеющая сталь", job.input.materialName)
        assertEquals(measurement.id, job.source2D?.measurementId)
        assertSame(measurement.points, job.source2D?.points)
    }

    @Test fun `the offset side is taken from the direction of travel on the page`() {
        fun side(vararg points: DocPoint) = measurementToInstallationJob(
            measurement(MeasurementType.POLYLINE, points.toList()),
            calibration,
            42L,
            InstallationJobId("job"),
        )!!.input.lateralDirection

        // The page's y runs down: heading east, down the page is on the right.
        assertEquals(InstallationLateralDirection.RIGHT, side(DocPoint(0.0, 0.0), DocPoint(6.0, 0.0), DocPoint(10.0, 4.0)))
        assertEquals(InstallationLateralDirection.LEFT, side(DocPoint(0.0, 0.0), DocPoint(6.0, 0.0), DocPoint(10.0, -4.0)))
        // Heading west, the same step down the page is on the left.
        assertEquals(InstallationLateralDirection.LEFT, side(DocPoint(10.0, 0.0), DocPoint(4.0, 0.0), DocPoint(0.0, 4.0)))
        // Heading down the page, east is on the left and west on the right.
        assertEquals(InstallationLateralDirection.LEFT, side(DocPoint(0.0, 0.0), DocPoint(0.0, 6.0), DocPoint(4.0, 10.0)))
        assertEquals(InstallationLateralDirection.RIGHT, side(DocPoint(0.0, 0.0), DocPoint(0.0, 6.0), DocPoint(-4.0, 10.0)))
    }

    @Test fun `a run with a rise between its ends becomes a 3D offset welded to pipes`() {
        val measurement = measurement(
            MeasurementType.POLYLINE,
            listOf(DocPoint(0.0, 0.0), DocPoint(6.0, 0.0), DocPoint(10.0, -4.0)),
        ).copy(route = PipeRoute(risesMm = listOf(900.0, -350.0, 120.0)))

        val job = measurementToInstallationJob(measurement, calibration, 42L, InstallationJobId("job"))!!

        assertEquals(InstallationTaskType.ROLLING_OFFSET, job.taskType)
        assertEquals(350.0, job.input.verticalOffsetMm, 1e-9)
        assertEquals(InstallationVerticalDirection.DOWN, job.input.verticalDirection)
        assertEquals(400.0, job.input.lateralOffsetMm, 0.001)
        assertEquals(InstallationMeasurementReference.PIPE_FACE, job.input.startReference)
        assertEquals(InstallationMeasurementReference.PIPE_FACE, job.input.endReference)
    }

    @Test fun `a straight run that only changes level is a vertical offset`() {
        val measurement = measurement(
            MeasurementType.POLYLINE,
            listOf(DocPoint(0.0, 0.0), DocPoint(5.0, 0.0), DocPoint(10.0, 0.0)),
        ).copy(route = PipeRoute(risesMm = listOf(0.0, 600.0, 0.0)))

        val job = measurementToInstallationJob(measurement, calibration, 42L, InstallationJobId("job"))!!

        assertEquals(InstallationTaskType.ROLLING_OFFSET, job.taskType)
        assertEquals(0.0, job.input.lateralOffsetMm, 0.0)
        assertEquals(600.0, job.input.verticalOffsetMm, 1e-9)
        assertEquals(InstallationVerticalDirection.UP, job.input.verticalDirection)
    }

    @Test fun `unsupported or uncalibrated measurement is refused`() {
        assertNull(measurementToInstallationJob(measurement(MeasurementType.AREA, listOf(
            DocPoint(0.0, 0.0), DocPoint(1.0, 0.0), DocPoint(1.0, 1.0),
        )), calibration, 1L, InstallationJobId("area")))
        assertNull(measurementToInstallationJob(measurement(MeasurementType.DISTANCE, listOf(
            DocPoint(0.0, 0.0), DocPoint(1.0, 0.0),
        )), null, 1L, InstallationJobId("uncalibrated")))
    }

    private fun measurement(
        type: MeasurementType,
        points: List<DocPoint>,
        takeoff: TakeoffProperties = TakeoffProperties(),
    ) = Measurement(
        id = MeasurementId("measurement"),
        type = type,
        points = points,
        takeoff = takeoff,
        createdAtEpochMs = 10L,
    )
}
