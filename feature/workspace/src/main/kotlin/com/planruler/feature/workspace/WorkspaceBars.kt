package com.planruler.feature.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planruler.designsystem.PlanRulerTestTags
import com.planruler.designsystem.component.IndicatorChip
import com.planruler.designsystem.component.IndicatorStatus
import com.planruler.designsystem.component.PlanRulerIconButton
import com.planruler.designsystem.component.ToolButton
import com.planruler.designsystem.icon.PlanRulerIcons
import com.planruler.designsystem.theme.LocalPlanRulerDimens
import com.planruler.designsystem.theme.Space
import com.planruler.model.SnapMode

/** Back, identity, save state, undo/redo, view mode and the project menu in 56 dp. */
@Composable
fun WorkspaceTopBar(
    title: String,
    pageLabel: String,
    saveBadge: SaveBadge,
    canUndo: Boolean,
    canRedo: Boolean,
    mode: WorkspaceMode,
    text: Wt,
    onBack: () -> Unit,
    onTitle: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleMode: () -> Unit,
    onMenu: () -> Unit,
    onSaveStatus: () -> Unit,
    modifier: Modifier = Modifier,
    compactActions: Boolean = false,
) {
    val dimens = LocalPlanRulerDimens.current
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().height(dimens.topBarHeight).padding(horizontal = Space.x1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlanRulerIconButton(
                PlanRulerIcons.Back,
                text.projects,
                onBack,
                Modifier.testTag(PlanRulerTestTags.WorkspaceBack),
            )
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onTitle)
                    .padding(horizontal = Space.x2),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pageLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SaveIndicator(saveBadge, text, onSaveStatus)
            PlanRulerIconButton(PlanRulerIcons.Undo, text.undo, onUndo, Modifier.testTag(PlanRulerTestTags.Undo), canUndo)
            PlanRulerIconButton(PlanRulerIcons.Redo, text.redo, onRedo, Modifier.testTag(PlanRulerTestTags.Redo), canRedo)
            if (!compactActions) {
                PlanRulerIconButton(
                    PlanRulerIcons.Eye,
                    if (mode == WorkspaceMode.EDIT) text.viewMode else text.editMode,
                    onToggleMode,
                    tint = if (mode == WorkspaceMode.VIEW) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            PlanRulerIconButton(PlanRulerIcons.More, text.projectMenu, onMenu)
        }
    }
}

