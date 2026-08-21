package com.planruler.feature.pipecalculator

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.planruler.fabrication3d.FabricationPartKind
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal data class AssemblyDrawingColors(
    val background: Int,
    val foreground: Int,
    val pipe: Int,
    val fitting: Int,
    val selected: Int,
    val dimension: Int,
    val muted: Int,
    val weld: Int,
)

internal object AssemblyDrawingRenderer {
    const val VIEWPORT_PADDING = 52.0

    fun viewportPadding(drawing: AssemblyDrawing2D, width: Double, height: Double): Double {
        val shortSide = min(width, height)
        val density = (shortSide / 360.0).coerceIn(0.72, 2.2)
        val maxLane = drawing.dimensions.maxOfOrNull { it.lane } ?: 1
        val dimensionRoom = (22.0 + maxLane * 7.0) * density
        return min(shortSide * 0.29, max(VIEWPORT_PADDING * density, dimensionRoom))
    }

    fun draw(
        canvas: Canvas,
        drawing: AssemblyDrawing2D,
        area: RectF,
        colors: AssemblyDrawingColors,
        selectedPartId: String? = null,
        showLabels: Boolean = true,
    ) {
        if (area.width() <= 1f || area.height() <= 1f) return
        val density = (min(area.width(), area.height()) / 360f).coerceIn(0.72f, 2.2f)
        val viewport = DrawingViewport2D(
            drawingBounds = drawing.bounds,
            left = area.left.toDouble(),
            top = area.top.toDouble(),
            width = area.width().toDouble(),
            height = area.height().toDouble(),
            padding = viewportPadding(drawing, area.width().toDouble(), area.height().toDouble()),
        )
        canvas.save()
        canvas.clipRect(area)
        canvas.drawColor(colors.background)
        drawGrid(canvas, area, colors, density)
        drawCircles(canvas, drawing, viewport, colors, selectedPartId, density)
        drawStrokes(canvas, drawing, viewport, colors, selectedPartId, density)
        drawWelds(canvas, drawing, viewport, colors, density)
        drawDimensions(canvas, drawing, viewport, colors, selectedPartId, density)
        if (showLabels) drawLabels(canvas, drawing, viewport, colors, selectedPartId, density, area)
        canvas.restore()
    }

