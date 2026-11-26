package com.planruler.feature.workspace

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.planruler.designsystem.component.PlanRulerIconButton
import com.planruler.designsystem.component.SheetHeader
import com.planruler.designsystem.component.ToolButton
import com.planruler.designsystem.icon.PlanRulerIcons
import com.planruler.designsystem.theme.Space
import com.planruler.isometric.IsoAxis
import com.planruler.isometric.IsoGrid
import com.planruler.isometric.IsoHit
import com.planruler.isometric.IsoHitTester
import com.planruler.isometric.IsoNetwork
import com.planruler.isometric.IsoNodeDrag
import com.planruler.isometric.IsoProjection
import com.planruler.isometric.IsoRunPlanner
import com.planruler.isometric.IsoSelection
import com.planruler.isometric.IsoTakeoff
import com.planruler.isometric.P2
import com.planruler.isometric.RunPlan
import com.planruler.isometric.RunPlanOptions
import com.planruler.isometric.RunStart
import com.planruler.isometric.Vec3
import com.planruler.isometric.toVec
import com.planruler.model.AppLanguage
import com.planruler.model.AppSettings
import com.planruler.model.IsoComponentId
import com.planruler.model.IsoComponentKind
import com.planruler.model.IsoNodeId
import com.planruler.model.IsoPipeId
import com.planruler.model.IsoPipeSpec
import com.planruler.model.IsoPipeSystem
import com.planruler.model.IsoProjectionStyle
import com.planruler.model.IsometricSheet
import com.planruler.model.ViewportState
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private enum class IsoTool { SELECT, PIPE, VALVE }

/** What the user picked on the sheet. */
private sealed interface IsoPick {
    data class Pipe(val id: IsoPipeId) : IsoPick
    data class Node(val id: IsoNodeId) : IsoPick
    data class Component(val id: IsoComponentId) : IsoPick
}

/** Where the next run of the chain starts. */
private data class ChainHead(val start: RunStart, val position: Vec3)

private val PIPE_SIZES = listOf(15, 20, 25, 32, 40, 50, 65, 80, 100, 110, 160)
private val INLINE_KINDS = listOf(
    IsoComponentKind.BALL_VALVE,
    IsoComponentKind.GATE_VALVE,
    IsoComponentKind.CHECK_VALVE,
    IsoComponentKind.BALANCING_VALVE,
    IsoComponentKind.STRAINER,
    IsoComponentKind.PUMP,
    IsoComponentKind.METER,
)
private val TERMINAL_KINDS = listOf(
    IsoComponentKind.RADIATOR,
    IsoComponentKind.FIXTURE,
    IsoComponentKind.AIR_VENT,
    IsoComponentKind.DRAIN,
    IsoComponentKind.PLUG,
)
private const val VIEWPORT_SETTLE_MS = 300L
private const val MAX_TYPED_RUN_MM = 500_000.0

