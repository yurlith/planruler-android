package com.planruler.document.android

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.planruler.document.api.CameraProfileObservation
import com.planruler.document.api.CameraProfileStatistics
import com.planruler.document.api.CaptureContainerInput
import com.planruler.document.api.CaptureEvidence
import com.planruler.document.api.DepthEncoding
import com.planruler.document.api.DepthDecodeFailure
import com.planruler.document.api.DepthDecodeReport
import com.planruler.document.api.DepthDecodeStatus
import com.planruler.document.api.DepthStandard
import com.planruler.document.api.PhotoMetadataAnalyzer
import com.planruler.document.api.PhotoMetadataInput
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Reads only bounded container fragments and normalized EXIF values. Large image/depth payloads
 * are never copied into memory by the inspector.
 */
internal class AndroidPhotoMetadataInspector(context: Context) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val statisticsStore = CameraProfileStatisticsStore(app)
    private val depthDecoder = DepthContainerDecoder()

    fun inspect(uri: Uri, pixelWidth: Int, pixelHeight: Int): CaptureEvidence {
        val file = hashAndInspectContainer(uri)
        val depth = file.fullBytes?.let { depthDecoder.decode(it, file.container) }
            ?: DepthDecodingOutcome(
                container = file.container,
                report = DepthDecodeReport(
                    status = DepthDecodeStatus.RESOURCE_LIMIT_EXCEEDED,
                    failure = DepthDecodeFailure.FILE_TOO_LARGE,
                ),
            )
        val metadata = resolver.openInputStream(uri).use { raw ->
            val exif = ExifInterface(requireNotNull(raw))
            PhotoMetadataInput(
                pixelWidth = pixelWidth,
                pixelHeight = pixelHeight,
                orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                ),
                make = exif.safeText(ExifInterface.TAG_MAKE),
                model = exif.safeText(ExifInterface.TAG_MODEL),
                lensModel = exif.safeText(ExifInterface.TAG_LENS_MODEL),
                software = exif.safeText(ExifInterface.TAG_SOFTWARE),
                capturedAt = exif.safeText(ExifInterface.TAG_DATETIME_ORIGINAL),
                focalLengthMm = exif.positiveDouble(ExifInterface.TAG_FOCAL_LENGTH),
                focalLength35Mm = exif.positiveDouble(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM),
                subjectDistanceMeters = exif.positiveDouble(ExifInterface.TAG_SUBJECT_DISTANCE),
                digitalZoomRatio = exif.positiveDouble(ExifInterface.TAG_DIGITAL_ZOOM_RATIO),
                focalPlaneXResolution = exif.positiveDouble(ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION),
                focalPlaneYResolution = exif.positiveDouble(ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION),
                focalPlaneResolutionUnitMm = focalPlaneUnitMm(
                    exif.getAttributeInt(ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT, 0),
                ),
            )
        }
        val firstPass = PhotoMetadataAnalyzer.analyze(
            metadata = metadata,
            container = depth.container,
            sourceSha256 = file.sha256,
            sourceByteCount = file.byteCount,
            depthDecode = depth.report,
        )
        val statistics = PhotoMetadataAnalyzer.observation(firstPass)?.let(statisticsStore::observe)
        return if (statistics == null) firstPass else PhotoMetadataAnalyzer.analyze(
            metadata = metadata,
            container = depth.container,
            sourceSha256 = file.sha256,
            sourceByteCount = file.byteCount,
            statistics = statistics,
            depthDecode = depth.report,
        )
    }

    private fun hashAndInspectContainer(uri: Uri): InspectedFile {
        val digest = MessageDigest.getInstance("SHA-256")
        var byteCount = 0L
        resolver.openInputStream(uri).use { raw ->
            val input = requireNotNull(raw)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                digest.update(buffer, 0, count)
                byteCount += count
            }
        }
        val prefix = resolver.openInputStream(uri).use { readBounded(requireNotNull(it), PREFIX_LIMIT_BYTES) }
        val suffix = resolver.openInputStream(uri).use { raw ->
            val input = requireNotNull(raw)
            skipFully(input, (byteCount - SUFFIX_LIMIT_BYTES).coerceAtLeast(0L))
            readBounded(input, SUFFIX_LIMIT_BYTES)
        }
        val fullBytes = if (byteCount <= MAX_DEPTH_DECODER_FILE_BYTES) {
            resolver.openInputStream(uri).use { readBounded(requireNotNull(it), byteCount.toInt()) }
        } else {
            null
        }
        return InspectedFile(
            sha256 = digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) },
            byteCount = byteCount,
            container = ContainerSignalScanner.scan(prefix, suffix),
            fullBytes = fullBytes,
        )
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val result = ByteArray(limit)
        var offset = 0
        while (offset < limit) {
            val count = input.read(result, offset, limit - offset)
            if (count < 0) break
            if (count == 0) continue
            offset += count
        }
        return result.copyOf(offset)
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        val discard = ByteArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = input.read(discard, 0, minOf(discard.size.toLong(), remaining).toInt())
                if (read < 0) break
                remaining -= read
            }
        }
    }

    private fun ExifInterface.safeText(tag: String): String? =
        getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_EXIF_TEXT_LENGTH)

    private fun ExifInterface.positiveDouble(tag: String): Double? =
        getAttributeDouble(tag, Double.NaN).takeIf { it.isFinite() && it > 0.0 }

    private fun focalPlaneUnitMm(code: Int): Double? = when (code) {
        2 -> 25.4 // inch
        3 -> 10.0 // centimetre
        4 -> 1.0 // millimetre
        5 -> 0.001 // micrometre
        else -> null
    }

    private data class InspectedFile(
        val sha256: String,
        val byteCount: Long,
        val container: CaptureContainerInput,
        val fullBytes: ByteArray?,
    )

    private companion object {
        const val PREFIX_LIMIT_BYTES = 4 * 1024 * 1024
        const val SUFFIX_LIMIT_BYTES = 1024 * 1024
        const val MAX_EXIF_TEXT_LENGTH = 256
        const val MAX_DEPTH_DECODER_FILE_BYTES = 96L * 1024L * 1024L
    }
}

