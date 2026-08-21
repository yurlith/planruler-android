package com.planruler.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RevisionAlignmentTest {
    @Test
    fun `two control pairs recover rotation scale and translation`() {
        val alignment = requireNotNull(
            RevisionAlignment.calculate(
                listOf(
                    RevisionControlPoint(DocPoint(0.0, 0.0), DocPoint(20.0, 30.0)),
                    RevisionControlPoint(DocPoint(10.0, 0.0), DocPoint(20.0, 50.0)),
                ),
            ),
        )

        val mapped = alignment.transform.map(DocPoint(4.0, 3.0))
        assertEquals(14.0, mapped.x, 1e-9)
        assertEquals(38.0, mapped.y, 1e-9)
        assertTrue(alignment.transform.determinant > 0.0)
    }

    @Test
    fun `three control pairs recover affine skew`() {
        val alignment = requireNotNull(
            RevisionAlignment.calculate(
                listOf(
                    RevisionControlPoint(DocPoint(0.0, 0.0), DocPoint(5.0, 7.0)),
                    RevisionControlPoint(DocPoint(10.0, 0.0), DocPoint(25.0, 12.0)),
                    RevisionControlPoint(DocPoint(0.0, 10.0), DocPoint(15.0, 37.0)),
                ),
            ),
        )

        val mapped = alignment.transform.map(DocPoint(2.0, 4.0))
        assertEquals(13.0, mapped.x, 1e-9)
        assertEquals(20.0, mapped.y, 1e-9)
    }

    @Test
    fun `duplicate or collinear control points are rejected`() {
        assertNull(
            RevisionAlignment.calculate(
                listOf(
                    RevisionControlPoint(DocPoint(1.0, 1.0), DocPoint(2.0, 2.0)),
                    RevisionControlPoint(DocPoint(1.0, 1.0), DocPoint(3.0, 3.0)),
                ),
            ),
        )
        assertNull(
            RevisionAlignment.calculate(
                listOf(
                    RevisionControlPoint(DocPoint(0.0, 0.0), DocPoint(0.0, 0.0)),
                    RevisionControlPoint(DocPoint(1.0, 1.0), DocPoint(2.0, 2.0)),
                    RevisionControlPoint(DocPoint(2.0, 2.0), DocPoint(4.0, 4.0)),
                ),
            ),
        )
    }

    @Test
    fun `carried measurements receive new identities and require explicit review`() {
        val source = Measurement(
            id = MeasurementId("old"),
            type = MeasurementType.DISTANCE,
            points = listOf(DocPoint(1.0, 2.0), DocPoint(3.0, 4.0)),
            pageIndex = 2,
            createdAtEpochMs = 10,
        )
        val untouched = source.copy(id = MeasurementId("other"), pageIndex = 1)
        val carried = carryMeasurementsToRevision(
            listOf(untouched, source),
            pageIndex = 2,
            revisionId = "r1",
            transform = RevisionTransform(2.0, 0.0, 0.0, 2.0, 5.0, 6.0),
            createdAtEpochMs = 20,
            idGenerator = { MeasurementId("new") },
        )

        assertEquals(untouched, carried[0])
        assertEquals(MeasurementId("new"), carried[1].id)
        assertEquals(MeasurementId("old"), carried[1].sourceMeasurementId)
        assertEquals(MeasurementReviewStatus.NEEDS_REVIEW, carried[1].reviewStatus)
        assertEquals(listOf(DocPoint(7.0, 10.0), DocPoint(11.0, 14.0)), carried[1].points)
    }
}
