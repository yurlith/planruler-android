package com.planruler.document.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoMetadataAnalyzerTest {
    @Test
    fun `35mm equivalent produces diagonal intrinsic and field of view`() {
        val input = PhotoMetadataInput(
            pixelWidth = 4000,
            pixelHeight = 3000,
            orientation = 1,
            make = "Example",
            model = "Phone",
            focalLengthMm = 6.0,
            focalLength35Mm = 26.0,
        )

        val estimate = PhotoMetadataAnalyzer.opticalEstimate(input)

        assertEquals(3004.7, requireNotNull(estimate.focalLengthPixels), 0.2)
        assertEquals(4.333, requireNotNull(estimate.cropFactor), 0.001)
        assertEquals(9.985, requireNotNull(estimate.sensorDiagonalMm), 0.01)
        assertTrue(requireNotNull(estimate.horizontalFieldOfViewDegrees) in 67.0..69.0)
    }

    @Test
    fun `subject distance is only an approximate focus plane`() {
        val evidence = PhotoMetadataAnalyzer.analyze(
            metadata = PhotoMetadataInput(
                pixelWidth = 4000,
                pixelHeight = 3000,
                orientation = 1,
                focalLength35Mm = 26.0,
                subjectDistanceMeters = 2.5,
            ),
            container = CaptureContainerInput(),
            sourceSha256 = "a",
            sourceByteCount = 10,
        )

        assertEquals(CaptureReadiness.APPROXIMATE_FOCUS_PLANE, evidence.readiness)
        assertTrue(CaptureCapability.DISTANCE_HINT in evidence.capabilities)
        assertTrue(CaptureWarning.SUBJECT_DISTANCE_IS_ONLY_A_HINT in evidence.warnings)
        assertNotNull(evidence.opticalEstimate.metersPerPixelAtSubject)
        assertFalse(CaptureCapability.METRIC_RECONSTRUCTION_READY in evidence.capabilities)
    }

    @Test
    fun `metric dynamic depth with pose and planes is survey ready`() {
        val map = MetricDepthMap(
            width = 2,
            height = 2,
            depthMeters = floatArrayOf(1f, 2f, 3f, 4f),
            confidence = floatArrayOf(1f, 1f, 0.8f, 0.9f),
            encoding = DepthEncoding.RANGE_LINEAR,
            measureType = DepthMeasureType.OPTICAL_AXIS,
            standard = DepthStandard.DYNAMIC_DEPTH,
            sourceMime = "image/png",
            sourceBitDepth = 16,
        )
        val evidence = PhotoMetadataAnalyzer.analyze(
            metadata = PhotoMetadataInput(4032, 3024, 6, focalLength35Mm = 24.0),
            container = CaptureContainerInput(
                xmpPresent = true,
                depthStandards = setOf(DepthStandard.DYNAMIC_DEPTH),
                depthUnits = "Meters",
                depthPayloadConfirmed = true,
                confidencePayloadConfirmed = true,
                cameraPosePresent = true,
                worldPlanesPresent = true,
                imagingModelPresent = true,
            ),
            sourceSha256 = "b",
            sourceByteCount = 20,
            depthDecode = DepthDecodeReport(
                status = DepthDecodeStatus.DECODED_METRIC,
                map = map,
            ),
        )

        assertEquals(CaptureReadiness.AR_SURVEY_AVAILABLE, evidence.readiness)
        assertTrue(CaptureCapability.METRIC_DEPTH in evidence.capabilities)
        assertTrue(CaptureCapability.METRIC_RECONSTRUCTION_READY in evidence.capabilities)
        assertTrue(CaptureCapability.CAMERA_POSE in evidence.capabilities)
        assertTrue(CaptureCapability.WORLD_PLANES in evidence.capabilities)
        assertEquals(2.5f, evidence.depthDecode.map?.medianMeters)
    }

    @Test
    fun `depth without units is not accepted as metric`() {
        val evidence = PhotoMetadataAnalyzer.analyze(
            metadata = PhotoMetadataInput(1000, 800, 1),
            container = CaptureContainerInput(
                depthStandards = setOf(DepthStandard.GDEPTH),
                depthPayloadConfirmed = true,
            ),
            sourceSha256 = "c",
            sourceByteCount = 30,
        )

        assertEquals(CaptureReadiness.REFERENCE_REQUIRED, evidence.readiness)
        assertTrue(CaptureWarning.DEPTH_UNITS_MISSING in evidence.warnings)
        assertFalse(CaptureCapability.METRIC_DEPTH in evidence.capabilities)
    }

    @Test
    fun `focal plane resolution offers independent focal estimate`() {
        val input = PhotoMetadataInput(
            pixelWidth = 4000,
            pixelHeight = 3000,
            orientation = 1,
            focalLengthMm = 6.0,
            focalLength35Mm = 26.0,
            focalPlaneXResolution = 5000.0,
            focalPlaneResolutionUnitMm = 10.0,
        )

        val estimate = PhotoMetadataAnalyzer.opticalEstimate(input)

        assertEquals(8.0, requireNotNull(estimate.sensorWidthFromFocalPlaneMm), 0.001)
        assertEquals(3000.0, requireNotNull(estimate.focalLengthPixelsFromFocalPlane), 0.001)
        assertTrue(requireNotNull(estimate.focalEstimateRelativeDifference) < 0.01)
    }

    @Test
    fun `camera statistics deduplicate files and require three stable observations`() {
        val key = CameraProfileKey("A", "B", "Lens", "4:3", 26, 100)
        fun observation(hash: String, normalized: Double, crop: Double) = CameraProfileObservation(
            hash,
            key,
            normalized,
            crop,
            10.0,
            null,
        )
        val statistics = PhotoMetadataAnalyzer.statistics(
            listOf(
                observation("1", 0.600, 4.30),
                observation("2", 0.603, 4.32),
                observation("3", 0.598, 4.29),
                observation("3", 2.000, 8.00),
            ),
        )

        assertEquals(3, statistics.sampleCount)
        assertTrue(statistics.stable)
        assertEquals(0.600, requireNotNull(statistics.medianNormalizedFocalDiagonal), 0.001)
    }

    @Test
    fun `camera statistics reject unstable profile`() {
        val key = CameraProfileKey("A", "B", "", "4:3", 26, null)
        val observations = listOf(0.4, 0.6, 0.9).mapIndexed { index, value ->
            CameraProfileObservation(index.toString(), key, value, null, null, null)
        }

        val statistics = PhotoMetadataAnalyzer.statistics(observations)

        assertFalse(statistics.stable)
        assertNull(statistics.medianCropFactor)
    }
}
