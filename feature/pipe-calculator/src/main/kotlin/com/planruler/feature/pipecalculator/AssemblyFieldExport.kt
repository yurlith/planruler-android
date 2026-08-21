package com.planruler.feature.pipecalculator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.planruler.fabrication3d.CapGeometry3D
import com.planruler.fabrication3d.ElbowGeometry3D
import com.planruler.fabrication3d.EqualTeeGeometry3D
import com.planruler.fabrication3d.FabricationPartKind
import com.planruler.fabrication3d.ParametricAssembly3D
import com.planruler.fabrication3d.ReducerGeometry3D
import com.planruler.fabrication3d.StraightPipeGeometry3D
import com.planruler.fabrication3d.WeldNeckFlangeGeometry3D
import com.planruler.model.AppLanguage
import com.planruler.model.InstallationJob
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

/** Compact, high-contrast sheet intended for a phone gallery or messenger attachment. */
internal object AssemblyFieldImageWriter {
    fun writePng(
        output: OutputStream,
        assembly: ParametricAssembly3D,
        jobName: String,
        language: AppLanguage,
        checkedBy: String?,
        checkedAtEpochMs: Long?,
    ) {
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(18, 29, 38)
                textSize = 44f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(65, 73, 80)
                textSize = 24f
            }
            canvas.drawText(jobName.take(64), 40f, 58f, title)
            canvas.drawText(
                "DN ${assembly.metadata.nominalDiameter} · ${fieldText(language, "размеры в mm", "dimensions in mm")}",
                40f,
                96f,
                subtitle,
            )
            val verification = verificationLabel(language, checkedBy, checkedAtEpochMs)
            val verificationPaint = Paint(subtitle).apply {
                color = if (checkedAtEpochMs != null) Color.rgb(18, 112, 70) else Color.rgb(173, 75, 21)
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(verification, 1560f, 65f, verificationPaint)

            drawImagePanel(canvas, assembly, AssemblyDrawingView.ISOMETRIC, RectF(40f, 125f, 1030f, 760f), language)
            drawSchedule(canvas, assembly, RectF(1050f, 125f, 1560f, 760f), language)
            drawImagePanel(canvas, assembly, AssemblyDrawingView.TOP, RectF(40f, 790f, 780f, 1160f), language)
            drawImagePanel(canvas, assembly, AssemblyDrawingView.SIDE, RectF(810f, 790f, 1560f, 1160f), language)

            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not encode field image" }
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawImagePanel(
        canvas: Canvas,
        assembly: ParametricAssembly3D,
        view: AssemblyDrawingView,
        area: RectF,
        language: AppLanguage,
    ) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 49, 56)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 32, 40)
            textSize = 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawRect(area, border)
        canvas.drawText(view.fieldLabel(language), area.left + 12f, area.top + 31f, heading)
        AssemblyDrawingRenderer.draw(
            canvas,
            AssemblyDrawingGenerator.generate(assembly, view, AssemblyDrawingLayer.INSTALLATION),
            RectF(area.left + 4f, area.top + 42f, area.right - 4f, area.bottom - 4f),
            defaultPrintDrawingColors(),
            showLabels = view == AssemblyDrawingView.ISOMETRIC,
        )
    }

    private fun drawSchedule(
        canvas: Canvas,
        assembly: ParametricAssembly3D,
        area: RectF,
        language: AppLanguage,
    ) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 49, 56)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 32, 40)
            textSize = 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 42, 48)
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        canvas.drawRect(area, border)
        canvas.drawText(fieldText(language, "РЕЗЫ И ДЕТАЛИ", "CUTS AND PARTS"), area.left + 12f, area.top + 31f, heading)
        assembly.parts.take(20).forEachIndexed { index, part ->
            val y = area.top + 68f + index * 27f
            canvas.drawText("${part.code}  ${part.fieldDescription().take(38)}", area.left + 12f, y, body)
        }
    }
}

