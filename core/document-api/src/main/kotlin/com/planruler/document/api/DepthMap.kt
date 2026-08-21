package com.planruler.document.api

import kotlin.math.roundToInt

/** Where the decoder found the bytes that carry depth information. */
enum class DepthPayloadLocation {
    XMP_BASE64,
    CONCATENATED_CONTAINER_ITEM,
    ISO_AUXILIARY_ITEM,
    UNKNOWN,
}

enum class DepthMeasureType {
    /** Distance along the camera Z axis. */
    OPTICAL_AXIS,

    /** Distance from the optical centre along the ray through a pixel. */
    OPTIC_RAY,
    UNKNOWN,
}

enum class DepthDecodeStatus {
    NOT_PRESENT,
    DECODED_METRIC,
    DECODED_RELATIVE,
    MALFORMED_METADATA,
    UNSUPPORTED_PAYLOAD,
    RESOURCE_LIMIT_EXCEEDED,
}

enum class DepthDecodeFailure {
    NONE,
    PAYLOAD_NOT_FOUND,
    INVALID_BASE64,
    INVALID_CONTAINER_DIRECTORY,
    UNSUPPORTED_RASTER,
    INVALID_DIMENSIONS,
    INVALID_RANGE,
    NON_METRIC_UNITS,
    FILE_TOO_LARGE,
    PAYLOAD_TOO_LARGE,
    APPLE_AUXILIARY_UNAVAILABLE_ON_ANDROID,
}

/**
 * Format-independent depth representation used by measurement code.
 *
 * All finite positive values are metres, regardless of how the source image encoded them.
 * Invalid or absent samples are [Float.NaN]. The array is row-major and never serialized into
 * project JSON; it is reconstructed from the source photo when the document is opened.
 */
class MetricDepthMap(
    val width: Int,
    val height: Int,
    val depthMeters: FloatArray,
    val confidence: FloatArray?,
    val encoding: DepthEncoding,
    val measureType: DepthMeasureType,
    val standard: DepthStandard,
    val sourceMime: String?,
    val sourceBitDepth: Int?,
) {
    init {
        require(width > 0 && height > 0)
        require(width.toLong() * height.toLong() == depthMeters.size.toLong())
        require(confidence == null || confidence.size == depthMeters.size)
    }

    val validSampleCount: Int = depthMeters.count { it.isFinite() && it > 0f }
    val minimumMeters: Float? = depthMeters.filterFinitePositive().minOrNull()
    val maximumMeters: Float? = depthMeters.filterFinitePositive().maxOrNull()
    val medianMeters: Float? = sampledMedian(depthMeters)

    /** Nearest-neighbour lookup in normalized colour-image coordinates. */
    fun sample(normalizedX: Double, normalizedY: Double, minimumConfidence: Float = 0f): Float? {
        if (!normalizedX.isFinite() || !normalizedY.isFinite()) return null
        val x = (normalizedX.coerceIn(0.0, 1.0) * (width - 1)).roundToInt()
        val y = (normalizedY.coerceIn(0.0, 1.0) * (height - 1)).roundToInt()
        val index = y * width + x
        if (confidence?.get(index)?.let { !it.isFinite() || it < minimumConfidence } == true) return null
        return depthMeters[index].takeIf { it.isFinite() && it > 0f }
    }

    private fun FloatArray.filterFinitePositive(): Sequence<Float> = asSequence().filter { it.isFinite() && it > 0f }

    private fun sampledMedian(values: FloatArray): Float? {
        if (validSampleCount == 0) return null
        val stride = (values.size / MAX_MEDIAN_SAMPLES).coerceAtLeast(1)
        val sample = ArrayList<Float>(minOf(validSampleCount, MAX_MEDIAN_SAMPLES))
        var index = 0
        while (index < values.size) {
            values[index].takeIf { it.isFinite() && it > 0f }?.let(sample::add)
            index += stride
        }
        if (sample.isEmpty()) return null
        sample.sort()
        val middle = sample.size / 2
        return if (sample.size % 2 == 0) (sample[middle - 1] + sample[middle]) / 2f else sample[middle]
    }

    private companion object {
        const val MAX_MEDIAN_SAMPLES = 65_536
    }
}