    private fun drawGrid(canvas: Canvas, area: RectF, colors: AssemblyDrawingColors, density: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.muted
            alpha = 28
            strokeWidth = density
        }
        val spacing = 28f * density
        var x = area.left
        while (x <= area.right) {
            canvas.drawLine(x, area.top, x, area.bottom, paint)
            x += spacing
        }
        var y = area.top
        while (y <= area.bottom) {
            canvas.drawLine(area.left, y, area.right, y, paint)
            y += spacing
        }
    }

    private fun drawStrokes(
        canvas: Canvas,
        drawing: AssemblyDrawing2D,
        viewport: DrawingViewport2D,
        colors: AssemblyDrawingColors,
        selectedPartId: String?,
        density: Float,
    ) {
        drawing.strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            val selected = stroke.partId == selectedPartId
            val baseColor = if (stroke.kind == FabricationPartKind.PIPE) colors.pipe else colors.fitting
            val path = Path()
            stroke.points.map(viewport::map).forEachIndexed { index, point ->
                if (index == 0) path.moveTo(point.x.toFloat(), point.y.toFloat())
                else path.lineTo(point.x.toFloat(), point.y.toFloat())
            }
            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = if (selected) colors.selected else baseColor
                alpha = if (selected) 220 else 110
                strokeWidth = max(5f * density, (stroke.outsideRadiusMm * 2.0 * viewport.scale).toFloat())
            }
            canvas.drawPath(path, body)
            val centerline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = if (selected) colors.foreground else baseColor
                strokeWidth = if (selected) 2.8f * density else 1.8f * density
                pathEffect = DashPathEffect(floatArrayOf(10f * density, 4f * density, 2f * density, 4f * density), 0f)
            }
            canvas.drawPath(path, centerline)
        }
    }

    private fun drawCircles(
        canvas: Canvas,
        drawing: AssemblyDrawing2D,
        viewport: DrawingViewport2D,
        colors: AssemblyDrawingColors,
        selectedPartId: String?,
        density: Float,
    ) {
        val seen = hashSetOf<String>()
        drawing.circles.forEach { circle ->
            val center = viewport.map(circle.center)
            val radius = max(2.5f * density, (circle.radiusMm * viewport.scale).toFloat())
            val key = "${circle.partId}:${center.x.toInt()}:${center.y.toInt()}:${radius.toInt()}"
            if (!seen.add(key)) return@forEach
            val selected = circle.partId == selectedPartId
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = if (selected) colors.selected else colors.fitting
                strokeWidth = if (selected) 3f * density else 1.8f * density
            }
            canvas.drawCircle(center.x.toFloat(), center.y.toFloat(), radius, paint)
            canvas.drawLine(center.x.toFloat() - radius, center.y.toFloat(), center.x.toFloat() + radius, center.y.toFloat(), paint)
            canvas.drawLine(center.x.toFloat(), center.y.toFloat() - radius, center.x.toFloat(), center.y.toFloat() + radius, paint)
        }
    }

    private fun drawWelds(
        canvas: Canvas,
        drawing: AssemblyDrawing2D,
        viewport: DrawingViewport2D,
        colors: AssemblyDrawingColors,
        density: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.weld
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        val arm = 5f * density
        drawing.welds.forEach { weld ->
            val point = viewport.map(weld)
            canvas.drawLine(point.x.toFloat() - arm, point.y.toFloat() - arm, point.x.toFloat() + arm, point.y.toFloat() + arm, paint)
            canvas.drawLine(point.x.toFloat() - arm, point.y.toFloat() + arm, point.x.toFloat() + arm, point.y.toFloat() - arm, paint)
        }
    }

    private fun drawDimensions(
        canvas: Canvas,
        drawing: AssemblyDrawing2D,
        viewport: DrawingViewport2D,
        colors: AssemblyDrawingColors,
        selectedPartId: String?,
        density: Float,
    ) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10.5f * density
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        drawing.dimensions.forEach { dimension ->
            val start = viewport.map(dimension.start)
            val end = viewport.map(dimension.end)
            val dx = end.x - start.x
            val dy = end.y - start.y
            val length = hypot(dx, dy)
            if (length < 2.0) return@forEach
            val nx = -dy / length
            val ny = dx / length
            val direction = if (dimension.lane % 2 == 0) -1.0 else 1.0
            val distance = (12.0 + dimension.lane * 7.0) * density * direction
            val a = DrawingPoint2D(start.x + nx * distance, start.y + ny * distance)
            val b = DrawingPoint2D(end.x + nx * distance, end.y + ny * distance)
            val highlighted = dimension.partId != null && dimension.partId == selectedPartId
            val color = if (highlighted) colors.selected else colors.dimension
            linePaint.color = color
            textPaint.color = color
            canvas.drawLine(start.x.toFloat(), start.y.toFloat(), a.x.toFloat(), a.y.toFloat(), linePaint)
            canvas.drawLine(end.x.toFloat(), end.y.toFloat(), b.x.toFloat(), b.y.toFloat(), linePaint)
            canvas.drawLine(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), linePaint)
            drawArrow(canvas, a, b, linePaint, density)
            drawArrow(canvas, b, a, linePaint, density)
            val midX = ((a.x + b.x) / 2.0).toFloat()
            val midY = ((a.y + b.y) / 2.0 - 4.0 * density).toFloat()
            val textWidth = textPaint.measureText(dimension.label)
            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = colors.background
                alpha = 228
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                midX - textWidth / 2f - 4f * density,
                midY - textPaint.textSize,
                midX + textWidth / 2f + 4f * density,
                midY + 3f * density,
                4f * density,
                4f * density,
                background,
            )
            canvas.drawText(dimension.label, midX, midY, textPaint)
        }
    }

    private fun drawArrow(canvas: Canvas, tip: DrawingPoint2D, other: DrawingPoint2D, paint: Paint, density: Float) {
        val dx = other.x - tip.x
        val dy = other.y - tip.y
        val length = hypot(dx, dy).coerceAtLeast(1e-6)
        val ux = dx / length
        val uy = dy / length
        val size = 5.5 * density
        val normalX = -uy
        val normalY = ux
        canvas.drawLine(
            tip.x.toFloat(),
            tip.y.toFloat(),
            (tip.x + ux * size + normalX * size * 0.55).toFloat(),
            (tip.y + uy * size + normalY * size * 0.55).toFloat(),
            paint,
        )
        canvas.drawLine(
            tip.x.toFloat(),
            tip.y.toFloat(),
            (tip.x + ux * size - normalX * size * 0.55).toFloat(),
            (tip.y + uy * size - normalY * size * 0.55).toFloat(),
            paint,
        )
    }

    private fun drawLabels(
        canvas: Canvas,
        drawing: AssemblyDrawing2D,
        viewport: DrawingViewport2D,
        colors: AssemblyDrawingColors,
        selectedPartId: String?,
        density: Float,
        area: RectF,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f * density
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val occupied = mutableListOf<RectF>()
        drawing.labels.forEachIndexed { index, label ->
            val anchor = viewport.map(label.position)
            paint.color = if (label.partId == selectedPartId) colors.selected else colors.foreground
            val text = label.text
            val width = paint.measureText(text)
            var x = (anchor.x + 7.0 * density).toFloat().coerceIn(area.left + 3f, max(area.left + 3f, area.right - width - 7f))
            var y = (anchor.y - 7.0 * density + (index % 2) * 13.0 * density).toFloat()
            var box = RectF(x - 3f, y - paint.textSize, x + width + 3f, y + 3f)
            repeat(6) {
                if (occupied.none { RectF.intersects(it, box) }) return@repeat
                y += 13f * density
                if (y > area.bottom - 4f) y = area.top + paint.textSize + 4f
                box = RectF(x - 3f, y - paint.textSize, x + width + 3f, y + 3f)
            }
            val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.background
                alpha = 220
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(box, 4f * density, 4f * density, bubble)
            canvas.drawText(text, x, y, paint)
            occupied += box
        }
    }
}

internal fun defaultPrintDrawingColors() = AssemblyDrawingColors(
    background = Color.WHITE,
    foreground = Color.rgb(25, 32, 40),
    pipe = Color.rgb(28, 92, 137),
    fitting = Color.rgb(45, 73, 89),
    selected = Color.rgb(214, 78, 34),
    dimension = Color.rgb(117, 55, 136),
    muted = Color.rgb(108, 117, 125),
    weld = Color.rgb(190, 83, 23),
)
