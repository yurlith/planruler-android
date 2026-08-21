package com.planruler.document.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.os.Build
import com.planruler.document.api.CaptureContainerInput
import com.planruler.document.api.DepthDecodeFailure
import com.planruler.document.api.DepthDecodeReport
import com.planruler.document.api.DepthDecodeStatus
import com.planruler.document.api.DepthEncoding
import com.planruler.document.api.DepthMapNormalizer
import com.planruler.document.api.DepthMeasureType
import com.planruler.document.api.DepthPayloadLocation
import com.planruler.document.api.DepthStandard
import com.planruler.document.api.MetricDepthMap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Base64
import java.util.Locale
import java.util.zip.InflaterInputStream

internal data class DepthDecodingOutcome(
    val container: CaptureContainerInput,
    val report: DepthDecodeReport,
)

internal data class DecodedDepthRaster(
    val width: Int,
    val height: Int,
    val normalizedSamples: FloatArray,
    val bitDepth: Int,
    val decoderName: String,
)

internal fun interface DepthRasterDecoder {
    fun decode(encoded: ByteArray): DecodedDepthRaster?
}

/**
 * Decodes depth from either legacy GDepth XMP or a Dynamic Depth concatenated container.
 * Selection is based on content, never on a file name or extension.
 */
internal class DepthContainerDecoder(
    private val rasterDecoder: DepthRasterDecoder = AndroidDepthRasterDecoder,
) {
    fun decode(fileBytes: ByteArray, fallback: CaptureContainerInput): DepthDecodingOutcome {
        if (fileBytes.size > MAX_FILE_BYTES) {
            return DepthDecodingOutcome(
                fallback,
                DepthDecodeReport(
                    status = DepthDecodeStatus.RESOURCE_LIMIT_EXCEEDED,
                    failure = DepthDecodeFailure.FILE_TOO_LARGE,
                ),
            )
        }
        val packets = XmpPacketExtractor.extract(fileBytes)
        val xmp = packets.joinToString("\n")
        val completeSignals = if (xmp.isBlank()) {
            fallback
        } else {
            val tail = fileBytes.copyOfRange((fileBytes.size - TAIL_SCAN_BYTES).coerceAtLeast(0), fileBytes.size)
            mergeContainerSignals(fallback, ContainerSignalScanner.scan(xmp.toByteArray(), tail))
        }

        val gDepth = parseGDepth(xmp)
        if (gDepth != null) {
            return DepthDecodingOutcome(
                completeSignals.withDepthDescriptor(gDepth),
                decodeDescriptor(gDepth),
            )
        }

        val dynamic = parseDynamicDepth(xmp, fileBytes)
        if (dynamic != null) {
            return DepthDecodingOutcome(
                completeSignals.withDepthDescriptor(dynamic),
                decodeDescriptor(dynamic),
            )
        }

        val report = when {
            DepthStandard.APPLE_AUXILIARY in completeSignals.depthStandards -> DepthDecodeReport(
                status = DepthDecodeStatus.UNSUPPORTED_PAYLOAD,
                failure = DepthDecodeFailure.APPLE_AUXILIARY_UNAVAILABLE_ON_ANDROID,
                payloadLocation = DepthPayloadLocation.ISO_AUXILIARY_ITEM,
            )
            completeSignals.depthPayloadConfirmed -> DepthDecodeReport(
                status = DepthDecodeStatus.MALFORMED_METADATA,
                failure = DepthDecodeFailure.PAYLOAD_NOT_FOUND,
            )
            else -> DepthDecodeReport()
        }
        return DepthDecodingOutcome(completeSignals, report)
    }

    private fun decodeDescriptor(descriptor: DepthPayloadDescriptor): DepthDecodeReport {
        if (descriptor.preflightFailure != DepthDecodeFailure.NONE) {
            return descriptor.failure(DepthDecodeStatus.MALFORMED_METADATA, descriptor.preflightFailure)
        }
        if (descriptor.payload.size > MAX_PAYLOAD_BYTES) {
            return descriptor.failure(
                DepthDecodeStatus.RESOURCE_LIMIT_EXCEEDED,
                DepthDecodeFailure.PAYLOAD_TOO_LARGE,
            )
        }
        val raster = rasterDecoder.decode(descriptor.payload)
            ?: return descriptor.failure(DepthDecodeStatus.UNSUPPORTED_PAYLOAD, DepthDecodeFailure.UNSUPPORTED_RASTER)
        if (raster.width <= 0 || raster.height <= 0 ||
            raster.width.toLong() * raster.height.toLong() > MAX_DEPTH_PIXELS ||
            raster.normalizedSamples.size.toLong() != raster.width.toLong() * raster.height.toLong()
        ) {
            return descriptor.failure(DepthDecodeStatus.RESOURCE_LIMIT_EXCEEDED, DepthDecodeFailure.INVALID_DIMENSIONS)
        }

        val meters = DepthMapNormalizer.normalizedRasterToMeters(
            normalizedSamples = raster.normalizedSamples,
            encoding = descriptor.encoding,
            near = descriptor.near,
            far = descriptor.far,
            units = descriptor.units,
        )
        if (meters == null) {
            val failure = if (DepthMapNormalizer.metricUnitScale(descriptor.units) == null) {
                DepthDecodeFailure.NON_METRIC_UNITS
            } else {
                DepthDecodeFailure.INVALID_RANGE
            }
            return descriptor.failure(
                status = DepthDecodeStatus.DECODED_RELATIVE,
                failure = failure,
                raster = raster,
            )
        }

        val confidence = descriptor.confidencePayload?.let(rasterDecoder::decode)?.let { decoded ->
            resample(decoded.normalizedSamples, decoded.width, decoded.height, raster.width, raster.height)
        }
        val map = MetricDepthMap(
            width = raster.width,
            height = raster.height,
            depthMeters = meters,
            confidence = confidence,
            encoding = descriptor.encoding,
            measureType = descriptor.measureType,
            standard = descriptor.standard,
            sourceMime = descriptor.mime ?: sniffMime(descriptor.payload),
            sourceBitDepth = raster.bitDepth,
        )
        return DepthDecodeReport(
            status = DepthDecodeStatus.DECODED_METRIC,
            payloadLocation = descriptor.location,
            decoder = raster.decoderName,
            sourceMime = descriptor.mime ?: sniffMime(descriptor.payload),
            sourceBitDepth = raster.bitDepth,
            decodedWidth = raster.width,
            decodedHeight = raster.height,
            map = map,
        )
    }

    private fun parseGDepth(xmp: String): DepthPayloadDescriptor? {
        if (!xmp.contains("GDepth:", ignoreCase = true)) return null
        val encoded = xmpField(xmp, "GDepth:Data") ?: return null
        val payload = decodeBase64(encoded) ?: return DepthPayloadDescriptor.invalidBase64(
            standard = DepthStandard.GDEPTH,
            location = DepthPayloadLocation.XMP_BASE64,
        )
        val confidence = xmpField(xmp, "GDepth:Confidence")?.let(::decodeBase64)
        return DepthPayloadDescriptor(
            standard = DepthStandard.GDEPTH,
            location = DepthPayloadLocation.XMP_BASE64,
            payload = payload,
            confidencePayload = confidence,
            mime = xmpField(xmp, "GDepth:Mime") ?: sniffMime(payload),
            encoding = parseEncoding(xmpField(xmp, "GDepth:Format")),
            near = xmpField(xmp, "GDepth:Near")?.toDoubleOrNull(),
            far = xmpField(xmp, "GDepth:Far")?.toDoubleOrNull(),
            units = xmpField(xmp, "GDepth:Units") ?: "m",
            measureType = parseMeasureType(xmpField(xmp, "GDepth:MeasureType")),
        )
    }

    private fun parseDynamicDepth(xmp: String, fileBytes: ByteArray): DepthPayloadDescriptor? {
        if (!xmp.contains("ns.google.com/photos/dd/1.0", ignoreCase = true) &&
            !xmp.contains("DepthMap:", ignoreCase = true)
        ) return null
        val depthUri = xmpField(xmp, "DepthMap:DepthURI") ?: return null
        val items = parseContainerItems(xmp)
        if (items.size < 2) return null
        val payloads = sliceConcatenatedItems(fileBytes, items) ?: return null
        val depthItem = items.firstOrNull { it.dataUri == depthUri } ?: return null
        val payload = payloads[depthUri] ?: return null
        val confidenceUri = xmpField(xmp, "DepthMap:ConfidenceURI")
        return DepthPayloadDescriptor(
            standard = DepthStandard.DYNAMIC_DEPTH,
            location = DepthPayloadLocation.CONCATENATED_CONTAINER_ITEM,
            payload = payload,
            confidencePayload = confidenceUri?.let(payloads::get),
            mime = depthItem.mime ?: sniffMime(payload),
            encoding = parseEncoding(xmpField(xmp, "DepthMap:Format")),
            near = xmpField(xmp, "DepthMap:Near")?.toDoubleOrNull(),
            far = xmpField(xmp, "DepthMap:Far")?.toDoubleOrNull(),
            units = xmpField(xmp, "DepthMap:Units"),
            measureType = parseMeasureType(xmpField(xmp, "DepthMap:MeasureType")),
        )
    }

    private fun parseContainerItems(xmp: String): List<ContainerItem> = Regex(
        "<rdf:li\\b(?:[^>]*/>|[\\s\\S]*?</rdf:li>)",
        setOf(RegexOption.IGNORE_CASE),
    ).findAll(xmp).mapNotNull { match ->
        val block = match.value
        val mime = xmpField(block, "(?:Item|GContainerItem):Mime")
        val length = xmpField(block, "(?:Item|GContainerItem):Length")?.toLongOrNull()
        val padding = xmpField(block, "(?:Item|GContainerItem):Padding")?.toLongOrNull() ?: 0L
        val dataUri = xmpField(block, "(?:Item|GContainerItem):DataURI")
        if (mime == null && length == null && dataUri == null) null else ContainerItem(
            mime = mime,
            length = length ?: 0L,
            padding = padding,
            dataUri = dataUri,
        )
    }.toList()

    /** Uses the directory lengths from the end, so primary JPEG/PNG/HEIF parsing is unnecessary. */
    private fun sliceConcatenatedItems(file: ByteArray, items: List<ContainerItem>): Map<String, ByteArray>? {
        val secondaryLength = items.drop(1).sumOf { it.length.coerceAtLeast(0L) }
        if (secondaryLength <= 0L || secondaryLength > file.size || secondaryLength > MAX_PAYLOAD_BYTES * 2L) return null
        var cursor = file.size - secondaryLength.toInt()
        var previous: ByteArray? = null
        val result = linkedMapOf<String, ByteArray>()
        items.drop(1).forEach { item ->
            val payload = if (item.length == 0L) {
                previous ?: return null
            } else {
                if (item.length > Int.MAX_VALUE || cursor + item.length > file.size) return null
                file.copyOfRange(cursor, cursor + item.length.toInt()).also { cursor += item.length.toInt() }
            }
            previous = payload
            item.dataUri?.let { result[it] = payload }
        }
        return result.takeIf { cursor == file.size }
    }

    private fun parseEncoding(value: String?): DepthEncoding = when (value?.trim()?.lowercase(Locale.ROOT)) {
        "rangelinear" -> DepthEncoding.RANGE_LINEAR
        "rangeinverse" -> DepthEncoding.RANGE_INVERSE
        "axial", "depth" -> DepthEncoding.AXIAL
        "rayrange" -> DepthEncoding.RAY_RANGE
        "disparity" -> DepthEncoding.DISPARITY
        else -> DepthEncoding.UNKNOWN
    }

    private fun parseMeasureType(value: String?): DepthMeasureType = when (value?.trim()?.lowercase(Locale.ROOT)) {
        null, "", "opticalaxis" -> DepthMeasureType.OPTICAL_AXIS
        "opticray" -> DepthMeasureType.OPTIC_RAY
        else -> DepthMeasureType.UNKNOWN
    }

    private fun resample(
        values: FloatArray,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): FloatArray? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || values.size != sourceWidth * sourceHeight) return null
        if (sourceWidth == targetWidth && sourceHeight == targetHeight) return values
        return FloatArray(targetWidth * targetHeight) { index ->
            val targetX = index % targetWidth
            val targetY = index / targetWidth
            val sourceX = ((targetX + 0.5) * sourceWidth / targetWidth).toInt().coerceIn(0, sourceWidth - 1)
            val sourceY = ((targetY + 0.5) * sourceHeight / targetHeight).toInt().coerceIn(0, sourceHeight - 1)
            values[sourceY * sourceWidth + sourceX]
        }
    }

    private fun xmpField(text: String, namePattern: String): String? {
        val attribute = Regex(
            "$namePattern\\s*=\\s*[\"']([^\"']*)[\"']",
            setOf(RegexOption.IGNORE_CASE),
        ).find(text)?.groupValues?.getOrNull(1)
        val element = Regex(
            "<$namePattern(?:\\s[^>]*)?>([\\s\\S]*?)</$namePattern>",
            setOf(RegexOption.IGNORE_CASE),
        ).find(text)?.groupValues?.getOrNull(1)
        return (attribute ?: element)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun decodeBase64(value: String): ByteArray? {
        val compact = value.filterNot(Char::isWhitespace)
        if (compact.length > MAX_BASE64_CHARACTERS) return null
        return runCatching { Base64.getDecoder().decode(compact) }
            .recoverCatching { Base64.getUrlDecoder().decode(compact) }
            .getOrNull()
    }

    private fun CaptureContainerInput.withDepthDescriptor(value: DepthPayloadDescriptor): CaptureContainerInput = copy(
        depthStandards = depthStandards + value.standard,
        depthEncoding = value.encoding,
        depthUnits = value.units,
        depthNear = value.near,
        depthFar = value.far,
        depthPayloadConfirmed = value.payload.isNotEmpty(),
        confidencePayloadConfirmed = value.confidencePayload?.isNotEmpty() == true,
    )

    private data class ContainerItem(
        val mime: String?,
        val length: Long,
        val padding: Long,
        val dataUri: String?,
    )

    private data class DepthPayloadDescriptor(
        val standard: DepthStandard,
        val location: DepthPayloadLocation,
        val payload: ByteArray,
        val confidencePayload: ByteArray?,
        val mime: String?,
        val encoding: DepthEncoding,
        val near: Double?,
        val far: Double?,
        val units: String?,
        val measureType: DepthMeasureType,
        val preflightFailure: DepthDecodeFailure = DepthDecodeFailure.NONE,
    ) {
        fun failure(
            status: DepthDecodeStatus,
            failure: DepthDecodeFailure,
            raster: DecodedDepthRaster? = null,
        ) = DepthDecodeReport(
            status = status,
            failure = if (preflightFailure != DepthDecodeFailure.NONE) preflightFailure else failure,
            payloadLocation = location,
            decoder = raster?.decoderName,
            sourceMime = mime ?: sniffMime(payload),
            sourceBitDepth = raster?.bitDepth,
            decodedWidth = raster?.width,
            decodedHeight = raster?.height,
        )

        companion object {
            fun invalidBase64(standard: DepthStandard, location: DepthPayloadLocation) = DepthPayloadDescriptor(
                standard = standard,
                location = location,
                payload = byteArrayOf(),
                confidencePayload = null,
                mime = null,
                encoding = DepthEncoding.UNKNOWN,
                near = null,
                far = null,
                units = null,
                measureType = DepthMeasureType.UNKNOWN,
                preflightFailure = DepthDecodeFailure.INVALID_BASE64,
            )
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 96 * 1024 * 1024
        const val MAX_PAYLOAD_BYTES = 32 * 1024 * 1024
        const val MAX_BASE64_CHARACTERS = MAX_PAYLOAD_BYTES * 4 / 3 + 16
        const val MAX_DEPTH_PIXELS = 16_000_000L
        const val TAIL_SCAN_BYTES = 1024 * 1024
    }
}

private fun mergeContainerSignals(
    first: CaptureContainerInput,
    second: CaptureContainerInput,
) = CaptureContainerInput(
    xmpPresent = first.xmpPresent || second.xmpPresent,
    xmpByteCount = maxOf(first.xmpByteCount, second.xmpByteCount),
    depthStandards = first.depthStandards + second.depthStandards,
    depthEncoding = second.depthEncoding.takeUnless { it == DepthEncoding.UNKNOWN } ?: first.depthEncoding,
    depthUnits = second.depthUnits ?: first.depthUnits,
    depthNear = second.depthNear ?: first.depthNear,
    depthFar = second.depthFar ?: first.depthFar,
    depthPayloadConfirmed = first.depthPayloadConfirmed || second.depthPayloadConfirmed,
    confidencePayloadConfirmed = first.confidencePayloadConfirmed || second.confidencePayloadConfirmed,
    cameraPosePresent = first.cameraPosePresent || second.cameraPosePresent,
    worldPlanesPresent = first.worldPlanesPresent || second.worldPlanesPresent,
    imagingModelPresent = first.imagingModelPresent || second.imagingModelPresent,
    motionPhotoVideoConfirmed = first.motionPhotoVideoConfirmed || second.motionPhotoVideoConfirmed,
    stereoBaselineMeters = second.stereoBaselineMeters ?: first.stereoBaselineMeters,
)

/** XMP packets from JPEG APP1/extended APP1, PNG iTXt, or an uncompressed ISO item. */
internal object XmpPacketExtractor {
    fun extract(file: ByteArray): List<String> {
        val packets = linkedSetOf<String>()
        extractJpeg(file).forEach { it.decodeXml()?.let(packets::add) }
        extractPng(file).forEach { it.decodeXml()?.let(packets::add) }
        extractRawXml(file).forEach { it.decodeXml()?.let(packets::add) }
        return packets.toList()
    }

    private fun extractJpeg(file: ByteArray): List<ByteArray> {
        if (file.size < 4 || file[0] != 0xff.toByte() || file[1] != 0xd8.toByte()) return emptyList()
        val result = mutableListOf<ByteArray>()
        val extended = linkedMapOf<String, ExtendedPacket>()
        var offset = 2
        while (offset + 4 <= file.size) {
            if (file[offset] != 0xff.toByte()) break
            val marker = file[offset + 1].toInt() and 0xff
            if (marker == 0xda || marker == 0xd9) break
            if (marker == 0x00 || marker in 0xd0..0xd8) {
                offset += 2
                continue
            }
            val length = file.readU16(offset + 2)
            if (length < 2 || offset + 2 + length > file.size) break
            val start = offset + 4
            val end = offset + 2 + length
            if (marker == 0xe1) {
                when {
                    file.startsWithAscii(start, MAIN_XMP_HEADER) ->
                        result += file.copyOfRange(start + MAIN_XMP_HEADER.length, end)
                    file.startsWithAscii(start, EXTENDED_XMP_HEADER) -> {
                        val headerEnd = start + EXTENDED_XMP_HEADER.length
                        if (headerEnd + 40 <= end) {
                            val guid = file.copyOfRange(headerEnd, headerEnd + 32).toString(Charsets.US_ASCII)
                            val fullLength = file.readU32(headerEnd + 32)
                            val chunkOffset = file.readU32(headerEnd + 36)
                            val chunkStart = headerEnd + 40
                            if (fullLength in 1..MAX_XMP_BYTES && chunkOffset >= 0 &&
                                chunkOffset + (end - chunkStart) <= fullLength
                            ) {
                                val packet = extended.getOrPut(guid) { ExtendedPacket(fullLength) }
                                packet.put(chunkOffset, file, chunkStart, end)
                            }
                        }
                    }
                }
            }
            offset = end
        }
        extended.values.filter(ExtendedPacket::complete).mapTo(result) { it.bytes }
        return result
    }

    private fun extractPng(file: ByteArray): List<ByteArray> {
        if (!file.startsWithBytes(PNG_SIGNATURE)) return emptyList()
        val result = mutableListOf<ByteArray>()
        var offset = PNG_SIGNATURE.size
        while (offset + 12 <= file.size) {
            val length = file.readU32(offset)
            if (length < 0 || length > MAX_XMP_BYTES || offset + 12L + length > file.size) break
            val type = file.copyOfRange(offset + 4, offset + 8).toString(Charsets.US_ASCII)
            val dataStart = offset + 8
            val dataEnd = dataStart + length
            if (type == "iTXt") decodeITxt(file.copyOfRange(dataStart, dataEnd))?.let(result::add)
            offset = dataEnd + 4
            if (type == "IEND") break
        }
        return result
    }

    private fun decodeITxt(data: ByteArray): ByteArray? {
        val keywordEnd = data.indexOf(0)
        if (keywordEnd < 0) return null
        val keyword = data.copyOfRange(0, keywordEnd).toString(Charsets.ISO_8859_1)
        if (!keyword.equals("XML:com.adobe.xmp", ignoreCase = true)) return null
        var cursor = keywordEnd + 1
        if (cursor + 2 > data.size) return null
        val compressed = data[cursor++].toInt() != 0
        cursor++ // compression method
        repeat(2) {
            val end = data.indexOf(0, cursor)
            if (end < 0) return null
            cursor = end + 1
        }
        val payload = data.copyOfRange(cursor, data.size)
        return if (!compressed) payload else runCatching {
            InflaterInputStream(ByteArrayInputStream(payload)).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= MAX_XMP_BYTES) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray().takeIf { it.size <= MAX_XMP_BYTES }
            }
        }.getOrNull()
    }

    private fun extractRawXml(file: ByteArray): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var cursor = 0
        while (result.sumOf(ByteArray::size) < MAX_XMP_BYTES) {
            val start = file.indexOfAscii("<x:xmpmeta", cursor).takeIf { it >= 0 }
                ?: file.indexOfAscii("<rdf:RDF", cursor).takeIf { it >= 0 }
                ?: break
            val close = if (file.startsWithAscii(start, "<x:xmpmeta")) "</x:xmpmeta>" else "</rdf:RDF>"
            val closeStart = file.indexOfAscii(close, start)
            if (closeStart < 0) break
            val end = closeStart + close.length
            if (end - start <= MAX_XMP_BYTES) result += file.copyOfRange(start, end)
            cursor = end
        }
        return result
    }

    private fun ByteArray.decodeXml(): String? = takeIf { it.size <= MAX_XMP_BYTES }
        ?.toString(Charsets.UTF_8)
        ?.trim('\u0000', '\uFEFF', ' ', '\r', '\n', '\t')
        ?.takeIf { it.contains("<") }

    private class ExtendedPacket(length: Int) {
        val bytes = ByteArray(length)
        private val present = BooleanArray(length)
        private var count = 0
        val complete: Boolean get() = count == bytes.size

        fun put(offset: Int, source: ByteArray, start: Int, end: Int) {
            for (sourceIndex in start until end) {
                val target = offset + sourceIndex - start
                bytes[target] = source[sourceIndex]
                if (!present[target]) {
                    present[target] = true
                    count++
                }
            }
        }
    }

    private const val MAIN_XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000"
    private const val EXTENDED_XMP_HEADER = "http://ns.adobe.com/xmp/extension/\u0000"
    private const val MAX_XMP_BYTES = 16 * 1024 * 1024
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
}