/** Metadata signature scanner; it confirms declarations, not depth-map numeric correctness. */
internal object ContainerSignalScanner {
    fun scan(prefix: ByteArray, suffix: ByteArray): CaptureContainerInput {
        val prefixText = prefix.toString(Charsets.ISO_8859_1)
        val suffixText = suffix.toString(Charsets.ISO_8859_1)
        val combined = "$prefixText\n$suffixText"
        val lower = combined.lowercase(Locale.ROOT)

        val hasGDepth = lower.contains("ns.google.com/photos/1.0/depthmap") || lower.contains("gdepth:")
        val hasDynamicDepth = lower.contains("ns.google.com/photos/dd/1.0") ||
            lower.contains("depthmap:") || lower.contains("profile=\"arphoto\"") ||
            lower.contains("profile='arphoto'")
        val gDepthPayload = lower.contains("gdepth:data")
        val dynamicPayload = lower.contains("semantic=\"depth\"") ||
            lower.contains("semantic='depth'") || lower.contains("depthmap:depthmap")
        val depthPayload = gDepthPayload || dynamicPayload
        val confidence = lower.contains("gdepth:confidence") ||
            lower.contains("semantic=\"confidence\"") || lower.contains("confidencemap")
        val appleAuxiliary = lower.contains("auxl") &&
            listOf("hdep", "hdis", "disparity", "depth").any(lower::contains)
        val motionDeclared = listOf(
            "motionphoto=\"1\"",
            "motionphoto='1'",
            "microvideo=\"1\"",
            "microvideo='1'",
            "ns.google.com/photos/1.0/camera",
        ).any(lower::contains)
        val videoConfirmed = motionDeclared && suffix.indexOfAscii("ftyp") >= 0

        val standards = linkedSetOf<DepthStandard>()
        if (hasGDepth) standards += DepthStandard.GDEPTH
        if (hasDynamicDepth) standards += DepthStandard.DYNAMIC_DEPTH
        if (hasDynamicDepth && depthPayload) standards += DepthStandard.ANDROID_DEPTH_JPEG
        if (appleAuxiliary) standards += DepthStandard.APPLE_AUXILIARY

        return CaptureContainerInput(
            xmpPresent = lower.contains("<x:xmpmeta") || lower.contains("<?xpacket") ||
                lower.contains("http://ns.adobe.com/xap/1.0/"),
            xmpByteCount = estimateXmpByteCount(combined),
            depthStandards = standards,
            depthEncoding = when {
                lower.contains("rangeinverse") -> DepthEncoding.RANGE_INVERSE
                lower.contains("rangelinear") -> DepthEncoding.RANGE_LINEAR
                lower.contains("rayrange") -> DepthEncoding.RAY_RANGE
                lower.contains("disparity") -> DepthEncoding.DISPARITY
                lower.contains("axial") -> DepthEncoding.AXIAL
                else -> DepthEncoding.UNKNOWN
            },
            depthUnits = firstAttribute(combined, "(?:GDepth|DepthMap):Units")
                ?: firstElement(combined, "(?:GDepth|DepthMap):Units"),
            depthNear = firstNumberAttribute(combined, "(?:GDepth|DepthMap):Near"),
            depthFar = firstNumberAttribute(combined, "(?:GDepth|DepthMap):Far"),
            depthPayloadConfirmed = depthPayload,
            confidencePayloadConfirmed = confidence,
            cameraPosePresent = lower.contains("camerapose") || lower.contains("pose:position") ||
                lower.contains("pose:rotation"),
            worldPlanesPresent = lower.contains("device:planes") || lower.contains("<planes") ||
                lower.contains("plane:boundary"),
            imagingModelPresent = lower.contains("imagingmodel") || lower.contains("cameraintrinsics"),
            motionPhotoVideoConfirmed = videoConfirmed,
            stereoBaselineMeters = firstNumberAttribute(combined, "(?:Camera|Image|DepthMap):Baseline"),
        )
    }