/**
 * Pipe isometric editor: an isometric (or frontal) grid, runs drawn along the axes with
 * snapped lengths, typed true lengths, valves and devices, and a live bill of materials.
 * Everything is stored with the project; the plan workspace stays underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsometricEditorScreen(
    viewModel: WorkspaceViewModel,
    settings: AppSettings,
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
    onExport: (IsometricExportFormat) -> Unit,
) {
    val text = remember(settings.language) { Wt(settings.language) }
    val labeler = remember(settings.language) { IsometricLabeler(settings.language) }
    val state by viewModel.isometric.state.collectAsState()
    val sheets by viewModel.isometric.sheets.collectAsState()
    val sheet = state.sheet
    val projection = IsoProjection.of(sheet.projection)
    val density = LocalDensity.current
    val view = LocalView.current

    var tool by remember { mutableStateOf(if (sheet.pipes.isEmpty()) IsoTool.PIPE else IsoTool.SELECT) }
    var spec by remember { mutableStateOf(IsoPipeSpec(IsoPipeSystem.HEATING_SUPPLY, 20)) }
    var componentKind by remember { mutableStateOf(IsoComponentKind.BALL_VALVE) }
    var allowOffsets by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var viewport by remember(sheet.id) { mutableStateOf(sheet.viewport ?: ViewportState(0.1, 0.0, 0.0)) }
    var fitted by remember(sheet.id) { mutableStateOf(sheet.viewport != null) }
    var pick by remember(sheet.id) { mutableStateOf<IsoPick?>(null) }
    var head by remember(sheet.id) { mutableStateOf<ChainHead?>(null) }
    var dragOrigin by remember(sheet.id) { mutableStateOf<ChainHead?>(null) }
    var preview by remember(sheet.id) { mutableStateOf<RunPlan?>(null) }
    var nodeDrag by remember(sheet.id) { mutableStateOf<IsoNodeDrag?>(null) }
    var nodeTarget by remember(sheet.id) { mutableStateOf<Vec3?>(null) }
    var menu by remember { mutableStateOf(false) }
    var lengthEdit by remember { mutableStateOf<IsoPipeId?>(null) }
    var gridEdit by remember { mutableStateOf(false) }
    var renameEdit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showBom by remember { mutableStateOf(false) }
    var showSheets by remember { mutableStateOf(false) }
    var showPad by remember { mutableStateOf(false) }

    fun haptic() {
        if (settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    BackHandler {
        when {
            head != null -> head = null
            pick != null -> pick = null
            else -> onClose()
        }
    }
    LaunchedEffect(sheet.id, canvasSize) {
        if (!fitted && canvasSize.width > 0) {
            viewport = isoFitViewport(sheet, canvasSize)
            fitted = true
        }
    }
    LaunchedEffect(viewport) {
        delay(VIEWPORT_SETTLE_MS)
        if (fitted) viewModel.updateIsometricViewport(viewport)
    }

    val zoom = viewport.zoom.coerceIn(ISO_MIN_ZOOM, ISO_MAX_ZOOM)
    val gridStep = IsoGrid.spec(sheet.gridStepMm, zoom, minSpacingPx = with(density) { 12.dp.toPx() }.toDouble()).minorStepMm
    val toleranceMm = with(density) { 22.dp.toPx() } / zoom
    val network = remember(sheet) { IsoNetwork(sheet) }
    val bom = remember(sheet) { IsoTakeoff.of(sheet) }

    fun position(id: IsoNodeId): Vec3? = network.position(id)

    /** Start of a run under the finger: a joint, a point on a run, or a grid node on paper. */
    fun startAt(point: P2): ChainHead? = when (val hit = IsoHitTester.hit(sheet, point, toleranceMm)) {
        is IsoHit.Node -> position(hit.id)?.let { ChainHead(RunStart.Node(hit.id), it) }
        is IsoHit.Pipe -> {
            val pipe = network.pipesById[hit.id]
            val a = pipe?.let { position(it.from) }
            val b = pipe?.let { position(it.to) }
            if (pipe == null || a == null || b == null) {
                null
            } else {
                val length = a.distanceTo(b)
                val along = (Math.round(hit.distanceAlongMm / gridStep) * gridStep).coerceIn(0.0, length)
                ChainHead(RunStart.OnPipe(hit.id, along), a + (b - a) / length * along)
            }
        }
        is IsoHit.Component -> null
        null -> IsoGrid.modelPointOnBase(projection, point, gridStep, sheet.workingLevelMm)
            ?.let { ChainHead(RunStart.Point(it), it) }
    }

    fun planTo(from: Vec3, point: P2): RunPlan? = IsoRunPlanner.plan(
        sheet,
        projection,
        from,
        point,
        RunPlanOptions(lengthStepMm = gridStep, allowOffsets = allowOffsets, joinToleranceMm = toleranceMm),
    )

    fun addRun(from: ChainHead, delta: Vec3) {
        val added = viewModel.isoEdit { addRun(from.start, delta, spec) } ?: return
        val end = viewModel.isometric.engine.sheet.nodes.firstOrNull { it.id == added.endNodeId } ?: return
        head = ChainHead(RunStart.Node(added.endNodeId), end.position.toVec())
        pick = IsoPick.Pipe(added.pipeId)
        haptic()
    }

    fun pickAt(point: P2): IsoPick? = when (val hit = IsoHitTester.hit(sheet, point, toleranceMm)) {
        is IsoHit.Node -> IsoPick.Node(hit.id)
        is IsoHit.Pipe -> IsoPick.Pipe(hit.id)
        is IsoHit.Component -> IsoPick.Component(hit.id)
        null -> null
    }

    fun placeComponent(point: P2) {
        when (val hit = IsoHitTester.hit(sheet, point, toleranceMm)) {
            is IsoHit.Pipe -> {
                val pipe = network.pipesById[hit.id] ?: return
                val length = network.length(pipe).coerceAtLeast(1.0)
                if (componentKind in INLINE_KINDS) {
                    viewModel.isoEdit { addComponent(componentKind, hit.id, hit.distanceAlongMm / length) }
                } else {
                    // A device goes on the run end nearest to the finger.
                    val node = if (hit.distanceAlongMm < length / 2) pipe.from else pipe.to
                    viewModel.isoEdit { addComponentAtNode(componentKind, node) }
                }
                haptic()
            }
            is IsoHit.Node -> {
                if (componentKind in TERMINAL_KINDS) {
                    viewModel.isoEdit { addComponentAtNode(componentKind, hit.id) }
                    haptic()
                }
            }
            is IsoHit.Component -> pick = IsoPick.Component(hit.id)
            null -> Unit
        }
    }

    val selection = when (val current = pick) {
        is IsoPick.Pipe -> IsoSelection(pipeIds = setOf(current.id))
        is IsoPick.Node -> IsoSelection(nodeIds = setOf(current.id))
        is IsoPick.Component -> IsoSelection(componentIds = setOf(current.id))
        null -> IsoSelection()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.x1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlanRulerIconButton(PlanRulerIcons.Back, text.back, onClose)
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { showSheets = true }
                            .padding(horizontal = Space.x2, vertical = Space.x1),
                    ) {
                        Text(text.isometric, style = MaterialTheme.typography.labelSmall)
                        Text(sheet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PlanRulerIconButton(
                        PlanRulerIcons.Undo,
                        text.undo,
                        { viewModel.isoEdit { undo() }; head = null; pick = null },
                        enabled = state.canUndo,
                    )
                    PlanRulerIconButton(
                        PlanRulerIcons.Redo,
                        text.redo,
                        { viewModel.isoEdit { redo() }; head = null; pick = null },
                        enabled = state.canRedo,
                    )
                    Box {
                        PlanRulerIconButton(PlanRulerIcons.More, text.more, { menu = true })
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(text.isoFromPlan) },
                                onClick = {
                                    menu = false
                                    if (viewModel.generateIsometricFromPlan(text.isoFromPlanName)) {
                                        pick = null
                                        head = null
                                        tool = IsoTool.SELECT
                                    }
                                },
                            )
                            HorizontalDivider()
                            IsoProjectionStyle.entries.forEach { style ->
                                DropdownMenuItem(
                                    text = { Text(checked(projectionName(style, text), sheet.projection == style)) },
                                    onClick = {
                                        menu = false
                                        viewModel.isoChange { setProjection(style) }
                                        viewport = isoFitViewport(viewModel.isometric.engine.sheet, canvasSize)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("${text.isoGridStep}: ${sheet.gridStepMm.roundToInt()}") },
                                onClick = { menu = false; gridEdit = true },
                            )
                            DropdownMenuItem(
                                text = { Text(checked(text.isoDimensions, sheet.showDimensions)) },
                                onClick = { viewModel.isoChange { setDisplay(dimensions = !sheet.showDimensions) } },
                            )
                            DropdownMenuItem(
                                text = { Text(checked(text.isoLevels, sheet.showElevations)) },
                                onClick = { viewModel.isoChange { setDisplay(elevations = !sheet.showElevations) } },
                            )
                            DropdownMenuItem(
                                text = { Text(checked(text.isoSystemCodes, sheet.showSystemCodes)) },
                                onClick = { viewModel.isoChange { setDisplay(systemCodes = !sheet.showSystemCodes) } },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(text.isoExportPdf) },
                                onClick = { menu = false; onExport(IsometricExportFormat.PDF) },
                            )
                            DropdownMenuItem(
                                text = { Text(text.isoExportSvg) },
                                onClick = { menu = false; onExport(IsometricExportFormat.SVG) },
                            )
                            DropdownMenuItem(
                                text = { Text(text.isoExportDxf) },
                                onClick = { menu = false; onExport(IsometricExportFormat.DXF) },
                            )
                            DropdownMenuItem(
                                text = { Text(text.isoExportCsv) },
                                onClick = { menu = false; onExport(IsometricExportFormat.CSV) },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(text.isoRename) },
                                onClick = { menu = false; renameEdit = true },
                            )
                            DropdownMenuItem(
                                text = { Text(text.isoDeleteSheet, color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                IsometricCanvas(
                    sheet = sheet,
                    viewport = viewport,
                    selection = selection,
                    preview = preview,
                    chainStart = if (tool == IsoTool.PIPE) head?.position else null,
                    labeler = labeler,
                    description = text.isometric,
                    onSize = { canvasSize = it },
                    onViewport = { viewport = it },
                    onTap = { point, double ->
                        when (tool) {
                            IsoTool.SELECT -> pick = pickAt(point)
                            IsoTool.VALVE -> placeComponent(point)
                            IsoTool.PIPE -> {
                                val from = head
                                when {
                                    double -> head = null
                                    from == null -> head = startAt(point)
                                    else -> planTo(from.position, point)?.let { plan -> addRun(from, plan.delta) }
                                }
                            }
                        }
                    },
                    onLongPress = { point ->
                        when (val picked = pickAt(point)) {
                            is IsoPick.Pipe -> {
                                pick = picked
                                lengthEdit = picked.id
                            }
                            else -> pick = picked
                        }
                    },
                    onDragStart = { point ->
                        if (tool == IsoTool.SELECT) {
                            val node = (IsoHitTester.hit(sheet, point, toleranceMm) as? IsoHit.Node)?.id
                            nodeDrag = node?.let { id -> position(id)?.let { IsoNodeDrag(id, it, point) } }
                            nodeTarget = nodeDrag?.origin
                            if (node != null) pick = IsoPick.Node(node)
                            nodeDrag != null
                        } else if (tool != IsoTool.PIPE) {
                            false
                        } else {
                            val current = head
                            val origin = if (current != null && projection.project(current.position).distanceTo(point) <= toleranceMm) {
                                current
                            } else {
                                startAt(point)
                            }
                            dragOrigin = origin
                            preview = null
                            origin != null
                        }
                    },
                    onDrag = { point ->
                        nodeDrag?.let { nodeTarget = it.target(point, projection, gridStep) }
                        dragOrigin?.let { origin -> preview = planTo(origin.position, point) }
                    },
                    onDragEnd = {
                        val moving = nodeDrag
                        val target = nodeTarget
                        nodeDrag = null
                        nodeTarget = null
                        if (moving != null && target != null && target != moving.origin) {
                            viewModel.isoEdit { moveNode(moving.nodeId, target) }
                        }
                        val origin = dragOrigin
                        val plan = preview
                        dragOrigin = null
                        preview = null
                        if (origin != null && plan != null) addRun(origin, plan.delta)
                    },
                    onDragCancel = {
                        nodeDrag = null
                        nodeTarget = null
                        dragOrigin = null
                        preview = null
                    },
                    movingSheet = nodeDrag?.let { moving -> nodeTarget?.let { moving.preview(sheet, it) } },
                )

                if (sheet.pipes.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = 4.dp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(Space.x3),
                    ) {
                        Text(text.isoEmptyHint, Modifier.padding(Space.x3), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    val currentHead = head
                    if (tool == IsoTool.PIPE && currentHead != null) {
                        IsoBar {
                            Text(text.isoChainHint, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton({ head = null }) { Text(text.isoDone) }
                        }
                    }
                    if (tool == IsoTool.VALVE) {
                        IsoBar { Text(text.isoValveHint, style = MaterialTheme.typography.bodySmall) }
                    }
                    when (val current = pick) {
                        is IsoPick.Pipe -> network.pipesById[current.id]?.let { pipe ->
                            IsoBar {
                                TextButton({ lengthEdit = pipe.id }) {
                                    Text("L ${labeler.length(network.length(pipe))} mm ✎", maxLines = 1)
                                }
                                PipeCatalogButton(text) { entry ->
                                    val chosen = pipe.spec.copy(nominalDiameter = entry.dn,
                                        material = "${entry.material} · Ø${entry.outsideDiameterMm} × ${entry.wallThicknessMm} mm")
                                    spec = chosen
                                    viewModel.isoEdit { setPipeSpec(listOf(pipe.id), chosen) }
                                }
                                TextButton({ viewModel.isoEdit { setPipeSpec(listOf(pipe.id), spec) } }) {
                                    Text(text.isoApplySpec, maxLines = 1)
                                }
                                TextButton({
                                    val half = network.length(pipe) / 2.0
                                    val distance = (Math.round(half / gridStep) * gridStep).takeIf { it > 1.0 && it < network.length(pipe) - 1.0 } ?: half
                                    viewModel.isoEdit { splitPipe(pipe.id, distance) }?.let { pick = IsoPick.Node(it) }
                                }) { Text(text.isoSplit) }
                                TextButton({
                                    viewModel.isoEdit { deletePipe(pipe.id) }
                                    pick = null
                                    head = null
                                }) { Text(text.delete, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        is IsoPick.Node -> position(current.id)?.let { at ->
                            if (tool == IsoTool.SELECT) IsoBar { Text(text.isoMoveNodeHint, style = MaterialTheme.typography.bodySmall) }
                            IsoBar {
                                Text(labeler.elevation(at.z), style = MaterialTheme.typography.labelLarge)
                                TextButton({
                                    head = ChainHead(RunStart.Node(current.id), at)
                                    tool = IsoTool.PIPE
                                    pick = null
                                }) { Text(text.isoContinue) }
                                TextButton({
                                    viewModel.isoEdit { deleteNode(current.id) }
                                    pick = null
                                    head = null
                                }) { Text(text.delete, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        is IsoPick.Component -> sheet.components.firstOrNull { it.id == current.id }?.let { component ->
                            IsoBar {
                                Text(text.isoComponent(component.kind), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                TextButton({
                                    viewModel.isoEdit { removeComponent(component.id) }
                                    pick = null
                                }) { Text(text.delete, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        null -> Unit
                    }
                }
            }

            Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showBom = true }
                            .padding(horizontal = Space.x3, vertical = Space.x1),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.x2),
                    ) {
                        Text(
                            bomSummary(bom.totalLengthMm, bom.elbowCount, bom.teeCount, text, settings.language),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(text.isoBom, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    when (tool) {
                        IsoTool.PIPE -> Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.x2),
                            horizontalArrangement = Arrangement.spacedBy(Space.x1),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IsoPipeSystem.entries.forEach { system ->
                                FilterChip(
                                    spec.system == system,
                                    { spec = spec.copy(system = system) },
                                    { Text(listOfNotNull(isoSystemCode(settings.language, system), text.isoSystemName(system)).joinToString(" ")) },
                                )
                            }
                            PipeCatalogButton(text) { entry ->
                                spec = spec.copy(nominalDiameter = entry.dn,
                                    material = "${entry.material} · Ø${entry.outsideDiameterMm} × ${entry.wallThicknessMm} mm")
                            }
                            PIPE_SIZES.forEach { size ->
                                FilterChip(
                                    spec.nominalDiameter == size,
                                    { spec = spec.copy(nominalDiameter = size, material = null) },
                                    { Text("DN$size") },
                                )
                            }
                            FilterChip(allowOffsets, { allowOffsets = !allowOffsets }, { Text(text.isoOffsets) })
                        }
                        IsoTool.VALVE -> Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.x2),
                            horizontalArrangement = Arrangement.spacedBy(Space.x1),
                        ) {
                            (INLINE_KINDS + TERMINAL_KINDS).forEach { kind ->
                                FilterChip(componentKind == kind, { componentKind = kind }, { Text(text.isoComponent(kind)) })
                            }
                        }
                        IsoTool.SELECT -> Unit
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.x2, vertical = Space.x1),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToolButton(PlanRulerIcons.Cursor, text.isoSelect, tool == IsoTool.SELECT, {
                            tool = IsoTool.SELECT
                            head = null
                        })
                        ToolButton(PlanRulerIcons.Polyline, text.isoPipe, tool == IsoTool.PIPE, { tool = IsoTool.PIPE })
                        ToolButton(PlanRulerIcons.Valve, text.isoValves, tool == IsoTool.VALVE, {
                            tool = IsoTool.VALVE
                            head = null
                        })
                        ToolButton(PlanRulerIcons.Ruler, text.isoByLengths, false, { showPad = true })
                        ToolButton(PlanRulerIcons.Target, text.isoFit, false, { viewport = isoFitViewport(sheet, canvasSize) })
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp))
    }

    lengthEdit?.let { id -> network.pipesById[id] }?.let { pipe ->
        IsoValueDialog(
            title = text.isoLengthMm,
            label = text.isoLengthMm,
            initial = labeler.length(network.length(pipe)),
            numeric = true,
            text = text,
            onDismiss = { lengthEdit = null },
            onApply = { value ->
                value.replace(',', '.').toDoubleOrNull()?.let { mm ->
                    if (viewModel.isoEdit { setPipeLength(pipe.id, mm) } != null) lengthEdit = null
                }
            },
        )
    }
    if (gridEdit) {
        IsoValueDialog(
            title = text.isoGridStep,
            label = text.isoGridStep,
            initial = sheet.gridStepMm.roundToInt().toString(),
            numeric = true,
            text = text,
            onDismiss = { gridEdit = false },
            onApply = { value ->
                value.replace(',', '.').toDoubleOrNull()?.let { mm ->
                    if (viewModel.isoEdit { setGridStep(mm) } != null) gridEdit = false
                }
            },
        )
    }
    if (renameEdit) {
        IsoValueDialog(
            title = text.isoRename,
            label = text.isoSheetName,
            initial = sheet.name,
            numeric = false,
            text = text,
            onDismiss = { renameEdit = false },
            onApply = { value -> if (viewModel.isoEdit { rename(value) } != null) renameEdit = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(text.isoDeleteSheet) },
            text = { Text(text.isoDeleteSheetBody) },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    viewModel.deleteIsometricSheet(sheet.id)
                    if (viewModel.isometric.active.value == null) onClose()
                }) { Text(text.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(text.cancel) } },
        )
    }
    if (showBom) {
        IsoBomSheet(
            lines = isoBomLines(bom, text, settings.language),
            text = text,
            onExportCsv = { showBom = false; onExport(IsometricExportFormat.CSV) },
            onDismiss = { showBom = false },
        )
    }
    if (showSheets) {
        IsoSheetsSheet(
            sheets = sheets,
            activeId = sheet.id,
            text = text,
            onSelect = { id ->
                viewModel.selectIsometricSheet(id)
                showSheets = false
            },
            onNew = {
                viewModel.newIsometricSheet(text.isometric)
                tool = IsoTool.PIPE
                showSheets = false
            },
            onFromPlan = {
                showSheets = false
                if (viewModel.generateIsometricFromPlan(text.isoFromPlanName)) tool = IsoTool.SELECT
            },
            onDismiss = { showSheets = false },
        )
    }
    if (showPad) {
        val selectedNode = (pick as? IsoPick.Node)?.id
        val from = head ?: selectedNode?.let { id -> position(id)?.let { ChainHead(RunStart.Node(id), it) } }
        IsoLengthPad(
            projection = projection,
            canAdd = from != null,
            text = text,
            onAdd = { axis, lengthMm -> from?.let { origin -> addRun(origin, axis.unit * lengthMm) } },
            onDismiss = { showPad = false },
        )
    }
}

private fun checked(label: String, on: Boolean) = if (on) "✓ $label" else label

private fun projectionName(style: IsoProjectionStyle, text: Wt) = when (style) {
    IsoProjectionStyle.ISOMETRIC -> text.isoProjectionIsometric
    IsoProjectionStyle.FRONTAL -> text.isoProjectionFrontal
}

private fun bomSummary(totalMm: Double, elbows: Int, tees: Int, text: Wt, language: AppLanguage): String {
    val metres = String.format(Locale.US, "%.2f", totalMm / 1_000.0).let { if (language == AppLanguage.ENGLISH) it else it.replace('.', ',') }
    return "${text.isoTotal}: $metres m · ${text.isoElbows} $elbows · ${text.isoTees} $tees"
}

@Composable
private fun IsoBar(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.x2, vertical = Space.x1),
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.x3, vertical = Space.x1),
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun IsoValueDialog(
    title: String,
    label: String,
    initial: String,
    numeric: Boolean,
    text: Wt,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    val valid = if (numeric) value.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true else value.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value,
                { next ->
                    value = if (numeric) next.filter { it.isDigit() || it == '.' || it == ',' }.take(9) else next.take(120)
                },
                label = { Text(label) },
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button({ onApply(value) }, enabled = valid) { Text(text.apply) } },
        dismissButton = { TextButton(onDismiss) { Text(text.cancel) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IsoBomSheet(
    lines: List<IsoBomLine>,
    text: Wt,
    onExportCsv: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.x6),
        ) {
            SheetHeader(text.isoBom)
            if (lines.isEmpty()) {
                Text(text.isoBomEmpty, Modifier.padding(horizontal = Space.x5, vertical = Space.x3))
            }
            lines.groupBy { it.section }.forEach { (section, group) ->
                Text(
                    section,
                    Modifier.padding(start = Space.x5, end = Space.x5, top = Space.x3, bottom = Space.x1),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                group.forEach { line ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.x5, vertical = Space.x1),
                        horizontalArrangement = Arrangement.spacedBy(Space.x3),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(line.item, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOf(line.system, line.size, line.material).filter { it.isNotBlank() && it != "—" }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("${line.quantity} ${line.unit}", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (lines.isNotEmpty()) {
                OutlinedButton(onExportCsv, Modifier.padding(horizontal = Space.x5, vertical = Space.x3)) {
                    Text(text.isoExportCsv)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IsoSheetsSheet(
    sheets: List<IsometricSheet>,
    activeId: com.planruler.model.IsometricSheetId,
    text: Wt,
    onSelect: (com.planruler.model.IsometricSheetId) -> Unit,
    onNew: () -> Unit,
    onFromPlan: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.x6),
        ) {
            SheetHeader(text.isoSheets)
            sheets.forEach { candidate ->
                val total = IsoTakeoff.of(candidate).totalLengthMm / 1_000.0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(candidate.id) }
                        .padding(horizontal = Space.x5, vertical = Space.x3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        checked(candidate.name, candidate.id == activeId),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(String.format(Locale.US, "%.1f m", total), style = MaterialTheme.typography.labelMedium)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = Space.x2))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.x5),
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                OutlinedButton(onNew) { Text(text.isoNewSheet) }
                Button(onFromPlan) { Text(text.isoFromPlan) }
            }
        }
    }
}

/** Typed input for site measurements: direction along an axis and the true length. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IsoLengthPad(
    projection: IsoProjection,
    canAdd: Boolean,
    text: Wt,
    onAdd: (IsoAxis, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var axis by remember { mutableStateOf(IsoAxis.EAST) }
    var value by remember { mutableStateOf("") }
    val length = value.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 1.0 && it <= MAX_TYPED_RUN_MM }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = Space.x5).padding(bottom = Space.x6),
            verticalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            SheetHeader(text.isoByLengths)
            Text(text.isoDirection, style = MaterialTheme.typography.labelLarge)
            listOf(
                listOf(IsoAxis.EAST, IsoAxis.WEST),
                listOf(IsoAxis.NORTH, IsoAxis.SOUTH),
                listOf(IsoAxis.UP, IsoAxis.DOWN),
            ).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    pair.forEach { candidate ->
                        FilterChip(
                            axis == candidate,
                            { axis = candidate },
                            { Text("${isoArrow(projection, candidate)}  ${isoAxisCode(candidate)}") },
                        )
                    }
                }
            }
            OutlinedTextField(
                value,
                { next -> value = next.filter { it.isDigit() || it == '.' || it == ',' }.take(9) },
                label = { Text(text.isoLengthMm) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!canAdd) {
                Text(text.isoStartFirst, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                {
                    length?.let {
                        onAdd(axis, it)
                        value = ""
                    }
                },
                enabled = canAdd && length != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(text.isoAddRun) }
        }
    }
}