data class DepthDecodeReport(
    val status: DepthDecodeStatus = DepthDecodeStatus.NOT_PRESENT,
    val failure: DepthDecodeFailure = DepthDecodeFailure.NONE,
    val payloadLocation: DepthPayloadLocation = DepthPayloadLocation.UNKNOWN,
    val decoder: String? = null,
    val sourceMime: String? = null,
    val sourceBitDepth: Int? = null,
    val decodedWidth: Int? = null,
    val decodedHeight: Int? = null,
    val map: MetricDepthMap? = null,
) {
    val isMetric: Boolean get() = status == DepthDecodeStatus.DECODED_METRIC && map != null
}

/** Mathematical conversion shared by every container/raster adapter. */
object DepthMapNormalizer {
    fun normalizedRasterToMeters(
        normalizedSamples: FloatArray,
        encoding: DepthEncoding,
        near: Double?,
        far: Double?,
        units: String?,
    ): FloatArray? {
        val unitScale = metricUnitScale(units) ?: return null
        val nearValue = near?.takeIf { value ->
            value.isFinite() && when (encoding) {
                DepthEncoding.RANGE_LINEAR -> value >= 0.0
                DepthEncoding.RANGE_INVERSE -> value > 0.0
                else -> false
            }
        } ?: return null
        val farValue = far?.takeIf { it.isFinite() && it > nearValue } ?: return null
        if (encoding != DepthEncoding.RANGE_LINEAR && encoding != DepthEncoding.RANGE_INVERSE) return null

        return FloatArray(normalizedSamples.size) { index ->
            val normalized = normalizedSamples[index]
            if (!normalized.isFinite() || normalized !in 0f..1f) {
                Float.NaN
            } else {
                val value = when (encoding) {
                    DepthEncoding.RANGE_LINEAR -> nearValue + normalized * (farValue - nearValue)
                    DepthEncoding.RANGE_INVERSE ->
                        farValue * nearValue / (farValue - normalized * (farValue - nearValue))
                    else -> Double.NaN
                } * unitScale
                value.toFloat().takeIf { it.isFinite() && it > 0f } ?: Float.NaN
            }
        }
    }

    fun directValuesToMeters(
        samples: FloatArray,
        encoding: DepthEncoding,
        units: String?,
    ): FloatArray? {
        val unitScale = metricUnitScale(units)
        return when {
            encoding == DepthEncoding.DISPARITY && isInverseMetricUnit(units) -> FloatArray(samples.size) { index ->
                samples[index].takeIf { it.isFinite() && it > 0f }?.let { 1f / it } ?: Float.NaN
            }
            unitScale != null && (encoding == DepthEncoding.AXIAL || encoding == DepthEncoding.RAY_RANGE) ->
                FloatArray(samples.size) { index ->
                    samples[index].takeIf { it.isFinite() && it > 0f }?.times(unitScale.toFloat()) ?: Float.NaN
                }
            else -> null
        }
    }

    fun metricUnitScale(units: String?): Double? = when (units?.trim()?.lowercase()) {
        "m", "meter", "meters", "metre", "metres" -> 1.0
        "cm", "centimeter", "centimeters", "centimetre", "centimetres" -> 0.01
        "mm", "millimeter", "millimeters", "millimetre", "millimetres" -> 0.001
        else -> null
    }

    private fun isInverseMetricUnit(units: String?): Boolean = units?.trim()?.lowercase() in setOf(
        "1/m",
        "m^-1",
        "m-1",
        "diopter",
        "diopters",
        "dioptre",
        "dioptres",
    )
}
