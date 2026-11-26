package com.planruler.pipecalculator

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class CatalogConvenienceTest {
    @Test fun `search accepts decimal comma and separated DN`() {
        val pipe = PIPE_INSTALLATION_SERIES.single { it.dn == 50 }
        assertTrue(catalogMatches("DN 50 60,3", "DN50", pipe.sizeLabel, pipe.material))
        assertFalse(catalogMatches("DN80", "DN50", pipe.sizeLabel))
        assertTrue(catalogMatches("", pipe.sizeLabel))
    }

    @Test fun `quarter elbow has one radius takeout and correct three arc lengths`() {
        val result = elbowDimensions(100.0, 60.0, 90.0)
        assertEquals(100.0, result.centerToEndMm, 1e-9)
        assertEquals(50.0 * PI, result.centerlineArcMm, 1e-9)
        assertEquals(35.0 * PI, result.insideArcMm, 1e-9)
        assertEquals(65.0 * PI, result.outsideArcMm, 1e-9)
        val catalog = ELBOW_45_3D_CATALOG.first()
        assertEquals(catalog.centerToEndMm, elbowDimensions(catalog.centerlineRadiusMm, catalog.outsideDiameterMm, 45.0).centerToEndMm, 1e-9)
    }

    @Test fun `impossible and nonfinite elbow geometry is refused`() {
        listOf(0.0, 180.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertTrue(runCatching { elbowDimensions(100.0, 60.0, it) }.isFailure)
        }
        assertTrue(runCatching { elbowDimensions(30.0, 60.0, 90.0) }.isFailure)
    }
}
