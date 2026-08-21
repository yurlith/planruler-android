package com.planruler.document.api

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot

/** Evidence that can be present in an imported camera file. No flag is a compliance claim. */
enum class CaptureCapability {
    PIXEL_GEOMETRY,
    CAMERA_INTRINSICS,
    LENS_CORRECTION,
    DISTANCE_HINT,
    METRIC_DEPTH,
    DEPTH_CONFIDENCE,
    CAMERA_POSE,
    WORLD_PLANES,
    MULTIFRAME,
    STEREO_BASELINE,
    METRIC_RECONSTRUCTION_READY,
}

enum class CaptureWarning {
    NO_CAMERA_IDENTITY,
    NO_OPTICAL_METADATA,
    SUBJECT_DISTANCE_IS_ONLY_A_HINT,
    FOCAL_PLANE_AND_35MM_ESTIMATES_DISAGREE,
    DEPTH_UNITS_MISSING,
    DEPTH_PAYLOAD_NOT_CONFIRMED,
    DEPTH_DECODE_FAILED,
    MOTION_PHOTO_NEEDS_METRIC_ANCHOR,
    AUXILIARY_DEPTH_REQUIRES_DECODER,
    CAMERA_PROFILE_HAS_TOO_FEW_SAMPLES,
    CAMERA_PROFILE_IS_UNSTABLE,
}

enum class CaptureReadiness {
    REFERENCE_REQUIRED,
    APPROXIMATE_FOCUS_PLANE,
    METRIC_DEPTH_AVAILABLE,
    AR_SURVEY_AVAILABLE,
}

enum class DepthStandard { GDEPTH, DYNAMIC_DEPTH, ANDROID_DEPTH_JPEG, APPLE_AUXILIARY }
enum class DepthEncoding { RANGE_LINEAR, RANGE_INVERSE, AXIAL, RAY_RANGE, DISPARITY, UNKNOWN }

/** Normalized values read from EXIF/Camera2. Android parsing lives in document-android. */
data class PhotoMetadataInput(
    val pixelWidth: Int,
    val pixelHeight: Int,
    val orientation: Int,
    val make: String? = null,
    val model: String? = null,
    val lensModel: String? = null,
    val software: String? = null,
    val capturedAt: String? = null,
    val focalLengthMm: Double? = null,
    val focalLength35Mm: Double? = null,
    val subjectDistanceMeters: Double? = null,
    val digitalZoomRatio: Double? = null,
    /** Pixels per focal-plane unit. */
    val focalPlaneXResolution: Double? = null,
    val focalPlaneYResolution: Double? = null,
    /** Physical length of one focal-plane resolution unit in millimetres. */
    val focalPlaneResolutionUnitMm: Double? = null,
    val cameraIntrinsicFxPixels: Double? = null,
    val cameraIntrinsicFyPixels: Double? = null,
    val cameraPrincipalPointX: Double? = null,
    val cameraPrincipalPointY: Double? = null,
    val hasLensDistortionModel: Boolean = false,
) {
    init {
        require(pixelWidth > 0 && pixelHeight > 0)
    }
}

/** Container-level signals found in bounded XMP/box inspection. */
data class CaptureContainerInput(
    val xmpPresent: Boolean = false,
    val xmpByteCount: Int = 0,
    val depthStandards: Set<DepthStandard> = emptySet(),
    val depthEncoding: DepthEncoding = DepthEncoding.UNKNOWN,
    val depthUnits: String? = null,
    val depthNear: Double? = null,
    val depthFar: Double? = null,
    val depthPayloadConfirmed: Boolean = false,
    val confidencePayloadConfirmed: Boolean = false,
    val cameraPosePresent: Boolean = false,
    val worldPlanesPresent: Boolean = false,
    val imagingModelPresent: Boolean = false,
    val motionPhotoVideoConfirmed: Boolean = false,
    val stereoBaselineMeters: Double? = null,
) {
    val hasMetricDepth: Boolean
        get() = depthPayloadConfirmed && depthUnits?.trim()?.lowercase() in METRIC_DEPTH_UNITS

    private companion object {
        val METRIC_DEPTH_UNITS = setOf(
            "m", "meter", "meters", "metre", "metres",
            "cm", "centimeter", "centimeters", "centimetre", "centimetres",
            "mm", "millimeter", "millimeters", "millimetre", "millimetres",
        )
    }
}

