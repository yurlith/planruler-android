package com.planruler.fabrication3d

import org.junit.Assert.assertEquals
import org.junit.Test

class OffsetPlacement3DTest {
    @Test
    fun `a share places the cut before the offset between the shortest cuts on either side`() {
        val spare = 1_000.0
        val minimum = 50.0

        assertEquals(50.0, OffsetPlacement3D.Share(0.0).leadCutMm(spare, minimum), 1e-12)
        assertEquals(500.0, OffsetPlacement3D.CENTERED.leadCutMm(spare, minimum), 1e-12)
        assertEquals(950.0, OffsetPlacement3D.Share(1.0).leadCutMm(spare, minimum), 1e-12)
        // The middle does not depend on the shortest cut, which is why centring never moved.
        assertEquals(500.0, OffsetPlacement3D.CENTERED.leadCutMm(spare, 0.0), 1e-12)
    }

    @Test
    fun `an exact cut is taken as it is and the cut after the offset gets the rest`() {
        assertEquals(300.0, OffsetPlacement3D.LeadCut(300.0).leadCutMm(1_000.0, 50.0), 0.0)
        assertEquals(300.0, OffsetPlacement3D.LeadCut(300.0).leadCutMm(100.0, 50.0), 0.0)
    }

    @Test
    fun `the shortest cut is the fitter's minimum but never below the shop rule`() {
        val rules = FabricationRules3D(weldGapMm = 2.0, minPipeLengthMm = 10.0)

        assertEquals(50.0, rules.minimumCutMm(50.0), 0.0)
        assertEquals(10.0, rules.minimumCutMm(5.0), 0.0)
    }
}