@Composable
private fun SaveIndicator(badge: SaveBadge, text: Wt, onClick: () -> Unit) {
    val dimens = LocalPlanRulerDimens.current
    when (badge) {
        SaveBadge.SAVING -> Box(
            Modifier.size(dimens.minTouchTarget).testTag(PlanRulerTestTags.SaveStatus),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        SaveBadge.SAVED -> Box(
            Modifier.size(dimens.minTouchTarget).testTag(PlanRulerTestTags.SaveStatus),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PlanRulerIcons.Check,
                text.saved,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        SaveBadge.FAILED -> PlanRulerIconButton(
            PlanRulerIcons.Alert,
            text.saveFailed,
            onClick,
            Modifier.testTag(PlanRulerTestTags.SaveStatus),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

/** The four questions a measuring tool must always answer, in 40 dp. */
@Composable
fun IndicatorStrip(
    pageLabel: String,
    scaleLabel: String,
    scaleDescription: String,
    scaleStatus: IndicatorStatus,
    unitLabel: String,
    snapMode: SnapMode,
    text: Wt,
    onPages: () -> Unit,
    onScale: () -> Unit,
    onUnits: () -> Unit,
    onUnitsLong: () -> Unit,
    onSnap: () -> Unit,
    onSnapLong: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalPlanRulerDimens.current
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(dimens.indicatorStripHeight)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            IndicatorChip(
                PlanRulerIcons.Pages,
                pageLabel,
                IndicatorStatus.NEUTRAL,
                onPages,
                Modifier.testTag(PlanRulerTestTags.IndicatorPage),
                contentDescription = "${text.page} $pageLabel",
            )
            IndicatorChip(
                PlanRulerIcons.Calibrate,
                scaleLabel,
                scaleStatus,
                onScale,
                Modifier.testTag(PlanRulerTestTags.IndicatorScale),
                contentDescription = "${text.calibrate}: $scaleLabel. $scaleDescription",
            )
            IndicatorChip(
                PlanRulerIcons.Ruler,
                unitLabel,
                IndicatorStatus.NEUTRAL,
                onUnits,
                Modifier.testTag(PlanRulerTestTags.IndicatorUnits),
                onLongClick = onUnitsLong,
                contentDescription = "${text.units}: $unitLabel",
            )
            IndicatorChip(
                PlanRulerIcons.Snap,
                text.snap(snapMode),
                if (snapMode == SnapMode.OFF) IndicatorStatus.NEUTRAL else IndicatorStatus.OK,
                onSnap,
                Modifier.testTag(PlanRulerTestTags.IndicatorSnap),
                onLongClick = onSnapLong,
            )
        }
    }
}

val PrimaryTools = listOf(
    WorkspaceTool.NAVIGATE,
    WorkspaceTool.SELECT,
    WorkspaceTool.DISTANCE,
    WorkspaceTool.POLYLINE,
)

val SecondaryTools = listOf(
    WorkspaceTool.AREA,
    WorkspaceTool.COUNTER,
    WorkspaceTool.ANGLE,
    WorkspaceTool.ANNOTATION,
    WorkspaceTool.CALIBRATE,
)

fun toolIcon(tool: WorkspaceTool) = when (tool) {
    WorkspaceTool.NAVIGATE -> PlanRulerIcons.Hand
    WorkspaceTool.SELECT -> PlanRulerIcons.Cursor
    WorkspaceTool.DISTANCE -> PlanRulerIcons.Ruler
    WorkspaceTool.POLYLINE -> PlanRulerIcons.Polyline
    WorkspaceTool.AREA -> PlanRulerIcons.Area
    WorkspaceTool.ANGLE -> PlanRulerIcons.Angle
    WorkspaceTool.COUNTER -> PlanRulerIcons.Counter
    WorkspaceTool.ANNOTATION -> PlanRulerIcons.Note
    WorkspaceTool.CALIBRATE -> PlanRulerIcons.Calibrate
}

@Composable
fun WorkspaceToolBar(
    tool: WorkspaceTool,
    text: Wt,
    counterBadge: String?,
    onTool: (WorkspaceTool) -> Unit,
    onToolSettings: (WorkspaceTool) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
    val dimens = LocalPlanRulerDimens.current
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.x3, vertical = Space.x2),
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (PrimaryTools + if (tool in SecondaryTools) listOf(tool) else emptyList()).forEach { candidate ->
                ToolButton(
                    icon = toolIcon(candidate),
                    label = text.tool(candidate),
                    selected = tool == candidate,
                    onClick = { if (tool == candidate) onToolSettings(candidate) else onTool(candidate) },
                    modifier = Modifier.testTag(PlanRulerTestTags.tool(candidate.name)),
                    onLongClick = { onToolSettings(candidate) },
                    badge = if (candidate == WorkspaceTool.COUNTER) counterBadge else null,
                    showLabel = showLabels,
                    selectedState = text.selectedState,
                )
            }
            ToolButton(
                icon = PlanRulerIcons.More,
                label = text.more,
                selected = false,
                onClick = onMore,
                modifier = Modifier.testTag(PlanRulerTestTags.ToolMore),
                showLabel = showLabels,
            )
            Box(Modifier.size(dimens.minTouchTarget / 2))
        }
    }
}

/** Persistent field readout: the installer sees the last result and running material total. */
@Composable
fun FieldSummaryStrip(
    lastValue: String,
    materialTotal: String,
    materialName: String?,
    canCalculateAssembly: Boolean,
    text: Wt,
    onCalculateAssembly: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.fillMaxWidth().testTag(PlanRulerTestTags.FieldSummary),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.x3, vertical = Space.x2),
            horizontalArrangement = Arrangement.spacedBy(Space.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).testTag(PlanRulerTestTags.LastMeasurement)) {
                Text(text.lastMeasurement, style = MaterialTheme.typography.labelSmall)
                Text(lastValue.ifBlank { "—" }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
            Column(Modifier.weight(1f).testTag(PlanRulerTestTags.MaterialTotal)) {
                Text(
                    materialName?.let { "${text.materialTotal} · $it" } ?: text.materialTotal,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(materialTotal, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
            Button(
                onClick = onCalculateAssembly,
                enabled = canCalculateAssembly,
                modifier = Modifier.testTag(PlanRulerTestTags.CalculateAssembly),
            ) { Text(text.calculateAssembly, maxLines = 1) }
        }
    }
}

/** Only visible while a draft exists: cancel, undo one point, confirm, live value. */
@Composable
fun ConfirmBar(
    hint: String,
    confirmEnabled: Boolean,
    canRemovePoint: Boolean,
    text: Wt,
    onCancel: () -> Unit,
    onRemovePoint: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalPlanRulerDimens.current
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().height(dimens.confirmBarHeight).padding(horizontal = Space.x2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            TextButton(onCancel, Modifier.testTag(PlanRulerTestTags.CancelDraft)) { Text(text.cancel) }
            OutlinedButton(
                onRemovePoint,
                Modifier.testTag(PlanRulerTestTags.BackPoint),
                enabled = canRemovePoint,
            ) { Text(text.backPoint) }
            Text(
                hint,
                Modifier.weight(1f).padding(horizontal = Space.x2),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onConfirm,
                Modifier.widthIn(min = 120.dp).testTag(PlanRulerTestTags.ConfirmDraft),
                enabled = confirmEnabled,
            ) { Text(text.finish) }
        }
    }
}