data class OpticalEstimate(
    /** Equivalent focal length in the saved image's pixels, assuming square pixels. */
    val focalLengthPixels: Double?,
    val normalizedFocalDiagonal: Double?,
    val horizontalFieldOfViewDegrees: Double?,
    val diagonalFieldOfViewDegrees: Double?,
    val cropFactor: Double?,
    val sensorDiagonalMm: Double?,
    val sensorWidthFromFocalPlaneMm: Double?,
    val focalLengthPixelsFromFocalPlane: Double?,
    val focalEstimateRelativeDifference: Double?,
    /** A hypothesis at the focus plane, never an accepted image-wide scale. */
    val metersPerPixelAtSubject: Double?,
)

data class CameraProfileKey(
    val make: String,
    val model: String,
    val lensModel: String,
    val aspectBucket: String,
    val focalLength35Bucket: Int?,
    val digitalZoomBucket: Int?,
) {
    val stableId: String
        get() = listOf(make, model, lensModel, aspectBucket, focalLength35Bucket, digitalZoomBucket)
            .joinToString("|") { it?.toString()?.trim()?.lowercase().orEmpty() }
}

data class CameraProfileObservation(
    val sourceSha256: String,
    val key: CameraProfileKey,
    val normalizedFocalDiagonal: Double?,
    val cropFactor: Double?,
    val sensorDiagonalMm: Double?,
    val focalEstimateRelativeDifference: Double?,
)

data class CameraProfileStatistics(
    val sampleCount: Int,
    val medianNormalizedFocalDiagonal: Double?,
    val normalizedFocalRelativeMad: Double?,
    val medianCropFactor: Double?,
    val cropFactorRelativeMad: Double?,
    val stable: Boolean,
)

data class CaptureEvidence(
    val sourceSha256: String,
    val sourceByteCount: Long,
    val metadata: PhotoMetadataInput,
    val container: CaptureContainerInput,
    val opticalEstimate: OpticalEstimate,
    val capabilities: Set<CaptureCapability>,
    val warnings: Set<CaptureWarning>,
    val readiness: CaptureReadiness,
    val cameraProfileKey: CameraProfileKey?,
    val cameraProfileStatistics: CameraProfileStatistics? = null,
    val depthDecode: DepthDecodeReport = DepthDecodeReport(),
)

object PhotoMetadataAnalyzer {
    private const val FULL_FRAME_WIDTH_MM = 36.0
    private const val FULL_FRAME_HEIGHT_MM = 24.0
    private val FULL_FRAME_DIAGONAL_MM = hypot(FULL_FRAME_WIDTH_MM, FULL_FRAME_HEIGHT_MM)

