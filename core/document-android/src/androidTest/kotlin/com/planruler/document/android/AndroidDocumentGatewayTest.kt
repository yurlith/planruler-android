package com.planruler.document.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Debug
import android.os.SystemClock
import android.graphics.pdf.PdfRenderer
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.exifinterface.media.ExifInterface
import com.planruler.document.api.DocumentResult
import com.planruler.document.api.CaptureCapability
import com.planruler.document.api.CaptureReadiness
import com.planruler.document.api.DepthDecodeStatus
import com.planruler.document.api.CameraProfileKey
import com.planruler.document.api.CameraProfileObservation
import com.planruler.document.api.RenderRequest
import com.planruler.document.api.TileRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class AndroidDocumentGatewayTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun importsAndRendersTwoPagePdf() = runBlocking {
        val file = File(context.cacheDir, "vector-two-page.pdf")
        val pdf = PdfDocument()
        repeat(2) { index ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(612, 792, index + 1).create())
            page.canvas.drawLine(0f, 100f, 72f, 100f, Paint().apply { color = Color.BLACK; strokeWidth = 2f })
            pdf.finishPage(page)
        }
        FileOutputStream(file).use(pdf::writeTo)
        pdf.close()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> assertEquals(2, renderer.pageCount) }
        }

        val gateway = AndroidDocumentGateway(context)
        val openResult = gateway.open(Uri.fromFile(file).toString())
        assertTrue("gateway PDF open failed: $openResult", openResult is DocumentResult.Ok)
        val opened = openResult as DocumentResult.Ok
        assertEquals(2, opened.value.pages.size)
        val rendered = gateway.renderPage(opened.value.id, 1, RenderRequest(512))
        assertTrue(rendered is DocumentResult.Ok && rendered.value.argb.isNotEmpty())
        gateway.close(opened.value.id)
    }

    @Test fun importsAndRendersPngAndJpeg() = runBlocking {
        for ((name, format) in listOf("sample.png" to Bitmap.CompressFormat.PNG, "sample.jpg" to Bitmap.CompressFormat.JPEG)) {
            val file = File(context.cacheDir, name)
            val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            FileOutputStream(file).use { bitmap.compress(format, 90, it) }
            bitmap.recycle()

            val gateway = AndroidDocumentGateway(context)
            val opened = gateway.open(Uri.fromFile(file).toString()) as DocumentResult.Ok
            assertEquals(1, opened.value.pages.size)
            assertTrue(gateway.renderPage(opened.value.id, 0, RenderRequest(256)) is DocumentResult.Ok)
            gateway.close(opened.value.id)
        }
    }

    @Test fun importsExifOpticsAndBuildsLocalCameraProfile() = runBlocking {
        context.getSharedPreferences("planruler.camera.profile.statistics.v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val file = File(context.cacheDir, "metadata-inspector.jpg")
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.LTGRAY)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_MAKE, "PlanRuler Test Camera")
            setAttribute(ExifInterface.TAG_MODEL, "Field Lens 1")
            setAttribute(ExifInterface.TAG_LENS_MODEL, "Main 6 mm")
            setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "6/1")
            setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, "26")
            setAttribute(ExifInterface.TAG_SUBJECT_DISTANCE, "5/2")
            setAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, "1/1")
            setAttribute(ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION, "100/1")
            setAttribute(ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION, "100/1")
            setAttribute(ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT, "4")
            saveAttributes()
        }

        val opened = (AndroidDocumentGateway(context).open(Uri.fromFile(file).toString()) as DocumentResult.Ok).value
        val evidence = requireNotNull(opened.captureEvidence)

        assertEquals("PlanRuler Test Camera", evidence.metadata.make)
        assertEquals("Field Lens 1", evidence.metadata.model)
        assertEquals(6.0, evidence.metadata.focalLengthMm!!, 0.0)
        assertEquals(26.0, evidence.metadata.focalLength35Mm!!, 0.0)
        assertTrue(CaptureCapability.CAMERA_INTRINSICS in evidence.capabilities)
        // The Android JPEG writer may legally omit SubjectDistance even when set on a fixture.
        // Real files that retain it are covered by the platform-independent analyzer tests.
        assertEquals(CaptureReadiness.REFERENCE_REQUIRED, evidence.readiness)
        assertEquals(1, evidence.cameraProfileStatistics?.sampleCount)
        assertEquals(64, evidence.sourceSha256.length)
        assertTrue(evidence.sourceByteCount > 0)
    }

    @Test fun localCameraProfileDeduplicatesAndRequiresThreeStablePhotos() {
        context.getSharedPreferences("planruler.camera.profile.statistics.v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val store = CameraProfileStatisticsStore(context)
        val key = CameraProfileKey(
            make = "PlanRuler",
            model = "Survey Cam",
            lensModel = "Main",
            aspectBucket = "4:3",
            focalLength35Bucket = 26,
            digitalZoomBucket = 100,
        )
        fun observation(index: Int, normalizedFocal: Double) = CameraProfileObservation(
            sourceSha256 = index.toString().padStart(64, '0'),
            key = key,
            normalizedFocalDiagonal = normalizedFocal,
            cropFactor = 4.32 + index * 0.001,
            sensorDiagonalMm = 10.0,
            focalEstimateRelativeDifference = 0.01,
        )

        assertTrue(!store.observe(observation(1, 0.600)).stable)
        assertTrue(!store.observe(observation(2, 0.601)).stable)
        val third = store.observe(observation(3, 0.599))
        assertEquals(3, third.sampleCount)
        assertTrue(third.stable)
        assertEquals(3, store.observe(observation(3, 0.599)).sampleCount)
    }

    @Test fun decodesEmbeddedGDepthPngToMetricMapWithoutUsingFileExtension() = runBlocking {
        val depthBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.rgb(0, 0, 0))
            setPixel(1, 0, Color.rgb(128, 128, 128))
            setPixel(0, 1, Color.rgb(192, 192, 192))
            setPixel(1, 1, Color.rgb(255, 255, 255))
        }
        val depthBytes = java.io.ByteArrayOutputStream().also { output ->
            depthBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }.toByteArray()
        depthBitmap.recycle()
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/" xmlns:GDepth="http://ns.google.com/photos/1.0/depthmap/">
              <rdf:Description GDepth:Format="RangeLinear" GDepth:Near="1" GDepth:Far="5"
                GDepth:Units="m" GDepth:Mime="image/png"
                GDepth:Data="${Base64.getEncoder().encodeToString(depthBytes)}" />
            </x:xmpmeta>
        """.trimIndent()
        val file = File(context.cacheDir, "content-sniffed-depth.bin")
        val colourBytes = java.io.ByteArrayOutputStream().also { output ->
            Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(Color.LTGRAY)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
                bitmap.recycle()
            }
        }.toByteArray()
        file.writeBytes(withJpegXmp(colourBytes, xmp))

        val opened = (AndroidDocumentGateway(context).open(Uri.fromFile(file).toString()) as DocumentResult.Ok).value
        val decode = requireNotNull(opened.captureEvidence).depthDecode

        assertEquals(DepthDecodeStatus.DECODED_METRIC, decode.status)
        val map = requireNotNull(decode.map)
        assertEquals(2, map.width)
        assertEquals(2, map.height)
        assertEquals(1f, map.depthMeters.first(), 0.02f)
        assertEquals(5f, map.depthMeters.last(), 0.02f)
        assertTrue(decode.decoder in setOf("PNG-Grayscale", "ImageDecoder", "BitmapFactory"))
    }

    @Test fun rejectsCorruptPdf() = runBlocking {
        val file = File(context.cacheDir, "corrupt.pdf").apply { writeText("%PDF-truncated-without-eof") }
        val gateway = AndroidDocumentGateway(context)
        assertTrue(gateway.open(Uri.fromFile(file).toString()) is DocumentResult.Error)

        val valid = File(context.cacheDir, "valid-after-corrupt.pdf")
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(300, 400, 1).create())
        page.canvas.drawColor(Color.WHITE)
        pdf.finishPage(page)
        FileOutputStream(valid).use(pdf::writeTo)
        pdf.close()
        assertTrue(
            "a rejected corrupt file must not poison the next PdfRenderer open",
            gateway.open(Uri.fromFile(valid).toString()) is DocumentResult.Ok,
        )
    }

    @Test fun appliesExifRotationsAndReportsOrientedDimensions() = runBlocking {
        val cases = listOf(
            "normal" to ExifInterface.ORIENTATION_NORMAL,
            "rotate90" to ExifInterface.ORIENTATION_ROTATE_90,
            "rotate180" to ExifInterface.ORIENTATION_ROTATE_180,
            "rotate270" to ExifInterface.ORIENTATION_ROTATE_270,
        )
        for ((name, orientation) in cases) {
            val file = createOrientedJpeg(name, orientation)
            val gateway = AndroidDocumentGateway(context)
            val opened = gateway.open(Uri.fromFile(file).toString()) as DocumentResult.Ok
            val swapsAxes = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270
            assertEquals(if (swapsAxes) 20.0 else 40.0, opened.value.pages.single().width, 0.0)
            assertEquals(if (swapsAxes) 40.0 else 20.0, opened.value.pages.single().height, 0.0)
            val rendered = gateway.renderPage(opened.value.id, 0, RenderRequest(256)) as DocumentResult.Ok
            assertEquals(if (swapsAxes) 20 else 40, rendered.value.pixelWidth)
            assertEquals(if (swapsAxes) 40 else 20, rendered.value.pixelHeight)
            assertTrue(
                "oriented JPEG must contain its red marker",
                rendered.value.argb.any { Color.red(it) > 150 && Color.green(it) < 100 && Color.blue(it) < 100 },
            )
            gateway.close(opened.value.id)
        }
    }

    @Test fun appliesExifMirroringWithoutChangingDimensions() = runBlocking {
        val file = createOrientedJpeg("flip-horizontal", ExifInterface.ORIENTATION_FLIP_HORIZONTAL)
        val gateway = AndroidDocumentGateway(context)
        val opened = gateway.open(Uri.fromFile(file).toString()) as DocumentResult.Ok
        val rendered = gateway.renderPage(opened.value.id, 0, RenderRequest(256)) as DocumentResult.Ok
        assertEquals(40, rendered.value.pixelWidth)
        assertEquals(20, rendered.value.pixelHeight)
        val leftRed = rendered.value.argb.filterIndexed { index, _ -> index % rendered.value.pixelWidth < 10 }
            .count { Color.red(it) > 150 && Color.green(it) < 100 && Color.blue(it) < 100 }
        val rightRed = rendered.value.argb.filterIndexed { index, _ -> index % rendered.value.pixelWidth >= 30 }
            .count { Color.red(it) > 150 && Color.green(it) < 100 && Color.blue(it) < 100 }
        assertTrue("horizontal EXIF flip must move the marker to the right", rightRed > leftRed)
        gateway.close(opened.value.id)
    }

    @Test fun adaptiveRendererStressMatrixCompletesWithoutOom() = runBlocking {
        val pdfCases = listOf(
            Triple("A4", 595, 842),
            Triple("A3", 842, 1191),
            Triple("A0-like", 2384, 3370),
        )
        pdfCases.forEach { (name, width, height) ->
            val file = File(context.cacheDir, "stress-$name.pdf")
            val pdf = PdfDocument()
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
            page.canvas.drawLine(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
                color = Color.BLACK
                strokeWidth = 3f
            })
            pdf.finishPage(page)
            FileOutputStream(file).use(pdf::writeTo)
            pdf.close()
            measureRender(name, file)
        }

        val image = File(context.cacheDir, "stress-large.png")
        Bitmap.createBitmap(4096, 3072, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            FileOutputStream(image).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        measureRender("Large-PNG", image)
    }

    @Test fun rendersPdfTilesAtHigherDensityThanTheCappedPage() = runBlocking {
        val file = File(context.cacheDir, "tiled-source.pdf")
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(1000, 1000, 1).create())
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawRect(
            android.graphics.Rect(0, 0, 200, 200),
            Paint().apply { color = Color.RED },
        )
        pdf.finishPage(page)
        FileOutputStream(file).use(pdf::writeTo)
        pdf.close()

        val gateway = AndroidDocumentGateway(context)
        val opened = gateway.open(Uri.fromFile(file).toString()) as DocumentResult.Ok
        gateway.renderPage(opened.value.id, 0, RenderRequest(512))

        // 4 px per document unit is far past the page render; only a tile can supply it.
        val corner = gateway.renderTile(
            opened.value.id,
            TileRequest(pageIndex = 0, left = 0.0, top = 0.0, right = 200.0, bottom = 200.0, scale = 4.0),
        )
        assertTrue("tile render failed: $corner", corner is DocumentResult.Ok)
        val tile = (corner as DocumentResult.Ok).value
        assertEquals(800, tile.pixelWidth)
        assertEquals(800, tile.pixelHeight)
        assertTrue(
            "the marked corner must be red in its own tile",
            tile.argb.count { Color.red(it) > 150 && Color.green(it) < 100 && Color.blue(it) < 100 } >
                tile.argb.size / 2,
        )

        val empty = gateway.renderTile(
            opened.value.id,
            TileRequest(pageIndex = 0, left = 400.0, top = 400.0, right = 600.0, bottom = 600.0, scale = 4.0),
        ) as DocumentResult.Ok
        assertTrue(
            "a tile outside the marker must not contain it",
            empty.value.argb.none { Color.red(it) > 150 && Color.green(it) < 100 && Color.blue(it) < 100 },
        )

        assertTrue(
            gateway.renderTile(
                opened.value.id,
                TileRequest(pageIndex = 7, left = 0.0, top = 0.0, right = 10.0, bottom = 10.0, scale = 1.0),
            ) is DocumentResult.Error,
        )
        gateway.close(opened.value.id)
    }

    /**
     * Cross-checks the tile path against the already covered full page render: a tile must
     * show exactly what the same rectangle of the whole page shows, EXIF rotation included.
     */
    @Test fun tilesOfRotatedJpegMatchTheFullPageRender() = runBlocking {
        val file = createOrientedJpeg("tile-rotate90", ExifInterface.ORIENTATION_ROTATE_90)
        val gateway = AndroidDocumentGateway(context)
        val opened = gateway.open(Uri.fromFile(file).toString()) as DocumentResult.Ok
        assertEquals(20.0, opened.value.pages.single().width, 0.0)
        assertEquals(40.0, opened.value.pages.single().height, 0.0)

        fun redShare(pixels: List<Int>) = pixels
            .count { Color.red(it) > 150 && Color.green(it) < 100 && Color.blue(it) < 100 }
            .toDouble() / pixels.size

        val page = (gateway.renderPage(opened.value.id, 0, RenderRequest(160)) as DocumentResult.Ok).value
        val rowsPerHalf = page.pixelHeight / 2
        val pageTop = page.argb.toList().take(rowsPerHalf * page.pixelWidth)
        val pageBottom = page.argb.toList().drop(rowsPerHalf * page.pixelWidth)

        val top = (
            gateway.renderTile(
                opened.value.id,
                TileRequest(pageIndex = 0, left = 0.0, top = 0.0, right = 20.0, bottom = 20.0, scale = 4.0),
            ) as DocumentResult.Ok
            ).value
        val bottom = (
            gateway.renderTile(
                opened.value.id,
                TileRequest(pageIndex = 0, left = 0.0, top = 20.0, right = 20.0, bottom = 40.0, scale = 4.0),
            ) as DocumentResult.Ok
            ).value
        assertEquals(80, top.pixelWidth)
        assertEquals(80, top.pixelHeight)
        assertEquals(
            "the upper tile must match the upper half of the page render",
            redShare(pageTop),
            redShare(top.argb.toList()),
            0.06,
        )
        assertEquals(
            "the lower tile must match the lower half of the page render",
            redShare(pageBottom),
            redShare(bottom.argb.toList()),
            0.06,
        )
        gateway.close(opened.value.id)
    }

    private fun createOrientedJpeg(name: String, orientation: Int): File {
        val file = File(context.cacheDir, "$name.jpg")
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        for (y in 0 until 20) {
            for (x in 0 until 10) bitmap.setPixel(x, y, Color.RED)
        }
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }

    private fun withJpegXmp(jpeg: ByteArray, xmp: String): ByteArray {
        val header = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
        val body = header + xmp.toByteArray(Charsets.UTF_8)
        val length = body.size + 2
        require(jpeg.size >= 2 && jpeg[0] == 0xff.toByte() && jpeg[1] == 0xd8.toByte())
        require(length <= 65_535)
        return byteArrayOf(
            0xff.toByte(), 0xd8.toByte(),
            0xff.toByte(), 0xe1.toByte(),
            (length ushr 8).toByte(), length.toByte(),
        ) + body + jpeg.copyOfRange(2, jpeg.size)
    }

    private suspend fun measureRender(name: String, file: File) {
        val gateway = AndroidDocumentGateway(context)
        val opened = gateway.open(Uri.fromFile(file).toString()) as DocumentResult.Ok
        Runtime.getRuntime().gc()
        val beforeKb = totalPssKb()
        val firstStart = SystemClock.elapsedRealtime()
        val first = gateway.renderPage(opened.value.id, 0, RenderRequest(2048)) as DocumentResult.Ok
        val firstMs = SystemClock.elapsedRealtime() - firstStart
        val afterFirstKb = totalPssKb()
        val repeatStart = SystemClock.elapsedRealtime()
        val repeated = gateway.renderPage(opened.value.id, 0, RenderRequest(2048)) as DocumentResult.Ok
        val repeatMs = SystemClock.elapsedRealtime() - repeatStart
        val zoomStart = SystemClock.elapsedRealtime()
        val zoomed = gateway.renderPage(opened.value.id, 0, RenderRequest(3072)) as DocumentResult.Ok
        val zoomMs = SystemClock.elapsedRealtime() - zoomStart
        val peakDeltaKb = (maxOf(afterFirstKb, totalPssKb()) - beforeKb).coerceAtLeast(0)
        assertTrue(first.value.argb.isNotEmpty())
        assertEquals(first.value.pixelWidth, repeated.value.pixelWidth)
        assertTrue(maxOf(zoomed.value.pixelWidth, zoomed.value.pixelHeight) <= 3072)
        Log.i(
            "PLANRULER_RENDER_PERF",
            "$name source=${opened.value.pages[0].width.toInt()}x${opened.value.pages[0].height.toInt()} " +
                "bitmap=${first.value.pixelWidth}x${first.value.pixelHeight} first_ms=$firstMs " +
                "repeat_ms=$repeatMs peak_delta_kb=$peakDeltaKb zoom_ms=$zoomMs oom=no",
        )
        gateway.close(opened.value.id)
    }

    private fun totalPssKb(): Int = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss
}
