package com.planruler.feature.pipecalculator

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planruler.fabrication3d.AssemblyMesh3D
import com.planruler.fabrication3d.ChainCommand3D
import com.planruler.fabrication3d.ChainEditorState3D
import com.planruler.fabrication3d.ElbowGeometry3D
import com.planruler.fabrication3d.FabricationPartKind
import com.planruler.fabrication3d.MeshMaterial3D
import com.planruler.fabrication3d.MeshTriangle3D
import com.planruler.fabrication3d.ParametricAssembly3D
import com.planruler.fabrication3d.PartInstance3D
import com.planruler.fabrication3d.Quaternion
import com.planruler.fabrication3d.StraightPipeGeometry3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.WeldNeckFlangeGeometry3D
import com.planruler.designsystem.theme.LocalScenePalette
import com.planruler.designsystem.theme.PlanRulerScenePalette
import com.planruler.model.AppLanguage
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.roundToInt

internal enum class ViewPreset3D(val yaw: Float, val pitch: Float) {
    ISOMETRIC(-32f, 24f),
    FRONT(0f, 0f),
    TOP(0f, 88f),
    RIGHT(88f, 0f),
}

@Composable
internal fun Assembly3DViewerCard(
    assembly: ParametricAssembly3D,
    language: AppLanguage,
    /** Null while the engine is still tessellating, or when the scene exceeds its quota. */
    mesh: AssemblyMesh3D?,
    dimensionTarget: Vec3?,
    selectedPartId: String? = null,
    onSelectPart: (String) -> Unit = {},
    editor: ChainEditorState3D? = null,
    canAddAtOpenEnd: Boolean = false,
    onPreview: (ChainCommand3D) -> Unit = {},
    onCommitPreview: () -> Unit = {},
    onCancelPreview: () -> Unit = {},
    onSceneAdd: () -> Unit = {},
    onSceneRemove: () -> Unit = {},
) {
    val text = remember(language) { Model3DText(language) }
    val palette = LocalScenePalette.current
    var yaw by rememberSaveable { mutableFloatStateOf(ViewPreset3D.ISOMETRIC.yaw) }
    var pitch by rememberSaveable { mutableFloatStateOf(ViewPreset3D.ISOMETRIC.pitch) }
    var zoom by rememberSaveable { mutableFloatStateOf(1.15f) }
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }
    var perspective by rememberSaveable { mutableStateOf(true) }
    var localSelection by remember(assembly) {
        mutableStateOf(assembly.parts.firstOrNull { it.id == "P2" }?.id ?: assembly.parts.firstOrNull()?.id)
    }
    val activeSelection = selectedPartId ?: localSelection
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var liveDirectValue by remember(activeSelection) { mutableStateOf<String?>(null) }
    var controlHint by remember { mutableStateOf<String?>(null) }
    val selectedPart = assembly.parts.firstOrNull { it.id == activeSelection }

    if (mesh == null) {
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Text(
                text.meshUnavailable,
                Modifier.fillMaxWidth().padding(16.dp).testTag(PipeCalculatorTags.Assembly3DCanvas),
                fontWeight = FontWeight.Bold,
            )
        }
        return
    }

    val projector = remember(mesh, viewportSize, yaw, pitch, zoom, perspective, panX, panY) {
        viewportSize.takeIf { it.width > 0 && it.height > 0 }?.let {
            SceneProjector3D(
                mesh = mesh,
                width = it.width.toFloat(),
                height = it.height.toFloat(),
                yawDeg = yaw,
                pitchDeg = pitch,
                zoom = zoom,
                perspective = perspective,
                panX = panX,
                panY = panY,
            )
        }
    }
    val directHandles = remember(editor, activeSelection) {
        editor?.let { directHandleSpecs(it, activeSelection) }.orEmpty()
    }
    val directValue = liveDirectValue ?: directHandles.joinToString(" · ") { it.valueLabel }
    val canRemoveFromScene = editor?.pathForPart(activeSelection.orEmpty()) != null

    ElevatedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    text.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(ViewPreset3D.entries) { preset ->
                    FilterChip(
                        selected = abs(yaw - preset.yaw) < 0.1f && abs(pitch - preset.pitch) < 0.1f,
                        onClick = {
                            yaw = preset.yaw
                            pitch = preset.pitch
                            zoom = 1.15f
                            panX = 0f
                            panY = 0f
                        },
                        label = { Text(text.preset(preset)) },
                    )
                }
                item {
                    FilterChip(
                        selected = perspective,
                        onClick = { perspective = !perspective },
                        label = { Text(if (perspective) text.perspective else text.orthographic) },
                    )
                }
            }
            val sceneDescription = "${text.title}. ${assembly.parts.size} ${text.parts}, " +
                "${assembly.connections.size} ${text.welds}. ${text.gestureHint}"
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .background(palette.backdropTop, RoundedCornerShape(20.dp))
                    .onSizeChanged { viewportSize = it },
            ) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .testTag(PipeCalculatorTags.Assembly3DCanvas)
                        .semantics { contentDescription = sceneDescription }
                        .pointerInput(mesh) {
                            // A handle is a child hit target and wins. Everywhere else one finger orbits.
                            detectTransformGestures(panZoomLock = false) { _, pan, gestureZoom, gestureRotation ->
                                if (gestureZoom != 1f || abs(gestureRotation) > 0.01f) {
                                    zoom = (zoom * gestureZoom).coerceIn(0.35f, 8.0f)
                                    panX += pan.x
                                    panY += pan.y
                                } else {
                                    yaw = normalizeDegrees(yaw + pan.x * 0.28f)
                                    pitch = (pitch - pan.y * 0.22f).coerceIn(-88f, 88f)
                                }
                                controlHint = null
                            }
                        }
                        .pointerInput(mesh, projector) {
                            detectTapGestures { tap ->
                                projector?.let { sceneProjector ->
                                    pickPart(mesh, sceneProjector, tap)?.let {
                                        localSelection = it
                                        onSelectPart(it)
                                    }
                                }
                            }
                        },
                ) {
                    val sceneProjector = SceneProjector3D(
                        mesh,
                        size.width,
                        size.height,
                        yaw,
                        pitch,
                        zoom,
                        perspective,
                        panX,
                        panY,
                    )
                    drawAssemblyScene3D(
                        mesh = mesh,
                        projector = sceneProjector,
                        selectedPartId = activeSelection,
                        dimensionTarget = dimensionTarget,
                        overallLabel = text.overall,
                        heightLabel = text.offset,
                        palette = palette,
                    )
                }

                if (editor != null) {
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Button(
                            onClick = onSceneAdd,
                            enabled = canAddAtOpenEnd,
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp),
                            modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DSceneAdd),
                        ) { Text("+ ${text.pipe}") }
                        Button(
                            onClick = onSceneRemove,
                            enabled = canRemoveFromScene,
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp),
                            modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DSceneRemove),
                        ) { Text("×") }
                    }
                }

                projector?.let { sceneProjector ->
                    directHandles.forEach { spec ->
                        val projected = sceneProjector.project(spec.anchorWorld)
                        if (projected.visible) {
                            DirectHandle3D(
                                spec = spec,
                                screen = projected.screen,
                                projector = sceneProjector,
                                palette = palette,
                                onPreview = onPreview,
                                onCommit = onCommitPreview,
                                onCancel = onCancelPreview,
                                onLiveValue = { liveDirectValue = it },
                                onUncontrollable = { blocked ->
                                    val preset = bestControllablePreset(
                                        mesh = mesh,
                                        width = viewportSize.width.toFloat(),
                                        height = viewportSize.height.toFloat(),
                                        zoom = zoom,
                                        perspective = perspective,
                                        origin = blocked.anchorWorld,
                                        axis = blocked.dragAxisWorld,
                                    )
                                    yaw = preset.yaw
                                    pitch = preset.pitch
                                    panX = 0f
                                    panY = 0f
                                    controlHint = text.viewAdjusted
                                },
                            )
                        }
                    }
                }

                if (directValue.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.backdropBottom.copy(alpha = 0.92f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                    ) {
                        Text(
                            directValue,
                            modifier = Modifier
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                                .testTag(PipeCalculatorTags.Assembly3DDirectValue),
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = palette.onScene,
                        )
                    }
                }
                controlHint?.let { hint ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                    ) {
                        Text(
                            hint,
                            Modifier
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                                .testTag(PipeCalculatorTags.Assembly3DControlHint),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            selectedPart?.let { part ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = palette.selection.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.Assembly3DSelection),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("${text.selected}: ${part.code}", fontWeight = FontWeight.Black)
                            Text(text.partKind(part.definition.kind), style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            partDimensions(part),
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = palette.selection,
                        )
                    }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SceneLegend(palette.pipe, text.pipe) }
                item { SceneLegend(palette.elbow, text.elbows) }
                item { SceneLegend(palette.flange, text.flanges) }
                item { SceneLegend(palette.weld, text.welds) }
            }
            Text(
                "${assembly.parts.size} ${text.parts} · ${assembly.connections.size} ${text.welds} · " +
                    "${assembly.freePorts().size} ${text.freePorts}",
                modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DSummary),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DirectHandle3D(
    spec: DirectHandleSpec3D,
    screen: Offset,
    projector: SceneProjector3D,
    palette: PlanRulerScenePalette,
    onPreview: (ChainCommand3D) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onLiveValue: (String?) -> Unit,
    onUncontrollable: (DirectHandleSpec3D) -> Unit,
) {
    val handleSize = 52.dp
    val radiusPx = with(LocalDensity.current) { handleSize.toPx() / 2f }
    val currentSpec by rememberUpdatedState(spec)
    val currentProjector by rememberUpdatedState(projector)
    val currentPreview by rememberUpdatedState(onPreview)
    val currentCommit by rememberUpdatedState(onCommit)
    val currentCancel by rememberUpdatedState(onCancel)
    val currentLiveValue by rememberUpdatedState(onLiveValue)
    val currentUncontrollable by rememberUpdatedState(onUncontrollable)

    Canvas(
        Modifier
            .offset {
                IntOffset(
                    (screen.x - radiusPx).roundToInt(),
                    (screen.y - radiusPx).roundToInt(),
                )
            }
            .size(handleSize)
            .testTag(
                when (spec.kind) {
                    DirectHandleKind3D.LENGTH -> PipeCalculatorTags.Assembly3DLengthHandle
                    DirectHandleKind3D.ANGLE -> PipeCalculatorTags.Assembly3DAngleHandle
                    DirectHandleKind3D.ROLL -> PipeCalculatorTags.Assembly3DRollHandle
                    DirectHandleKind3D.START_X -> PipeCalculatorTags.Assembly3DStartXHandle
                    DirectHandleKind3D.START_Y -> PipeCalculatorTags.Assembly3DStartYHandle
                    DirectHandleKind3D.START_Z -> PipeCalculatorTags.Assembly3DStartZHandle
                },
            )
            .semantics { contentDescription = spec.valueLabel }
            .pointerInput(spec.path, spec.kind) {
                var baseline = currentSpec
                var baselineProjector = currentProjector
                var totalDrag = Offset.Zero
                var previewed = false
                var controllable = true
                detectDragGestures(
                    onDragStart = {
                        baseline = currentSpec
                        baselineProjector = currentProjector
                        totalDrag = Offset.Zero
                        previewed = false
                        val scale = DragProjection3D.axisPixelsPerMm(
                            baselineProjector,
                            baseline.anchorWorld,
                            baseline.dragAxisWorld,
                        ) ?: 0f
                        controllable = scale >= DragProjection3D.MIN_PIXELS_PER_MM
                        if (controllable) currentLiveValue(baseline.valueLabel)
                        else currentUncontrollable(baseline)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (controllable) {
                            totalDrag += dragAmount
                            directEditForDrag(baseline, baselineProjector, totalDrag)?.let { edit ->
                                currentPreview(edit.command)
                                currentLiveValue(edit.valueLabel)
                                previewed = true
                            }
                        }
                    },
                    onDragEnd = {
                        if (previewed) currentCommit()
                        currentLiveValue(null)
                    },
                    onDragCancel = {
                        if (previewed) currentCancel()
                        currentLiveValue(null)
                    },
                )
            },
    ) {
        val center = this.center
        val color = when (spec.kind) {
            DirectHandleKind3D.LENGTH -> palette.selection
            DirectHandleKind3D.ANGLE -> palette.elbow
            DirectHandleKind3D.ROLL -> palette.weld
            DirectHandleKind3D.START_X -> Color(0xFFE53935)
            DirectHandleKind3D.START_Y -> Color(0xFF43A047)
            DirectHandleKind3D.START_Z -> Color(0xFF1E88E5)
        }
        drawCircle(palette.backdropTop.copy(alpha = 0.94f), radius = size.minDimension * 0.45f)
        drawCircle(color, radius = size.minDimension * 0.36f, style = Stroke(width = 4.5f))
        when (spec.kind) {
            DirectHandleKind3D.LENGTH -> {
                drawLine(color, Offset(center.x - 12f, center.y), Offset(center.x + 12f, center.y), 4f, StrokeCap.Round)
                drawLine(color, Offset(center.x - 12f, center.y), Offset(center.x - 5f, center.y - 7f), 4f, StrokeCap.Round)
                drawLine(color, Offset(center.x - 12f, center.y), Offset(center.x - 5f, center.y + 7f), 4f, StrokeCap.Round)
                drawLine(color, Offset(center.x + 12f, center.y), Offset(center.x + 5f, center.y - 7f), 4f, StrokeCap.Round)
                drawLine(color, Offset(center.x + 12f, center.y), Offset(center.x + 5f, center.y + 7f), 4f, StrokeCap.Round)
            }
            DirectHandleKind3D.ANGLE -> {
                drawArc(color, startAngle = 200f, sweepAngle = 115f, useCenter = false, style = Stroke(4f, cap = StrokeCap.Round))
                drawCircle(color, radius = 3.5f, center = Offset(center.x - 11f, center.y + 5f))
            }
            DirectHandleKind3D.ROLL -> {
                drawArc(color, startAngle = 25f, sweepAngle = 285f, useCenter = false, style = Stroke(4f, cap = StrokeCap.Round))
                drawLine(color, Offset(center.x + 9f, center.y - 10f), Offset(center.x + 15f, center.y - 12f), 4f, StrokeCap.Round)
            }
            DirectHandleKind3D.START_X,
            DirectHandleKind3D.START_Y,
            DirectHandleKind3D.START_Z,
            -> {
                drawLine(color, Offset(center.x - 12f, center.y + 8f), Offset(center.x + 12f, center.y - 8f), 4f, StrokeCap.Round)
                drawLine(color, Offset(center.x + 12f, center.y - 8f), Offset(center.x + 3f, center.y - 9f), 4f, StrokeCap.Round)
                drawLine(color, Offset(center.x + 12f, center.y - 8f), Offset(center.x + 8f, center.y + 1f), 4f, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun SceneLegend(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Spacer(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private data class ProjectedTriangle3D(
    val source: MeshTriangle3D,
    val a: Offset,
    val b: Offset,
    val c: Offset,
    val depth: Double,
    val shade: Float,
)

private fun DrawScope.drawAssemblyScene3D(
    mesh: AssemblyMesh3D,
    projector: SceneProjector3D,
    selectedPartId: String?,
    dimensionTarget: Vec3?,
    overallLabel: String,
    heightLabel: String,
    palette: PlanRulerScenePalette,
) {
    drawRect(palette.backdropTop)
    drawRect(
        color = palette.backdropBottom.copy(alpha = 0.56f),
        topLeft = Offset(0f, size.height * 0.44f),
        size = Size(size.width, size.height * 0.56f),
    )
    drawEngineeringGrid(mesh, projector, palette)

    val light = Vec3(-0.25, -0.35, 1.0).normalized()
    val triangles = mesh.triangles.mapNotNull { triangle ->
        val a = projector.project(triangle.a)
        val b = projector.project(triangle.b)
        val c = projector.project(triangle.c)
        if (!a.visible || !b.visible || !c.visible) return@mapNotNull null
        val normal = projector.rotateDirection(triangle.normal)
        ProjectedTriangle3D(
            source = triangle,
            a = a.screen,
            b = b.screen,
            c = c.screen,
            depth = (a.depth + b.depth + c.depth) / 3.0,
            // The ambient floor comes from the theme: a face that shades to black reads as
            // a hole on a pale backdrop and vanishes on a dark one.
            shade = (palette.shadeFloor + (1f - palette.shadeFloor) * abs(normal.dot(light))).toFloat(),
        )
    }.sortedBy { it.depth }

    triangles.forEach { triangle ->
        val selected = triangle.source.partId == selectedPartId
        val base = if (selected) palette.selection else materialColor(triangle.source.material, palette)
        val path = Path().apply {
            moveTo(triangle.a.x, triangle.a.y)
            lineTo(triangle.b.x, triangle.b.y)
            lineTo(triangle.c.x, triangle.c.y)
            close()
        }
        drawPath(path, base.adjustBrightness(triangle.shade))
        when {
            selected -> drawPath(
                path,
                palette.selection.copy(alpha = 0.28f),
                style = Stroke(palette.outlineWidth),
            )
            // Glare and low-vision themes carry shape in the outline, not in the fill.
            palette.outlineEveryPart -> drawPath(
                path,
                palette.onScene.copy(alpha = 0.22f),
                style = Stroke(palette.outlineWidth),
            )
        }
    }

    mesh.polylines.forEach { polyline ->
        val projected = polyline.points.map(projector::project).filter { it.visible }
        if (projected.size >= 2) {
            val color = materialColor(polyline.material, palette)
            projected.zipWithNext().forEach { (start, end) ->
                drawLine(color, start.screen, end.screen, if (polyline.material == MeshMaterial3D.WELD) 2.6f else 1.8f)
            }
            if (polyline.closed) drawLine(color, projected.last().screen, projected.first().screen, 1.8f)
        }
    }

    dimensionTarget?.let { drawSceneDimensions(projector, mesh, it, overallLabel, heightLabel, palette) }
    drawPartLabels(mesh, projector, selectedPartId, palette)
    drawAxisTriad(projector, palette)
}

private fun DrawScope.drawEngineeringGrid(
    mesh: AssemblyMesh3D,
    projector: SceneProjector3D,
    palette: PlanRulerScenePalette,
) {
    val bounds = mesh.bounds
    val span = max(bounds.size.x, bounds.size.y).coerceAtLeast(100.0)
    val rawStep = span / 8.0
    val step = when {
        rawStep <= 50.0 -> 50.0
        rawStep <= 100.0 -> 100.0
        rawStep <= 250.0 -> 250.0
        rawStep <= 500.0 -> 500.0
        else -> 1_000.0
    }
    val minX = kotlin.math.floor((bounds.minimum.x - step) / step) * step
    val maxX = kotlin.math.ceil((bounds.maximum.x + step) / step) * step
    val minY = kotlin.math.floor((bounds.minimum.y - step) / step) * step
    val maxY = kotlin.math.ceil((bounds.maximum.y + step) / step) * step
    val floorZ = bounds.minimum.z - bounds.size.z * 0.12
    var x = minX
    while (x <= maxX + 1e-6) {
        drawProjectedLine(projector, Vec3(x, minY, floorZ), Vec3(x, maxY, floorZ), palette.grid.copy(alpha = 0.38f), 1f)
        x += step
    }
    var y = minY
    while (y <= maxY + 1e-6) {
        drawProjectedLine(projector, Vec3(minX, y, floorZ), Vec3(maxX, y, floorZ), palette.grid.copy(alpha = 0.38f), 1f)
        y += step
    }
}

private fun DrawScope.drawSceneDimensions(
    projector: SceneProjector3D,
    mesh: AssemblyMesh3D,
    target: Vec3,
    overallLabel: String,
    heightLabel: String,
    palette: PlanRulerScenePalette,
) {
    val margin = (mesh.bounds.radius * 0.14).coerceAtLeast(35.0)
    val overallStart = Vec3(0.0, -margin, 0.0)
    val overallEnd = Vec3(target.x, -margin, 0.0)
    val heightStart = Vec3(-margin, 0.0, 0.0)
    val heightEnd = Vec3(-margin, target.y, 0.0)
    drawDimension3D(projector, overallStart, overallEnd, "$overallLabel X ${sceneNumber(target.x)} mm", palette)
    if (abs(target.y) > 1e-7) {
        drawDimension3D(projector, heightStart, heightEnd, "$heightLabel Y ${sceneNumber(target.y)} mm", palette)
    }
    if (abs(target.z) > 1e-7) {
        val depthStart = Vec3(target.x, target.y, 0.0)
        val depthEnd = Vec3(target.x, target.y, target.z)
        drawDimension3D(projector, depthStart, depthEnd, "Z ${sceneNumber(target.z)} mm", palette)
    }
}

private fun DrawScope.drawDimension3D(
    projector: SceneProjector3D,
    start: Vec3,
    end: Vec3,
    label: String,
    palette: PlanRulerScenePalette,
) {
    val a = projector.project(start)
    val b = projector.project(end)
    if (!a.visible || !b.visible) return
    drawLine(palette.dimension, a.screen, b.screen, 1.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f)))
    val vector = b.screen - a.screen
    val length = sqrt(vector.x * vector.x + vector.y * vector.y).coerceAtLeast(1f)
    val normal = Offset(-vector.y / length, vector.x / length) * 7f
    drawLine(palette.dimension, a.screen - normal, a.screen + normal, 2f)
    drawLine(palette.dimension, b.screen - normal, b.screen + normal, 2f)
    val midpoint = (a.screen + b.screen) * 0.5f
    val paint = scenePaint(palette.dimension, 10.sp.toPx(), bold = true)
    drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(label, midpoint.x, midpoint.y - 9f, paint) }
}

private fun DrawScope.drawPartLabels(
    mesh: AssemblyMesh3D,
    projector: SceneProjector3D,
    selectedPartId: String?,
    palette: PlanRulerScenePalette,
) {
    val paint = scenePaint(palette.onScene, 10.sp.toPx(), bold = true)
    mesh.labels.forEach { label ->
        val point = projector.project(label.position)
        if (point.visible) {
            val selected = label.partId == selectedPartId
            val radius = if (selected) 18f else 15f
            drawCircle(
                color = if (selected) palette.selection else palette.labelBackdrop,
                radius = radius,
                center = point.screen,
            )
            drawCircle(
                color = if (selected) palette.onScene else materialColorForPart(label.partId, palette),
                radius = radius,
                center = point.screen,
                style = Stroke(if (selected) 2.5f else 1.5f),
            )
            drawIntoCanvas { canvas ->
                paint.color = palette.onScene.toArgb()
                canvas.nativeCanvas.drawText(label.text, point.screen.x, point.screen.y + 4f, paint)
            }
        }
    }
}

private fun DrawScope.drawAxisTriad(projector: SceneProjector3D, palette: PlanRulerScenePalette) {
    val origin = Offset(34f, size.height - 36f)
    val axes = listOf(
        Triple(Vec3.UNIT_X, palette.axisX, "X"),
        Triple(Vec3.UNIT_Y, palette.axisY, "Y"),
        Triple(Vec3.UNIT_Z, palette.axisZ, "Z"),
    )
    val paint = scenePaint(palette.onScene, 9.sp.toPx(), bold = true)
    axes.forEach { (axis, color, label) ->
        val direction = projector.rotateDirection(axis)
        val end = origin + Offset(direction.x.toFloat(), -direction.y.toFloat()) * 27f
        drawLine(color, origin, end, 3f, StrokeCap.Round)
        drawIntoCanvas { canvas ->
            paint.color = color.toArgb()
            canvas.nativeCanvas.drawText(label, end.x, end.y - 4f, paint)
        }
    }
    drawCircle(palette.onScene, 3f, origin)
}

private fun DrawScope.drawProjectedLine(
    projector: SceneProjector3D,
    start: Vec3,
    end: Vec3,
    color: Color,
    strokeWidth: Float,
) {
    val a = projector.project(start)
    val b = projector.project(end)
    if (a.visible && b.visible) drawLine(color, a.screen, b.screen, strokeWidth)
}

private fun pickPart(mesh: AssemblyMesh3D, projector: SceneProjector3D, tap: Offset): String? {
    val labelHit = mesh.labels.mapNotNull { label ->
        val projected = projector.project(label.position)
        if (!projected.visible) null else label.partId to distanceSquared(tap, projected.screen)
    }.filter { it.second <= 52f * 52f }.minByOrNull { it.second }
    if (labelHit != null) return labelHit.first

    return mesh.triangles.mapNotNull { triangle ->
        val a = projector.project(triangle.a)
        val b = projector.project(triangle.b)
        val c = projector.project(triangle.c)
        if (a.visible && b.visible && c.visible && pointInTriangle(tap, a.screen, b.screen, c.screen)) {
            triangle.partId to (a.depth + b.depth + c.depth) / 3.0
        } else null
    }.maxByOrNull { it.second }?.first
}

private fun pointInTriangle(point: Offset, a: Offset, b: Offset, c: Offset): Boolean {
    val d1 = edgeSign(point, a, b)
    val d2 = edgeSign(point, b, c)
    val d3 = edgeSign(point, c, a)
    val hasNegative = d1 < 0f || d2 < 0f || d3 < 0f
    val hasPositive = d1 > 0f || d2 > 0f || d3 > 0f
    return !(hasNegative && hasPositive)
}

private fun edgeSign(point: Offset, a: Offset, b: Offset): Float =
    (point.x - b.x) * (a.y - b.y) - (a.x - b.x) * (point.y - b.y)

private fun distanceSquared(first: Offset, second: Offset): Float {
    val dx = first.x - second.x
    val dy = first.y - second.y
    return dx * dx + dy * dy
}

private fun materialColor(material: MeshMaterial3D, palette: PlanRulerScenePalette): Color = when (material) {
    MeshMaterial3D.PIPE -> palette.pipe
    MeshMaterial3D.ELBOW -> palette.elbow
    MeshMaterial3D.FLANGE -> palette.flange
    MeshMaterial3D.TEE -> palette.tee
    MeshMaterial3D.REDUCER -> palette.reducer
    MeshMaterial3D.CAP -> palette.cap
    MeshMaterial3D.INNER_BORE -> palette.bore
    MeshMaterial3D.WELD -> palette.weld
    MeshMaterial3D.GUIDE -> palette.grid
}

private fun materialColorForPart(partId: String, palette: PlanRulerScenePalette): Color = when {
    partId.startsWith("F") -> palette.flange
    partId.startsWith("E") -> palette.elbow
    else -> palette.pipe
}

private fun Color.adjustBrightness(factor: Float): Color = Color(
    red = (red * factor).coerceIn(0f, 1f),
    green = (green * factor).coerceIn(0f, 1f),
    blue = (blue * factor).coerceIn(0f, 1f),
    alpha = alpha,
)

private fun partDimensions(part: PartInstance3D): String = when (val geometry = part.definition.geometry) {
    is StraightPipeGeometry3D -> "L ${sceneNumber(geometry.lengthMm)} mm"
    is ElbowGeometry3D -> "${sceneNumber(abs(geometry.angleDeg))}° · R ${sceneNumber(geometry.centerlineRadiusMm)}"
    is WeldNeckFlangeGeometry3D -> "D ${sceneNumber(geometry.outsideDiameterMm)} · h ${sceneNumber(geometry.faceToWeldMm)}"
    else -> "DN ${part.definition.ports.first().nominalDiameter}"
}

private fun scenePaint(color: Color, sizePx: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    textSize = sizePx
    textAlign = Paint.Align.CENTER
    if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
}

private fun normalizeDegrees(value: Float): Float {
    var result = value % 360f
    if (result > 180f) result -= 360f
    if (result < -180f) result += 360f
    return result
}

private fun sceneNumber(value: Double): String = String.format(Locale.US, "%.1f", value)

private operator fun Offset.times(scale: Float) = Offset(x * scale, y * scale)

private class Model3DText(private val language: AppLanguage) {
    private fun t(pl: String, en: String, de: String, fr: String, it: String, ru: String) = when (language) {
        AppLanguage.POLISH -> pl
        AppLanguage.ENGLISH -> en
        AppLanguage.GERMAN -> de
        AppLanguage.FRENCH -> fr
        AppLanguage.ITALIAN -> it
        AppLanguage.RUSSIAN -> ru
    }

    val title get() = t("Parametryczny model 3D", "Parametric 3D assembly", "Parametrische 3D-Baugruppe", "Assemblage 3D paramétrique", "Assieme 3D parametrico", "Параметрическая 3D-сборка")
    val subtitle get() = t(
        "Przeciągnij, aby obrócić, uszczypnij, aby powiększyć i dotknij części.",
        "Drag to orbit, pinch to zoom and tap a part to inspect it.",
        "Ziehen zum Drehen, zoomen mit zwei Fingern und Bauteil antippen.",
        "Faites glisser pour tourner, pincez pour zoomer et touchez une pièce.",
        "Trascina per ruotare, pizzica per zoomare e tocca un componente.",
        "Вращайте пальцем, масштабируйте щипком и нажимайте на детали.",
    )
    val gestureHint get() = subtitle
    val perspective get() = t("Perspektywa", "Perspective", "Perspektive", "Perspective", "Prospettiva", "Перспектива")
    val orthographic get() = t("Ortograficzny", "Orthographic", "Orthografisch", "Orthographique", "Ortografica", "Ортографический")
    val selected get() = t("Wybrano", "Selected", "Ausgewählt", "Sélection", "Selezionato", "Выбрано")
    val parts get() = t("części", "parts", "Bauteile", "pièces", "componenti", "деталей")
    val welds get() = t("spoin", "welds", "Schweißnähte", "soudures", "saldature", "швов")
    val freePorts get() = t("wolne porty", "free ports", "freie Anschlüsse", "ports libres", "porte libere", "свободных портов")
    val pipe get() = t("Rura", "Pipe", "Rohr", "Tube", "Tubo", "Труба")
    val elbows get() = t("Kolana", "Elbows", "Bögen", "Coudes", "Curve", "Отводы")
    val flanges get() = t("Kołnierze", "Flanges", "Flansche", "Brides", "Flange", "Фланцы")
    val overall get() = t("Długość", "Overall", "Gesamt", "Longueur", "Totale", "Длина")
    val offset get() = t("Przesunięcie", "Offset", "Versatz", "Décalage", "Offset", "Смещение")
    val viewAdjusted get() = t(
        "Widok ustawiony — przeciągnij ponownie",
        "View adjusted — drag again",
        "Ansicht angepasst — erneut ziehen",
        "Vue ajustée — faites glisser à nouveau",
        "Vista regolata — trascina di nuovo",
        "Ракурс настроен — потяните ещё раз",
    )
    val meshUnavailable get() = t(
        "Model przekracza limit siatki silnika.",
        "The model exceeds the engine mesh quota.",
        "Das Modell überschreitet das Netzkontingent der Engine.",
        "Le modèle dépasse le quota de maillage du moteur.",
        "Il modello supera la quota mesh del motore.",
        "Модель превышает лимит сетки движка.",
    )

    fun preset(preset: ViewPreset3D): String = when (preset) {
        ViewPreset3D.ISOMETRIC -> t("Izometria", "Isometric", "Isometrie", "Isométrique", "Isometria", "Изометрия")
        ViewPreset3D.FRONT -> t("Przód", "Front", "Vorne", "Face", "Fronte", "Спереди")
        ViewPreset3D.TOP -> t("Góra", "Top", "Oben", "Haut", "Alto", "Сверху")
        ViewPreset3D.RIGHT -> t("Prawo", "Right", "Rechts", "Droite", "Destra", "Справа")
    }

    fun partKind(kind: FabricationPartKind): String = when (kind) {
        FabricationPartKind.PIPE -> pipe
        FabricationPartKind.ELBOW -> elbows
        FabricationPartKind.FLANGE -> flanges
        FabricationPartKind.TEE -> t("Trójnik", "Tee", "T-Stück", "Té", "Tee", "Тройник")
        FabricationPartKind.REDUCER -> t("Redukcja", "Reducer", "Reduzierung", "Réducteur", "Riduzione", "Переход")
        FabricationPartKind.CAP -> t("Zaślepka", "Cap", "Kappe", "Bouchon", "Tappo", "Заглушка")
    }
}
