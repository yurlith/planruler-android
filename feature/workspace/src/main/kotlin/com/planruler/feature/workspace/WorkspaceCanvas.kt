package com.planruler.feature.workspace

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planruler.designsystem.PlanRulerTestTags
import com.planruler.designsystem.theme.LocalCanvasColors
import com.planruler.designsystem.theme.LocalPlanRulerDimens
import com.planruler.designsystem.theme.PlanRulerCanvasColors
import com.planruler.document.api.RenderedPage
import com.planruler.document.api.RenderedTile
import com.planruler.engine.api.EngineState
import com.planruler.engine.api.SnapResult
import com.planruler.model.DocPoint
import com.planruler.model.LayerId
import com.planruler.model.MagnifierPlacement
import com.planruler.model.Measurement
import com.planruler.model.MeasurementId
import com.planruler.model.MeasurementType
import com.planruler.model.RevisionTransform
import com.planruler.model.ScreenPoint
import com.planruler.model.ViewportState
import com.planruler.model.ViewportTransform
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Hit(
    val measurement: Measurement,
    val vertexIndex: Int?,
    /** True when the value badge, rather than the measured geometry, was tapped. */
    val label: Boolean = false,
)

/**
 * One gesture state machine for the whole canvas. It is deliberately outside the
 * composable body: pan/zoom, drawing and vertex editing used to fight over the same
 * pointer stream, and a cancelled gesture must never commit a draft.
 */
fun Modifier.canvasGestures(
    key: Any?,
    dragSlopPx: Float,
    longPressMs: Long,
    doubleTapMs: Long,
    onTap: (Offset, Boolean) -> Unit,
    onLongPress: (Offset) -> Unit,
    onDragStart: (Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit,
    onPan: (Offset) -> Unit,
): Modifier = this.pointerInput(key) {
    var lastTapUptime = 0L
    var lastTapPosition = Offset.Zero
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var position = down.position
        var released = false
        var transformStart: PointerEvent? = null
        var slopExceeded = false

        val resolved = withTimeoutOrNull(longPressMs) {
            var done = false
            while (!done) {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } > 1) {
                    transformStart = event
                    done = true
                } else {
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        released = true
                        done = true
                    } else {
                        position = change.position
                        if ((position - down.position).getDistance() > dragSlopPx) {
                            slopExceeded = true
                            done = true
                        }
                    }
                }
            }
            true
        }

        if (resolved == null) {
            onLongPress(position)
            consumeRest()
            return@awaitEachGesture
        }

        if (released) {
            val doubleTap = down.uptimeMillis - lastTapUptime <= doubleTapMs &&
                (down.position - lastTapPosition).getDistance() <= dragSlopPx * 4f
            lastTapUptime = down.uptimeMillis
            lastTapPosition = down.position
            onTap(down.position, doubleTap)
            return@awaitEachGesture
        }

        transformStart?.let { firstEvent ->
            transformLoop(firstEvent, onTransform)
            return@awaitEachGesture
        }

        if (!slopExceeded) return@awaitEachGesture

        val dragging = onDragStart(down.position)
        if (dragging) onDrag(position) else onPan(position - down.position)
        var switchedToTransform: PointerEvent? = null
        var finished = false
        while (!finished) {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } > 1) {
                if (dragging) onDragCancel()
                switchedToTransform = event
                finished = true
            } else {
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    if (dragging) onDragEnd()
                    finished = true
                } else {
                    if (dragging) onDrag(change.position) else onPan(change.positionChange())
                    change.consume()
                }
            }
        }
        switchedToTransform?.let { firstEvent -> transformLoop(firstEvent, onTransform) }
    }
}

