package com.planruler.feature.pipecalculator

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.planruler.fabrication3d.CapGeometry3D
import com.planruler.fabrication3d.ElbowGeometry3D
import com.planruler.fabrication3d.EqualTeeGeometry3D
import com.planruler.fabrication3d.ParametricAssembly3D
import com.planruler.fabrication3d.ReducerGeometry3D
import com.planruler.fabrication3d.StraightPipeGeometry3D
import com.planruler.fabrication3d.WeldNeckFlangeGeometry3D
import com.planruler.model.AppLanguage
import java.io.OutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

internal enum class AssemblyDrawingPaper(val widthPoints: Int, val heightPoints: Int) {
    A4(842, 595),
    A3(1191, 842),
}

internal object AssemblyDrawingPdfWriter {
    fun write(
        output: OutputStream,
        assembly: ParametricAssembly3D,
        jobName: String,
        paper: AssemblyDrawingPaper,
        language: AppLanguage,
        checkedBy: String? = null,
        checkedAtEpochMs: Long? = null,
    ) {
        val pdf = PdfDocument()
        try {
            drawViewsPage(pdf, assembly, jobName, paper, language, checkedBy, checkedAtEpochMs)
            drawFabricationPage(pdf, assembly, jobName, paper, language, checkedBy, checkedAtEpochMs)
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    private fun drawViewsPage(
        pdf: PdfDocument,
        assembly: ParametricAssembly3D,
        jobName: String,
        paper: AssemblyDrawingPaper,
        language: AppLanguage,
        checkedBy: String?,
        checkedAtEpochMs: Long?,
    ) {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(paper.widthPoints, paper.heightPoints, 1).create())
        val canvas = page.canvas
        val margin = if (paper == AssemblyDrawingPaper.A3) 26f else 18f
        val header = if (paper == AssemblyDrawingPaper.A3) 48f else 38f
        val titleBlock = if (paper == AssemblyDrawingPaper.A3) 58f else 46f
        drawPageFrame(canvas, paper, margin)
        drawHeader(canvas, jobName, language, margin, paper.widthPoints - margin, margin, header)
        val contentTop = margin + header
        val contentBottom = paper.heightPoints - margin - titleBlock
        val gap = if (paper == AssemblyDrawingPaper.A3) 12f else 7f
        val columns = 3
        val rows = 2
        val cellWidth = (paper.widthPoints - margin * 2f - gap * (columns - 1)) / columns
        val cellHeight = (contentBottom - contentTop - gap) / rows
        val views = listOf(
            AssemblyDrawingView.ISOMETRIC,
            AssemblyDrawingView.TOP,
            AssemblyDrawingView.SIDE,
            AssemblyDrawingView.RIGHT,
            AssemblyDrawingView.END,
        )
        views.forEachIndexed { index, view ->
            val row = index / columns
            val column = index % columns
            val left = margin + column * (cellWidth + gap)
            val top = contentTop + row * (cellHeight + gap)
            drawProjectionPanel(
                canvas = canvas,
                assembly = assembly,
                view = view,
                layer = AssemblyDrawingLayer.INSTALLATION,
                area = RectF(left, top, left + cellWidth, top + cellHeight),
                language = language,
                showLabels = view == AssemblyDrawingView.ISOMETRIC,
            )
        }
        val notesLeft = margin + 2 * (cellWidth + gap)
        val notesTop = contentTop + cellHeight + gap
        drawNotes(
            canvas,
            assembly,
            RectF(notesLeft, notesTop, notesLeft + cellWidth, contentBottom),
            language,
        )
        drawTitleBlock(
            canvas, assembly, jobName, paper, language, checkedBy, checkedAtEpochMs,
            pageNumber = 1, margin = margin, height = titleBlock,
        )
        pdf.finishPage(page)
    }

    private fun drawFabricationPage(
        pdf: PdfDocument,
        assembly: ParametricAssembly3D,
        jobName: String,
        paper: AssemblyDrawingPaper,
        language: AppLanguage,
        checkedBy: String?,
        checkedAtEpochMs: Long?,
    ) {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(paper.widthPoints, paper.heightPoints, 2).create())
        val canvas = page.canvas
        val margin = if (paper == AssemblyDrawingPaper.A3) 26f else 18f
        val header = if (paper == AssemblyDrawingPaper.A3) 48f else 38f
        val titleBlock = if (paper == AssemblyDrawingPaper.A3) 58f else 46f
        val gap = if (paper == AssemblyDrawingPaper.A3) 14f else 8f
        drawPageFrame(canvas, paper, margin)
        drawHeader(
            canvas,
            if (language == AppLanguage.RUSSIAN) "$jobName · Резка и детали" else "$jobName · Cutting and details",
            language,
            margin,
            paper.widthPoints - margin,
            margin,
            header,
        )
        val top = margin + header
        val bottom = paper.heightPoints - margin - titleBlock
        val leftWidth = (paper.widthPoints - margin * 2f) * 0.58f
        drawProjectionPanel(
            canvas,
            assembly,
            AssemblyDrawingView.ISOMETRIC,
            AssemblyDrawingLayer.CUTTING,
            RectF(margin, top, margin + leftWidth, bottom),
            language,
            showLabels = false,
        )
        drawPartsTable(
            canvas,
            assembly,
            RectF(margin + leftWidth + gap, top, paper.widthPoints - margin, bottom),
            language,
        )
        drawTitleBlock(
            canvas, assembly, jobName, paper, language, checkedBy, checkedAtEpochMs,
            pageNumber = 2, margin = margin, height = titleBlock,
        )
        pdf.finishPage(page)
    }