    fun analyze(
        metadata: PhotoMetadataInput,
        container: CaptureContainerInput,
        sourceSha256: String,
        sourceByteCount: Long,
        statistics: CameraProfileStatistics? = null,
        depthDecode: DepthDecodeReport = DepthDecodeReport(),
    ): CaptureEvidence {
        val estimate = opticalEstimate(metadata)
        val capabilities = linkedSetOf(CaptureCapability.PIXEL_GEOMETRY)
        val warnings = linkedSetOf<CaptureWarning>()

        if (estimate.focalLengthPixels != null || metadata.cameraIntrinsicFxPixels.isPositive()) {
            capabilities += CaptureCapability.CAMERA_INTRINSICS
        } else {
            warnings += CaptureWarning.NO_OPTICAL_METADATA
        }
        if (metadata.hasLensDistortionModel) capabilities += CaptureCapability.LENS_CORRECTION
        if (metadata.subjectDistanceMeters.isPositive()) {
            capabilities += CaptureCapability.DISTANCE_HINT
            warnings += CaptureWarning.SUBJECT_DISTANCE_IS_ONLY_A_HINT
        }
        if (depthDecode.isMetric) capabilities += CaptureCapability.METRIC_DEPTH
        if (depthDecode.map?.confidence != null) capabilities += CaptureCapability.DEPTH_CONFIDENCE
        if (container.cameraPosePresent) capabilities += CaptureCapability.CAMERA_POSE
        if (container.worldPlanesPresent) capabilities += CaptureCapability.WORLD_PLANES
        if (container.motionPhotoVideoConfirmed) capabilities += CaptureCapability.MULTIFRAME
        if (container.stereoBaselineMeters.isPositive()) capabilities += CaptureCapability.STEREO_BASELINE

        if (container.depthStandards.isNotEmpty() && !container.depthPayloadConfirmed) {
            warnings += CaptureWarning.DEPTH_PAYLOAD_NOT_CONFIRMED
        }
        if (container.depthPayloadConfirmed && !container.hasMetricDepth) {
            warnings += CaptureWarning.DEPTH_UNITS_MISSING
        }
        if (container.depthPayloadConfirmed && !depthDecode.isMetric &&
            depthDecode.status != DepthDecodeStatus.NOT_PRESENT
        ) {
            warnings += CaptureWarning.DEPTH_DECODE_FAILED
        }
        if (container.motionPhotoVideoConfirmed && !container.hasMetricDepth && !container.stereoBaselineMeters.isPositive()) {
            warnings += CaptureWarning.MOTION_PHOTO_NEEDS_METRIC_ANCHOR
        }
        if (DepthStandard.APPLE_AUXILIARY in container.depthStandards && !depthDecode.isMetric) {
            warnings += CaptureWarning.AUXILIARY_DEPTH_REQUIRES_DECODER
        }
        estimate.focalEstimateRelativeDifference?.takeIf { it > 0.05 }?.let {
            warnings += CaptureWarning.FOCAL_PLANE_AND_35MM_ESTIMATES_DISAGREE
        }
        val profileKey = cameraProfileKey(metadata)
        if (profileKey == null) warnings += CaptureWarning.NO_CAMERA_IDENTITY
        when {
            statistics == null -> Unit
            statistics.sampleCount < 3 -> warnings += CaptureWarning.CAMERA_PROFILE_HAS_TOO_FEW_SAMPLES
            !statistics.stable -> warnings += CaptureWarning.CAMERA_PROFILE_IS_UNSTABLE
        }

        val readiness = when {
            depthDecode.isMetric && container.cameraPosePresent && container.worldPlanesPresent ->
                CaptureReadiness.AR_SURVEY_AVAILABLE
            depthDecode.isMetric -> CaptureReadiness.METRIC_DEPTH_AVAILABLE
            estimate.metersPerPixelAtSubject != null -> CaptureReadiness.APPROXIMATE_FOCUS_PLANE
            else -> CaptureReadiness.REFERENCE_REQUIRED
        }
        if (readiness == CaptureReadiness.AR_SURVEY_AVAILABLE || readiness == CaptureReadiness.METRIC_DEPTH_AVAILABLE) {
            capabilities += CaptureCapability.METRIC_RECONSTRUCTION_READY
        }
        return CaptureEvidence(
            sourceSha256 = sourceSha256,
            sourceByteCount = sourceByteCount,
            metadata = metadata,
            container = container,
            opticalEstimate = estimate,
            capabilities = capabilities,
            warnings = warnings,
            readiness = readiness,
            cameraProfileKey = profileKey,
            cameraProfileStatistics = statistics,
            depthDecode = depthDecode,
        )
    }

    fun opticalEstimate(input: PhotoMetadataInput): OpticalEstimate {
        val diagonalPixels = hypot(input.pixelWidth.toDouble(), input.pixelHeight.toDouble())
        val focal35 = input.focalLength35Mm.positiveOrNull()
        val focalMm = input.focalLengthMm.positiveOrNull()
        val normalizedFocal = focal35?.div(FULL_FRAME_DIAGONAL_MM)
        val focalPixels35 = normalizedFocal?.times(diagonalPixels)
        val focalPixels = input.cameraIntrinsicFxPixels.positiveOrNull() ?: focalPixels35
        val equivalentSensorWidth = FULL_FRAME_DIAGONAL_MM * input.pixelWidth / diagonalPixels
        val horizontalFov = focal35?.let { radiansToDegrees(2.0 * atan(equivalentSensorWidth / (2.0 * it))) }
        val diagonalFov = focal35?.let { radiansToDegrees(2.0 * atan(FULL_FRAME_DIAGONAL_MM / (2.0 * it))) }
        val cropFactor = if (focal35 != null && focalMm != null) focal35 / focalMm else null
        val sensorDiagonal = cropFactor?.takeIf { it > 0.0 }?.let { FULL_FRAME_DIAGONAL_MM / it }

        val planeUnitMm = input.focalPlaneResolutionUnitMm.positiveOrNull()
        val xResolution = input.focalPlaneXResolution.positiveOrNull()
        val sensorWidth = if (planeUnitMm != null && xResolution != null) {
            input.pixelWidth / xResolution * planeUnitMm
        } else {
            null
        }?.takeIf { it in 1.0..100.0 }
        val focalPixelsPlane = if (sensorWidth != null && focalMm != null) {
            focalMm / sensorWidth * input.pixelWidth
        } else {
            null
        }
        val focalDifference = relativeDifference(focalPixels35, focalPixelsPlane)
        val subject = input.subjectDistanceMeters.positiveOrNull()
        val metersPerPixel = if (subject != null && focalPixels != null && focalPixels > 0.0) subject / focalPixels else null

        return OpticalEstimate(
            focalLengthPixels = focalPixels,
            normalizedFocalDiagonal = normalizedFocal,
            horizontalFieldOfViewDegrees = horizontalFov,
            diagonalFieldOfViewDegrees = diagonalFov,
            cropFactor = cropFactor,
            sensorDiagonalMm = sensorDiagonal,
            sensorWidthFromFocalPlaneMm = sensorWidth,
            focalLengthPixelsFromFocalPlane = focalPixelsPlane,
            focalEstimateRelativeDifference = focalDifference,
            metersPerPixelAtSubject = metersPerPixel,
        )
    }