internal object AssemblyFieldCsvWriter {
    fun write(
        output: OutputStream,
        assembly: ParametricAssembly3D,
        jobName: String,
        language: AppLanguage,
        checkedBy: String?,
        checkedAtEpochMs: Long?,
    ) {
        output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        output.write(csvText(assembly, jobName, language, checkedBy, checkedAtEpochMs).toByteArray(Charsets.UTF_8))
    }

    fun csvText(
        assembly: ParametricAssembly3D,
        jobName: String,
        language: AppLanguage,
        checkedBy: String?,
        checkedAtEpochMs: Long?,
    ): String = buildString {
        fun row(vararg values: Any?) {
            append(values.joinToString(";") { csvCell(it?.toString().orEmpty()) }).append("\r\n")
        }
        row(fieldText(language, "Монтажный узел", "Installation job"), jobName)
        row("DN", assembly.metadata.nominalDiameter)
        row(fieldText(language, "Статус", "Status"), verificationLabel(language, checkedBy, checkedAtEpochMs))
        row()
        row(
            fieldText(language, "Код", "Code"),
            fieldText(language, "Тип", "Type"),
            fieldText(language, "Описание", "Description"),
            fieldText(language, "Количество", "Quantity"),
            fieldText(language, "Рез, mm", "Cut, mm"),
        )
        assembly.parts.forEach { part ->
            row(
                part.code,
                part.definition.kind.name,
                part.fieldDescription(),
                1,
                (part.definition.geometry as? StraightPipeGeometry3D)?.lengthMm?.let(::fieldNumber).orEmpty(),
            )
        }
        row()
        row(fieldText(language, "МАТЕРИАЛЫ", "MATERIALS"))
        row(fieldText(language, "Тип", "Type"), fieldText(language, "Количество", "Quantity"), fieldText(language, "Всего трубы, mm", "Total pipe, mm"))
        FabricationPartKind.entries.forEach { kind ->
            val parts = assembly.parts.filter { it.definition.kind == kind }
            if (parts.isNotEmpty()) {
                val pipeLength = parts.sumOf { (it.definition.geometry as? StraightPipeGeometry3D)?.lengthMm ?: 0.0 }
                row(kind.name, parts.size, pipeLength.takeIf { it > 0.0 }?.let(::fieldNumber).orEmpty())
            }
        }
    }
}

/** One-file handoff: printable drawing, phone image, spreadsheet and acceptance passport. */
internal object AssemblyFieldPackageWriter {
    fun write(
        output: OutputStream,
        assembly: ParametricAssembly3D,
        job: InstallationJob,
        paper: AssemblyDrawingPaper,
        language: AppLanguage,
    ) {
        val pdf = ByteArrayOutputStream().also { bytes ->
            AssemblyDrawingPdfWriter.write(
                bytes,
                assembly,
                job.name,
                paper,
                language,
                job.checkedBy,
                job.checkedAtEpochMs,
            )
        }.toByteArray()
        val png = ByteArrayOutputStream().also { bytes ->
            AssemblyFieldImageWriter.writePng(
                bytes,
                assembly,
                job.name,
                language,
                job.checkedBy,
                job.checkedAtEpochMs,
            )
        }.toByteArray()
        val csv = ByteArrayOutputStream().also { bytes ->
            AssemblyFieldCsvWriter.write(
                bytes,
                assembly,
                job.name,
                language,
                job.checkedBy,
                job.checkedAtEpochMs,
            )
        }.toByteArray()
        val passport = buildString {
            appendLine("PLANRULER FIELD PACK v1")
            appendLine("${fieldText(language, "Монтажный узел", "Installation job")}: ${job.name}")
            appendLine("DN: ${assembly.metadata.nominalDiameter}")
            appendLine("${fieldText(language, "Формат листа", "Drawing paper")}: ${paper.name}")
            appendLine("${fieldText(language, "Статус", "Status")}: ${verificationLabel(language, job.checkedBy, job.checkedAtEpochMs)}")
            appendLine("${fieldText(language, "Создано", "Created")}: ${fieldDate(System.currentTimeMillis())}")
            appendLine()
            appendLine(fieldText(language, "Содержимое", "Contents"))
            appendLine("- installation_drawing.pdf")
            appendLine("- field_image.png")
            appendLine("- cuts_and_materials.csv")
        }.toByteArray(Charsets.UTF_8)

        ZipOutputStream(output).use { zip ->
            zip.entry("installation_drawing.pdf", pdf)
            zip.entry("field_image.png", png)
            zip.entry("cuts_and_materials.csv", csv)
            zip.entry("verification_passport.txt", passport)
        }
    }

