package com.planruler.export.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.planruler.export.api.ExportError
import com.planruler.export.api.ExportFormat
import com.planruler.export.api.ExportGateway
import com.planruler.export.api.ExportLabels
import com.planruler.export.api.ExportPageSelection
import com.planruler.export.api.ExportRequest
import com.planruler.export.api.ExportResult
import com.planruler.model.Calibration
import com.planruler.model.LengthUnit
import com.planruler.model.Measurement
import com.planruler.model.MeasurementType
import com.planruler.model.MeasurementReviewStatus
import com.planruler.model.PlanProject
import com.planruler.model.calculateTakeoffTotals
import com.planruler.model.currentPageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2

class AndroidExportGateway(context: Context) : ExportGateway {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    override suspend fun export(request: ExportRequest): ExportResult = withContext(Dispatchers.IO) {
        val temporary = File.createTempFile("planruler-export-", ".tmp", app.cacheDir)
        try {
            FileOutputStream(temporary).use { output ->
                when (request.format) {
                    ExportFormat.JSON ->
                        output.write(json.encodeToString(request.project).toByteArray(Charsets.UTF_8))
                    ExportFormat.CSV -> writeCsv(request, output)
                    ExportFormat.ANNOTATED_PDF -> writePdf(request, output)
                }
            }
            // "wt" and not "w": plain write mode does not truncate, so exporting over an
            // existing longer file would leave the previous tail and corrupt the result.
            resolver.openOutputStream(Uri.parse(request.targetUri), "wt").use { target ->
                requireNotNull(target)
                temporary.inputStream().use { source -> source.copyTo(target) }
            }
            ExportResult.Success
        } catch (_: SecurityException) {
            ExportResult.Error(ExportError.PermissionDenied)
        } catch (e: Exception) {
            ExportResult.Error(ExportError.Io(e.message ?: "Export failed"))
        } finally {
            temporary.delete()
        }
    }