    private fun firstAttribute(text: String, name: String): String? =
        Regex("$name\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun firstElement(text: String, name: String): String? =
        Regex("<$name[^>]*>([^<]+)</$name>", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun firstNumberAttribute(text: String, name: String): Double? =
        firstAttribute(text, name)?.toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun estimateXmpByteCount(text: String): Int {
        val start = text.indexOf("<x:xmpmeta", ignoreCase = true)
        if (start < 0) return 0
        val endMarker = "</x:xmpmeta>"
        val end = text.indexOf(endMarker, startIndex = start, ignoreCase = true)
        return if (end >= 0) end + endMarker.length - start else 0
    }

    private fun ByteArray.indexOfAscii(value: String): Int {
        val needle = value.toByteArray(Charsets.US_ASCII)
        if (needle.isEmpty() || size < needle.size) return -1
        for (start in 0..size - needle.size) {
            var matches = true
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return start
        }
        return -1
    }
}

/** Local-only, de-identified optical profile observations. */
internal class CameraProfileStatisticsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun observe(observation: CameraProfileObservation): CameraProfileStatistics {
        val preferenceKey = "profile_${sha256(observation.key.stableId).take(32)}"
        val existing = parse(preferences.getString(preferenceKey, null), observation)
        val merged = (existing + observation)
            .distinctBy { it.sourceSha256 }
            .takeLast(MAX_OBSERVATIONS)
        preferences.edit().putString(preferenceKey, encode(merged)).apply()
        return PhotoMetadataAnalyzer.statistics(merged)
    }

    private fun parse(raw: String?, template: CameraProfileObservation): List<CameraProfileObservation> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val hash = item.optString("sha").takeIf { it.length == 64 } ?: continue
                    add(
                        CameraProfileObservation(
                            sourceSha256 = hash,
                            key = template.key,
                            normalizedFocalDiagonal = item.optionalDouble("nf"),
                            cropFactor = item.optionalDouble("crop"),
                            sensorDiagonalMm = item.optionalDouble("sensor"),
                            focalEstimateRelativeDifference = item.optionalDouble("difference"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(observations: List<CameraProfileObservation>): String = JSONArray().apply {
        observations.forEach { observation ->
            put(JSONObject().apply {
                put("sha", observation.sourceSha256)
                observation.normalizedFocalDiagonal?.let { put("nf", it) }
                observation.cropFactor?.let { put("crop", it) }
                observation.sensorDiagonalMm?.let { put("sensor", it) }
                observation.focalEstimateRelativeDifference?.let { put("difference", it) }
            })
        }
    }.toString()

    private fun JSONObject.optionalDouble(name: String): Double? =
        optDouble(name, Double.NaN).takeIf { it.isFinite() }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }

    private companion object {
        const val PREFERENCES_NAME = "planruler.camera.profile.statistics.v1"
        const val MAX_OBSERVATIONS = 50
    }
}
