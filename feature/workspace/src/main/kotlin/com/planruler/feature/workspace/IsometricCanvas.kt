package com.planruler.feature.workspace

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planruler.designsystem.theme.LocalPlanRulerDimens
import com.planruler.isometric.IsoGrid
import com.planruler.isometric.IsoLabeler
import com.planruler.isometric.IsoProjection
import com.planruler.isometric.IsoScene
import com.planruler.isometric.IsoSceneBuilder
import com.planruler.isometric.IsoSceneStyle
import com.planruler.isometric.IsoSelection
import com.planruler.isometric.IsoStroke
import com.planruler.isometric.IsoText
import com.planruler.isometric.IsoTextAlign
import com.planruler.isometric.IsoWeight
import com.planruler.isometric.P2
import com.planruler.isometric.Rect2
import com.planruler.isometric.RunPlan
import com.planruler.isometric.Vec3
import com.planruler.model.DocPoint
import com.planruler.model.IsometricSheet
import com.planruler.model.ScreenPoint
import com.planruler.model.ViewportState
import com.planruler.model.ViewportTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

internal const val ISO_MIN_ZOOM = 0.002
internal const val ISO_MAX_ZOOM = 20.0
private const val EMPTY_FIELD_MM = 6_000.0

/**
 * The isometric canvas reuses the plan viewport maths: document coordinates are drawing
 * millimetres with Y flipped, zoom is screen pixels per drawing millimetre.
 */
internal fun isoTransform(width: Double, height: Double, viewport: ViewportState) =
    ViewportTransform(width, height, viewport, minZoom = ISO_MIN_ZOOM, maxZoom = ISO_MAX_ZOOM)

internal fun ViewportTransform.toDrawing(offset: Offset): P2 =
    screenToDocument(ScreenPoint(offset.x.toDouble(), offset.y.toDouble())).let { P2(it.x, -it.y) }

internal fun ViewportTransform.toScreen(point: P2): Offset =
    documentToScreen(DocPoint(point.x, -point.y)).let { Offset(it.x.toFloat(), it.y.toFloat()) }

/** Fits the drawn network into the view, or shows a 6 m field around the origin when empty. */
internal fun isoFitViewport(sheet: IsometricSheet, size: IntSize): ViewportState {
    val width = size.width.coerceAtLeast(1).toDouble()
    val height = size.height.coerceAtLeast(1).toDouble()
    val projection = IsoProjection.of(sheet.projection)
    val bounds = Rect2.around(sheet.nodes.map { projection.project(it.position) })
    if (bounds == null) {
        return ViewportState((min(width, height) / EMPTY_FIELD_MM).coerceIn(ISO_MIN_ZOOM, ISO_MAX_ZOOM), 0.0, 0.0)
    }
    // Annotations sit outside the pipes; leave a generous frame for them.
    val spanX = max(bounds.width, 500.0)
    val spanY = max(bounds.height, 500.0)
    val zoom = (min(width / spanX, height / spanY) * 0.7).coerceIn(ISO_MIN_ZOOM, ISO_MAX_ZOOM)
    val center = bounds.center
    return ViewportState(zoom, center.x, -center.y)
}

