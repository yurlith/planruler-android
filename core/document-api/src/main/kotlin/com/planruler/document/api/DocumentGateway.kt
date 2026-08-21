package com.planruler.document.api

import com.planruler.model.DocumentId
import com.planruler.model.PageMetadata

enum class DocumentKind { PDF, IMAGE }
data class OpenedDocument(
    val id: DocumentId,
    val title: String,
    val mimeType: String,
    val kind: DocumentKind,
    val pages: List<PageMetadata>,
    /** Present for images when metadata inspection succeeded. */
    val captureEvidence: CaptureEvidence? = null,
)
data class RenderRequest(val maxEdgePixels: Int = 2048, val rotationDegrees: Int = 0)
data class RenderedPage(
    val documentId: DocumentId,
    val pageIndex: Int,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val source: PageMetadata,
    val argb: IntArray,
)
sealed interface DocumentError {
    data object UnsupportedFormat : DocumentError
    data object AccessLost : DocumentError
    data object CorruptDocument : DocumentError
    data object OutOfMemory : DocumentError
    data class PageUnavailable(val index: Int) : DocumentError
    data class Io(val message: String) : DocumentError
}
sealed interface DocumentResult<out T> {
    data class Ok<T>(val value: T) : DocumentResult<T>
    data class Error(val error: DocumentError) : DocumentResult<Nothing>
}
interface DocumentGateway {
    suspend fun open(uri: String): DocumentResult<OpenedDocument>
    suspend fun renderPage(documentId: DocumentId, pageIndex: Int, request: RenderRequest): DocumentResult<RenderedPage>
    suspend fun close(documentId: DocumentId)
}

/**
 * Region renderer. The full-page render is capped at [RenderRequest.maxEdgePixels], so
 * deep zoom is served by re-rendering only the visible rectangle at the needed density.
 */
data class TileRequest(
    val pageIndex: Int,
    /** Tile rectangle in document coordinates. */
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    /** Rendered pixels per document unit. */
    val scale: Double,
    val rotationDegrees: Int = 0,
) {
    val documentWidth get() = right - left
    val documentHeight get() = bottom - top
}

data class RenderedTile(
    val documentId: DocumentId,
    val pageIndex: Int,
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val argb: IntArray,
) {
    /** Identity: tiles are large pixel buffers and are never compared by value. */
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

interface TileDocumentGateway {
    suspend fun renderTile(documentId: DocumentId, request: TileRequest): DocumentResult<RenderedTile>
}

/**
 * EXIF orientation as a plain transform, free of Android types so the region maths
 * can be unit tested. Values match the EXIF specification.
 */
enum class ImageOrientation(val exif: Int) {
    NORMAL(1),
    FLIP_HORIZONTAL(2),
    ROTATE_180(3),
    FLIP_VERTICAL(4),
    TRANSPOSE(5),
    ROTATE_90(6),
    TRANSVERSE(7),
    ROTATE_270(8);

    /** True when the transform exchanges the width and the height. */
    val swapsAxes get() = this == TRANSPOSE || this == ROTATE_90 || this == TRANSVERSE || this == ROTATE_270

    companion object {
        fun ofExif(value: Int) = entries.firstOrNull { it.exif == value } ?: NORMAL
    }
}

data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/**
 * Maps a rectangle expressed in oriented (user visible) pixels back to the raw pixels a
 * region decoder understands. Without it a tile of a rotated JPEG would decode the wrong
 * part of the file.
 */
fun ImageOrientation.toRawRegion(rawWidth: Int, rawHeight: Int, region: PixelRect): PixelRect {
    fun map(x: Int, y: Int): Pair<Int, Int> = when (this) {
        ImageOrientation.NORMAL -> x to y
        ImageOrientation.FLIP_HORIZONTAL -> (rawWidth - x) to y
        ImageOrientation.ROTATE_180 -> (rawWidth - x) to (rawHeight - y)
        ImageOrientation.FLIP_VERTICAL -> x to (rawHeight - y)
        ImageOrientation.TRANSPOSE -> y to x
        ImageOrientation.ROTATE_90 -> y to (rawHeight - x)
        ImageOrientation.TRANSVERSE -> (rawWidth - y) to (rawHeight - x)
        ImageOrientation.ROTATE_270 -> (rawWidth - y) to x
    }
    val corners = listOf(
        map(region.left, region.top),
        map(region.right, region.top),
        map(region.left, region.bottom),
        map(region.right, region.bottom),
    )
    return PixelRect(
        left = corners.minOf { it.first }.coerceIn(0, rawWidth),
        top = corners.minOf { it.second }.coerceIn(0, rawHeight),
        right = corners.maxOf { it.first }.coerceIn(0, rawWidth),
        bottom = corners.maxOf { it.second }.coerceIn(0, rawHeight),
    )
}
