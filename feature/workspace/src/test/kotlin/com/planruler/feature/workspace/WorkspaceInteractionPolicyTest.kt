package com.planruler.feature.workspace

import com.planruler.model.DocPoint
import com.planruler.model.Measurement
import com.planruler.model.MeasurementId
import com.planruler.model.MeasurementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceInteractionPolicyTest {
    private val first = line("first", 0.0)
    private val second = line("second", 20.0)

    @Test fun `handles are shown only on selected editable measurement`() {
        assertTrue(shouldShowHandles(true, first.id, first.id))
        assertFalse(shouldShowHandles(true, second.id, first.id))
        assertFalse(shouldShowHandles(false, first.id, first.id))
    }

    @Test fun `distance label has a dedicated hit target`() {
        val hit = labelHitTest(listOf(first, second), DocPoint(12.0, -0.5), tolerance = 1.0)
        assertEquals(first.id, hit?.measurement?.id)
        assertTrue(hit?.label == true)
        assertNull(labelHitTest(listOf(first), DocPoint(30.0, 30.0), tolerance = 1.0))
    }

    private fun line(id: String, x: Double) = Measurement(
        id = MeasurementId(id),
        type = MeasurementType.DISTANCE,
        points = listOf(DocPoint(x, 0.0), DocPoint(x + 10.0, 0.0)),
        createdAtEpochMs = 1L,
    )
}
