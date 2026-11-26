package com.planruler.export.android

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import com.planruler.isometric.IsoPaperDrawing
import com.planruler.isometric.IsoTextAlign
import com.planruler.isometric.IsoWeight
import java.io.OutputStream
import java.util.Locale
import kotlin.math.ceil

/** Vector geometry and embedded Android font glyphs, including Cyrillic. */
internal fun writeIsometricPdf(title: String, drawing: IsoPaperDrawing, output: OutputStream) {
    val pointsPerMm = 72.0 / 25.4
    val unit = pointsPerMm / drawing.scale
    val pageWidth = ceil(drawing.widthMm * pointsPerMm).toInt()
    val pageHeight = ceil(drawing.heightMm * pointsPerMm).toInt()
    val bounds = drawing.bounds
    val left = (pageWidth - bounds.width * unit) / 2.0
    val top = (pageHeight - 15.0 * pointsPerMm - bounds.height * unit) / 2.0
    fun x(value: Double) = (left + (value - bounds.minX) * unit).toFloat()
    fun y(value: Double) = (top + (bounds.maxY - value) * unit).toFloat()
    fun width(weight: IsoWeight) = (drawing.style.textHeightMm * unit * when (weight) {
        IsoWeight.THICK -> 0.28
        IsoWeight.MEDIUM -> 0.14
        IsoWeight.THIN -> 0.08
    }).toFloat()

    val pdf = PdfDocument()
    try {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        drawing.scene.strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            val path = Path().apply {
                moveTo(x(stroke.points.first().x), y(stroke.points.first().y))
                stroke.points.drop(1).forEach { lineTo(x(it.x), y(it.y)) }
                if (stroke.closed || stroke.filled) close()
            }
            paint.color = stroke.colorArgb.toInt()
            paint.strokeWidth = width(stroke.weight)
            paint.style = if (stroke.filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
            paint.pathEffect = if (stroke.dashed) DashPathEffect(
                floatArrayOf((drawing.style.textHeightMm * unit * 1.6).toFloat(), (drawing.style.textHeightMm * unit * 0.8).toFloat()), 0f,
            ) else null
            canvas.drawPath(path, paint)
        }
        paint.pathEffect = null
        drawing.scene.dots.forEach { dot ->
            val radius = (dot.radiusMm * unit).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = if (dot.filled) dot.colorArgb.toInt() else Color.WHITE
            canvas.drawCircle(x(dot.center.x), y(dot.center.y), radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = width(IsoWeight.THIN)
            paint.color = dot.colorArgb.toInt()
            canvas.drawCircle(x(dot.center.x), y(dot.center.y), radius, paint)
        }
        paint.style = Paint.Style.FILL
        drawing.scene.texts.forEach { text ->
            val cap = (text.heightMm * unit).toFloat()
            paint.textSize = cap * 1.35f
            paint.color = text.colorArgb.toInt()
            paint.textAlign = when (text.align) {
                IsoTextAlign.START -> Paint.Align.LEFT
                IsoTextAlign.CENTER -> Paint.Align.CENTER
                IsoTextAlign.END -> Paint.Align.RIGHT
            }
            val ax = x(text.anchor.x)
            val ay = y(text.anchor.y)
            canvas.save()
            canvas.rotate(-text.angleDeg.toFloat(), ax, ay)
            canvas.drawText(text.text, ax, ay + cap / 2f, paint)
            canvas.restore()
        }
        paint.color = Color.DKGRAY
        paint.textSize = 11f
        paint.textAlign = Paint.Align.RIGHT
        val scaleText = "1:" + String.format(Locale.US, "%.0f", drawing.scale)
        val margin = (10 * pointsPerMm).toFloat()
        val baseline = pageHeight - margin
        canvas.drawText(scaleText, pageWidth - margin, baseline, paint)
        paint.textAlign = Paint.Align.LEFT
        val cleanTitle = title.replace('\n', ' ').replace('\r', ' ')
        val available = pageWidth - margin * 2 - paint.measureText(scaleText) - 24
        val count = paint.breakText(cleanTitle, true, available, null)
        canvas.drawText(cleanTitle.take(count), margin, baseline, paint)
        pdf.finishPage(page)
        pdf.writeTo(output)
    } finally {
        pdf.close()
    }
}