    private fun drawProjectionPanel(
        canvas: Canvas,
        assembly: ParametricAssembly3D,
        view: AssemblyDrawingView,
        layer: AssemblyDrawingLayer,
        area: RectF,
        language: AppLanguage,
        showLabels: Boolean = true,
    ) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(112, 120, 126)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        canvas.drawRect(area, border)
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 32, 40)
            textSize = (area.height() / 19f).coerceIn(8f, 15f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(view.pdfLabel(language), area.left + 6f, area.top + headingPaint.textSize + 3f, headingPaint)
        val drawingArea = RectF(area.left + 2f, area.top + headingPaint.textSize + 7f, area.right - 2f, area.bottom - 2f)
        AssemblyDrawingRenderer.draw(
            canvas,
            AssemblyDrawingGenerator.generate(assembly, view, layer),
            drawingArea,
            defaultPrintDrawingColors(),
            showLabels = showLabels,
        )
    }

    private fun drawHeader(
        canvas: Canvas,
        jobName: String,
        language: AppLanguage,
        left: Float,
        right: Float,
        top: Float,
        height: Float,
    ) {
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 29, 38)
            textSize = height * 0.42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(70, 79, 87)
            textSize = height * 0.24f
        }
        canvas.drawText(jobName, left + 6f, top + height * 0.42f, title)
        canvas.drawText(
            if (language == AppLanguage.RUSSIAN) "Монтажный чертёж · размеры в миллиметрах" else "Installation drawing · dimensions in millimetres",
            left + 6f,
            top + height * 0.76f,
            subtitle,
        )
        canvas.drawLine(left, top + height, right, top + height, Paint().apply { color = Color.DKGRAY; strokeWidth = 1f })
    }

    private fun drawNotes(canvas: Canvas, assembly: ParametricAssembly3D, area: RectF, language: AppLanguage) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 0.8f }
        canvas.drawRect(area, border)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 32, 40)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 8.5f }
        canvas.drawText(if (language == AppLanguage.RUSSIAN) "МОНТАЖ" else "INSTALLATION", area.left + 7f, area.top + 16f, title)
        val lines = if (language == AppLanguage.RUSSIAN) {
            listOf(
                "1. Проверить точки замера до резки.",
                "2. Размеры имеют приоритет над масштабом.",
                "3. Крестом отмечены сварные стыки.",
                "4. Выдержать направление конечного торца.",
                "5. DN ${assembly.metadata.nominalDiameter}; деталей ${assembly.parts.size}.",
            )
        } else {
            listOf(
                "1. Verify measurement points before cutting.",
                "2. Written dimensions override drawing scale.",
                "3. Cross marks identify welded joints.",
                "4. Keep the shown terminal direction.",
                "5. DN ${assembly.metadata.nominalDiameter}; ${assembly.parts.size} parts.",
            )
        }
        lines.forEachIndexed { index, line -> canvas.drawText(line, area.left + 7f, area.top + 32f + index * 13f, body) }
    }

    private fun drawPartsTable(canvas: Canvas, assembly: ParametricAssembly3D, area: RectF, language: AppLanguage) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 0.8f }
        val header = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 32, 40)
            textSize = (area.width() / 34f).coerceIn(9f, 14f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 42, 48)
            textSize = (area.width() / 48f).coerceIn(7f, 10f)
        }
        canvas.drawRect(area, border)
        canvas.drawText(if (language == AppLanguage.RUSSIAN) "ВЕДОМОСТЬ ДЕТАЛЕЙ" else "PART SCHEDULE", area.left + 7f, area.top + 17f, header)
        val maxRows = floor((area.height() - 28f) / 14f).toInt().coerceAtLeast(1)
        val shownParts = assembly.parts.take(maxRows)
        val rowHeight = ((area.height() - 28f) / (shownParts.size + 1).coerceAtLeast(2)).coerceIn(14f, 25f)
        var y = area.top + 28f
        shownParts.forEach { part ->
            canvas.drawLine(area.left, y, area.right, y, border)
            canvas.drawText(part.code, area.left + 6f, y + rowHeight * 0.67f, body)
            canvas.drawText(part.pdfDescription().take(72), area.left + area.width() * 0.18f, y + rowHeight * 0.67f, body)
            y += rowHeight
        }
    }

    private fun drawTitleBlock(
        canvas: Canvas,
        assembly: ParametricAssembly3D,
        jobName: String,
        paper: AssemblyDrawingPaper,
        language: AppLanguage,
        checkedBy: String?,
        checkedAtEpochMs: Long?,
        pageNumber: Int,
        margin: Float,
        height: Float,
    ) {
        val top = paper.heightPoints - margin - height
        val area = RectF(margin, top, paper.widthPoints - margin, paper.heightPoints - margin)
        val border = Paint().apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 1f }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 32, 40); textSize = height * 0.22f }
        val strong = Paint(text).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = height * 0.28f }
        canvas.drawRect(area, border)
        canvas.drawLine(area.left + area.width() * 0.58f, area.top, area.left + area.width() * 0.58f, area.bottom, border)
        canvas.drawText(jobName, area.left + 7f, area.top + height * 0.38f, strong)
        canvas.drawText(
            "DN ${assembly.metadata.nominalDiameter} · ${if (language == AppLanguage.RUSSIAN) "деталей" else "parts"} ${assembly.parts.size}",
            area.left + 7f,
            area.top + height * 0.72f,
            text,
        )
        val verification = verificationLabel(language, checkedBy, checkedAtEpochMs)
        val verificationPaint = Paint(text).apply {
            color = if (checkedAtEpochMs != null) Color.rgb(18, 112, 70) else Color.rgb(173, 75, 21)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = height * 0.18f
        }
        canvas.drawText(verification.take(74), area.left + area.width() * 0.30f, area.top + height * 0.72f, verificationPaint)
        val right = area.left + area.width() * 0.60f
        canvas.drawText("${paper.name} · ${if (language == AppLanguage.RUSSIAN) "Лист" else "Sheet"} $pageNumber/2", right, area.top + height * 0.42f, strong)
        canvas.drawText(if (language == AppLanguage.RUSSIAN) "НЕ МАСШТАБИРОВАТЬ" else "DO NOT SCALE", right, area.top + height * 0.75f, text)
    }

    private fun drawPageFrame(canvas: Canvas, paper: AssemblyDrawingPaper, margin: Float) {
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(
            RectF(margin, margin, paper.widthPoints - margin, paper.heightPoints - margin),
            Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.4f },
        )
    }
}

