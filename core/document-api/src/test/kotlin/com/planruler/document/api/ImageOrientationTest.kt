package com.planruler.document.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageOrientationTest {
    @Test fun `normal orientation keeps the region`() {
        val region = PixelRect(10, 20, 110, 220)
        assertEquals(region, ImageOrientation.NORMAL.toRawRegion(400, 600, region))
    }

    @Test fun `rotate 90 maps the oriented top left onto the raw bottom left`() {
        // Raw 400x600 becomes 600x400 once rotated; the leading oriented column is the
        // trailing raw row, so a decoder asked for the raw rect must be given that band.
        val raw = ImageOrientation.ROTATE_90.toRawRegion(400, 600, PixelRect(0, 0, 100, 50))
        assertEquals(PixelRect(0, 500, 50, 600), raw)
    }

    @Test fun `rotate 270 mirrors the rotate 90 mapping`() {
        val raw = ImageOrientation.ROTATE_270.toRawRegion(400, 600, PixelRect(0, 0, 100, 50))
        assertEquals(PixelRect(350, 0, 400, 100), raw)
    }

    @Test fun `rotate 180 flips both axes`() {
        val raw = ImageOrientation.ROTATE_180.toRawRegion(400, 600, PixelRect(10, 20, 60, 120))
        assertEquals(PixelRect(340, 480, 390, 580), raw)
    }

    @Test fun `mirrored orientations stay inside the raw bitmap`() {
        listOf(
            ImageOrientation.FLIP_HORIZONTAL,
            ImageOrientation.FLIP_VERTICAL,
            ImageOrientation.TRANSPOSE,
            ImageOrientation.TRANSVERSE,
        ).forEach { orientation ->
            val raw = orientation.toRawRegion(400, 600, PixelRect(-40, -40, 800, 900))
            assertTrue("$orientation left", raw.left >= 0)
            assertTrue("$orientation top", raw.top >= 0)
            assertTrue("$orientation right", raw.right <= 400)
            assertTrue("$orientation bottom", raw.bottom <= 600)
        }
    }

    @Test fun `transpose exchanges the axes`() {
        val raw = ImageOrientation.TRANSPOSE.toRawRegion(400, 600, PixelRect(10, 20, 30, 40))
        assertEquals(PixelRect(20, 10, 40, 30), raw)
    }

    @Test fun `axis swapping is reported for quarter turns only`() {
        assertTrue(ImageOrientation.ROTATE_90.swapsAxes)
        assertTrue(ImageOrientation.ROTATE_270.swapsAxes)
        assertTrue(ImageOrientation.TRANSPOSE.swapsAxes)
        assertTrue(ImageOrientation.TRANSVERSE.swapsAxes)
        assertFalse(ImageOrientation.NORMAL.swapsAxes)
        assertFalse(ImageOrientation.ROTATE_180.swapsAxes)
        assertFalse(ImageOrientation.FLIP_HORIZONTAL.swapsAxes)
        assertFalse(ImageOrientation.FLIP_VERTICAL.swapsAxes)
    }

    @Test fun `unknown exif values fall back to normal`() {
        assertEquals(ImageOrientation.NORMAL, ImageOrientation.ofExif(0))
        assertEquals(ImageOrientation.NORMAL, ImageOrientation.ofExif(99))
        assertEquals(ImageOrientation.ROTATE_270, ImageOrientation.ofExif(8))
    }
}