/** Preserves 16-bit grayscale PNG precision, then falls back to Android's content decoder. */
internal object AndroidDepthRasterDecoder : DepthRasterDecoder {
    override fun decode(encoded: ByteArray): DecodedDepthRaster? =
        PngGrayscaleDecoder.decode(encoded) ?: decodePlatform(encoded)

    private fun decodePlatform(encoded: ByteArray): DecodedDepthRaster? = runCatching {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(encoded))) { decoder, info, _ ->
                val pixels = info.size.width.toLong() * info.size.height.toLong()
                require(pixels in 1..MAX_PLATFORM_PIXELS)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
            }
        } else {
            BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
        } ?: return null
        bitmap.usePixels {
            DecodedDepthRaster(
                width = width,
                height = height,
                normalizedSamples = readLuminance(this),
                bitDepth = sniffBitDepth(encoded) ?: 8,
                decoderName = if (Build.VERSION.SDK_INT >= 28) "ImageDecoder" else "BitmapFactory",
            )
        }
    }.getOrNull()

    private fun readLuminance(bitmap: Bitmap): FloatArray {
        val result = FloatArray(bitmap.width * bitmap.height)
        if (Build.VERSION.SDK_INT >= 29 && bitmap.config == Bitmap.Config.RGBA_F16) {
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val color = bitmap.getColor(x, y)
                result[y * bitmap.width + x] =
                    (0.2126f * color.red() + 0.7152f * color.green() + 0.0722f * color.blue()).coerceIn(0f, 1f)
            }
        } else {
            val row = IntArray(bitmap.width)
            for (y in 0 until bitmap.height) {
                bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                for (x in row.indices) {
                    val pixel = row[x]
                    result[y * bitmap.width + x] =
                        (0.2126f * Color.red(pixel) + 0.7152f * Color.green(pixel) + 0.0722f * Color.blue(pixel)) / 255f
                }
            }
        }
        return result
    }

    private inline fun <T> Bitmap.usePixels(block: Bitmap.() -> T): T = try {
        block()
    } finally {
        recycle()
    }

    private const val MAX_PLATFORM_PIXELS = 16_000_000L
}

