package com.planruler.document.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DepthMapNormalizerTest {
    @Test fun `range linear is decoded and millimetres become metres`() {
        val result = requireNotNull(
            DepthMapNormalizer.normalizedRasterToMeters(
                normalizedSamples = floatArrayOf(0f, 0.5f, 1f),
                encoding = DepthEncoding.RANGE_LINEAR,
                near = 500.0,
                far = 2_500.0,
                units = "mm",
            ),
        )

        assertEquals(0.5f, result[0], 0.0001f)
        assertEquals(1.5f, result[1], 0.0001f)
        assertEquals(2.5f, result[2], 0.0001f)
    }

    @Test fun `range inverse follows the normative near to far curve`() {
        val result = requireNotNull(
            DepthMapNormalizer.normalizedRasterToMeters(
                normalizedSamples = floatArrayOf(0f, 0.5f, 1f),
                encoding = DepthEncoding.RANGE_INVERSE,
                near = 1.0,
                far = 9.0,
                units = "Meters",
            ),
        )

        assertEquals(1f, result[0], 0.0001f)
        assertEquals(1.8f, result[1], 0.0001f)
        assertEquals(9f, result[2], 0.0001f)
    }

    @Test fun `non metric grayscale range is rejected`() {
        assertNull(
            DepthMapNormalizer.normalizedRasterToMeters(
                normalizedSamples = floatArrayOf(0.5f),
                encoding = DepthEncoding.RANGE_LINEAR,
                near = 0.0,
                far = 255.0,
                units = "None",
            ),
        )
    }

    @Test fun `direct disparity in diopters becomes metres`() {
        val result = requireNotNull(
            DepthMapNormalizer.directValuesToMeters(
                samples = floatArrayOf(0.5f, 2f, Float.NaN),
                encoding = DepthEncoding.DISPARITY,
                units = "diopters",
            ),
        )

        assertEquals(2f, result[0], 0.0001f)
        assertEquals(0.5f, result[1], 0.0001f)
        assertEquals(true, result[2].isNaN())
    }

    @Test fun `map lookup honours confidence and normalized coordinates`() {
        val map = MetricDepthMap(
            width = 2,
            height = 2,
            depthMeters = floatArrayOf(1f, 2f, 3f, 4f),
            confidence = floatArrayOf(1f, 0.2f, 0.8f, 1f),
            encoding = DepthEncoding.RANGE_LINEAR,
            measureType = DepthMeasureType.OPTICAL_AXIS,
            standard = DepthStandard.GDEPTH,
            sourceMime = "image/png",
            sourceBitDepth = 16,
        )

        assertEquals(4f, requireNotNull(map.sample(1.0, 1.0, 0.5f)), 0f)
        assertNull(map.sample(1.0, 0.0, 0.5f))
        assertEquals(2.5f, map.medianMeters)
    }
}
