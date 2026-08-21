package com.planruler.document.api

import com.planruler.model.PageMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlankDocumentTest {
    @Test
    fun `every new sheet has a unique persistent uri`() {
        val first = BlankDocument.uri("draft-1")
        val second = BlankDocument.uri("draft-2")

        assertTrue(BlankDocument.isBlankUri(first))
        assertTrue(BlankDocument.isBlankUri(second))
        assertFalse(first == second)
        assertFalse(BlankDocument.isBlankUri("content://downloads/plan.pdf"))
    }

    @Test
    fun `blank sheet is an A4 landscape page in PDF points`() {
        assertEquals(0, BlankDocument.page.index)
        assertEquals(PageMetadata.CoordinateUnit.PDF_POINT, BlankDocument.page.coordinateUnit)
        assertEquals(841.89, BlankDocument.page.width, 0.01)
        assertEquals(595.28, BlankDocument.page.height, 0.01)
    }
}
