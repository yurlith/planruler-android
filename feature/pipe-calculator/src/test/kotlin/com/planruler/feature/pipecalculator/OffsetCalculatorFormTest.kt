package com.planruler.feature.pipecalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OffsetCalculatorFormTest {
    private fun ready(form: OffsetForm): OffsetOutcome.Ready {
        val outcome = form.calculate()
        assertTrue("expected a result, got $outcome", outcome is OffsetOutcome.Ready)
        return outcome as OffsetOutcome.Ready
    }

    @Test
    fun `default form gives the DN50 45 degree cut at once`() {
        val outcome = ready(OffsetForm())
        assertEquals(640.146, outcome.result.cutLengthMm, 0.001)
        assertEquals(76.0, outcome.radiusMm!!, 0.0)
        assertNull(outcome.nearestStockAngle)
    }

    @Test
    fun `decimal comma and spaces are accepted`() {
        val outcome = ready(OffsetForm(offset = "1 000,5", angle = "22,5", fittings = OffsetFittings.CUSTOM, takeout = "12,5", gap = "0"))
        assertEquals(1000.5, outcome.result.offsetMm, 1e-9)
        assertEquals(22.5, outcome.result.angleDeg, 1e-9)
        assertEquals(12.5, outcome.result.takeoutMm, 1e-9)
        assertNull(outcome.radiusMm)
    }

    @Test
    fun `rolling offset adds both planes`() {
        val outcome = ready(OffsetForm(mode = OffsetMode.ROLLING, offset = "300", second = "400"))
        assertEquals(500.0, outcome.result.offsetMm, 1e-9)
        assertEquals(53.130, outcome.result.rollDeg, 0.001)
    }

    @Test
    fun `plain mode ignores the sideways field`() {
        val outcome = ready(OffsetForm(mode = OffsetMode.PLAIN, offset = "300", second = "400"))
        assertEquals(300.0, outcome.result.offsetMm, 1e-9)
    }

    @Test
    fun `angle from the run suggests the closest stock elbow`() {
        val exact = ready(OffsetForm(mode = OffsetMode.BY_ADVANCE, offset = "500", advance = "500"))
        assertEquals(45.0, exact.result.angleDeg, 1e-9)
        assertEquals(45.0, exact.nearestStockAngle!!, 0.0)
        val flat = ready(OffsetForm(mode = OffsetMode.BY_ADVANCE, offset = "500", advance = "1000"))
        assertEquals(26.565, flat.result.angleDeg, 0.001)
        assertEquals(30.0, flat.nearestStockAngle!!, 0.0)
        assertEquals(1000.0, flat.result.advanceMm, 1e-6)
        val straightUp = ready(OffsetForm(mode = OffsetMode.BY_ADVANCE, offset = "500", advance = "0"))
        assertEquals(90.0, straightUp.result.angleDeg, 1e-9)
    }

    @Test
    fun `half typed or impossible input points at the field`() {
        assertEquals(OffsetOutcome.Invalid(OffsetField.OFFSET), OffsetForm(offset = "").calculate())
        assertEquals(OffsetOutcome.Invalid(OffsetField.OFFSET), OffsetForm(offset = "0").calculate())
        assertEquals(OffsetOutcome.Invalid(OffsetField.ANGLE), OffsetForm(angle = "0").calculate())
        assertEquals(OffsetOutcome.Invalid(OffsetField.ANGLE), OffsetForm(angle = "95").calculate())
        assertEquals(OffsetOutcome.Invalid(OffsetField.SECOND), OffsetForm(mode = OffsetMode.ROLLING, second = "").calculate())
        assertEquals(
            OffsetOutcome.Invalid(OffsetField.SECOND),
            OffsetForm(mode = OffsetMode.ROLLING, offset = "0", second = "0").calculate(),
        )
        assertEquals(OffsetOutcome.Invalid(OffsetField.ADVANCE), OffsetForm(mode = OffsetMode.BY_ADVANCE, advance = "").calculate())
        assertEquals(
            OffsetOutcome.Invalid(OffsetField.ADVANCE),
            OffsetForm(mode = OffsetMode.BY_ADVANCE, offset = "10", advance = "100000").calculate(),
        )
        assertEquals(OffsetOutcome.Invalid(OffsetField.TAKEOUT), OffsetForm(fittings = OffsetFittings.CUSTOM, takeout = "").calculate())
        assertEquals(OffsetOutcome.Invalid(OffsetField.GAP), OffsetForm(gap = "").calculate())
    }

    @Test
    fun `an offset smaller than the elbows is a result that does not fit`() {
        val outcome = ready(OffsetForm(offset = "30"))
        assertFalse(outcome.result.fits)
        assertTrue(outcome.result.minimumOffsetMm > 30.0)
    }

    @Test
    fun `switching the elbow kind switches the usual joint gap`() {
        val custom = OffsetForm().withFittings(OffsetFittings.CUSTOM)
        assertEquals("0", custom.gap)
        assertEquals(OFFSET_WELD_GAP_MM, custom.withFittings(OffsetFittings.WELDED_3D).gap)
        val typed = OffsetForm(gap = "3")
        assertEquals("3", typed.withFittings(OffsetFittings.WELDED_3D).gap)
    }

    @Test
    fun `stock angles print without a trailing zero`() {
        assertEquals("45", angleText(45.0))
        assertEquals("22.5", angleText(22.5))
    }
}