internal object PngGrayscaleDecoder {
    fun decode(bytes: ByteArray): DecodedDepthRaster? {
        if (!bytes.startsWithBytes(PNG_SIGNATURE) || bytes.size < 33) return null
        var offset = PNG_SIGNATURE.size
        var width = 0
        var height = 0
        var bitDepth = 0
        var colourType = -1
        var interlace = -1
        val compressed = ByteArrayOutputStream()
        while (offset + 12 <= bytes.size) {
            val length = bytes.readU32(offset)
            if (length < 0 || length > MAX_PNG_COMPRESSED_BYTES || offset + 12L + length > bytes.size) return null
            val type = bytes.copyOfRange(offset + 4, offset + 8).toString(Charsets.US_ASCII)
            val start = offset + 8
            when (type) {
                "IHDR" -> {
                    if (length != 13) return null
                    width = bytes.readU32(start)
                    height = bytes.readU32(start + 4)
                    bitDepth = bytes[start + 8].toInt() and 0xff
                    colourType = bytes[start + 9].toInt() and 0xff
                    interlace = bytes[start + 12].toInt() and 0xff
                }
                "IDAT" -> compressed.write(bytes, start, length)
                "IEND" -> break
            }
            offset = start + length + 4
        }
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_PNG_PIXELS ||
            colourType != 0 || bitDepth !in setOf(8, 16) || interlace != 0
        ) return null
        val bytesPerPixel = bitDepth / 8
        val rowBytes = width * bytesPerPixel
        val expected = height.toLong() * (rowBytes + 1L)
        if (expected > MAX_PNG_INFLATED_BYTES) return null
        val inflated = runCatching {
            InflaterInputStream(ByteArrayInputStream(compressed.toByteArray())).use { input ->
                val result = ByteArray(expected.toInt())
                var cursor = 0
                while (cursor < result.size) {
                    val count = input.read(result, cursor, result.size - cursor)
                    if (count < 0) break
                    cursor += count
                }
                result.takeIf { cursor == result.size }
            }
        }.getOrNull() ?: return null
        val reconstructed = ByteArray(rowBytes * height)
        var sourceOffset = 0
        for (y in 0 until height) {
            val filter = inflated[sourceOffset++].toInt() and 0xff
            val rowOffset = y * rowBytes
            for (x in 0 until rowBytes) {
                val raw = inflated[sourceOffset++].toInt() and 0xff
                val left = if (x >= bytesPerPixel) reconstructed[rowOffset + x - bytesPerPixel].toInt() and 0xff else 0
                val up = if (y > 0) reconstructed[rowOffset - rowBytes + x].toInt() and 0xff else 0
                val upperLeft = if (y > 0 && x >= bytesPerPixel) {
                    reconstructed[rowOffset - rowBytes + x - bytesPerPixel].toInt() and 0xff
                } else 0
                val value = when (filter) {
                    0 -> raw
                    1 -> raw + left
                    2 -> raw + up
                    3 -> raw + (left + up) / 2
                    4 -> raw + paeth(left, up, upperLeft)
                    else -> return null
                }
                reconstructed[rowOffset + x] = value.toByte()
            }
        }
        val samples = FloatArray(width * height)
        if (bitDepth == 8) {
            for (index in samples.indices) samples[index] = (reconstructed[index].toInt() and 0xff) / 255f
        } else {
            for (index in samples.indices) {
                val byteIndex = index * 2
                val word = ((reconstructed[byteIndex].toInt() and 0xff) shl 8) or
                    (reconstructed[byteIndex + 1].toInt() and 0xff)
                samples[index] = word / 65535f
            }
        }
        return DecodedDepthRaster(width, height, samples, bitDepth, "PNG-Grayscale")
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }

    private const val MAX_PNG_PIXELS = 16_000_000L
    private const val MAX_PNG_COMPRESSED_BYTES = 32 * 1024 * 1024
    private const val MAX_PNG_INFLATED_BYTES = 64 * 1024 * 1024L
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
}