    private fun writeCsv(request: ExportRequest, output: OutputStream) {
        val project = request.project
        val delimiter = request.csvDelimiter.takeIf { it.isNotEmpty() } ?: ","
        val templates = project.takeoffTemplates.associateBy { it.id }
        val layers = project.layers.associateBy { it.id }
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.appendLine(
                listOf(
                    "ID", "type", "template", "category", "material", "diameter", "size", "layer", "name", "page",
                    "length", "perimeter", "area", "angle", "unit", "quantity_multiplier", "waste_percent",
                    "base_total", "with_waste_total", "comment", "revision_id", "review_status",
                    "source_measurement_id", "reviewed_at",
                ).joinToString(delimiter),
            )
            project.measurements.forEach { measurement ->
                val unit = measurement.displayUnit ?: project.displayUnit
                val value = evaluate(measurement, project.calibration, unit)
                val totals = calculateTakeoffTotals(listOf(measurement), project.calibration)
                val (baseTotal, adjustedTotal) = when (measurement.type) {
                    MeasurementType.DISTANCE, MeasurementType.POLYLINE ->
                        unit.fromMeters(totals.baseLengthMeters) to unit.fromMeters(totals.adjustedLengthMeters)
                    MeasurementType.AREA -> {
                        val square = unit.metersPerUnit * unit.metersPerUnit
                        totals.baseAreaSquareMeters / square to totals.adjustedAreaSquareMeters / square
                    }
                    MeasurementType.COUNTER -> totals.baseCount to totals.adjustedCount
                    MeasurementType.ANGLE, MeasurementType.ANNOTATION -> 0.0 to 0.0
                }
                writer.appendLine(
                    listOf(
                        csv(measurement.id.value),
                        measurement.type.name,
                        csv(templates[measurement.templateId]?.name.orEmpty()),
                        measurement.takeoff.category.name,
                        csv(measurement.takeoff.material.orEmpty()),
                        csv(measurement.takeoff.diameter.orEmpty()),
                        csv(measurement.takeoff.size.orEmpty()),
                        csv(layers[measurement.layerId]?.name.orEmpty()),
                        csv(measurement.label.orEmpty()),
                        measurement.pageIndex + 1,
                        value.length,
                        value.perimeter,
                        value.area,
                        value.angle,
                        unit.symbol,
                        formatNumber(measurement.takeoff.quantity),
                        formatNumber((measurement.takeoff.wasteFactor - 1.0) * 100.0),
                        if (baseTotal == 0.0) "" else formatNumber(baseTotal),
                        if (adjustedTotal == 0.0) "" else formatNumber(adjustedTotal),
                        csv(measurement.takeoff.comment.orEmpty()),
                        csv(measurement.revisionId.orEmpty()),
                        measurement.reviewStatus.name,
                        csv(measurement.sourceMeasurementId?.value.orEmpty()),
                        measurement.reviewedAtEpochMs?.let(::timestamp).orEmpty(),
                    ).joinToString(delimiter),
                )
            }

            writer.appendLine()
            writer.appendLine(
                listOf(
                    "SUMMARY", "template", "category", "material", "layer", "items", "unit",
                    "base_length", "with_waste_length", "base_area", "with_waste_area",
                    "base_count", "with_waste_count",
                ).joinToString(delimiter),
            )
            data class SummaryKey(
                val template: String,
                val category: String,
                val material: String,
                val layer: String,
            )
            project.measurements.groupBy { measurement ->
                SummaryKey(
                    template = templates[measurement.templateId]?.name.orEmpty(),
                    category = measurement.takeoff.category.name,
                    material = measurement.takeoff.material.orEmpty(),
                    layer = layers[measurement.layerId]?.name.orEmpty(),
                )
            }.forEach { (key, group) ->
                val totals = calculateTakeoffTotals(group, project.calibration)
                val square = project.displayUnit.metersPerUnit * project.displayUnit.metersPerUnit
                writer.appendLine(
                    listOf(
                        "TOTAL",
                        csv(key.template),
                        key.category,
                        csv(key.material),
                        csv(key.layer),
                        totals.itemCount,
                        project.displayUnit.symbol,
                        nonZero(project.displayUnit.fromMeters(totals.baseLengthMeters)),
                        nonZero(project.displayUnit.fromMeters(totals.adjustedLengthMeters)),
                        nonZero(totals.baseAreaSquareMeters / square),
                        nonZero(totals.adjustedAreaSquareMeters / square),
                        nonZero(totals.baseCount),
                        nonZero(totals.adjustedCount),
                    ).joinToString(delimiter),
                )
            }

            if (project.pageRevisions.isNotEmpty()) {
                writer.appendLine()
                writer.appendLine(
                    listOf(
                        "REVISION_LOG", "revision", "page", "created_at", "previous_uri", "new_uri",
                        "control_points", "carried_measurements", "needs_review", "note",
                    ).joinToString(delimiter),
                )
                project.pageRevisions.forEach { revision ->
                    val needsReview = project.measurements.count {
                        it.revisionId == revision.id && it.reviewStatus == MeasurementReviewStatus.NEEDS_REVIEW
                    }
                    writer.appendLine(
                        listOf(
                            "REVISION",
                            revision.revisionNumber,
                            revision.logicalPageIndex + 1,
                            timestamp(revision.createdAtEpochMs),
                            csv(revision.previousSource.documentUri),
                            csv(revision.currentSource.documentUri),
                            revision.alignment.controlPoints.size,
                            revision.carriedMeasurementIds.size,
                            needsReview,
                            csv(revision.note.orEmpty()),
                        ).joinToString(delimiter),
                    )
                }
            }
        }
    }

    private fun writePdf(request: ExportRequest, output: OutputStream) {
        val indices = when (request.pageSelection) {
            ExportPageSelection.CURRENT -> listOf(request.project.selectedPage)
            ExportPageSelection.ALL -> request.project.pages.indices.toList()
            ExportPageSelection.RANGE -> {
                require(request.firstPage in request.project.pages.indices)
                require(request.lastPage in request.firstPage..request.project.pages.lastIndex)
                (request.firstPage..request.lastPage).toList()
            }
        }
        require(indices.isNotEmpty())

        val document = PdfDocument()
        try {
            indices.forEachIndexed { outputIndex, sourceIndex ->
                val metadata = request.project.pages[sourceIndex]
                val width = metadata.width.toInt().coerceAtLeast(1)
                val height = metadata.height.toInt().coerceAtLeast(1)
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(width, height, outputIndex + 1).create(),
                )
                try {
                    page.canvas.drawColor(Color.WHITE)
                    val source = request.project.currentPageSource(sourceIndex)
                    val isPdf = source?.let {
                        it.mimeType == "application/pdf" || it.documentUri.substringBefore('?').endsWith(".pdf", true)
                    } == true
                    val bitmap = if (source != null && isPdf) {
                        openSourceDescriptor(Uri.parse(source.documentUri)).use { descriptor ->
                            PdfRenderer(descriptor).use { renderer ->
                                renderer.openPage(source.sourcePageIndex).use { sourcePage ->
                                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { rendered ->
                                        rendered.eraseColor(Color.WHITE)
                                        val matrix = Matrix().apply {
                                            setScale(
                                                width.toFloat() / sourcePage.width,
                                                height.toFloat() / sourcePage.height,
                                            )
                                        }
                                        sourcePage.render(rendered, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                    }
                                }
                            }
                        }
                    } else {
                        currentImageBitmap(request, sourceIndex)
                    }
                    bitmap?.let {
                        page.canvas.drawBitmap(
                            it,
                            null,
                            RectF(0f, 0f, width.toFloat(), height.toFloat()),
                            Paint(Paint.ANTI_ALIAS_FLAG),
                        )
                        it.recycle()
                    }
                    // The stamp goes under the overlay: a title block may never hide a measurement.
                    drawInfoBlock(page.canvas, request, sourceIndex, width.toFloat(), height.toFloat())
                    drawMeasurements(
                        page.canvas,
                        request.project,
                        sourceIndex,
                        width.toFloat() / metadata.width.toFloat(),
                        height.toFloat() / metadata.height.toFloat(),
                    )
                } finally {
                    document.finishPage(page)
                }
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun currentImageBitmap(request: ExportRequest, sourceIndex: Int): Bitmap? {
        val argb = request.pageArgb
        if (sourceIndex != request.project.selectedPage ||
            argb == null ||
            request.pagePixelWidth <= 0 ||
            request.pagePixelHeight <= 0
        ) {
            return null
        }
        return Bitmap.createBitmap(
            request.pagePixelWidth,
            request.pagePixelHeight,
            Bitmap.Config.ARGB_8888,
        ).apply {
            setPixels(
                argb,
                0,
                request.pagePixelWidth,
                0,
                0,
                request.pagePixelWidth,
                request.pagePixelHeight,
            )
        }
    }

    private fun drawMeasurements(
        canvas: android.graphics.Canvas,
        project: PlanProject,
        pageIndex: Int,
        scaleX: Float,
        scaleY: Float,
    ) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        project.measurements.filter { it.pageIndex == pageIndex }.forEach { measurement ->
            linePaint.strokeWidth = measurement.style.strokeWidth.coerceIn(0.5f, 24f)
            linePaint.color = measurement.style.colorArgb.toInt()
            val path = Path()
            measurement.points.forEachIndexed { index, point ->
                val x = point.x.toFloat() * scaleX
                val y = point.y.toFloat() * scaleY
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (measurement.type == MeasurementType.AREA) path.close()
            canvas.drawPath(path, linePaint)
            if (measurement.showLabel) {
                measurement.points.lastOrNull()?.let { anchor ->
                    val calculated = evaluate(
                        measurement,
                        project.calibration,
                        measurement.displayUnit ?: project.displayUnit,
                    ).label
                    val label = listOf(calculated, measurement.label.orEmpty())
                        .filter(String::isNotBlank)
                        .joinToString(" • ")
                    if (label.isNotBlank()) {
                        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.BLACK
                            textSize = measurement.style.textSize.coerceIn(8f, 72f)
                            style = Paint.Style.FILL
                        }
                        canvas.drawText(
                            label,
                            anchor.x.toFloat() * scaleX + 5f,
                            anchor.y.toFloat() * scaleY - 5f,
                            labelPaint,
                        )
                    }
                }
            }
        }
    }

    private data class InfoLine(val text: String, val swatchArgb: Int? = null)

    /**
     * Drawn as a corner stamp instead of an added band: the exported page keeps the
     * source geometry, so coordinates stay comparable with the original drawing.
     */
    private fun drawInfoBlock(
        canvas: android.graphics.Canvas,
        request: ExportRequest,
        pageIndex: Int,
        width: Float,
        height: Float,
    ) {
        val project = request.project
        val labels = request.labels
        val unit = project.displayUnit
        val lines = mutableListOf(
            InfoLine("${labels.project}: ${project.name}"),
            InfoLine(
                "${labels.exportedAt}: ${timestamp(request.exportedAtEpochMs)} · ${labels.units}: ${unit.symbol}",
            ),
        )
        if (request.includeScale) {
            val calibration = project.calibration
            lines += InfoLine(
                if (calibration == null) {
                    "${labels.scale}: ${labels.notCalibrated}"
                } else {
                    "${labels.scale}: ${calibration.description} · 1 → " +
                        "${formatNumber(unit.fromMeters(calibration.metersPerDocumentUnit))} ${unit.symbol}"
                },
            )
            calibration?.audit?.let { audit ->
                val actor = audit.calibratedBy.takeUnless { it == "Local user" }.orEmpty()
                    .ifBlank { labels.localUser }
                lines += InfoLine(
                    "${labels.calibratedAt}: ${timestamp(audit.calibratedAtEpochMs)} · " +
                        "${labels.calibratedBy}: $actor",
                )
                val method = when (calibration.method) {
                    Calibration.Method.REFERENCE -> {
                        val entered = audit.enteredLength?.let { value ->
                            val unit = audit.enteredUnit?.symbol.orEmpty()
                            " · ${formatNumber(value)} $unit"
                        }.orEmpty()
                        "${labels.referenceMethod}$entered"
                    }
                    Calibration.Method.PRINT_RATIO -> {
                        val ratio = audit.printRatio?.let { " 1:${formatRatio(it)}" }.orEmpty()
                        "${labels.printRatioMethod}$ratio"
                    }
                }
                lines += InfoLine("${labels.calibrationMethod}: $method · ${labels.page} ${audit.pageIndex + 1}")
                audit.verification?.let { verification ->
                    lines += InfoLine(
                        "${labels.verification}: ${labels.expected} " +
                            "${formatNumber(verification.expectedLength)} ${verification.unit.symbol} · " +
                            "${labels.measured} ${formatNumber(verification.measuredLength)} ${verification.unit.symbol} · " +
                            "Δ ${formatNumber(verification.relativeError * 100.0)}%",
                    )
                }
            }
        }
        project.pageRevisions.lastOrNull { it.logicalPageIndex == pageIndex }?.let { revision ->
            val needsReview = project.measurements.count {
                it.revisionId == revision.id && it.reviewStatus == MeasurementReviewStatus.NEEDS_REVIEW
            }
            lines += InfoLine(labels.revisionLog)
            lines += InfoLine(
                "${labels.revision} ${revision.revisionNumber} · ${labels.page} ${pageIndex + 1} · " +
                    timestamp(revision.createdAtEpochMs),
            )
            lines += InfoLine(
                "${labels.controlPoints}: ${revision.alignment.controlPoints.size} · " +
                    "${labels.needsReview}: $needsReview / ${revision.carriedMeasurementIds.size}",
            )
            revision.note?.let { lines += InfoLine("${labels.note}: $it") }
        }
        if (request.includeLegend) {
            val rows = legendRows(project, pageIndex, labels)
            if (rows.isNotEmpty()) {
                lines += InfoLine(labels.legend)
                lines += rows
            }
        }

        val textSize = (minOf(width, height) * 0.022f).coerceIn(4f, 18f)
        val lineHeight = textSize * 1.35f
        val padding = textSize * 0.8f
        val swatch = textSize * 0.8f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
        }
        val rows = ((height * 0.3f - padding * 2) / lineHeight).toInt().coerceAtMost(lines.size)
        if (rows < 1) return
        val visible = lines.take(rows)
        val blockWidth = visible
            .maxOf { textPaint.measureText(it.text) + if (it.swatchArgb == null) 0f else swatch * 1.6f }
            .plus(padding * 2)
            .coerceAtMost(width - padding * 2)
        val blockHeight = visible.size * lineHeight + padding * 2
        val top = height - padding - blockHeight
        if (top < 0f || blockWidth <= 0f) return

        val frame = RectF(padding, top, padding + blockWidth, top + blockHeight)
        val radius = textSize * 0.4f
        canvas.drawRoundRect(
            frame,
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 255, 255, 255) },
        )
        canvas.drawRoundRect(
            frame,
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(140, 0, 0, 0)
                style = Paint.Style.STROKE
                strokeWidth = maxOf(0.4f, textSize * 0.06f)
            },
        )
        visible.forEachIndexed { index, line ->
            val baseline = top + padding + lineHeight * (index + 1) - lineHeight * 0.3f
            var x = padding * 2
            line.swatchArgb?.let { argb ->
                canvas.drawRect(
                    RectF(x, baseline - swatch, x + swatch, baseline),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb },
                )
                x += swatch * 1.6f
            }
            canvas.drawText(line.text, x, baseline, textPaint)
        }
    }

    private fun legendRows(project: PlanProject, pageIndex: Int, labels: ExportLabels): List<InfoLine> {
        val unit = project.displayUnit
        val templates = project.takeoffTemplates.associateBy { it.id }
        return project.measurements
            .filter { it.pageIndex == pageIndex }
            .groupBy { measurement ->
                templates[measurement.templateId]?.name
                    ?: measurement.takeoff.material?.takeIf(String::isNotBlank)
                    ?: labels.category(measurement.takeoff.category)
            }
            .map { (name, group) ->
                val totals = calculateTakeoffTotals(group, project.calibration)
                val parts = mutableListOf("${group.size} ${labels.items}")
                if (totals.baseLengthMeters > 0.0) {
                    parts += totalPair(
                        unit.fromMeters(totals.baseLengthMeters),
                        unit.fromMeters(totals.adjustedLengthMeters),
                        unit.symbol,
                        labels,
                    )
                }
                if (totals.baseAreaSquareMeters > 0.0) {
                    val square = unit.metersPerUnit * unit.metersPerUnit
                    parts += totalPair(
                        totals.baseAreaSquareMeters / square,
                        totals.adjustedAreaSquareMeters / square,
                        "${unit.symbol}²",
                        labels,
                    )
                }
                if (totals.baseCount > 0.0) {
                    parts += totalPair(totals.baseCount, totals.adjustedCount, labels.items, labels)
                }
                InfoLine(
                    "$name: ${parts.joinToString(" · ")}",
                    group.first().style.colorArgb.toInt(),
                )
            }
    }

    private fun totalPair(base: Double, adjusted: Double, suffix: String, labels: ExportLabels): String =
        if (abs(base - adjusted) < 0.000_001) {
            "${formatNumber(base)} $suffix"
        } else {
            "${labels.baseQuantity} ${formatNumber(base)} → " +
                "${labels.withWaste} ${formatNumber(adjusted)} $suffix"
        }

    private fun nonZero(value: Double) = if (abs(value) < 0.000_001) "" else formatNumber(value)

    private fun formatNumber(value: Double) = String.format(Locale.US, "%.2f", value)
    private fun formatRatio(value: Double) =
        if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)

    private fun timestamp(epochMs: Long) =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))

    private fun openSourceDescriptor(uri: Uri): ParcelFileDescriptor =
        if (uri.scheme == "file") {
            ParcelFileDescriptor.open(
                File(requireNotNull(uri.path)),
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
        } else {
            requireNotNull(resolver.openFileDescriptor(uri, "r"))
        }

    private data class Values(
        val length: String = "",
        val perimeter: String = "",
        val area: String = "",
        val angle: String = "",
        val label: String = "",
    )

    private fun evaluate(measurement: Measurement, calibration: Calibration?, unit: LengthUnit): Values {
        val factor = calibration?.metersPerDocumentUnit ?: 0.0
        val lengthMeters = measurement.points.zipWithNext()
            .sumOf { (start, end) -> start.distanceTo(end) } * factor
        fun number(value: Double) = "%.3f".format(java.util.Locale.US, value)
        fun length(value: Double) = number(unit.fromMeters(value))
        fun polygonArea(): Double {
            if (measurement.points.size < 3) return 0.0
            return abs(
                measurement.points.indices.sumOf { index ->
                    val start = measurement.points[index]
                    val end = measurement.points[(index + 1) % measurement.points.size]
                    start.x * end.y - end.x * start.y
                },
            ) / 2.0 * factor * factor
        }
        fun angle(): Double {
            if (measurement.points.size < 3) return 0.0
            val (a, b, c) = measurement.points
            var degrees = Math.toDegrees(
                abs(atan2(a.y - b.y, a.x - b.x) - atan2(c.y - b.y, c.x - b.x)),
            )
            if (degrees > 180.0) degrees = 360.0 - degrees
            return degrees
        }
        return when (measurement.type) {
            MeasurementType.DISTANCE, MeasurementType.POLYLINE ->
                Values(length = length(lengthMeters), label = "${length(lengthMeters)} ${unit.symbol}")
            MeasurementType.AREA -> {
                val perimeterMeters = lengthMeters +
                    measurement.points.last().distanceTo(measurement.points.first()) * factor
                val areaInUnit = polygonArea() / (unit.metersPerUnit * unit.metersPerUnit)
                Values(
                    perimeter = length(perimeterMeters),
                    area = number(areaInUnit),
                    label = "${number(areaInUnit)} ${unit.symbol}²",
                )
            }
            MeasurementType.ANGLE -> Values(angle = number(angle()), label = "${number(angle())}°")
            MeasurementType.COUNTER -> Values(label = "1")
            MeasurementType.ANNOTATION -> Values()
        }
    }

    private fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""
}
