package com.planruler.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.planruler.designsystem.PlanRulerTestTags
import com.planruler.designsystem.component.SheetHeader
import com.planruler.designsystem.theme.Space
import com.planruler.document.api.CaptureEvidence
import com.planruler.document.api.CaptureReadiness
import com.planruler.document.api.DepthDecodeStatus
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoMetadataSheet(
    evidence: CaptureEvidence?,
    text: Wt,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .navigationBarsPadding()
                .testTag(PlanRulerTestTags.PhotoMetadataSheet),
            verticalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            item { SheetHeader(text.photoDataInspector) }
            if (evidence == null) {
                item {
                    Text(
                        text.noPhotoMetadata,
                        modifier = Modifier.padding(horizontal = Space.x5, vertical = Space.x4),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item { ReadinessCard(evidence, text) }
                item { OpticsCard(evidence, text) }
                item { ContainerCard(evidence, text) }
                item { DepthDecodeCard(evidence, text) }
                item { ProfileCard(evidence, text) }
                item { FormulaCard(evidence, text) }
                if (evidence.warnings.isNotEmpty()) {
                    item {
                        PhotoCard(text.warnings) {
                            evidence.warnings.map(text::photoWarning).distinct().forEachIndexed { index, warning ->
                                if (index > 0) HorizontalDivider()
                                Text(
                                    warning,
                                    modifier = Modifier.padding(vertical = Space.x2),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                item {
                    PhotoCard(text.fileFingerprint) {
                        MetadataRow(text.fileFingerprint, evidence.sourceSha256.take(16) + "…")
                        MetadataRow(text.fileSize, formatBytes(evidence.sourceByteCount))
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x2)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Text(text.close)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadinessCard(evidence: CaptureEvidence, text: Wt) {
    val colours = when (evidence.readiness) {
        CaptureReadiness.AR_SURVEY_AVAILABLE, CaptureReadiness.METRIC_DEPTH_AVAILABLE ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        CaptureReadiness.APPROXIMATE_FOCUS_PLANE ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        CaptureReadiness.REFERENCE_REQUIRED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.x4),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colours.first),
    ) {
        Column(Modifier.padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            Text(text.readiness, style = MaterialTheme.typography.labelLarge, color = colours.second)
            Text(
                text.photoReadiness(evidence.readiness),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colours.second,
            )
            Text(
                if (evidence.depthDecode.isMetric) text.metricDepthDecoded else text.photoScaleCaution,
                style = MaterialTheme.typography.bodyMedium,
                color = colours.second,
            )
        }
    }
}

@Composable
private fun DepthDecodeCard(evidence: CaptureEvidence, text: Wt) {
    val decode = evidence.depthDecode
    val map = decode.map
    PhotoCard(text.depthDecoding) {
        MetadataRow(text.readiness, text.depthDecodeStatus(decode.status))
        MetadataRow(text.depthDecoder, decode.decoder ?: text.notFound)
        MetadataRow(text.sourceFormat, decode.sourceMime ?: text.notFound)
        MetadataRow(
            text.imageResolution,
            if (decode.decodedWidth != null && decode.decodedHeight != null) {
                "${decode.decodedWidth} × ${decode.decodedHeight} px"
            } else {
                text.notFound
            },
        )
        MetadataRow(text.bitDepth, decode.sourceBitDepth?.let { "$it bit" } ?: text.notFound)
        if (decode.status == DepthDecodeStatus.DECODED_METRIC && map != null) {
            MetadataRow(text.validDepthSamples, "${map.validSampleCount} / ${map.depthMeters.size}")
            MetadataRow(text.minimumDepth, formatNumber(map.minimumMeters?.toDouble(), "m", text.notFound, 3))
            MetadataRow(text.medianDepth, formatNumber(map.medianMeters?.toDouble(), "m", text.notFound, 3))
            MetadataRow(text.maximumDepth, formatNumber(map.maximumMeters?.toDouble(), "m", text.notFound, 3))
            MetadataRow(text.confidenceMap, if (map.confidence != null) text.available else text.notFound)
        }
    }
}

@Composable
private fun OpticsCard(evidence: CaptureEvidence, text: Wt) {
    val metadata = evidence.metadata
    val optical = evidence.opticalEstimate
    PhotoCard(text.cameraAndOptics) {
        MetadataRow(text.camera, listOfNotNull(metadata.make, metadata.model).joinToString(" ").ifBlank { text.notFound })
        MetadataRow(text.lens, metadata.lensModel ?: text.notFound)
        MetadataRow(text.imageResolution, "${metadata.pixelWidth} × ${metadata.pixelHeight} px")
        MetadataRow(text.orientation, metadata.orientation.toString())
        MetadataRow(text.focalLength, formatNumber(metadata.focalLengthMm, "mm", text.notFound))
        MetadataRow(text.equivalent35mm, formatNumber(metadata.focalLength35Mm, "mm", text.notFound))
        MetadataRow(text.fieldOfView, formatNumber(optical.horizontalFieldOfViewDegrees, "°", text.notFound))
        MetadataRow(text.sensorDiagonal, formatNumber(optical.sensorDiagonalMm, "mm", text.notFound))
        MetadataRow(text.focusDistance, formatNumber(metadata.subjectDistanceMeters, "m", text.notFound))
        MetadataRow(text.focusPlanePixelSize, formatNumber(optical.metersPerPixelAtSubject?.times(1000.0), "mm/px", text.notFound))
    }
}

@Composable
private fun ContainerCard(evidence: CaptureEvidence, text: Wt) {
    val container = evidence.container
    PhotoCard(text.containerSignals) {
        MetadataRow("XMP", if (container.xmpPresent) text.available else text.notFound)
        MetadataRow(
            text.depthStandard,
            container.depthStandards.joinToString { it.name.replace('_', ' ') }.ifBlank { text.notFound },
        )
        MetadataRow(text.depthUnits, container.depthUnits ?: text.notFound)
        MetadataRow(text.depthPayload, if (container.depthPayloadConfirmed) text.available else text.notFound)
        MetadataRow(text.confidenceMap, if (container.confidencePayloadConfirmed) text.available else text.notFound)
        MetadataRow(text.cameraPose, if (container.cameraPosePresent) text.available else text.notFound)
        MetadataRow(text.worldPlanes, if (container.worldPlanesPresent) text.available else text.notFound)
        MetadataRow(text.motionPhotoVideo, if (container.motionPhotoVideoConfirmed) text.available else text.notFound)
    }
}

@Composable
private fun ProfileCard(evidence: CaptureEvidence, text: Wt) {
    val profile = evidence.cameraProfileStatistics
    PhotoCard(text.localCameraProfile) {
        MetadataRow(text.samples, profile?.sampleCount?.toString() ?: "0")
        MetadataRow(text.readiness, if (profile?.stable == true) text.stable else text.collectingData)
        MetadataRow(
            text.medianNormalizedFocal,
            formatNumber(profile?.medianNormalizedFocalDiagonal, null, text.notFound, 4),
        )
        MetadataRow(
            text.relativeMad,
            formatNumber(profile?.normalizedFocalRelativeMad?.times(100.0), "%", text.notFound, 2),
        )
    }
}

@Composable
private fun FormulaCard(evidence: CaptureEvidence, text: Wt) {
    val optical = evidence.opticalEstimate
    PhotoCard(text.formulaCandidate) {
        MetadataRow(
            "k = fpx / √(w² + h²)",
            formatNumber(optical.normalizedFocalDiagonal, null, text.notFound, 6),
        )
        MetadataRow(
            "s(z) = z / fpx",
            formatNumber(optical.metersPerPixelAtSubject, "m/px", text.notFound, 6),
        )
        Text(
            text.formulaNeedsMetricAnchor,
            modifier = Modifier.padding(top = Space.x2),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PhotoCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.x4),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.x1),
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatNumber(value: Double?, unit: String?, fallback: String, digits: Int = 2): String {
    if (value == null || !value.isFinite()) return fallback
    val number = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = digits
        minimumFractionDigits = 0
    }.format(value)
    return if (unit.isNullOrBlank()) number else "$number $unit"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> formatNumber(bytes / (1024.0 * 1024.0), "MB", "0")
    bytes >= 1024L -> formatNumber(bytes / 1024.0, "KB", "0")
    else -> "$bytes B"
}
