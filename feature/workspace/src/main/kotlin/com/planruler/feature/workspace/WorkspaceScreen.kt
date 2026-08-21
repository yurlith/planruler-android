package com.planruler.feature.workspace

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.planruler.designsystem.component.CoachTip
import com.planruler.designsystem.component.IndicatorStatus
import com.planruler.designsystem.PlanRulerTestTags
import com.planruler.designsystem.theme.LocalPlanRulerDimens
import com.planruler.designsystem.theme.Space
import com.planruler.engine.api.SnapResult
import com.planruler.engine.api.SnapType
import com.planruler.export.api.ExportFormat
import com.planruler.export.api.ExportPageSelection
import com.planruler.document.api.DocumentKind
import com.planruler.model.AppSettings
import com.planruler.model.Calibration
import com.planruler.model.DocPoint
import com.planruler.model.LengthUnit
import com.planruler.model.Measurement
import com.planruler.model.MeasurementId
import com.planruler.model.MeasurementType
import com.planruler.model.MeasurementReviewStatus
import com.planruler.model.PageMetadata
import com.planruler.model.ProjectId
import com.planruler.model.SnapMode
import com.planruler.model.TakeoffTemplate
import com.planruler.model.ViewportState
import com.planruler.model.ViewportTransform
import com.planruler.model.latestRevision
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.math.roundToInt

private const val COACH_ZOOM = "coach-zoom"
private const val COACH_CALIBRATION = "coach-calibration"
private const val VIEWPORT_SETTLE_MS = 140L