private suspend fun AwaitPointerEventScope.transformLoop(
    firstEvent: PointerEvent,
    onTransform: (Offset, Offset, Float) -> Unit,
) {
    var event = firstEvent
    while (true) {
        if (event.changes.none { it.pressed }) {
            return
        } else {
            val zoom = event.calculateZoom()
            val pan = event.calculatePan()
            if (zoom != 1f || pan != Offset.Zero) {
                onTransform(event.calculateCentroid(useCurrent = true), pan, zoom)
            }
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
        event = awaitPointerEvent()
    }
}

private suspend fun AwaitPointerEventScope.consumeRest() {
    var finished = false
    while (!finished) {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
        if (event.changes.none { it.pressed }) finished = true
    }
}

@Composable
fun PlanCanvas(
    page: RenderedPage,
    logicalPageIndex: Int,
    engine: EngineState,
    viewport: ViewportState,
    tool: WorkspaceTool,
    mode: WorkspaceMode,
    selectedId: MeasurementId?,
    selectedVertex: Int?,
    snapResult: SnapResult?,
    calibrationPoints: List<DocPoint>,
    magnifierFocus: DocPoint?,
    magnifierPlacement: MagnifierPlacement,
    labelScale: Float,
    canvasDescription: String,
    valueOf: (Measurement) -> String,
    segmentsOf: (Measurement) -> List<String>,
    hiddenLayers: Set<LayerId>,
    lockedLayers: Set<LayerId>,
    tiles: List<RenderedTile>,
    visibleMeasurementIds: Set<MeasurementId>,
    revisionOverlay: RenderedPage?,
    revisionTransform: RevisionTransform?,
    revisionOverlayOpacity: Float,
    onSize: (IntSize) -> Unit,
    onTap: (DocPoint, Hit?, Boolean) -> Unit,
    onLongPress: (DocPoint, Hit?) -> Unit,
    onDragStart: (DocPoint, Hit?) -> Boolean,
    onDrag: (DocPoint) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onViewport: (ViewportState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCanvasColors.current
    val dimens = LocalPlanRulerDimens.current
    val density = LocalDensity.current
    val image = remember(page) {
        Bitmap.createBitmap(page.pixelWidth, page.pixelHeight, Bitmap.Config.ARGB_8888).apply {
            setPixels(page.argb, 0, page.pixelWidth, 0, 0, page.pixelWidth, page.pixelHeight)
        }.asImageBitmap()
    }
    val overlayBitmap = remember(revisionOverlay) {
        revisionOverlay?.let { overlay ->
            Bitmap.createBitmap(overlay.pixelWidth, overlay.pixelHeight, Bitmap.Config.ARGB_8888).apply {
                setPixels(overlay.argb, 0, overlay.pixelWidth, 0, 0, overlay.pixelWidth, overlay.pixelHeight)
            }
        }
    }
    // Keyed on the tile list so the bitmaps are built once per delivery, not once per frame.
    val tileImages = remember(tiles) {
        tiles.mapNotNull { tile ->
            if (tile.pixelWidth <= 0 || tile.pixelHeight <= 0) {
                null
            } else {
                tile to Bitmap.createBitmap(tile.pixelWidth, tile.pixelHeight, Bitmap.Config.ARGB_8888)
                    .apply { setPixels(tile.argb, 0, tile.pixelWidth, 0, 0, tile.pixelWidth, tile.pixelHeight) }
                    .asImageBitmap()
            }
        }
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Updated immediately inside a gesture and synchronized after toolbar/external
    // viewport actions. pointerInput can now remain alive for the whole pinch.
    val gestureViewport = remember(page.documentId, page.pageIndex) { mutableStateOf(viewport) }
    SideEffect { gestureViewport.value = viewport }
    val pageMeasurements = engine.measurements.filter {
        it.pageIndex == logicalPageIndex && it.id in visibleMeasurementIds && it.layerId !in hiddenLayers
    }
    // A locked layer stays visible but must not answer hit tests, or a stray tap edits it.
    val selectable = pageMeasurements.filter { it.layerId !in lockedLayers }
    // The gesture block below deliberately keeps the measurement list out of its pointerInput
    // key, so that drawing or previewing never tears down an in-flight drag. That makes the
    // list captured at block start permanently stale: a ruler drawn afterwards was invisible
    // to the hit test, so grabbing its endpoint started a brand-new ruler instead of
    // stretching it. Reading through this holder gives the gesture the current list.
    val hitTestTargets by rememberUpdatedState(selectable)
    fun liveTransform() = ViewportTransform(
        size.width.toDouble(),
        size.height.toDouble(),
        gestureViewport.value,
    )

    fun toDocument(offset: Offset) = liveTransform().screenToDocument(
        ScreenPoint(offset.x.toDouble(), offset.y.toDouble()),
    )

    fun hitAt(offset: Offset): Hit? {
        val liveZoom = gestureViewport.value.zoom.coerceAtLeast(0.05)
        val point = toDocument(offset)
        val geometryHit = hitTest(
            hitTestTargets,
            point,
            with(density) { dimens.vertexHitRadius.toPx() } / liveZoom,
            with(density) { dimens.segmentHitTolerance.toPx() } / liveZoom,
        )
        return geometryHit ?: labelHitTest(
            measurements = hitTestTargets,
            point = point,
            tolerance = with(density) { 34.dp.toPx() } / liveZoom,
        )
    }

    Box(modifier.fillMaxSize().background(colors.backdrop)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag(PlanRulerTestTags.WorkspaceCanvas)
                .semantics { contentDescription = canvasDescription }
                .onSizeChanged { size = it; onSize(it) }
                .canvasGestures(
                    // Viewport, size and selection deliberately stay out of this key.
                    // Selecting a ruler at drag start must not cancel the same pointer
                    // stream before its endpoint can be resized.
                    key = listOf(page.documentId, page.pageIndex, tool, mode),
                    dragSlopPx = with(density) { dimens.dragSlop.toPx() },
                    longPressMs = 480L,
                    doubleTapMs = dimens.doubleTapWindowMs,
                    onTap = { offset, double -> onTap(toDocument(offset), hitAt(offset), double) },
                    onLongPress = { offset -> onLongPress(toDocument(offset), hitAt(offset)) },
                    onDragStart = { offset -> onDragStart(toDocument(offset), hitAt(offset)) },
                    onDrag = { offset -> onDrag(toDocument(offset)) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    onTransform = { centroid, pan, zoom ->
                        if (size.width > 0) {
                            val current = liveTransform()
                            val zoomed = current.zoomAt(
                                zoom.toDouble(),
                                ScreenPoint(centroid.x.toDouble(), centroid.y.toDouble()),
                            )
                            val next =
                                ViewportTransform(size.width.toDouble(), size.height.toDouble(), zoomed)
                                    .panBy(pan.x.toDouble(), pan.y.toDouble())
                            gestureViewport.value = next
                            onViewport(next)
                        }
                    },
                    onPan = { pan ->
                        if (size.width > 0) {
                            val next = liveTransform().panBy(pan.x.toDouble(), pan.y.toDouble())
                            gestureViewport.value = next
                            onViewport(next)
                        }
                    },
                ),
        ) {
            val drawTransform = ViewportTransform(size.width.toDouble(), size.height.toDouble(), viewport)
            drawPlan(
                image = image,
                page = page,
                logicalPageIndex = logicalPageIndex,
                engine = engine,
                transform = drawTransform,
                colors = colors,
                selectedId = selectedId,
                selectedVertex = selectedVertex,
                handleRadiusPx = with(density) { dimens.vertexHandleRadius.toPx() },
                labelTextPx = with(density) { 13.sp.toPx() } * labelScale,
                showLabels = true,
                valueOf = valueOf,
                segmentsOf = segmentsOf,
                hiddenLayers = hiddenLayers,
                tiles = tileImages,
                visibleMeasurementIds = visibleMeasurementIds,
                revisionOverlay = revisionOverlay,
                revisionOverlayBitmap = overlayBitmap,
                revisionTransform = revisionTransform,
                revisionOverlayOpacity = revisionOverlayOpacity,
                editable = mode == WorkspaceMode.EDIT,
            )
            calibrationPoints.forEach { point ->
                val screen = drawTransform.documentToScreen(point).offset()
                drawCircle(colors.snapAccent, with(density) { 8.dp.toPx() }, screen, style = Stroke(3f))
            }
            if (calibrationPoints.size == 2) {
                drawLine(
                    colors.snapAccent,
                    drawTransform.documentToScreen(calibrationPoints[0]).offset(),
                    drawTransform.documentToScreen(calibrationPoints[1]).offset(),
                    strokeWidth = 3f,
                )
            }
            snapResult?.let { snap ->
                val point = drawTransform.documentToScreen(snap.point).offset()
                drawCircle(colors.snapAccent, with(density) { 9.dp.toPx() }, point, style = Stroke(4f))
                val guideStart = snap.guideStart
                val guideEnd = snap.guideEnd
                if (guideStart != null && guideEnd != null) {
                    drawLine(
                        colors.guide,
                        drawTransform.documentToScreen(guideStart).offset(),
                        drawTransform.documentToScreen(guideEnd).offset(),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    )
                }
            }
        }

        if (magnifierFocus != null && size.width > 0) {
            MeasurementMagnifier(
                image = image,
                page = page,
                logicalPageIndex = logicalPageIndex,
                engine = engine,
                focus = magnifierFocus,
                viewport = viewport,
                canvasSize = size,
                colors = colors,
                snapResult = snapResult,
                placement = magnifierPlacement,
                windowSize = dimens.magnifierSize,
                windowOffset = dimens.magnifierOffset,
                density = density,
                hiddenLayers = hiddenLayers,
                visibleMeasurementIds = visibleMeasurementIds,
            )
        }
    }
}

@Composable
private fun MeasurementMagnifier(
    image: ImageBitmap,
    page: RenderedPage,
    logicalPageIndex: Int,
    engine: EngineState,
    focus: DocPoint,
    viewport: ViewportState,
    canvasSize: IntSize,
    colors: PlanRulerCanvasColors,
    snapResult: SnapResult?,
    placement: MagnifierPlacement,
    windowSize: Dp,
    windowOffset: Dp,
    density: Density,
    hiddenLayers: Set<LayerId>,
    visibleMeasurementIds: Set<MeasurementId>,
) {
    val sizePx = with(density) { windowSize.toPx() }
    val offsetPx = with(density) { windowOffset.toPx() }
    val transform = ViewportTransform(canvasSize.width.toDouble(), canvasSize.height.toDouble(), viewport)
    val focusScreen = transform.documentToScreen(focus)
    val left = when (placement) {
        MagnifierPlacement.LEFT -> focusScreen.x - sizePx - offsetPx / 2
        MagnifierPlacement.RIGHT -> focusScreen.x + offsetPx / 2
        MagnifierPlacement.TOP -> focusScreen.x - sizePx / 2
        MagnifierPlacement.AUTO -> if (focusScreen.x > canvasSize.width / 2.0) {
            focusScreen.x - sizePx - offsetPx / 2
        } else {
            focusScreen.x + offsetPx / 2
        }
    }.coerceIn(0.0, (canvasSize.width - sizePx).toDouble().coerceAtLeast(0.0))
    val top = (focusScreen.y - sizePx - offsetPx / 2)
        .coerceIn(0.0, (canvasSize.height - sizePx).toDouble().coerceAtLeast(0.0))

    Canvas(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(windowSize)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.backdrop),
    ) {
        val magnified = ViewportState(
            zoom = viewport.zoom * MAGNIFICATION,
            centerX = focus.x,
            centerY = focus.y,
        )
        val magnifierTransform = ViewportTransform(size.width.toDouble(), size.height.toDouble(), magnified)
        drawPlan(
            image = image,
            page = page,
            logicalPageIndex = logicalPageIndex,
            engine = engine,
            transform = magnifierTransform,
            colors = colors,
            selectedId = null,
            selectedVertex = null,
            handleRadiusPx = 5f,
            labelTextPx = 0f,
            showLabels = false,
            valueOf = { "" },
            segmentsOf = { emptyList() },
            hiddenLayers = hiddenLayers,
            tiles = emptyList(),
            visibleMeasurementIds = visibleMeasurementIds,
            revisionOverlay = null,
            revisionOverlayBitmap = null,
            revisionTransform = null,
            revisionOverlayOpacity = 0f,
            editable = false,
        )
        val center = Offset(size.width / 2f, size.height / 2f)
        drawLine(colors.selection, Offset(center.x, 0f), Offset(center.x, size.height), strokeWidth = 1.5f)
        drawLine(colors.selection, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = 1.5f)
        snapResult?.let {
            val point = magnifierTransform.documentToScreen(it.point).offset()
            drawCircle(colors.snapAccent, 11f, point, style = Stroke(3f))
        }
        drawRect(colors.selection, style = Stroke(3f))
    }
}

private const val MAGNIFICATION = 2.5

private fun DrawScope.drawAlignedRevision(
    bitmap: Bitmap,
    page: RenderedPage,
    alignment: RevisionTransform,
    transform: ViewportTransform,
    opacity: Float,
) {
    if (bitmap.width <= 0 || bitmap.height <= 0) return
    val origin = transform.documentToScreen(alignment.map(DocPoint(0.0, 0.0)))
    val horizontal = transform.documentToScreen(alignment.map(DocPoint(page.source.width, 0.0)))
    val vertical = transform.documentToScreen(alignment.map(DocPoint(0.0, page.source.height)))
    val matrix = Matrix().apply {
        setValues(
            floatArrayOf(
                ((horizontal.x - origin.x) / bitmap.width).toFloat(),
                ((vertical.x - origin.x) / bitmap.height).toFloat(),
                origin.x.toFloat(),
                ((horizontal.y - origin.y) / bitmap.width).toFloat(),
                ((vertical.y - origin.y) / bitmap.height).toFloat(),
                origin.y.toFloat(),
                0f,
                0f,
                1f,
            ),
        )
    }
    drawContext.canvas.nativeCanvas.drawBitmap(
        bitmap,
        matrix,
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (opacity.coerceIn(0f, 1f) * 255).roundToInt()
        },
    )
}

private fun DrawScope.drawPlan(
    image: ImageBitmap,
    page: RenderedPage,
    logicalPageIndex: Int,
    engine: EngineState,
    transform: ViewportTransform,
    colors: PlanRulerCanvasColors,
    selectedId: MeasurementId?,
    selectedVertex: Int?,
    handleRadiusPx: Float,
    labelTextPx: Float,
    showLabels: Boolean,
    valueOf: (Measurement) -> String,
    segmentsOf: (Measurement) -> List<String>,
    hiddenLayers: Set<LayerId>,
    tiles: List<Pair<RenderedTile, ImageBitmap>>,
    visibleMeasurementIds: Set<MeasurementId>,
    revisionOverlay: RenderedPage?,
    revisionOverlayBitmap: Bitmap?,
    revisionTransform: RevisionTransform?,
    revisionOverlayOpacity: Float,
    editable: Boolean,
) {
    val topLeft = transform.documentToScreen(DocPoint(0.0, 0.0))
    val bottomRight = transform.documentToScreen(DocPoint(page.source.width, page.source.height))
    val width = (bottomRight.x - topLeft.x).roundToInt().coerceAtLeast(1)
    val height = (bottomRight.y - topLeft.y).roundToInt().coerceAtLeast(1)
    if (colors.pageShadowAlpha > 0f) {
        drawRect(
            Color.Black.copy(alpha = colors.pageShadowAlpha),
            topLeft = Offset(topLeft.x.toFloat() + 6f, topLeft.y.toFloat() + 8f),
            size = Size(width.toFloat(), height.toFloat()),
        )
    }
    drawImage(
        image,
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(width, height),
    )
    // Sharp regions land on top of the capped page render, which stays as the backdrop.
    tiles.forEach { (tile, bitmap) ->
        val tileTopLeft = transform.documentToScreen(DocPoint(tile.left, tile.top))
        val tileBottomRight = transform.documentToScreen(DocPoint(tile.right, tile.bottom))
        val tileWidth = (tileBottomRight.x - tileTopLeft.x).roundToInt()
        val tileHeight = (tileBottomRight.y - tileTopLeft.y).roundToInt()
        if (tileWidth > 0 && tileHeight > 0) {
            drawImage(
                bitmap,
                dstOffset = IntOffset(tileTopLeft.x.roundToInt(), tileTopLeft.y.roundToInt()),
                dstSize = IntSize(tileWidth, tileHeight),
            )
        }
    }
    if (
        revisionOverlay != null && revisionOverlayBitmap != null && revisionTransform != null &&
        revisionOverlayOpacity > 0f
    ) {
        drawAlignedRevision(
            bitmap = revisionOverlayBitmap,
            page = revisionOverlay,
            alignment = revisionTransform,
            transform = transform,
            opacity = revisionOverlayOpacity,
        )
    }
    drawRect(
        colors.pageBorder,
        topLeft = Offset(topLeft.x.toFloat(), topLeft.y.toFloat()),
        size = Size(width.toFloat(), height.toFloat()),
        style = Stroke(colors.pageBorderWidth),
    )
    engine.measurements
        .filter {
            it.pageIndex == logicalPageIndex && it.id in visibleMeasurementIds && it.layerId !in hiddenLayers
        }
        .forEach { measurement ->
            drawMeasurement(
                measurement = measurement,
                transform = transform,
                colors = colors,
                page = page,
                label = if (showLabels) valueOf(measurement) else "",
                segmentLabels = if (showLabels) segmentsOf(measurement) else emptyList(),
                committed = true,
                selected = measurement.id == selectedId,
                selectedVertex = if (measurement.id == selectedId) selectedVertex else null,
                handleRadiusPx = handleRadiusPx,
                labelTextPx = labelTextPx,
                showHandles = shouldShowHandles(editable, measurement.id, selectedId),
            )
        }
    engine.draft?.takeIf { it.pageIndex == logicalPageIndex }?.let {
        drawMeasurement(
            measurement = it,
            transform = transform,
            colors = colors,
            page = page,
            label = "",
            segmentLabels = emptyList(),
            committed = false,
            selected = false,
            selectedVertex = null,
            handleRadiusPx = handleRadiusPx,
            labelTextPx = labelTextPx,
            showHandles = true,
        )
    }
}

private fun DrawScope.drawMeasurement(
    measurement: Measurement,
    transform: ViewportTransform,
    colors: PlanRulerCanvasColors,
    page: RenderedPage,
    label: String,
    segmentLabels: List<String>,
    committed: Boolean,
    selected: Boolean,
    selectedVertex: Int?,
    handleRadiusPx: Float,
    labelTextPx: Float,
    showHandles: Boolean,
) {
    val points = measurement.points.map { transform.documentToScreen(it).offset() }
    if (points.isEmpty()) return
    val baseColor = if (committed) Color(measurement.style.colorArgb) else colors.draft
    val color = if (selected) colors.selection else baseColor
    val stroke = max(3f, measurement.style.strokeWidth * 2f) * if (selected) 1.6f else 1f
    val effect = if (committed) null else PathEffect.dashPathEffect(floatArrayOf(16f, 10f))
    points.zipWithNext().forEach { (a, b) ->
        drawLine(color, a, b, strokeWidth = stroke, pathEffect = effect)
    }
    if (measurement.type == MeasurementType.AREA && points.size > 2) {
        drawLine(color, points.last(), points.first(), strokeWidth = stroke, pathEffect = effect)
    }
    if (measurement.type == MeasurementType.COUNTER) {
        drawCircle(color, handleRadiusPx * 2f, points.first(), style = Stroke(stroke))
    }
    if (showHandles || !committed) {
        points.forEachIndexed { index, point ->
            val radius = if (selectedVertex == index) handleRadiusPx * 1.4f else handleRadiusPx
            drawCircle(colors.handleFill, radius, point)
            drawCircle(color, radius, point, style = Stroke(max(2f, stroke * 0.6f)))
        }
    }
    // Segment lengths sit on their own segment, so a polyline reads as a chain of runs.
    if (segmentLabels.isNotEmpty() && labelTextPx > 0f) {
        points.zipWithNext().forEachIndexed { index, (a, b) ->
            val value = segmentLabels.getOrNull(index) ?: return@forEachIndexed
            val documentMiddle = DocPoint(
                (measurement.points[index].x + measurement.points[index + 1].x) / 2.0,
                (measurement.points[index].y + measurement.points[index + 1].y) / 2.0,
            )
            drawLabel(
                value,
                Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f),
                documentMiddle,
                page,
                colors,
                labelTextPx * 0.85f,
            )
        }
    }
    if (measurement.showLabel && labelTextPx > 0f && label.isNotEmpty()) {
        val text = measurement.label?.takeIf { it.isNotBlank() }?.let { "$it · $label" } ?: label
        drawLabel(text, points.last(), measurement.points.last(), page, colors, labelTextPx)
    } else if (measurement.type == MeasurementType.ANNOTATION && labelTextPx > 0f) {
        measurement.label?.takeIf { it.isNotBlank() }?.let {
            drawLabel(it.lineSequence().first().take(48), points.first(), measurement.points.first(), page, colors, labelTextPx)
        }
    }
}

