package com.planruler.engine

import com.planruler.engine.api.SnapContext
import com.planruler.engine.api.SnapSensitivity
import com.planruler.engine.api.SnapType
import com.planruler.engine.default.DefaultSnapEngine
import com.planruler.model.DocPoint
import com.planruler.model.Measurement
import com.planruler.model.MeasurementId
import com.planruler.model.MeasurementType
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapEngineTest {
    private val engine = DefaultSnapEngine()
    private val line = Measurement(
        id = MeasurementId("line"),
        type = MeasurementType.POLYLINE,
        points = listOf(DocPoint(10.0, 10.0), DocPoint(30.0, 10.0)),
        createdAtEpochMs = 0,
    )

    @Test fun snapsToNearestVertex() {
        val result = engine.resolve(DocPoint(10.4, 10.3), SnapContext(listOf(line), sensitivityDocumentUnits = 1.0))
        assertEquals(SnapType.VERTEX, result.type)
        assertEquals(DocPoint(10.0, 10.0), result.point)
        assertEquals(0, result.target?.vertexIndex)
    }

    @Test fun snapsToNearestSegment() {
        val result = engine.resolve(DocPoint(20.0, 11.0), SnapContext(listOf(line), sensitivityDocumentUnits = 2.0))
        assertEquals(SnapType.SEGMENT, result.type)
        assertEquals(DocPoint(20.0, 10.0), result.point)
        assertEquals(0, result.target?.segmentIndex)
    }

    @Test fun snapsHorizontallyAndVerticallyFromAnchor() {
        val horizontal = engine.resolve(
            DocPoint(20.0, 10.4),
            SnapContext(emptyList(), anchor = DocPoint(5.0, 10.0), sensitivityDocumentUnits = 1.0),
        )
        assertEquals(SnapType.HORIZONTAL, horizontal.type)
        assertEquals(10.0, horizontal.point.y, 0.0)

        val vertical = engine.resolve(
            DocPoint(5.4, 20.0),
            SnapContext(emptyList(), anchor = DocPoint(5.0, 10.0), sensitivityDocumentUnits = 1.0),
        )
        assertEquals(SnapType.VERTICAL, vertical.type)
        assertEquals(5.0, vertical.point.x, 0.0)
    }

    @Test fun temporaryDisableReturnsOriginalCandidate() {
        val candidate = DocPoint(10.1, 10.1)
        val result = engine.resolve(candidate, SnapContext(listOf(line), sensitivityDocumentUnits = 2.0, enabled = false))
        assertEquals(SnapType.NONE, result.type)
        assertEquals(candidate, result.point)
    }

    @Test fun screenSensitivityConvertsAtDifferentZoomLevels() {
        assertEquals(24.0, SnapSensitivity.documentUnits(12.0, density = 2.0, zoom = 1.0), 0.0)
        assertEquals(3.0, SnapSensitivity.documentUnits(12.0, density = 2.0, zoom = 8.0), 0.0)
    }
}