private fun sniffMime(bytes: ByteArray): String? = when {
    bytes.startsWithBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) -> "image/png"
    bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() -> "image/jpeg"
    bytes.startsWithAscii(0, "RIFF") && bytes.startsWithAscii(8, "WEBP") -> "image/webp"
    bytes.size >= 12 && bytes.startsWithAscii(4, "ftyp") -> "image/heif"
    bytes.startsWithAscii(0, "II*") || bytes.startsWithAscii(0, "MM\u0000*") -> "image/tiff"
    else -> null
}

private fun sniffBitDepth(bytes: ByteArray): Int? = when {
    bytes.size > 24 && bytes.startsWithBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)) ->
        bytes[24].toInt() and 0xff
    bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() -> 8
    else -> null
}

private fun ByteArray.readU16(offset: Int): Int =
    ((this[offset].toInt() and 0xff) shl 8) or (this[offset + 1].toInt() and 0xff)

private fun ByteArray.readU32(offset: Int): Int {
    if (offset < 0 || offset + 4 > size) return -1
    val value = ((this[offset].toLong() and 0xff) shl 24) or
        ((this[offset + 1].toLong() and 0xff) shl 16) or
        ((this[offset + 2].toLong() and 0xff) shl 8) or
        (this[offset + 3].toLong() and 0xff)
    return value.takeIf { it <= Int.MAX_VALUE }?.toInt() ?: -1
}

private fun ByteArray.startsWithAscii(offset: Int, value: String): Boolean =
    offset >= 0 && offset + value.length <= size && value.indices.all { this[offset + it] == value[it].code.toByte() }

private fun ByteArray.startsWithBytes(value: ByteArray): Boolean =
    size >= value.size && value.indices.all { this[it] == value[it] }

private fun ByteArray.indexOfAscii(value: String, fromIndex: Int = 0): Int {
    if (value.isEmpty() || size < value.length) return -1
    val last = size - value.length
    for (start in fromIndex.coerceAtLeast(0)..last) if (startsWithAscii(start, value)) return start
    return -1
}

private fun ByteArray.indexOf(value: Byte, fromIndex: Int): Int {
    for (index in fromIndex.coerceAtLeast(0) until size) if (this[index] == value) return index
    return -1
}
