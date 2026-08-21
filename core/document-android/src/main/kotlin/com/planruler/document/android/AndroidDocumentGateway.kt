package com.planruler.document.android

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import com.planruler.document.api.*
import com.planruler.model.DocumentId
import com.planruler.model.PageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

class AndroidDocumentGateway(context: Context) : DocumentGateway, TileDocumentGateway {
    private val app = context.applicationContext
    private val photoMetadataInspector = AndroidPhotoMetadataInspector(app)
    private val open = ConcurrentHashMap<DocumentId, Stored>()
    private val cache = object : LruCache<String, RenderedPage>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: RenderedPage) = value.argb.size * 4
    }
    private val tiles = object : LruCache<String, RenderedTile>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: RenderedTile) = value.argb.size * 4
    }

    override suspend fun open(uri: String): DocumentResult<OpenedDocument> = withContext(Dispatchers.IO) {
        try {
            val parsed = Uri.parse(uri)
            if (BlankDocument.isBlankUri(uri)) {
                val id = DocumentId(UUID.randomUUID().toString())
                val pages = listOf(BlankDocument.page)
                open[id] = Stored(
                    uri = parsed,
                    title = BlankDocument.TITLE,
                    mime = BlankDocument.MIME_TYPE,
                    kind = DocumentKind.BLANK,
                    pages = pages,
                    imageOrientation = ExifInterface.ORIENTATION_NORMAL,
                )
                return@withContext DocumentResult.Ok(
                    OpenedDocument(
                        id = id,
                        title = BlankDocument.TITLE,
                        mimeType = BlankDocument.MIME_TYPE,
                        kind = DocumentKind.BLANK,
                        pages = pages,
                    ),
                )
            }
            runCatching {
                app.contentResolver.takePersistableUriPermission(parsed, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val name = displayName(parsed) ?: parsed.lastPathSegment ?: "Plan"
            val mime = app.contentResolver.getType(parsed).orEmpty()
            val kind = when {
                mime == "application/pdf" || name.endsWith(".pdf", true) -> DocumentKind.PDF
                mime.startsWith("image/") || listOf(".png", ".jpg", ".jpeg").any { name.endsWith(it, true) } -> DocumentKind.IMAGE
                else -> sniffDocumentKind(parsed)
                    ?: return@withContext DocumentResult.Error(DocumentError.UnsupportedFormat)
            }
            val imageInfo = if (kind == DocumentKind.IMAGE) readImageInfo(parsed) else null
            val pages = when (kind) {
                DocumentKind.PDF -> {
                    require(hasPdfStructure(parsed)) { "Invalid PDF structure" }
                    usePdf(parsed) { renderer ->
                    (0 until renderer.pageCount).map { index ->
                        renderer.openPage(index).use { page ->
                            PageMetadata(index, page.width.toDouble(), page.height.toDouble(), PageMetadata.CoordinateUnit.PDF_POINT)
                        }
                    }
                }
                }
                DocumentKind.IMAGE -> listOf(requireNotNull(imageInfo).metadata)
                DocumentKind.BLANK -> error("Blank documents are opened before provider inspection")
            }
            val id = DocumentId(UUID.randomUUID().toString())
            open[id] = Stored(
                parsed,
                name,
                mime.ifBlank { if (kind == DocumentKind.PDF) "application/pdf" else "image/*" },
                kind,
                pages,
                imageInfo?.orientation ?: ExifInterface.ORIENTATION_NORMAL,
            )
            DocumentResult.Ok(
                OpenedDocument(
                    id = id,
                    title = name,
                    mimeType = open.getValue(id).mime,
                    kind = kind,
                    pages = pages,
                    captureEvidence = imageInfo?.captureEvidence,
                ),
            )
        } catch (_: FileNotFoundException) {
            DocumentResult.Error(DocumentError.AccessLost)
        } catch (_: SecurityException) {
            DocumentResult.Error(DocumentError.AccessLost)
        } catch (_: OutOfMemoryError) {
            DocumentResult.Error(DocumentError.OutOfMemory)
        } catch (_: Exception) {
            DocumentResult.Error(DocumentError.CorruptDocument)
        }
    }

    override suspend fun renderPage(documentId: DocumentId, pageIndex: Int, request: RenderRequest): DocumentResult<RenderedPage> =
        withContext(Dispatchers.IO) {
            val stored = open[documentId] ?: return@withContext DocumentResult.Error(DocumentError.AccessLost)
            if (pageIndex !in stored.pages.indices) return@withContext DocumentResult.Error(DocumentError.PageUnavailable(pageIndex))
            val maxEdge = request.maxEdgePixels.coerceIn(256, 4096)
            val key = "${documentId.value}:$pageIndex:$maxEdge:${request.rotationDegrees}"
            cache.get(key)?.let { return@withContext DocumentResult.Ok(it) }
            try {
                coroutineContext.ensureActive()
                val rendered = when (stored.kind) {
                    DocumentKind.PDF -> renderPdf(stored, documentId, pageIndex, maxEdge, request.rotationDegrees)
                    DocumentKind.IMAGE -> renderImage(stored, documentId, maxEdge, request.rotationDegrees)
                    DocumentKind.BLANK -> renderBlank(stored, documentId, pageIndex, maxEdge, request.rotationDegrees)
                }
                coroutineContext.ensureActive()
                cache.put(key, rendered)
                DocumentResult.Ok(rendered)
            } catch (_: OutOfMemoryError) {
                cache.evictAll()
                DocumentResult.Error(DocumentError.OutOfMemory)
            } catch (_: SecurityException) {
                DocumentResult.Error(DocumentError.AccessLost)
            } catch (e: Exception) {
                DocumentResult.Error(DocumentError.Io(e.message ?: "Cannot render page"))
            }
        }

    /**
     * Renders one visible rectangle at full requested density. The page render is capped at
     * 4096 px, so this is what makes deep zoom readable without holding a huge bitmap.
     */
    override suspend fun renderTile(documentId: DocumentId, request: TileRequest): DocumentResult<RenderedTile> =
        withContext(Dispatchers.IO) {
            val stored = open[documentId] ?: return@withContext DocumentResult.Error(DocumentError.AccessLost)
            if (request.pageIndex !in stored.pages.indices) {
                return@withContext DocumentResult.Error(DocumentError.PageUnavailable(request.pageIndex))
            }
            val metadata = stored.pages[request.pageIndex]
            val left = request.left.coerceIn(0.0, metadata.width)
            val top = request.top.coerceIn(0.0, metadata.height)
            val right = request.right.coerceIn(left, metadata.width)
            val bottom = request.bottom.coerceIn(top, metadata.height)
            if (right - left <= 0.0 || bottom - top <= 0.0) {
                return@withContext DocumentResult.Error(DocumentError.Io("Empty tile"))
            }
            val scale = request.scale.coerceIn(0.01, MAX_TILE_SCALE)
            val pixelWidth = ((right - left) * scale).roundToInt().coerceIn(1, MAX_TILE_EDGE)
            val pixelHeight = ((bottom - top) * scale).roundToInt().coerceIn(1, MAX_TILE_EDGE)
            val key = "${documentId.value}:${request.pageIndex}:$left:$top:$right:$bottom:$pixelWidth:$pixelHeight"
            tiles.get(key)?.let { return@withContext DocumentResult.Ok(it) }
            try {
                coroutineContext.ensureActive()
                val bitmap = when (stored.kind) {
                    DocumentKind.PDF -> pdfTile(stored, request.pageIndex, left, top, right, bottom, pixelWidth, pixelHeight)
                    DocumentKind.IMAGE -> imageTile(stored, metadata, left, top, right, bottom, pixelWidth, pixelHeight)
                    DocumentKind.BLANK -> blankTile(pixelWidth, pixelHeight)
                }
                coroutineContext.ensureActive()
                val tile = try {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    RenderedTile(
                        documentId, request.pageIndex, left, top, right, bottom,
                        bitmap.width, bitmap.height, pixels,
                    )
                } finally {
                    bitmap.recycle()
                }
                tiles.put(key, tile)
                DocumentResult.Ok(tile)
            } catch (_: OutOfMemoryError) {
                tiles.evictAll()
                DocumentResult.Error(DocumentError.OutOfMemory)
            } catch (_: SecurityException) {
                DocumentResult.Error(DocumentError.AccessLost)
            } catch (e: Exception) {
                DocumentResult.Error(DocumentError.Io(e.message ?: "Cannot render tile"))
            }
        }

    private fun pdfTile(
        stored: Stored,
        pageIndex: Int,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        pixelWidth: Int,
        pixelHeight: Int,
    ): Bitmap = usePdf(stored.uri) { renderer ->
        renderer.openPage(pageIndex).use { page ->
            val scaleX = pixelWidth / (right - left)
            val scaleY = pixelHeight / (bottom - top)
            Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(android.graphics.Color.WHITE)
                val matrix = Matrix().apply {
                    setScale(scaleX.toFloat(), scaleY.toFloat())
                    postTranslate((-left * scaleX).toFloat(), (-top * scaleY).toFloat())
                }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    /**
     * Region decoding reads only the requested pixels off disk. The rectangle arrives in
     * oriented coordinates, so it is mapped back through the EXIF transform first.
     */
    private fun imageTile(
        stored: Stored,
        metadata: PageMetadata,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        pixelWidth: Int,
        pixelHeight: Int,
    ): Bitmap {
        val orientation = ImageOrientation.ofExif(stored.imageOrientation)
        val rawWidth = if (orientation.swapsAxes) metadata.height.roundToInt() else metadata.width.roundToInt()
        val rawHeight = if (orientation.swapsAxes) metadata.width.roundToInt() else metadata.height.roundToInt()
        val region = orientation.toRawRegion(
            rawWidth,
            rawHeight,
            PixelRect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt()),
        )
        if (region.width <= 0 || region.height <= 0) {
            throw IllegalStateException("Empty image region")
        }
        var sample = 1
        while (max(region.width / sample, region.height / sample) > max(pixelWidth, pixelHeight) * 2) sample *= 2
        val decoded = app.contentResolver.openInputStream(stored.uri).use { stream ->
            val decoder = newRegionDecoder(requireNotNull(stream))
            try {
                requireNotNull(
                    decoder.decodeRegion(
                        android.graphics.Rect(region.left, region.top, region.right, region.bottom),
                        BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        },
                    ),
                )
            } finally {
                decoder.recycle()
            }
        }
        val oriented = decoded.applyExifOrientation(stored.imageOrientation)
        if (oriented !== decoded) decoded.recycle()
        return if (oriented.width == pixelWidth && oriented.height == pixelHeight) {
            oriented
        } else {
            Bitmap.createScaledBitmap(oriented, pixelWidth, pixelHeight, true)
                .also { if (it !== oriented) oriented.recycle() }
        }
    }

    @Suppress("DEPRECATION")
    private fun newRegionDecoder(stream: java.io.InputStream): BitmapRegionDecoder =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            requireNotNull(BitmapRegionDecoder.newInstance(stream))
        } else {
            requireNotNull(BitmapRegionDecoder.newInstance(stream, false))
        }

    override suspend fun close(documentId: DocumentId) {
        open.remove(documentId)
        cache.snapshot().keys.filter { it.startsWith("${documentId.value}:") }.forEach(cache::remove)
        tiles.snapshot().keys.filter { it.startsWith("${documentId.value}:") }.forEach(tiles::remove)
    }

    private fun renderPdf(stored: Stored, id: DocumentId, index: Int, maxEdge: Int, rotation: Int) =
        usePdf(stored.uri) { renderer ->
            renderer.openPage(index).use { page ->
                val scale = maxEdge.toDouble() / max(page.width, page.height)
                val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, Matrix().apply { setScale(scale.toFloat(), scale.toFloat()) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.toRendered(id, index, stored.pages[index], rotation)
                } finally { bitmap.recycle() }
            }
        }

    private fun renderImage(stored: Stored, id: DocumentId, maxEdge: Int, rotation: Int): RenderedPage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        app.contentResolver.openInputStream(stored.uri).use { BitmapFactory.decodeStream(requireNotNull(it), null, bounds) }
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxEdge * 2) sample *= 2
        val decoded = app.contentResolver.openInputStream(stored.uri).use {
            requireNotNull(BitmapFactory.decodeStream(requireNotNull(it), null, BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
            }))
        }
        val scaled = if (max(decoded.width, decoded.height) > maxEdge) {
            val factor = maxEdge.toDouble() / max(decoded.width, decoded.height)
            Bitmap.createScaledBitmap(decoded, (decoded.width * factor).roundToInt(), (decoded.height * factor).roundToInt(), true)
                .also { if (it !== decoded) decoded.recycle() }
        } else decoded
        val oriented = scaled.applyExifOrientation(stored.imageOrientation)
        return try {
            oriented.toRendered(id, 0, stored.pages[0], rotation)
        } finally {
            if (oriented !== scaled) oriented.recycle()
            scaled.recycle()
        }
    }

    private fun renderBlank(stored: Stored, id: DocumentId, index: Int, maxEdge: Int, rotation: Int): RenderedPage {
        val metadata = stored.pages[index]
        val scale = maxEdge.toDouble() / max(metadata.width, metadata.height)
        val width = (metadata.width * scale).roundToInt().coerceAtLeast(1)
        val height = (metadata.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = blankTile(width, height)
        return try {
            bitmap.toRendered(id, index, metadata, rotation)
        } finally {
            bitmap.recycle()
        }
    }

    private fun blankTile(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }

    private fun Bitmap.toRendered(id: DocumentId, index: Int, metadata: PageMetadata, rotation: Int): RenderedPage {
        val normalized = ((rotation % 360) + 360) % 360
        val rotated = if (normalized == 0) this else Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(normalized.toFloat()) }, true)
        return try {
            val pixels = IntArray(rotated.width * rotated.height)
            rotated.getPixels(pixels, 0, rotated.width, 0, 0, rotated.width, rotated.height)
            RenderedPage(id, index, rotated.width, rotated.height, metadata.copy(rotationDegrees = normalized), pixels)
        } finally { if (rotated !== this) rotated.recycle() }
    }

    private fun readImageInfo(uri: Uri): ImageInfo {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        app.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(requireNotNull(it), null, options) }
        require(options.outWidth > 0 && options.outHeight > 0)
        val captureEvidence = runCatching {
            photoMetadataInspector.inspect(uri, options.outWidth, options.outHeight)
        }.getOrNull()
        val orientation = captureEvidence?.metadata?.orientation
            ?.takeIf { it in SUPPORTED_EXIF_ORIENTATIONS }
            ?: app.contentResolver.openInputStream(uri).use {
                ExifInterface(requireNotNull(it)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.takeIf { it in SUPPORTED_EXIF_ORIENTATIONS }
            ?: ExifInterface.ORIENTATION_NORMAL
        val swapsAxes = orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270
        val width = if (swapsAxes) options.outHeight else options.outWidth
        val height = if (swapsAxes) options.outWidth else options.outHeight
        return ImageInfo(
            PageMetadata(0, width.toDouble(), height.toDouble(), PageMetadata.CoordinateUnit.IMAGE_PIXEL),
            orientation,
            captureEvidence,
        )
    }

    /** File names and provider MIME declarations are hints; the encoded bytes are authoritative. */
    private fun sniffDocumentKind(uri: Uri): DocumentKind? {
        val header = app.contentResolver.openInputStream(uri).use { input ->
            val source = requireNotNull(input)
            val bytes = ByteArray(32)
            val count = source.read(bytes)
            if (count <= 0) byteArrayOf() else bytes.copyOf(count)
        }
        fun ascii(offset: Int, value: String) = offset >= 0 && offset + value.length <= header.size &&
            value.indices.all { header[offset + it] == value[it].code.toByte() }
        return when {
            ascii(0, "%PDF-") -> DocumentKind.PDF
            header.size >= 2 && header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() -> DocumentKind.IMAGE
            header.size >= 8 && header.copyOfRange(0, 8).contentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
            ) -> DocumentKind.IMAGE
            ascii(0, "RIFF") && ascii(8, "WEBP") -> DocumentKind.IMAGE
            ascii(4, "ftyp") -> DocumentKind.IMAGE // HEIF/HEIC/AVIF family; decoder validates it next.
            else -> null
        }
    }

    private fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return this
        }
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    setRotate(-90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
    private inline fun <T> usePdf(uri: Uri, block: (PdfRenderer) -> T): T =
        openPdfDescriptor(uri).use { descriptor -> PdfRenderer(descriptor).use(block) }
    private fun openPdfDescriptor(uri: Uri): ParcelFileDescriptor =
        if (uri.scheme == "file") {
            ParcelFileDescriptor.open(
                File(requireNotNull(uri.path)),
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
        } else {
            requireNotNull(app.contentResolver.openFileDescriptor(uri, "r"))
        }
    private fun displayName(uri: Uri): String? = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
    /**
     * Rejects obviously truncated/spoofed PDFs before they reach PdfRenderer.
     * This is intentionally streaming and bounded: large plans are not copied
     * into memory merely for validation.
     */
    private fun hasPdfStructure(uri: Uri): Boolean = app.contentResolver.openInputStream(uri).use { raw ->
        val input = requireNotNull(raw)
        val header = ByteArray(PDF_HEADER.size)
        if (input.read(header) != header.size || !header.contentEquals(PDF_HEADER)) return@use false
        var markerIndex = 0
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            for (index in 0 until count) {
                val byte = buffer[index]
                markerIndex = when {
                    byte == PDF_EOF[markerIndex] -> markerIndex + 1
                    byte == PDF_EOF[0] -> 1
                    else -> 0
                }
                if (markerIndex == PDF_EOF.size) return@use true
            }
        }
        false
    }
    private data class ImageInfo(
        val metadata: PageMetadata,
        val orientation: Int,
        val captureEvidence: CaptureEvidence?,
    )
    private data class Stored(
        val uri: Uri,
        val title: String,
        val mime: String,
        val kind: DocumentKind,
        val pages: List<PageMetadata>,
        val imageOrientation: Int,
    )

    private companion object {
        const val MAX_TILE_EDGE = 2048
        const val MAX_TILE_SCALE = 64.0
        val PDF_HEADER = "%PDF-".toByteArray(Charsets.US_ASCII)
        val PDF_EOF = "%%EOF".toByteArray(Charsets.US_ASCII)
        val SUPPORTED_EXIF_ORIENTATIONS = setOf(
            ExifInterface.ORIENTATION_UNDEFINED,
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_ROTATE_180,
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )
    }
}