private fun AssemblyDrawingView.pdfLabel(language: AppLanguage): String = if (language == AppLanguage.RUSSIAN) {
    when (this) {
        AssemblyDrawingView.ISOMETRIC -> "ИЗОМЕТРИЯ"
        AssemblyDrawingView.TOP -> "СВЕРХУ"
        AssemblyDrawingView.SIDE -> "СБОКУ"
        AssemblyDrawingView.RIGHT -> "СПРАВА"
        AssemblyDrawingView.END -> "С ТОРЦА"
    }
} else name

private fun com.planruler.fabrication3d.PartInstance3D.pdfDescription(): String = when (val geometry = definition.geometry) {
    is StraightPipeGeometry3D -> "PIPE · CUT ${pdfNumber(geometry.lengthMm)} · Ø${pdfNumber(geometry.outsideDiameterMm)}×${pdfNumber(geometry.wallThicknessMm)}"
    is ElbowGeometry3D -> "ELBOW ${pdfNumber(abs(geometry.angleDeg))}° · CLR ${pdfNumber(geometry.centerlineRadiusMm)} · Ø${pdfNumber(geometry.outsideDiameterMm)}"
    is WeldNeckFlangeGeometry3D -> "FLANGE · Ø${pdfNumber(geometry.outsideDiameterMm)} · L ${pdfNumber(geometry.faceToWeldMm)} · PCD ${pdfNumber(geometry.boltCircleDiameterMm)} · ${geometry.boltHoleCount}×Ø${pdfNumber(geometry.boltHoleDiameterMm)}"
    is EqualTeeGeometry3D -> "TEE · RUN ${pdfNumber(geometry.overallRunMm)} · BR ${pdfNumber(geometry.branchCenterToEndMm)} · Ø${pdfNumber(geometry.outsideDiameterMm)}"
    is ReducerGeometry3D -> "REDUCER · Ø${pdfNumber(geometry.largeOutsideDiameterMm)}/${pdfNumber(geometry.smallOutsideDiameterMm)} · L ${pdfNumber(geometry.lengthMm)}${if (geometry.eccentric) " · ECC" else ""}"
    is CapGeometry3D -> "CAP · H ${pdfNumber(geometry.heightMm)} · Ø${pdfNumber(geometry.outsideDiameterMm)}"
}

private fun pdfNumber(value: Double): String = if (abs(value - value.toLong()) < 1e-6) {
    value.toLong().toString()
} else String.format(Locale.US, "%.1f", value)