/**
 * A label must stay legible over any drawing, so the backdrop is chosen from the
 * luminance of the rendered page underneath it rather than from the theme alone.
 */
private fun DrawScope.drawLabel(
    text: String,
    anchor: Offset,
    documentAnchor: DocPoint,
    page: RenderedPage,
    colors: PlanRulerCanvasColors,
    textPx: Float,
) {
    val light = page.luminanceAt(documentAnchor) > 0.55f
    val backdrop = if (light) colors.labelBackdropLight else colors.labelBackdropDark
    val textColor = if (light) colors.labelTextLight else colors.labelTextDark
    val paint = Paint().apply {
        textSize = textPx
        isAntiAlias = true
        color = textColor.toArgb()
    }
    val width = paint.measureText(text)
    val metrics = paint.fontMetrics
    val padding = textPx * 0.35f
    val left = anchor.x + 14f
    val baseline = anchor.y - 12f
    drawRoundRect(
        backdrop,
        topLeft = Offset(left - padding, baseline + metrics.top - padding),
        size = Size(width + padding * 2, (metrics.bottom - metrics.top) + padding * 2),
        cornerRadius = CornerRadius(6f, 6f),
    )
    drawContext.canvas.nativeCanvas.drawText(text, left, baseline, paint)
}

private fun RenderedPage.luminanceAt(point: DocPoint): Float {
    if (pixelWidth <= 0 || pixelHeight <= 0 || argb.isEmpty()) return 1f
    val px = ((point.x / source.width) * pixelWidth).roundToInt().coerceIn(0, pixelWidth - 1)
    val py = ((point.y / source.height) * pixelHeight).roundToInt().coerceIn(0, pixelHeight - 1)
    var sum = 0f
    var samples = 0
    for (dy in -6..6 step 3) {
        for (dx in -6..6 step 3) {
            val x = (px + dx).coerceIn(0, pixelWidth - 1)
            val y = (py + dy).coerceIn(0, pixelHeight - 1)
            val pixel = argb[y * pixelWidth + x]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            sum += (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            samples++
        }
    }
    return if (samples == 0) 1f else sum / samples
}

private fun ScreenPoint.offset() = Offset(x.toFloat(), y.toFloat())

internal fun shouldShowHandles(
    editable: Boolean,
    measurementId: MeasurementId,
    selectedId: MeasurementId?,
): Boolean = editable && measurementId == selectedId

/**
 * Labels are drawn beside their final point. A generous field-sized target makes the
 * exact-value action usable with gloves without changing vertex hit precision.
 */
fun labelHitTest(
    measurements: List<Measurement>,
    point: DocPoint,
    tolerance: Double,
): Hit? = measurements.asReversed().firstNotNullOfOrNull { measurement ->
    measurement
        .takeIf { it.type == MeasurementType.DISTANCE && it.showLabel }
        ?.points
        ?.lastOrNull()
        ?.takeIf { anchor ->
            val dx = point.x - anchor.x
            val dy = point.y - anchor.y
            dx in (-tolerance * 0.25)..(tolerance * 4.5) &&
                dy in (-tolerance * 1.5)..(tolerance * 0.5)
        }
        ?.let { Hit(measurement, null, label = true) }
}

fun hitTest(
    measurements: List<Measurement>,
    point: DocPoint,
    vertexTolerance: Double,
    segmentTolerance: Double,
): Hit? {
    measurements.asReversed().forEach { measurement ->
        measurement.points.forEachIndexed { index, vertex ->
            if (vertex.distanceTo(point) <= vertexTolerance) return Hit(measurement, index)
        }
    }
    measurements.asReversed().forEach { measurement ->
        if (measurement.type in setOf(MeasurementType.ANNOTATION, MeasurementType.COUNTER)) {
            if (measurement.points.firstOrNull()?.distanceTo(point)?.let { it <= vertexTolerance } == true) {
                return Hit(measurement, null)
            }
        }
        val segments = measurement.points.zipWithNext().toMutableList()
        if (measurement.type == MeasurementType.AREA && measurement.points.size > 2) {
            segments += measurement.points.last() to measurement.points.first()
        }
        if (segments.any { distanceToSegment(point, it.first, it.second) <= segmentTolerance }) {
            return Hit(measurement, null)
        }
    }
    return null
}

fun distanceToSegment(point: DocPoint, start: DocPoint, end: DocPoint): Double {
    val dx = end.x - start.x
    val dy = end.y - start.y
    if (dx == 0.0 && dy == 0.0) return point.distanceTo(start)
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
    val projection = DocPoint(start.x + t * dx, start.y + t * dy)
    return sqrt(
        (point.x - projection.x) * (point.x - projection.x) +
            (point.y - projection.y) * (point.y - projection.y),
    )
}