@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    settings: AppSettings,
    onSettings: (AppSettings) -> Unit,
    onProjectResolved: (ProjectId) -> Unit,
    onBack: () -> Unit,
    onExport: (ExportFormat, ExportPageSelection, Int, Int) -> Unit,
    onImportRevision: () -> Unit,
    onCalculateAssembly: () -> Unit,
) {
    val text = remember(settings.language) { Wt(settings.language) }
    val ui by viewModel.ui.collectAsState()
    val engine by viewModel.engineState.collectAsState()
    val dimens = LocalPlanRulerDimens.current
    val density = LocalDensity.current.density.toDouble()
    val view = LocalView.current
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var tool by remember { mutableStateOf(WorkspaceTool.DISTANCE) }
    var mode by remember { mutableStateOf(WorkspaceMode.EDIT) }
    var focusMode by remember { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(ViewportState()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedId by remember { mutableStateOf<MeasurementId?>(null) }
    var selectedVertex by remember { mutableStateOf<Int?>(null) }
    var overlay by remember { mutableStateOf<WorkspaceOverlay?>(null) }
    var calibrationStep by remember { mutableStateOf(CalibrationStep.CHOICE) }
    var calibrationPoints by remember { mutableStateOf(emptyList<DocPoint>()) }
    var pickingCalibration by remember { mutableStateOf(false) }
    var pickingVerification by remember { mutableStateOf(false) }
    var annotationPoint by remember { mutableStateOf<DocPoint?>(null) }
    var editedAnnotation by remember { mutableStateOf<Measurement?>(null) }
    var snapResult by remember { mutableStateOf<SnapResult?>(null) }
    var magnifierFocus by remember { mutableStateOf<DocPoint?>(null) }
    var dragVertex by remember { mutableStateOf<Int?>(null) }
    var dragStart by remember { mutableStateOf<DocPoint?>(null) }
    var draggingNewDistance by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    var coach by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    var scaleWarned by remember { mutableStateOf(false) }
    var templateEditor by remember { mutableStateOf<TakeoffTemplate?>(null) }
    var exactMeasurement by remember { mutableStateOf<Measurement?>(null) }

    fun haptic(constant: Int) {
        if (settings.haptics) view.performHapticFeedback(constant)
    }

    LaunchedEffect(ui.project?.id) { ui.project?.id?.let(onProjectResolved) }
    LaunchedEffect(settings) { viewModel.applySettings(settings) }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.save()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(text.message(it))
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(warning) {
        warning?.let {
            snackbar.showSnackbar(it)
            warning = null
        }
    }
    LaunchedEffect(engine.calibration) { scaleWarned = false }
    LaunchedEffect(viewport, canvasSize, ui.renderedPage) {
        // Keep gesture frames local. Persisting and rendering tiles on every pointer
        // event caused a StateFlow/project copy, autosave and render cancellation loop.
        delay(VIEWPORT_SETTLE_MS)
        viewModel.updateViewport(viewport)
        viewModel.requestTiles(viewport, canvasSize.width, canvasSize.height)
    }
    LaunchedEffect(ui.renderedPage, canvasSize) {
        val page = ui.renderedPage ?: return@LaunchedEffect
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            val saved = ui.project?.viewport
            viewport = if (
                saved != null && saved.centerX.isFinite() && saved.centerY.isFinite() &&
                (saved.centerX != 0.0 || saved.centerY != 0.0)
            ) {
                saved
            } else {
                ViewportState(
                    minOf(canvasSize.width / page.source.width, canvasSize.height / page.source.height) * 0.92,
                    page.source.width / 2.0,
                    page.source.height / 2.0,
                )
            }
        }
        selectedId = null
        selectedVertex = null
        if (COACH_ZOOM !in settings.coachSeen) coach = COACH_ZOOM
    }

    val visibleMeasurements = engine.measurements.filter { it.matches(ui.revisionFilter) }
    val visibleMeasurementIds = remember(engine.measurements, ui.revisionFilter) {
        visibleMeasurements.mapTo(mutableSetOf()) { it.id }
    }
    val selected = visibleMeasurements.firstOrNull { it.id == selectedId }
    val page = ui.renderedPage
    val pages: List<PageMetadata> = ui.project?.pages.orEmpty()
    val pageIndex = ui.project?.selectedPage ?: 0
    val currentRevision = ui.project?.latestRevision(pageIndex)
    val vectorPage = page?.source?.coordinateUnit == PageMetadata.CoordinateUnit.PDF_POINT
    val revisionNeedsReview = currentRevision?.let { revision ->
        revision.scaleNeedsVerification || engine.measurements.any {
            it.revisionId == revision.id && it.reviewStatus == MeasurementReviewStatus.NEEDS_REVIEW
        }
    } == true
    val confidence = if (revisionNeedsReview) ScaleConfidence.NEEDS_CHECK else scaleConfidence(engine.calibration, vectorPage)
    val draft = engine.draft
    val fieldSummary = remember(engine.measurements, engine.calibration, pageIndex, selected) {
        fieldSummary(engine.measurements, pageIndex, engine.calibration, selected)
    }

    fun snapFor(point: DocPoint, anchor: DocPoint?, excluded: MeasurementId? = null): DocPoint {
        val resolved = viewModel.snap(
            point = point,
            anchor = anchor,
            zoom = viewport.zoom,
            density = density,
            mode = settings.snapMode,
            sensitivityDp = dimens.snapSensitivity.value.toDouble(),
            excludedId = excluded,
        )
        val next = resolved.takeIf { it.type != SnapType.NONE }
        if (next != null && snapResult?.point != next.point) haptic(HapticFeedbackConstants.CLOCK_TICK)
        snapResult = next
        return resolved.point
    }

    fun fitPage() {
        val source = page?.source ?: return
        if (canvasSize.width == 0) return
        viewport = ViewportState(
            minOf(canvasSize.width / source.width, canvasSize.height / source.height) * 0.92,
            source.width / 2.0,
            source.height / 2.0,
        )
        viewModel.updateViewport(viewport)
    }

    fun centerOn(measurement: Measurement) {
        val point = measurement.points.firstOrNull() ?: return
        viewport = viewport.copy(centerX = point.x, centerY = point.y)
        viewModel.updateViewport(viewport)
    }

    fun selectTool(next: WorkspaceTool) {
        if (draft != null) viewModel.cancel()
        pickingCalibration = false
        pickingVerification = false
        snapResult = null
        val active = viewModel.activeTemplate
        if (next.measurement != null && active != null && active.measurementType != next.measurement) {
            viewModel.selectTemplate(null)
        }
        tool = next
        // A scale taken from the printed ratio of a scan is a guess; say so once per calibration.
        if (settings.warnUncalibrated && next.draws && !scaleWarned) {
            when (confidence) {
                ScaleConfidence.LIKELY -> text.scaleLikely
                ScaleConfidence.NEEDS_CHECK -> text.scaleNeedsCheck
                else -> null
            }?.let {
                warning = it
                scaleWarned = true
            }
        }
        if (next == WorkspaceTool.CALIBRATE) {
            calibrationStep = CalibrationStep.CHOICE
            calibrationPoints = emptyList()
            overlay = WorkspaceOverlay.CALIBRATION
        }
    }

    fun placePoint(point: DocPoint) {
        val type = tool.measurement ?: return
        if (type != MeasurementType.ANNOTATION && type != MeasurementType.COUNTER && engine.calibration == null) {
            if (COACH_CALIBRATION !in settings.coachSeen) coach = COACH_CALIBRATION
            calibrationStep = CalibrationStep.CHOICE
            overlay = WorkspaceOverlay.CALIBRATION
            return
        }
        if (type == MeasurementType.ANNOTATION) {
            annotationPoint = point
            return
        }
        val current = engine.draft
        if (current == null) {
            viewModel.begin(type, snapFor(point, null))
            if (type == MeasurementType.COUNTER) {
                viewModel.commit()
                haptic(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        } else {
            viewModel.add(snapFor(point, current.points.lastOrNull()))
            val count = viewModel.engineState.value.draft?.points?.size ?: 0
            if ((type == MeasurementType.DISTANCE && count >= 2) || (type == MeasurementType.ANGLE && count >= 3)) {
                viewModel.commit()
                haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                if (!settings.keepToolAfterFinish) tool = WorkspaceTool.SELECT
            }
        }
    }

    fun finishDraft() {
        viewModel.commit()
        snapResult = null
        haptic(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        viewModel.delete(id)
        selectedId = null
        selectedVertex = null
        haptic(HapticFeedbackConstants.LONG_PRESS)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AnimatedVisibility(!focusMode) {
                Column {
                    Box {
                        WorkspaceTopBar(
                            title = ui.project?.name ?: text.workspace,
                            pageLabel = "${text.page} ${pageIndex + 1}/${pages.size.coerceAtLeast(1)}",
                            saveBadge = ui.saveBadge,
                            canUndo = engine.canUndo,
                            canRedo = engine.canRedo,
                            mode = mode,
                            text = text,
                            onBack = { viewModel.save(); onBack() },
                            onTitle = { projectMenu = true },
                            onUndo = { viewModel.undo(); haptic(HapticFeedbackConstants.CLOCK_TICK) },
                            onRedo = { viewModel.redo(); haptic(HapticFeedbackConstants.CLOCK_TICK) },
                            onToggleMode = {
                                mode = if (mode == WorkspaceMode.EDIT) WorkspaceMode.VIEW else WorkspaceMode.EDIT
                                selectedId = null
                                selectedVertex = null
                            },
                            onMenu = { projectMenu = true },
                            onSaveStatus = { viewModel.save() },
                        )
                        DropdownMenu(projectMenu, { projectMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(text.pages) },
                                onClick = {
                                    projectMenu = false
                                    viewModel.loadThumbnails()
                                    overlay = WorkspaceOverlay.PAGES
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(text.schedule) },
                                onClick = { projectMenu = false; overlay = WorkspaceOverlay.SCHEDULE },
                            )
                            DropdownMenuItem(
                                text = { Text(text.layers) },
                                onClick = { projectMenu = false; overlay = WorkspaceOverlay.LAYERS },
                            )
                            DropdownMenuItem(
                                text = { Text(text.calibrate) },
                                onClick = {
                                    projectMenu = false
                                    calibrationStep = CalibrationStep.CHOICE
                                    overlay = WorkspaceOverlay.CALIBRATION
                                },
                            )
                            if (ui.document?.kind == DocumentKind.IMAGE) {
                                DropdownMenuItem(
                                    text = { Text(text.photoData) },
                                    modifier = Modifier.testTag(PlanRulerTestTags.PhotoMetadataMenu),
                                    onClick = {
                                        projectMenu = false
                                        overlay = WorkspaceOverlay.PHOTO_METADATA
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(text.export) },
                                onClick = { projectMenu = false; overlay = WorkspaceOverlay.EXPORT },
                            )
                            DropdownMenuItem(
                                text = { Text(text.focusMode) },
                                onClick = { projectMenu = false; focusMode = true },
                            )
                        }
                    }
                    IndicatorStrip(
                        pageLabel = "${pageIndex + 1} / ${pages.size.coerceAtLeast(1)}",
                        scaleLabel = when {
                            engine.calibration == null -> text.notCalibrated
                            engine.calibration?.method == Calibration.Method.PRINT_RATIO ->
                                engine.calibration?.description?.substringBefore(" ").orEmpty()
                            else -> text.calibrated
                        },
                        scaleDescription = when (confidence) {
                            ScaleConfidence.CONFIRMED -> text.scaleConfirmed
                            ScaleConfidence.LIKELY -> text.scaleLikely
                            ScaleConfidence.NEEDS_CHECK -> text.scaleNeedsCheck
                            ScaleConfidence.MISSING -> text.notCalibrated
                        },
                        scaleStatus = confidence.status,
                        unitLabel = engine.displayUnit.symbol,
                        snapMode = settings.snapMode,
                        text = text,
                        onPages = {
                            viewModel.loadThumbnails()
                            overlay = WorkspaceOverlay.PAGES
                        },
                        onScale = {
                            calibrationStep = CalibrationStep.CHOICE
                            overlay = WorkspaceOverlay.CALIBRATION
                        },
                        onUnits = {
                            val units = LengthUnit.entries
                            val next = units[(units.indexOf(engine.displayUnit) + 1) % units.size]
                            viewModel.setUnit(next)
                        },
                        onUnitsLong = { viewModel.setUnit(LengthUnit.METER) },
                        onSnap = {
                            val modes = SnapMode.entries
                            onSettings(
                                settings.copy(
                                    snapMode = modes[(modes.indexOf(settings.snapMode) + 1) % modes.size],
                                ),
                            )
                        },
                        onSnapLong = { onSettings(settings.copy(snapMode = SnapMode.AUTO)) },
                    )
                }
            }
        },
        bottomBar = {
            Column {
                FieldSummaryStrip(
                    lastValue = fieldSummary.lastMeasurement?.let(viewModel::value).orEmpty(),
                    materialTotal = fieldTotalValue(fieldSummary.totals, text.pieces),
                    materialName = fieldSummary.material,
                    canCalculateAssembly = fieldSummary.assemblySource != null,
                    text = text,
                    onCalculateAssembly = {
                        fieldSummary.assemblySource?.let { source ->
                            viewModel.createInstallationJob(source, onCalculateAssembly)
                        }
                    },
                )
                WorkspaceToolBar(
                    tool = tool,
                    text = text,
                    counterBadge = engine.measurements
                        .count { it.type == MeasurementType.COUNTER && it.pageIndex == pageIndex }
                        .takeIf { it > 0 }
                        ?.toString(),
                    onTool = { selectTool(it) },
                    onToolSettings = { candidate ->
                        when (candidate) {
                            WorkspaceTool.NAVIGATE -> fitPage()
                            WorkspaceTool.CALIBRATE -> {
                                calibrationStep = CalibrationStep.CHOICE
                                overlay = WorkspaceOverlay.CALIBRATION
                            }
                            else -> overlay = WorkspaceOverlay.MORE_TOOLS
                        }
                    },
                    onMore = { overlay = WorkspaceOverlay.MORE_TOOLS },
                    showLabels = !focusMode,
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.isCtrlPressed && event.key == Key.Z && event.isShiftPressed -> {
                            viewModel.redo(); true
                        }
                        event.isCtrlPressed && event.key == Key.Z -> { viewModel.undo(); true }
                        event.key == Key.V -> { selectTool(WorkspaceTool.SELECT); true }
                        event.key == Key.H -> { selectTool(WorkspaceTool.NAVIGATE); true }
                        event.key == Key.D -> { selectTool(WorkspaceTool.DISTANCE); true }
                        event.key == Key.P -> { selectTool(WorkspaceTool.POLYLINE); true }
                        event.key == Key.A -> { selectTool(WorkspaceTool.AREA); true }
                        event.key == Key.C -> { selectTool(WorkspaceTool.COUNTER); true }
                        event.key == Key.N -> { selectTool(WorkspaceTool.ANNOTATION); true }
                        event.key == Key.F -> { fitPage(); true }
                        event.key == Key.R -> {
                            viewModel.repeatLast()?.let { repeated ->
                                selectedId = repeated.id
                                selectedVertex = null
                            }
                            true
                        }
                        event.key == Key.Escape -> {
                            when {
                                draft != null -> viewModel.cancel()
                                overlay != null -> overlay = null
                                focusMode -> focusMode = false
                                else -> { selectedId = null; selectedVertex = null }
                            }
                            true
                        }
                        event.key == Key.Enter -> { if (draft != null) finishDraft(); true }
                        event.key == Key.Delete -> { deleteSelected(); true }
                        else -> false
                    }
                },
        ) {
            when {
                ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                page != null -> PlanCanvas(
                    page = page,
                    logicalPageIndex = pageIndex,
                    engine = engine,
                    viewport = viewport,
                    tool = tool,
                    mode = mode,
                    selectedId = selectedId,
                    selectedVertex = selectedVertex,
                    snapResult = snapResult,
                    calibrationPoints = calibrationPoints,
                    magnifierFocus = if (settings.magnifier) magnifierFocus else null,
                    magnifierPlacement = settings.magnifierPlacement,
                    labelScale = settings.labelScale,
                    canvasDescription = text.canvasDescription,
                    valueOf = viewModel::value,
                    segmentsOf = { measurement ->
                        if (settings.showSegments) viewModel.segmentValues(measurement) else emptyList()
                    },
                    hiddenLayers = viewModel.hiddenLayers,
                    lockedLayers = viewModel.lockedLayers,
                    tiles = ui.tiles,
                    visibleMeasurementIds = visibleMeasurementIds,
                    revisionOverlay = ui.revisionOverlay,
                    revisionTransform = currentRevision?.alignment?.transform,
                    revisionOverlayOpacity = ui.revisionOverlayOpacity,
                    onSize = { canvasSize = it },
                    onTap = { point, hit, doubleTap ->
                        when {
                            pickingCalibration || pickingVerification -> {
                                val verification = pickingVerification
                                calibrationPoints = (calibrationPoints + point).takeLast(2)
                                if (calibrationPoints.size == 2) {
                                    pickingCalibration = false
                                    pickingVerification = false
                                    calibrationStep = if (verification) CalibrationStep.VERIFY else CalibrationStep.LENGTH
                                    overlay = WorkspaceOverlay.CALIBRATION
                                }
                            }
                            mode == WorkspaceMode.VIEW -> {
                                selectedId = hit?.measurement?.id
                                selectedVertex = hit?.vertexIndex
                            }
                            hit?.label == true -> {
                                selectedId = hit.measurement.id
                                selectedVertex = null
                                exactMeasurement = hit.measurement
                                haptic(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            doubleTap && draft != null -> finishDraft()
                            doubleTap && tool == WorkspaceTool.NAVIGATE -> {
                                viewport = viewport.copy(
                                    zoom = (viewport.zoom * 2).coerceAtMost(32.0),
                                    centerX = point.x,
                                    centerY = point.y,
                                )
                                viewModel.updateViewport(viewport)
                            }
                            tool == WorkspaceTool.SELECT || tool == WorkspaceTool.NAVIGATE -> {
                                selectedId = hit?.measurement?.id
                                selectedVertex = hit?.vertexIndex
                                if (hit != null) haptic(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            tool == WorkspaceTool.ANNOTATION && hit?.measurement?.type == MeasurementType.ANNOTATION ->
                                editedAnnotation = hit.measurement
                            draft == null && hit != null -> {
                                // Tapping an existing vertex/segment while a drawing tool is
                                // active selects it for editing (drag-to-stretch), matching
                                // onDragStart's `editable` condition below, instead of always
                                // starting a brand-new draft from the raw tap coordinates.
                                selectedId = hit.measurement.id
                                selectedVertex = hit.vertexIndex
                                haptic(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            else -> placePoint(point)
                        }
                    },
                    onLongPress = { _, hit ->
                        haptic(HapticFeedbackConstants.LONG_PRESS)
                        if (hit != null) {
                            selectedId = hit.measurement.id
                            selectedVertex = hit.vertexIndex
                            overlay = WorkspaceOverlay.PROPERTIES
                        } else {
                            overlay = WorkspaceOverlay.MORE_TOOLS
                        }
                    },
                    onDragStart = { point, hit ->
                        val editable = mode == WorkspaceMode.EDIT &&
                            tool != WorkspaceTool.NAVIGATE &&
                            hit != null &&
                            draft == null
                        if (editable) {
                            val target = requireNotNull(hit)
                            // A ruler is resized by dragging either handle or the line itself.
                            // When the line body is grabbed, move the endpoint nearest to the
                            // finger instead of translating the whole measurement.
                            val editableVertex = target.vertexIndex ?: target.measurement
                                .takeIf { it.type == MeasurementType.DISTANCE }
                                ?.points
                                ?.indices
                                ?.minByOrNull { index -> target.measurement.points[index].distanceTo(point) }
                            selectedId = target.measurement.id
                            selectedVertex = editableVertex
                            dragVertex = editableVertex
                            dragStart = point
                            magnifierFocus = point
                            viewModel.beginEdit(target.measurement.id)
                            true
                        } else if (
                            mode == WorkspaceMode.EDIT &&
                            tool == WorkspaceTool.DISTANCE &&
                            hit == null &&
                            draft == null &&
                            engine.calibration != null
                        ) {
                            val start = snapFor(point, null)
                            draggingNewDistance = true
                            dragStart = start
                            magnifierFocus = start
                            viewModel.begin(MeasurementType.DISTANCE, start)
                            true
                        } else {
                            false
                        }
                    },
                    onDrag = { point ->
                        val id = selectedId
                        val vertex = dragVertex
                        if (draggingNewDistance) {
                            val anchor = dragStart
                            val resolved = snapFor(point, anchor)
                            magnifierFocus = resolved
                            viewModel.updateLast(resolved)
                        } else if (id != null && vertex != null) {
                            val measurement = engine.measurements.firstOrNull { it.id == id }
                            val anchor = measurement?.points?.getOrNull(if (vertex == 0) 1 else vertex - 1)
                            val resolved = snapFor(point, anchor, id)
                            magnifierFocus = resolved
                            viewModel.previewVertex(vertex, resolved)
                        } else if (id != null) {
                            dragStart?.let { start ->
                                magnifierFocus = point
                                viewModel.previewMove(DocPoint(point.x - start.x, point.y - start.y))
                            }
                        }
                    },
                    onDragEnd = {
                        if (draggingNewDistance) {
                            viewModel.commit()
                            draggingNewDistance = false
                            selectedId = null
                            selectedVertex = null
                            if (!settings.keepToolAfterFinish) tool = WorkspaceTool.SELECT
                        } else {
                            viewModel.commitEdit()
                        }
                        haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                        dragVertex = null
                        dragStart = null
                        magnifierFocus = null
                        snapResult = null
                    },
                    onDragCancel = {
                        if (draggingNewDistance) {
                            viewModel.cancel()
                            draggingNewDistance = false
                        } else {
                            viewModel.cancelEdit()
                        }
                        dragVertex = null
                        dragStart = null
                        magnifierFocus = null
                        snapResult = null
                    },
                    onViewport = {
                        viewport = it
                    },
                )
                else -> ElevatedCard(Modifier.align(Alignment.Center).padding(Space.x6)) {
                    Text(
                        ui.fatal?.let(text::message) ?: text.noPage,
                        Modifier.padding(Space.x6),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (currentRevision != null && !focusMode && !pickingCalibration && !pickingVerification) {
                RevisionControlsBar(
                    revision = currentRevision,
                    opacity = ui.revisionOverlayOpacity,
                    filter = ui.revisionFilter,
                    needsReviewCount = engine.measurements.count {
                        it.revisionId == currentRevision.id &&
                            it.reviewStatus == MeasurementReviewStatus.NEEDS_REVIEW
                    },
                    text = text,
                    onOpacity = viewModel::setRevisionOverlayOpacity,
                    onFilter = {
                        selectedId = null
                        selectedVertex = null
                        viewModel.setRevisionFilter(it)
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            // Floating, not part of the Scaffold: a bar that appears mid-measurement must
            // never resize the canvas, or the plan shifts under the finger between points.
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (tool == WorkspaceTool.NAVIGATE && !focusMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = 6.dp,
                        modifier = Modifier.padding(Space.x2),
                    ) {
                        Row(
                            Modifier.padding(horizontal = Space.x2),
                            horizontalArrangement = Arrangement.spacedBy(Space.x1),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton({ fitPage() }) { Text(text.fitPage) }
                            TextButton({
                                viewport = viewport.copy(zoom = 1.0)
                                viewModel.updateViewport(viewport)
                            }) { Text(text.actualSize) }
                            selected?.let { measurement ->
                                TextButton({ centerOn(measurement) }) { Text(text.toSelection) }
                            }
                            Text(
                                "${(viewport.zoom * 100).roundToInt()} %",
                                Modifier.padding(end = Space.x2),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                if (selected != null && draft == null && mode == WorkspaceMode.EDIT) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = 6.dp,
                        modifier = Modifier.padding(Space.x2),
                    ) {
                        SelectionBar(
                            measurement = selected,
                            vertexIndex = selectedVertex,
                            text = text,
                            onProperties = { overlay = WorkspaceOverlay.PROPERTIES },
                            onDuplicate = { viewModel.duplicate(selected.id) },
                            onExactLength = { exactMeasurement = selected },
                            onInsertVertex = {
                                val index = selectedVertex ?: return@SelectionBar
                                val next = selected.points[(index + 1) % selected.points.size]
                                val current = selected.points[index]
                                viewModel.insertVertex(
                                    selected.id,
                                    index,
                                    DocPoint((current.x + next.x) / 2.0, (current.y + next.y) / 2.0),
                                )
                            },
                            onRemoveVertex = {
                                selectedVertex?.let { viewModel.removeVertex(selected.id, it) }
                                selectedVertex = null
                            },
                            onDelete = { if (settings.confirmDelete) confirmDelete = true else deleteSelected() },
                        )
                    }
                }
                if (selected == null && draft == null && mode == WorkspaceMode.EDIT && !focusMode) {
                    ActiveTemplateBar(
                        template = viewModel.activeTemplate,
                        canRepeat = engine.measurements.isNotEmpty(),
                        text = text,
                        onTemplates = { overlay = WorkspaceOverlay.TEMPLATES },
                        onRepeat = {
                            viewModel.repeatLast()?.let { repeated ->
                                selectedId = repeated.id
                                selectedVertex = null
                                haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                        },
                    )
                }
                if (draft != null) {
                    ConfirmBar(
                        hint = draftHint(draft, viewModel, text),
                        confirmEnabled = draft.points.size >= requiredPoints(draft.type),
                        canRemovePoint = draft.points.size > 1,
                        text = text,
                        onCancel = { viewModel.cancel(); snapResult = null },
                        onRemovePoint = { viewModel.removeLastPoint() },
                        onConfirm = { finishDraft() },
                    )
                }
            }
            if (pickingCalibration || pickingVerification) {
                Text(
                    if (pickingVerification) text.verificationDrawHint else text.calibrationDrawHint,
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(Space.x4),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (focusMode) {
                TextButton(
                    { focusMode = false },
                    Modifier.align(Alignment.TopEnd).padding(Space.x2),
                ) { Text(text.close) }
            }
            coach?.let { key ->
                CoachTip(
                    text = if (key == COACH_ZOOM) text.coachZoom else text.coachCalibration,
                    onUnderstood = { coach = null },
                    onNeverShow = {
                        coach = null
                        onSettings(settings.copy(coachSeen = settings.coachSeen + key))
                    },
                    understoodLabel = text.ok,
                    neverLabel = text.neverShow,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = Space.x4, end = Space.x4, bottom = 84.dp),
                )
            }
        }
    }

    when (overlay) {
        WorkspaceOverlay.CALIBRATION -> CalibrationSheet(
            step = calibrationStep,
            documentLength = if (calibrationPoints.size == 2) {
                calibrationPoints[0].distanceTo(calibrationPoints[1])
            } else {
                0.0
            },
            pageWidth = page?.source?.width ?: 0.0,
            coordinateUnit = page?.source?.coordinateUnit,
            initialUnit = engine.displayUnit,
            calibration = engine.calibration,
            allowRatio = vectorPage,
            text = text,
            onStep = { calibrationStep = it },
            onPickPoints = {
                calibrationPoints = emptyList()
                pickingCalibration = true
                pickingVerification = false
                overlay = null
            },
            onApplyLength = { value, unit, calibratedBy ->
                if (calibrationPoints.size == 2) {
                    if (viewModel.calibrate(calibrationPoints[0], calibrationPoints[1], value, unit, calibratedBy)) {
                        haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                        calibrationPoints = emptyList()
                        calibrationStep = CalibrationStep.VERIFY
                    }
                }
            },
            onApplyRatio = { ratio, sure, calibratedBy ->
                if (viewModel.calibrateRatio(ratio, sure, calibratedBy)) {
                    calibrationPoints = emptyList()
                    calibrationStep = CalibrationStep.VERIFY
                    haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            },
            onPickVerification = {
                calibrationPoints = emptyList()
                pickingCalibration = false
                pickingVerification = true
                overlay = null
            },
            onApplyVerification = { expected, unit ->
                if (
                    calibrationPoints.size == 2 &&
                    viewModel.verifyCalibration(calibrationPoints[0], calibrationPoints[1], expected, unit)
                ) {
                    haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                    calibrationPoints = emptyList()
                    overlay = null
                    if (tool == WorkspaceTool.CALIBRATE) tool = WorkspaceTool.DISTANCE
                }
            },
            onSkipVerification = {
                calibrationPoints = emptyList()
                overlay = null
                if (tool == WorkspaceTool.CALIBRATE) tool = WorkspaceTool.DISTANCE
            },
            onDismiss = {
                overlay = null
                calibrationPoints = emptyList()
                pickingCalibration = false
                pickingVerification = false
            },
        )
        WorkspaceOverlay.PROPERTIES -> selected?.let { measurement ->
            PropertiesSheet(
                measurement = measurement,
                value = viewModel.value(measurement),
                text = text,
                layers = ui.project?.layers.orEmpty(),
                onDismiss = { overlay = null },
                onDuplicate = { viewModel.duplicate(measurement.id); overlay = null },
                onDelete = {
                    overlay = null
                    if (settings.confirmDelete) confirmDelete = true else deleteSelected()
                },
                onMarkReviewed = {
                    viewModel.markMeasurementReviewed(measurement.id)
                    overlay = null
                    selectedId = null
                    selectedVertex = null
                },
                onApply = { update ->
                    viewModel.updateProperties(measurement.id, update)
                    overlay = null
                },
            )
        }
        WorkspaceOverlay.PAGES -> PagesSheet(
            pages = pages,
            selected = pageIndex,
            thumbnails = ui.thumbnails,
            measurementsPerPage = engine.measurements.groupingBy { it.pageIndex }.eachCount(),
            revisionsPerPage = ui.project?.pageRevisions.orEmpty().groupingBy { it.logicalPageIndex }.eachCount(),
            text = text,
            onSelect = { viewModel.goToPage(it); overlay = null },
            onNewRevision = {
                overlay = null
                onImportRevision()
            },
            onDismiss = { overlay = null },
        )
        WorkspaceOverlay.SCHEDULE -> ScheduleSheet(
            measurements = visibleMeasurements,
            calibration = engine.calibration,
            unit = engine.displayUnit,
            templates = ui.project?.takeoffTemplates.orEmpty(),
            layers = ui.project?.layers.orEmpty(),
            valueOf = viewModel::value,
            text = text,
            onSelect = { measurement ->
                overlay = null
                if (measurement.pageIndex != pageIndex) viewModel.goToPage(measurement.pageIndex)
                selectedId = measurement.id
                selectedVertex = null
                centerOn(measurement)
            },
            onDismiss = { overlay = null },
        )
        WorkspaceOverlay.EXPORT -> ExportWizard(
            pageCount = pages.size.coerceAtLeast(1),
            currentPage = pageIndex,
            text = text,
            settings = settings,
            onSettings = onSettings,
            onDismiss = { overlay = null },
            onExport = { format, selection, first, last ->
                overlay = null
                onExport(format, selection, first, last)
            },
        )
        WorkspaceOverlay.LAYERS -> LayersSheet(
            layers = ui.project?.layers.orEmpty(),
            counts = engine.measurements.groupingBy { it.layerId }.eachCount(),
            text = text,
            onDismiss = { overlay = null },
            onVisible = { id, visible -> viewModel.updateLayer(id, visible = visible) },
            onLocked = { id, locked -> viewModel.updateLayer(id, locked = locked) },
            onRename = { id, name -> viewModel.updateLayer(id, name = name) },
            onAdd = { viewModel.addLayer(it) },
            onDelete = { if (!viewModel.deleteLayer(it)) warning = text.layerInUse },
        )
        WorkspaceOverlay.TEMPLATES -> TemplatesSheet(
            templates = ui.project?.takeoffTemplates.orEmpty(),
            activeId = ui.project?.activeTakeoffTemplateId,
            text = text,
            onSelect = { template ->
                viewModel.selectTemplate(template.id)
                tool = toolOf(template.measurementType)
                overlay = null
            },
            onEdit = { template ->
                templateEditor = template
                overlay = null
            },
            onAdd = {
                templateEditor = blankTakeoffTemplate(UUID.randomUUID().toString())
                overlay = null
            },
            onDismiss = { overlay = null },
        )
        WorkspaceOverlay.PHOTO_METADATA -> PhotoMetadataSheet(
            evidence = ui.document?.captureEvidence,
            text = text,
            onDismiss = { overlay = null },
        )
        WorkspaceOverlay.MORE_TOOLS -> MoreToolsSheet(
            text = text,
            focusMode = focusMode,
            onTool = { selectTool(it) },
            onSchedule = { overlay = WorkspaceOverlay.SCHEDULE },
            onExport = { overlay = WorkspaceOverlay.EXPORT },
            onFocus = { focusMode = !focusMode; overlay = null },
            onDismiss = { overlay = null },
        )
        WorkspaceOverlay.TOOL_SETTINGS, null -> Unit
    }

    val pendingRevision = ui.pendingRevision
    if (pendingRevision != null && page != null) {
        RevisionAlignmentSheet(
            previousPage = page,
            pending = pendingRevision,
            text = text,
            onSelectPage = viewModel::selectPendingRevisionPage,
            onConfirm = viewModel::confirmPageRevision,
            onDismiss = viewModel::cancelPendingRevision,
        )
    }

    if (annotationPoint != null || editedAnnotation != null) {
        AnnotationDialog(
            initial = editedAnnotation?.label.orEmpty(),
            editing = editedAnnotation != null,
            text = text,
            onDismiss = { annotationPoint = null; editedAnnotation = null },
            onConfirm = { value ->
                editedAnnotation?.let { viewModel.updateAnnotation(it.id, value) }
                    ?: annotationPoint?.let {
                        viewModel.begin(MeasurementType.ANNOTATION, it, value)
                        viewModel.commit()
                    }
                annotationPoint = null
                editedAnnotation = null
            },
        )
    }

    if (confirmDelete && selected != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(text.deleteMeasurement) },
            text = { Text(text.deleteMeasurementBody) },
            confirmButton = {
                TextButton({
                    deleteSelected()
                    confirmDelete = false
                }) { Text(text.delete) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(text.cancel) } },
        )
    }

    templateEditor?.let { template ->
        val project = ui.project
        val exists = project?.takeoffTemplates?.any { it.id == template.id } == true
        val usedCount = engine.measurements.count { it.templateId == template.id }
        TemplateEditorDialog(
            initial = template,
            layers = project?.layers.orEmpty(),
            usedCount = usedCount,
            text = text,
            onDismiss = { templateEditor = null },
            onDelete = if (exists) {
                {
                    if (viewModel.deleteTemplate(template.id)) templateEditor = null
                    else warning = text.templateInUse
                }
            } else {
                null
            },
            onSave = { updated, updateExisting ->
                viewModel.saveTemplate(updated, updateExisting)
                tool = toolOf(updated.measurementType)
                templateEditor = null
            },
        )
    }

    exactMeasurement?.let { measurement ->
        ExactLengthDialog(
            currentLength = viewModel.value(measurement),
            initialUnit = measurement.displayUnit ?: engine.displayUnit,
            text = text,
            onDismiss = { exactMeasurement = null },
            onApply = { length, unit, constraint ->
                if (viewModel.setExactLength(measurement.id, length, unit, constraint)) {
                    exactMeasurement = null
                    haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            },
        )
    }
}

@Composable
private fun SelectionBar(
    measurement: Measurement,
    vertexIndex: Int?,
    text: Wt,
    onProperties: () -> Unit,
    onDuplicate: () -> Unit,
    onExactLength: () -> Unit,
    onInsertVertex: () -> Unit,
    onRemoveVertex: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.x3, vertical = Space.x1),
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onProperties) { Text(text.properties) }
        TextButton(onDuplicate) { Text(text.duplicate) }
        if (measurement.type == MeasurementType.DISTANCE) {
            TextButton(onExactLength) { Text(text.exactLength) }
        }
        if (vertexIndex != null && measurement.type in setOf(MeasurementType.POLYLINE, MeasurementType.AREA)) {
            TextButton(onInsertVertex) { Text(text.insertVertex) }
            TextButton(onRemoveVertex) { Text(text.removeVertex) }
        }
        TextButton(onDelete) { Text(text.delete, color = MaterialTheme.colorScheme.error) }
    }
}

private fun requiredPoints(type: MeasurementType) = when (type) {
    MeasurementType.DISTANCE, MeasurementType.POLYLINE -> 2
    MeasurementType.AREA, MeasurementType.ANGLE -> 3
    MeasurementType.ANNOTATION, MeasurementType.COUNTER -> 1
}

private fun draftHint(draft: Measurement, viewModel: WorkspaceViewModel, text: Wt): String {
    val value = viewModel.value(draft)
    val segment = (draft.points.size - 1).coerceAtLeast(0)
    return if (value.isBlank()) {
        "${text.tool(toolOf(draft.type))} · ${draft.points.size}"
    } else {
        "${text.tool(toolOf(draft.type))} · $segment · $value"
    }
}