    fun cameraProfileKey(input: PhotoMetadataInput): CameraProfileKey? {
        val make = input.make.clean() ?: return null
        val model = input.model.clean() ?: return null
        val aspect = reducedAspect(input.pixelWidth, input.pixelHeight)
        return CameraProfileKey(
            make = make,
            model = model,
            lensModel = input.lensModel.clean().orEmpty(),
            aspectBucket = aspect,
            focalLength35Bucket = input.focalLength35Mm.positiveOrNull()?.toInt(),
            digitalZoomBucket = input.digitalZoomRatio.positiveOrNull()?.times(100.0)?.toInt(),
        )
    }

    fun observation(evidence: CaptureEvidence): CameraProfileObservation? {
        val key = evidence.cameraProfileKey ?: return null
        val estimate = evidence.opticalEstimate
        return CameraProfileObservation(
            sourceSha256 = evidence.sourceSha256,
            key = key,
            normalizedFocalDiagonal = estimate.normalizedFocalDiagonal,
            cropFactor = estimate.cropFactor,
            sensorDiagonalMm = estimate.sensorDiagonalMm,
            focalEstimateRelativeDifference = estimate.focalEstimateRelativeDifference,
        )
    }

    fun statistics(observations: Collection<CameraProfileObservation>): CameraProfileStatistics {
        val unique = observations.distinctBy { it.sourceSha256 }
        val normalized = unique.mapNotNull { it.normalizedFocalDiagonal.positiveOrNull() }
        val crop = unique.mapNotNull { it.cropFactor.positiveOrNull() }
        val normalizedMedian = median(normalized)
        val cropMedian = median(crop)
        val normalizedMad = relativeMad(normalized, normalizedMedian)
        val cropMad = relativeMad(crop, cropMedian)
        val comparable = listOfNotNull(normalizedMad, cropMad)
        val stable = unique.size >= 3 && comparable.isNotEmpty() && comparable.all { it <= 0.02 }
        return CameraProfileStatistics(
            sampleCount = unique.size,
            medianNormalizedFocalDiagonal = normalizedMedian,
            normalizedFocalRelativeMad = normalizedMad,
            medianCropFactor = cropMedian,
            cropFactorRelativeMad = cropMad,
            stable = stable,
        )
    }

    private fun relativeDifference(first: Double?, second: Double?): Double? {
        if (!first.isPositive() || !second.isPositive()) return null
        return abs(first!! - second!!) / ((first + second) / 2.0)
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private fun relativeMad(values: List<Double>, median: Double?): Double? {
        if (values.size < 2 || !median.isPositive()) return null
        return median(values.map { abs(it - median!!) })?.div(median!!)
    }

    private fun reducedAspect(width: Int, height: Int): String {
        val divisor = gcd(width, height)
        return "${width / divisor}:${height / divisor}"
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) abs(a) else gcd(b, a % b)
    private fun radiansToDegrees(value: Double) = value * 180.0 / PI
    private fun Double?.positiveOrNull() = this?.takeIf { it.isFinite() && it > 0.0 }
    private fun Double?.isPositive() = this != null && isFinite() && this > 0.0
    private fun String?.clean() = this?.trim()?.takeIf { it.isNotEmpty() }
}