@Composable
internal fun IsometricCanvas(
    sheet: IsometricSheet,
    viewport: ViewportState,
    selection: IsoSelection,
    preview: RunPlan?,
    chainStart: Vec3?,
    labeler: IsoLabeler,
    description: String,
    onSize: (IntSize) -> Unit,
    onViewport: (ViewportState) -> Unit,
    onTap: (P2, Boolean) -> Unit,
    onLongPress: (P2) -> Unit,
    onDragStart: (P2) -> Boolean,
    onDrag: (P2) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    movingSheet: IsometricSheet? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dimens = LocalPlanRulerDimens.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Live inside a gesture, synchronised after outside changes (fit, sheet switch).
    val gestureViewport = remember(sheet.id) { mutableStateOf(viewport) }
    SideEffect { gestureViewport.value = viewport }
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val dragStart by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val dragCancel by rememberUpdatedState(onDragCancel)
    val viewportChanged by rememberUpdatedState(onViewport)

    fun live() = isoTransform(size.width.toDouble(), size.height.toDouble(), gestureViewport.value)

    // Annotations are laid out for zoom steps of 25 % and off the main thread: panning and
    // pinching between steps redraw the last layout scaled, so gestures stay smooth on large
    // schemes. The rubber band is cheap and follows the finger every frame.
    val zoom = viewport.zoom.coerceIn(ISO_MIN_ZOOM, ISO_MAX_ZOOM)
    val layoutZoom = remember(zoom) { LAYOUT_ZOOM_STEP.pow(round(ln(zoom) / ln(LAYOUT_ZOOM_STEP))) }
    val textPx = with(density) { 11.sp.toPx() }.toDouble()
    val style = remember(layoutZoom, textPx) { IsoSceneStyle.forScreen(pxPerMm = layoutZoom, textPx = textPx) }
    val scene by produceState<IsoScene?>(null, sheet, style, labeler, selection) {
        value = withContext(Dispatchers.Default) { IsoSceneBuilder.build(sheet, style, labeler, selection) }
    }
    val rubberBand = remember(sheet, preview, style, labeler) {
        preview?.let { IsoSceneBuilder.preview(sheet, it, style, labeler) }
    }
    // Only cheap pipe geometry follows a node gesture; annotations are laid out after release.
    val movingScene = remember(movingSheet, style, selection, labeler) {
        movingSheet?.let {
            IsoSceneBuilder.build(it.copy(showDimensions = false, showElevations = false, showSystemCodes = false), style, labeler, selection)
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .testTag("isometric-canvas")
            .semantics { contentDescription = description }
            .onSizeChanged { size = it; onSize(it) }
            .canvasGestures(
                key = sheet.id,
                dragSlopPx = with(density) { dimens.dragSlop.toPx() },
                longPressMs = 480L,
                doubleTapMs = dimens.doubleTapWindowMs,
                onTap = { offset, double -> tap(live().toDrawing(offset), double) },
                onLongPress = { offset -> longPress(live().toDrawing(offset)) },
                onDragStart = { offset -> dragStart(live().toDrawing(offset)) },
                onDrag = { offset -> drag(live().toDrawing(offset)) },
                onDragEnd = { dragEnd() },
                onDragCancel = { dragCancel() },
                onTransform = { centroid, pan, zoom ->
                    if (size.width > 0) {
                        val zoomed = live().zoomAt(zoom.toDouble(), ScreenPoint(centroid.x.toDouble(), centroid.y.toDouble()))
                        val next = isoTransform(size.width.toDouble(), size.height.toDouble(), zoomed)
                            .panBy(pan.x.toDouble(), pan.y.toDouble())
                        gestureViewport.value = next
                        viewportChanged(next)
                    }
                },
                onPan = { pan ->
                    if (size.width > 0) {
                        val next = live().panBy(pan.x.toDouble(), pan.y.toDouble())
                        gestureViewport.value = next
                        viewportChanged(next)
                    }
                },
            ),
    ) {
        drawRect(PAPER)
        val transform = isoTransform(this.size.width.toDouble(), this.size.height.toDouble(), viewport)
        drawGrid(sheet, transform, zoom)
        listOfNotNull(movingScene ?: scene, rubberBand).forEach { layer ->
            layer.strokes.forEach { stroke -> drawIsoStroke(stroke, transform) }
            layer.dots.forEach { dot ->
                val center = transform.toScreen(dot.center)
                val radius = (dot.radiusMm * zoom).toFloat().coerceAtLeast(2.dp.toPx())
                if (dot.filled) {
                    drawCircle(Color(dot.colorArgb), radius, center)
                } else {
                    drawCircle(PAPER, radius, center)
                    drawCircle(Color(dot.colorArgb), radius, center, style = Stroke(1.5.dp.toPx()))
                }
            }
            drawIsoTexts(layer.texts, transform, zoom)
        }
        chainStart?.let { start ->
            val point = transform.toScreen(IsoProjection.of(sheet.projection).project(start))
            drawCircle(Color(style.previewArgb), 7.dp.toPx(), point, style = Stroke(2.dp.toPx()))
            drawCircle(Color(style.previewArgb), 2.5.dp.toPx(), point)
        }
    }
}

private const val LAYOUT_ZOOM_STEP = 1.25

private val PAPER = Color(0xFFFFFFFF)
private val GRID_MINOR = Color(0xFFE6EBF0)
private val GRID_MAJOR = Color(0xFFC3CDD6)

private fun DrawScope.drawGrid(sheet: IsometricSheet, transform: ViewportTransform, zoom: Double) {
    val projection = IsoProjection.of(sheet.projection)
    val corners = listOf(
        transform.toDrawing(Offset.Zero),
        transform.toDrawing(Offset(size.width, 0f)),
        transform.toDrawing(Offset(0f, size.height)),
        transform.toDrawing(Offset(size.width, size.height)),
    )
    val bounds = Rect2.around(corners) ?: return
    val spec = IsoGrid.spec(sheet.gridStepMm, zoom, minSpacingPx = 12.dp.toPx().toDouble())
    IsoGrid.lines(projection, bounds, spec).forEach { line ->
        drawLine(
            if (line.major) GRID_MAJOR else GRID_MINOR,
            transform.toScreen(line.start),
            transform.toScreen(line.end),
            strokeWidth = if (line.major) 1.2.dp.toPx() else 0.7.dp.toPx(),
        )
    }
}

private fun DrawScope.weightPx(weight: IsoWeight): Float = when (weight) {
    IsoWeight.THICK -> 3.dp.toPx()
    IsoWeight.MEDIUM -> 1.6.dp.toPx()
    IsoWeight.THIN -> 1.dp.toPx()
}

private fun DrawScope.drawIsoStroke(stroke: IsoStroke, transform: ViewportTransform) {
    if (stroke.points.size < 2) return
    val color = Color(stroke.colorArgb)
    val width = weightPx(stroke.weight)
    val effect = if (stroke.dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())) else null
    val points = stroke.points.map(transform::toScreen)
    if (points.size == 2 && !stroke.closed && !stroke.filled) {
        drawLine(color, points[0], points[1], strokeWidth = width, cap = StrokeCap.Round, pathEffect = effect)
        return
    }
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        if (stroke.closed || stroke.filled) close()
    }
    if (stroke.filled) drawPath(path, color)
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, pathEffect = effect))
}

/** Text anchored at mid cap height and rotated counter-clockwise in drawing space. */
private fun DrawScope.drawIsoTexts(texts: List<IsoText>, transform: ViewportTransform, zoom: Double) {
    if (texts.isEmpty()) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    drawIntoCanvas { canvas ->
        texts.forEach { text ->
            val anchor = transform.toScreen(text.anchor)
            val capHeight = (text.heightMm * zoom).toFloat()
            if (capHeight < 4f) return@forEach
            paint.color = text.colorArgb.toInt()
            paint.textSize = capHeight * 1.35f
            paint.textAlign = when (text.align) {
                IsoTextAlign.START -> Paint.Align.LEFT
                IsoTextAlign.CENTER -> Paint.Align.CENTER
                IsoTextAlign.END -> Paint.Align.RIGHT
            }
            canvas.nativeCanvas.save()
            // Screen Y points down, so a counter-clockwise drawing angle is a negative rotation.
            canvas.nativeCanvas.rotate(-text.angleDeg.toFloat(), anchor.x, anchor.y)
            canvas.nativeCanvas.drawText(text.text, anchor.x, anchor.y + capHeight / 2f, paint)
            canvas.nativeCanvas.restore()
        }
    }
}