    private fun ZipOutputStream.entry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }
}

internal fun verificationLabel(language: AppLanguage, checkedBy: String?, checkedAtEpochMs: Long?): String =
    if (checkedBy.isNullOrBlank() || checkedAtEpochMs == null) {
        fieldText(language, "НЕ ПРОВЕРЕНО", "NOT CHECKED")
    } else {
        "${fieldText(language, "ПРОВЕРЕНО", "CHECKED")}: ${checkedBy.trim()} · ${fieldDate(checkedAtEpochMs)}"
    }

private fun com.planruler.fabrication3d.PartInstance3D.fieldDescription(): String = when (val geometry = definition.geometry) {
    is StraightPipeGeometry3D -> "PIPE · CUT ${fieldNumber(geometry.lengthMm)} · Ø${fieldNumber(geometry.outsideDiameterMm)}×${fieldNumber(geometry.wallThicknessMm)}"
    is ElbowGeometry3D -> "ELBOW ${fieldNumber(abs(geometry.angleDeg))}° · CLR ${fieldNumber(geometry.centerlineRadiusMm)}"
    is WeldNeckFlangeGeometry3D -> "FLANGE · Ø${fieldNumber(geometry.outsideDiameterMm)} · PCD ${fieldNumber(geometry.boltCircleDiameterMm)} · ${geometry.boltHoleCount}×Ø${fieldNumber(geometry.boltHoleDiameterMm)}"
    is EqualTeeGeometry3D -> "TEE · RUN ${fieldNumber(geometry.overallRunMm)} · BR ${fieldNumber(geometry.branchCenterToEndMm)}"
    is ReducerGeometry3D -> "REDUCER · Ø${fieldNumber(geometry.largeOutsideDiameterMm)}/${fieldNumber(geometry.smallOutsideDiameterMm)} · L ${fieldNumber(geometry.lengthMm)}${if (geometry.eccentric) " · ECC" else ""}"
    is CapGeometry3D -> "CAP · H ${fieldNumber(geometry.heightMm)} · Ø${fieldNumber(geometry.outsideDiameterMm)}"
}

private fun AssemblyDrawingView.fieldLabel(language: AppLanguage): String = when (this) {
    AssemblyDrawingView.ISOMETRIC -> fieldText(language, "ИЗОМЕТРИЯ", "ISOMETRIC")
    AssemblyDrawingView.TOP -> fieldText(language, "СВЕРХУ", "TOP")
    AssemblyDrawingView.SIDE -> fieldText(language, "СБОКУ", "SIDE")
    AssemblyDrawingView.RIGHT -> fieldText(language, "СПРАВА", "RIGHT")
    AssemblyDrawingView.END -> fieldText(language, "С ТОРЦА", "END")
}

private fun fieldText(language: AppLanguage, russian: String, english: String): String =
    if (language == AppLanguage.RUSSIAN) russian else english

private fun fieldDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(epochMs))

private fun fieldNumber(value: Double): String = if (abs(value - value.toLong()) < 1e-6) {
    value.toLong().toString()
} else String.format(Locale.US, "%.1f", value)

private fun csvCell(value: String): String {
    val safe = if (value.firstOrNull() in setOf('=', '+', '-', '@')) "'$value" else value
    return if (safe.any { it == ';' || it == '"' || it == '\r' || it == '\n' }) {
        "\"${safe.replace("\"", "\"\"")}\""
    } else safe
}
