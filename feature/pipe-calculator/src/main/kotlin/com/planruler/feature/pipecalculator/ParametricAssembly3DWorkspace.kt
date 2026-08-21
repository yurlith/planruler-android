package com.planruler.feature.pipecalculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planruler.designsystem.localization.localizedUi
import com.planruler.fabrication3d.AssemblyIssue3D
import com.planruler.fabrication3d.AssemblyProfile3D
import com.planruler.fabrication3d.AssemblyProfileOverrides3D
import com.planruler.fabrication3d.ChainCommand3D
import com.planruler.fabrication3d.ChainEditorState3D
import com.planruler.fabrication3d.ChainPath3D
import com.planruler.fabrication3d.ChainPlan3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.ElbowRadiusMode3D
import com.planruler.fabrication3d.Fabrication3DEngine
import com.planruler.fabrication3d.FabricationPartKind
import com.planruler.fabrication3d.MeshQuality3D
import com.planruler.fabrication3d.ParametricAssembly3D
import com.planruler.fabrication3d.StraightPipeGeometry3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.catalog.CatalogPositionInfo3D
import com.planruler.fabrication3d.catalog.FlangeOption3D
import com.planruler.fabrication3d.catalog.elbowCatalogPosition
import com.planruler.fabrication3d.catalog.reducerCatalogOption
import com.planruler.fabrication3d.catalog.teeCatalogPosition
import com.planruler.fabrication3d.catalog.weldNeckFlangeOptions
import com.planruler.model.AppLanguage
import com.planruler.pipecalculator.FlangedOffsetAssemblyResult
import com.planruler.pipecalculator.PipeSupportMaterial
import com.planruler.pipecalculator.maximumSupportSpanM
import com.planruler.pipecalculator.theoreticalPipeMassKg
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun ParametricAssembly3DCard(
    result: FlangedOffsetAssemblyResult,
    language: AppLanguage,
    engine: Fabrication3DEngine,
    jobKey: String = "temporary",
    initialChainRecipe: String = "",
    onChainRecipeChanged: (String) -> Unit = {},
    installerRequest: InstallerRouteRequest? = null,
    showAdvancedControls: Boolean = true,
) {
    val text = remember(language) { Workspace3DText(language) }
    val messages = remember(language) { Fabrication3DMessages(language) }
    val viewModel: Assembly3DViewModel = viewModel(
        key = "assembly-3d:$jobKey",
        factory = Assembly3DViewModel.factory(engine),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val initialPlan = remember(jobKey, installerRequest?.taskType) {
        if (initialChainRecipe.isNotBlank()) decodeChainPlan(initialChainRecipe)
        else installerRequest?.manualTemplate ?: ChainPlan3D()
    }
    var savedPlan by rememberSaveable(jobKey, installerRequest?.taskType?.name, stateSaver = ChainPlanSaver) {
        mutableStateOf(initialPlan)
    }
    var radiusMode by rememberSaveable { mutableStateOf(ElbowRadiusMode3D.CATALOG.name) }
    var customRadius by rememberSaveable { mutableStateOf("") }
    var wallOverride by rememberSaveable { mutableStateOf("") }
    var weldGapOverride by rememberSaveable { mutableStateOf("") }
    var minCutOverride by rememberSaveable { mutableStateOf("") }
    var elbowLimit by rememberSaveable { mutableIntStateOf(0) }
    var flangeOutside by rememberSaveable { mutableStateOf("") }
    var flangeFaceToWeld by rememberSaveable { mutableStateOf("") }
    var flangeBoltCircle by rememberSaveable { mutableStateOf("") }
    var flangeBoltCount by rememberSaveable { mutableStateOf("") }
    // Set only by the catalog picker; typing the visible fields leaves these alone.
    var flangeThickness by rememberSaveable { mutableStateOf("") }
    var flangeHoleDiameter by rememberSaveable { mutableStateOf("") }
    var selectedFlangeId by rememberSaveable { mutableStateOf<String?>(null) }

    val flangeOptions = remember(result.input.dn) { weldNeckFlangeOptions(result.input.dn) }
    val elbowPosition = remember(result.input.dn) { elbowCatalogPosition(result.input.dn) }
    val teePosition = remember(result.input.dn) { teeCatalogPosition(result.input.dn) }

    LaunchedEffect(result, installerRequest?.taskType) { viewModel.bind(result, savedPlan) }

    val overrides = AssemblyProfileOverrides3D(
        elbowRadiusMode = runCatching { ElbowRadiusMode3D.valueOf(radiusMode) }
            .getOrDefault(ElbowRadiusMode3D.CATALOG),
        elbowCenterlineRadiusMm = customRadius.toOptionalNumber(),
        pipeWallThicknessMm = wallOverride.toOptionalNumber(),
        weldGapMm = weldGapOverride.toOptionalNumber(),
        minPipeLengthMm = minCutOverride.toOptionalNumber(),
        maxElbows = elbowLimit.takeIf { it > 0 },
        flangeFaceToWeldMm = flangeFaceToWeld.toOptionalNumber(),
        flangeOutsideDiameterMm = flangeOutside.toOptionalNumber(),
        flangeBoltCircleDiameterMm = flangeBoltCircle.toOptionalNumber(),
        flangeBoltHoleCount = flangeBoltCount.toOptionalNumber()?.toInt(),
        flangeThicknessMm = flangeThickness.toOptionalNumber(),
        flangeBoltHoleDiameterMm = flangeHoleDiameter.toOptionalNumber(),
    )
    LaunchedEffect(overrides) { viewModel.setOverrides(overrides) }

    val plan = state.editor?.plan
    LaunchedEffect(plan) {
        plan?.let {
            savedPlan = it
            onChainRecipeChanged(encodeChainPlan(it))
        }
    }
    val solvedPlan = state.solution?.plan
    LaunchedEffect(solvedPlan) {
        solvedPlan?.let {
            savedPlan = it
            onChainRecipeChanged(encodeChainPlan(it))
        }
    }
    LaunchedEffect(state.profile, installerRequest) {
        if (state.profile == null || installerRequest == null) return@LaunchedEffect
        when (installerRequest.calculationPath) {
            InstallerCalculationPath.AUTO_ROUTE -> {
                delay(300)
                viewModel.setMode(AssemblyWorkspaceMode3D.AUTO)
                viewModel.solve(
                    installerRequest.target,
                    installerRequest.endDirection,
                    installerRequest.minimumStraightMm,
                    installerRequest.preferredAngleDeg,
                )
            }
            InstallerCalculationPath.MANUAL_TEMPLATE ->
                viewModel.setMode(AssemblyWorkspaceMode3D.MANUAL)
        }
    }

    val dimensionTarget = installerRequest?.target?.takeIf { state.solution != null } ?: when (state.mode) {
        AssemblyWorkspaceMode3D.AUTO -> state.solveTarget.takeIf { state.solution != null }
        AssemblyWorkspaceMode3D.MANUAL -> null
        else -> Vec3(result.input.overallFaceToFaceMm, result.input.targetOffsetMm, 0.0)
    }

    Column(
        Modifier.fillMaxWidth().testTag(PipeCalculatorTags.Assembly3D),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElevatedCard(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text.workspaceTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (installerRequest != null && !showAdvancedControls) {
                            localizedUi(
                                language,
                                "Проверка формы и резов без инженерных координат",
                                "Shape and cut verification without engineering coordinates",
                            )
                        } else text.modeCaption(state.mode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (installerRequest != null && !showAdvancedControls) {
                    InstallerAutomaticSummary(
                        state = state,
                        request = installerRequest,
                        language = language,
                    )
                } else {
                    ModeSelector(
                        mode = state.mode,
                        onMode = viewModel::setMode,
                        label = text::mode,
                    )

                    WorkspaceSection(title = text.sectionTitle(state.mode)) {
                        when (state.mode) {
                        AssemblyWorkspaceMode3D.VERIFIED -> VerifiedAssemblySummary(
                            result = result,
                            text = text,
                            onEdit = { viewModel.editVerified(result) },
                        )

                        AssemblyWorkspaceMode3D.MANUAL -> ChainControls(
                            state = state,
                            text = text,
                            onCommand = viewModel::execute,
                            onUndo = viewModel::undo,
                            onRedo = viewModel::redo,
                            onBranch = viewModel::setActiveBranch,
                        )

                        AssemblyWorkspaceMode3D.AUTO -> SolverControls(
                            state = state,
                            result = result,
                            text = text,
                            onSolve = viewModel::solve,
                            onAdopt = viewModel::adoptSolution,
                        )

                        AssemblyWorkspaceMode3D.PARAMETERS -> EngineParameterControls(
                            profile = state.profile,
                            text = text,
                            radiusMode = radiusMode,
                            onRadiusMode = { radiusMode = it },
                            customRadius = customRadius,
                            onCustomRadius = { customRadius = it },
                            wallOverride = wallOverride,
                            onWallOverride = { wallOverride = it },
                            weldGapOverride = weldGapOverride,
                            onWeldGapOverride = { weldGapOverride = it },
                            minCutOverride = minCutOverride,
                            onMinCutOverride = { minCutOverride = it },
                            elbowLimit = elbowLimit,
                            onElbowLimit = { elbowLimit = it },
                            flangeOutside = flangeOutside,
                            onFlangeOutside = { flangeOutside = it },
                            flangeFaceToWeld = flangeFaceToWeld,
                            onFlangeFaceToWeld = { flangeFaceToWeld = it },
                            flangeBoltCircle = flangeBoltCircle,
                            onFlangeBoltCircle = { flangeBoltCircle = it },
                            flangeBoltCount = flangeBoltCount,
                            onFlangeBoltCount = { flangeBoltCount = it },
                            flangeOptions = flangeOptions,
                            selectedFlangeId = selectedFlangeId,
                            onFlangeOption = { option ->
                                selectedFlangeId = option?.catalogId
                                // A catalog pick simply fills the same fields the fitter could type.
                                flangeOutside = option?.outsideDiameterMm?.let(::workspaceNumber).orEmpty()
                                flangeFaceToWeld = option?.faceToWeldMm?.let(::workspaceNumber).orEmpty()
                                flangeBoltCircle = option?.boltCircleDiameterMm?.let(::workspaceNumber).orEmpty()
                                flangeBoltCount = option?.boltHoleCount?.toString().orEmpty()
                                flangeThickness = option?.thicknessMm?.let(::workspaceNumber).orEmpty()
                                flangeHoleDiameter = option?.boltHoleDiameterMm?.let(::workspaceNumber).orEmpty()
                            },
                            elbowPosition = elbowPosition,
                            teePosition = teePosition,
                            quality = state.quality,
                            onQuality = viewModel::setQuality,
                            maxElbowCeiling = engine.limits.maxElbows,
                        )
                        }
                    }
                }
                state.error?.let { InlineWorkspaceMessage(messages.of(it), error = true) }
                if (state.selfIntersections.isNotEmpty()) {
                    CollisionWarning(state.selfIntersections, text)
                }
            }
        }
        val assembly = state.shownAssembly
        if (assembly != null) {
            AssemblyStatsSummary(assembly, text)
            Assembly3DViewerCard(
                assembly = assembly,
                mesh = state.mesh,
                language = language,
                dimensionTarget = dimensionTarget,
                selectedPartId = state.selectedPartId,
                onSelectPart = viewModel::selectPart,
                editor = state.editor.takeIf { state.mode == AssemblyWorkspaceMode3D.MANUAL },
                canAddAtOpenEnd = state.editor?.canAppendTo(state.activeBranch) == true,
                onPreview = viewModel::preview,
                onCommitPreview = viewModel::commitPreview,
                onCancelPreview = viewModel::cancelPreview,
                onSceneAdd = {
                    viewModel.execute(
                        ChainCommand3D.Append(
                            step = ChainStep3D.Pipe(300.0),
                            parent = state.activeBranch,
                        ),
                    )
                },
                onSceneRemove = {
                    val editor = state.editor
                    val selected = state.selectedPartId
                    if (editor != null && selected != null) {
                        editor.pathForPart(selected)?.let { path ->
                            viewModel.execute(ChainCommand3D.RemoveAt(path))
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun InstallerAutomaticSummary(
    state: Assembly3DUiState,
    request: InstallerRouteRequest,
    language: AppLanguage,
) {
    fun t(russian: String, english: String) = localizedUi(language, russian, english)
    WorkspaceSection(
        title = if (request.calculationPath == InstallerCalculationPath.AUTO_ROUTE) {
            t("Автоматический расчёт", "Automatic calculation")
        } else {
            t("Готовая схема", "Prepared diagram")
        },
    ) {
        when {
            state.busy -> Text(t("Подбираем собираемый маршрут…", "Finding a fabricable route…"), fontWeight = FontWeight.Bold)
            request.calculationPath == InstallerCalculationPath.AUTO_ROUTE && state.solution != null -> {
                val solution = state.solution
                Text(
                    t("Маршрут готов", "Route ready") +
                        " · ${t("отводов", "elbows")}: ${solution.elbowCount}",
                    fontWeight = FontWeight.Black,
                )
                Text(
                    t("Ошибка замыкания", "Closure error") + ": ${workspacePrecise(solution.targetErrorMm)} mm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(solution.pipeCuts) { cut ->
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
                            Text(
                                "${cut.code}: ${workspaceNumber(cut.lengthMm)} mm",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            request.calculationPath == InstallerCalculationPath.MANUAL_TEMPLATE && state.editor != null -> {
                val assembly = state.editor.assembly
                Text(t("Шаблон узла готов", "Job template ready"), fontWeight = FontWeight.Black)
                Text(
                    t("Деталей", "Parts") + ": ${assembly.parts.size} · " +
                        t("прямых участков", "straight sections") +
                        ": ${assembly.parts.count { it.definition.kind == FabricationPartKind.PIPE }}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> Text(t("Подготавливаем расчёт…", "Preparing calculation…"))
        }
        if (abs(request.referenceCorrectionMm) > 1e-6) {
            Text(
                t("Учтена поправка точек замера", "Measurement-point correction applied") +
                    ": ${workspaceNumber(request.referenceCorrectionMm)} mm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** One visual level below the card: a titled container holding a mode's own controls. */
@Composable
private fun WorkspaceSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun ModeSelector(
    mode: AssemblyWorkspaceMode3D,
    onMode: (AssemblyWorkspaceMode3D) -> Unit,
    label: (AssemblyWorkspaceMode3D) -> String,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        AssemblyWorkspaceMode3D.entries.forEachIndexed { index, candidate ->
            SegmentedButton(
                selected = mode == candidate,
                onClick = { onMode(candidate) },
                shape = SegmentedButtonDefaults.itemShape(index, AssemblyWorkspaceMode3D.entries.size),
                label = { Text(label(candidate), maxLines = 1) },
                modifier = when (candidate) {
                    AssemblyWorkspaceMode3D.MANUAL -> Modifier.testTag(PipeCalculatorTags.Assembly3DManualMode)
                    AssemblyWorkspaceMode3D.AUTO -> Modifier.testTag(PipeCalculatorTags.Assembly3DAutoMode)
                    AssemblyWorkspaceMode3D.PARAMETERS ->
                        Modifier.testTag(PipeCalculatorTags.Assembly3DParametersMode)
                    else -> Modifier.testTag(PipeCalculatorTags.Assembly3DVerifiedMode)
                },
            )
        }
    }
}

@Composable
private fun VerifiedAssemblySummary(
    result: FlangedOffsetAssemblyResult,
    text: Workspace3DText,
    onEdit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryFact("DN", "${result.input.dn}", Modifier.weight(1f))
            SummaryFact("PN", "${result.input.pn}", Modifier.weight(1f))
            SummaryFact("∠", "${workspaceNumber(result.input.angleDeg)}°", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryFact("X", "${workspaceNumber(result.input.overallFaceToFaceMm)} mm", Modifier.weight(1f))
            SummaryFact("Y", "${workspaceNumber(result.input.targetOffsetMm)} mm", Modifier.weight(1f))
        }
        Text(
            text.verifiedIsTemplate,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.Assembly3DEditVerified),
        ) { Text(text.editVerified) }
    }
}

@Composable
private fun CollisionWarning(intersections: List<AssemblyIssue3D>, text: Workspace3DText) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DCollisionWarning),
    ) {
        Text(
            text.collisionWarning(intersections.size),
            Modifier.fillMaxWidth().padding(10.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * Straight-pipe steel mass (fittings excluded, see [Workspace3DText.straightPipeMassHint])
 * and an ASME B31.1 support-span note for the assembly's own nominal diameter — both reuse
 * the same formulas already shown on the standalone pipe-calculator pages, surfaced here
 * where the assembly's actual parts list lives instead of only on a separate calculator.
 */
@Composable
private fun AssemblyStatsSummary(assembly: ParametricAssembly3D, text: Workspace3DText) {
    val straightPipeMassKg = remember(assembly) {
        assembly.parts.sumOf { part ->
            val geometry = part.definition.geometry
            if (part.definition.kind == FabricationPartKind.PIPE && geometry is StraightPipeGeometry3D) {
                theoreticalPipeMassKg(geometry.outsideDiameterMm, geometry.wallThicknessMm, geometry.lengthMm / 1_000.0)
            } else {
                0.0
            }
        }
    }
    if (straightPipeMassKg <= 0.0) return
    val supportSpanM = remember(assembly.metadata.nominalDiameter) {
        maximumSupportSpanM(assembly.metadata.nominalDiameter.toDouble(), PipeSupportMaterial.STEEL).maximumSpanM
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.Assembly3DMassSummary),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryFact(text.straightPipeMass, "${workspaceNumber(straightPipeMassKg)} kg")
            Text(
                text.straightPipeMassHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text.supportSpan(workspaceNumber(supportSpanM)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text.supportSpanHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryFact(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun ChainControls(
    state: Assembly3DUiState,
    text: Workspace3DText,
    onCommand: (ChainCommand3D) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBranch: (ChainPath3D) -> Unit,
) {
    val editor = state.editor
    val profile = state.profile
    if (editor == null || profile == null) {
        InlineWorkspaceMessage(text.engineUnavailable, error = true)
        return
    }
    var pipeLength by rememberSaveable { mutableStateOf("300") }
    var roll by rememberSaveable { mutableStateOf("0") }
    var angle by rememberSaveable { mutableStateOf("45") }
    var bendSign by rememberSaveable { mutableIntStateOf(1) }
    var startX by rememberSaveable { mutableStateOf("0") }
    var startY by rememberSaveable { mutableStateOf("0") }
    var startZ by rememberSaveable { mutableStateOf("0") }
    var reducerSmallDn by rememberSaveable { mutableStateOf("") }
    var reducerSmallOd by rememberSaveable { mutableStateOf("") }
    var reducerSmallWall by rememberSaveable { mutableStateOf("") }
    var reducerLength by rememberSaveable { mutableStateOf("") }
    val target = state.activeBranch.takeIf { editor.plan.chainAt(it) != null } ?: ChainPath3D.ROOT
    // The catalog only publishes one reduction per large diameter, so this suggests the
    // step down from where the profile starts; a second reduction needs typed dimensions.
    val reducerOption = remember(profile.nominalDiameter) { reducerCatalogOption(profile.nominalDiameter) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "${text.elbows}: ${editor.elbowCount}/${profile.rules.maxElbows} · " +
                "${text.pipes}: ${editor.pipeCount} · ${text.tees}: ${editor.teeCount} · " +
                "${text.reducers}: ${editor.reducerCount}",
            fontWeight = FontWeight.Bold,
        )
        Text(text.manualHint, style = MaterialTheme.typography.bodySmall)

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DBranch),
        ) {
            item {
                FilterChip(
                    selected = target == ChainPath3D.ROOT,
                    onClick = { onBranch(ChainPath3D.ROOT) },
                    label = { Text(text.mainRun) },
                )
            }
            items(editor.branchPaths()) { path ->
                FilterChip(
                    selected = target == path,
                    onClick = { onBranch(path) },
                    label = { Text(editor.partIdAt(path) ?: path.toString()) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                value = pipeLength,
                onValue = { pipeLength = it },
                label = text.pipeLength,
                suffix = "mm",
                modifier = Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DPipeLength),
            )
            NumberField(roll, { roll = it }, text.roll, "°", Modifier.weight(1f))
            NumberField(
                value = angle,
                onValue = { angle = it },
                label = text.angle,
                suffix = "°",
                modifier = Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DAngleField),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(profile.rules.allowedElbowAnglesDeg) { value ->
                FilterChip(
                    selected = abs((angle.toDoubleOrNull() ?: -1.0) - value) < 1e-9,
                    onClick = { angle = workspaceNumber(value) },
                    label = { Text("${workspaceNumber(value)}°") },
                )
            }
            item {
                FilterChip(
                    selected = bendSign > 0,
                    onClick = { bendSign = 1 },
                    label = { Text(text.positiveBend) },
                )
            }
            item {
                FilterChip(
                    selected = bendSign < 0,
                    onClick = { bendSign = -1 },
                    label = { Text(text.negativeBend) },
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DActionsRow),
        ) {
            item {
                Button(
                    onClick = {
                        pipeLength.toNumberOrNull()?.let {
                            onCommand(ChainCommand3D.Append(ChainStep3D.Pipe(it), target))
                        }
                    },
                    modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DAddPipe),
                ) { Text(text.addPipe) }
            }
            item {
                Button(
                    onClick = {
                        angle.toNumberOrNull()?.let {
                            onCommand(
                                ChainCommand3D.Append(
                                    ChainStep3D.Elbow(it * bendSign, roll.toNumber(0.0)),
                                    target,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DAddElbow),
                ) { Text(text.addElbow) }
            }
            item {
                Button(
                    enabled = profile.tee != null,
                    onClick = {
                        onCommand(ChainCommand3D.Append(ChainStep3D.Tee(roll.toNumber(0.0)), target))
                    },
                    modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DAddTee),
                ) { Text(text.addTee) }
            }
            item {
                Button(
                    onClick = {
                        val smallDn = reducerSmallDn.toIntOrNull()
                        val smallOd = reducerSmallOd.toNumberOrNull()
                        val smallWall = reducerSmallWall.toNumberOrNull()
                        val length = reducerLength.toNumberOrNull()
                        if (smallDn != null && smallOd != null && smallWall != null && length != null) {
                            onCommand(
                                ChainCommand3D.Append(
                                    ChainStep3D.Reducer(
                                        lengthMm = length,
                                        smallNominalDiameter = smallDn,
                                        smallOutsideDiameterMm = smallOd,
                                        smallWallThicknessMm = smallWall,
                                        rollDeg = roll.toNumber(0.0),
                                    ),
                                    target,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DAddReducer),
                ) { Text(text.addReducer) }
            }
            item {
                OutlinedButton(
                    onClick = {
                        onCommand(ChainCommand3D.Append(ChainStep3D.Cap(roll.toNumber(0.0)), target))
                    },
                    modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DAddCap),
                ) { Text(text.addCap) }
            }
            item {
                OutlinedButton(
                    onClick = {
                        onCommand(ChainCommand3D.Append(ChainStep3D.Flange(roll.toNumber(0.0)), target))
                    },
                ) { Text(text.finishFlange) }
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.Assembly3DReducerFields),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text.reducerHint, style = MaterialTheme.typography.bodySmall)
                reducerOption?.let { option ->
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        item {
                            FilterChip(
                                selected = reducerSmallDn == option.smallNominalDiameter.toString(),
                                onClick = {
                                    reducerSmallDn = option.smallNominalDiameter.toString()
                                    reducerSmallOd = workspaceNumber(option.smallOutsideDiameterMm)
                                    reducerSmallWall = workspaceNumber(option.smallWallThicknessMm)
                                    reducerLength = workspaceNumber(option.lengthMm)
                                },
                                label = { Text("${text.catalogReducer} DN ${option.smallNominalDiameter}") },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = reducerSmallDn,
                        onValue = { reducerSmallDn = it },
                        label = text.reducerSmallDn,
                        suffix = "",
                        modifier = Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DReducerSmallDn),
                    )
                    NumberField(reducerSmallOd, { reducerSmallOd = it }, text.reducerSmallOd, "mm", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        reducerSmallWall,
                        { reducerSmallWall = it },
                        text.reducerSmallWall,
                        "mm",
                        Modifier.weight(1f),
                    )
                    NumberField(reducerLength, { reducerLength = it }, text.reducerLength, "mm", Modifier.weight(1f))
                }
            }
        }

        StepInspector(editor = editor, text = text, onCommand = onCommand)

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.Assembly3DStartFrame),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text.startFrame, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    NumberField(startX, { startX = it }, "X", "mm", Modifier.weight(1f))
                    NumberField(startY, { startY = it }, "Y", "mm", Modifier.weight(1f))
                    NumberField(startZ, { startZ = it }, "Z", "mm", Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = {
                        onCommand(
                            ChainCommand3D.MoveStart(
                                editor.plan.start.copy(
                                    position = Vec3(
                                        startX.toNumber(0.0),
                                        startY.toNumber(0.0),
                                        startZ.toNumber(0.0),
                                    ),
                                ),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(text.applyStart) }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = editor.canUndo,
                onClick = onUndo,
                modifier = Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DUndo),
            ) { Text(text.undo) }
            OutlinedButton(
                enabled = editor.canRedo,
                onClick = onRedo,
                modifier = Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DRedo),
            ) { Text(text.redo) }
            OutlinedButton(
                onClick = { onCommand(ChainCommand3D.Clear) },
                modifier = Modifier.weight(1f),
            ) { Text(text.reset) }
        }
    }
}

@Composable
private fun StepInspector(
    editor: ChainEditorState3D,
    text: Workspace3DText,
    onCommand: (ChainCommand3D) -> Unit,
) {
    val partId = editor.selectedPartId ?: return
    val path = editor.pathForPart(partId) ?: return
    val step = editor.plan.stepAt(path) ?: return
    var value by rememberSaveable(partId) {
        mutableStateOf(
            when (step) {
                is ChainStep3D.Pipe -> workspaceNumber(step.lengthMm)
                is ChainStep3D.Elbow -> workspaceNumber(step.angleDeg)
                is ChainStep3D.Reducer -> workspaceNumber(step.lengthMm)
                is ChainStep3D.Tee -> workspaceNumber(step.rollDeg)
                is ChainStep3D.Flange -> workspaceNumber(step.rollDeg)
                is ChainStep3D.Cap -> workspaceNumber(step.rollDeg)
            },
        )
    }
    var rollValue by rememberSaveable(partId) {
        mutableStateOf(
            when (step) {
                is ChainStep3D.Elbow -> workspaceNumber(step.rollDeg)
                is ChainStep3D.Reducer -> workspaceNumber(step.rollDeg)
                is ChainStep3D.Tee -> workspaceNumber(step.rollDeg)
                is ChainStep3D.Flange -> workspaceNumber(step.rollDeg)
                is ChainStep3D.Cap -> workspaceNumber(step.rollDeg)
                is ChainStep3D.Pipe -> "0"
            },
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.Assembly3DInspector),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${text.editing}: $partId", fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    value = value,
                    onValue = { value = it },
                    label = when (step) {
                        is ChainStep3D.Pipe -> text.pipeLength
                        is ChainStep3D.Elbow -> text.angle
                        is ChainStep3D.Reducer -> text.reducerLength
                        else -> text.roll
                    },
                    suffix = if (step is ChainStep3D.Pipe || step is ChainStep3D.Reducer) "mm" else "°",
                    modifier = Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DInspectorValue),
                )
                if (step is ChainStep3D.Elbow || step is ChainStep3D.Reducer) {
                    NumberField(rollValue, { rollValue = it }, text.roll, "°", Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val primary = value.toNumberOrNull() ?: return@Button
                        val replacement = when (step) {
                            is ChainStep3D.Pipe -> ChainStep3D.Pipe(primary)
                            is ChainStep3D.Elbow -> ChainStep3D.Elbow(primary, rollValue.toNumber(0.0))
                            is ChainStep3D.Reducer -> step.copy(lengthMm = primary, rollDeg = rollValue.toNumber(0.0))
                            is ChainStep3D.Tee -> ChainStep3D.Tee(primary, step.branch)
                            is ChainStep3D.Flange -> ChainStep3D.Flange(primary)
                            is ChainStep3D.Cap -> ChainStep3D.Cap(primary)
                        }
                        onCommand(ChainCommand3D.Replace(path, replacement))
                    },
                    modifier = Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DInspectorApply),
                ) { Text(text.applyChange) }
                OutlinedButton(
                    onClick = { onCommand(ChainCommand3D.RemoveAt(path)) },
                    modifier = Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DInspectorRemove),
                ) { Text(text.removeStep) }
            }
        }
    }
}

@Composable
private fun SolverControls(
    state: Assembly3DUiState,
    result: FlangedOffsetAssemblyResult,
    text: Workspace3DText,
    onSolve: (Vec3, Vec3, Double, Double?) -> Unit,
    onAdopt: () -> Unit,
) {
    var targetX by rememberSaveable(result.input.dn) {
        mutableStateOf(workspaceNumber(result.input.overallFaceToFaceMm))
    }
    var targetY by rememberSaveable(result.input.dn) {
        mutableStateOf(workspaceNumber(result.input.targetOffsetMm))
    }
    var targetZ by rememberSaveable(result.input.dn) { mutableStateOf("300") }
    var directionX by rememberSaveable { mutableStateOf("1") }
    var directionY by rememberSaveable { mutableStateOf("0") }
    var directionZ by rememberSaveable { mutableStateOf("0") }
    var minimumStraight by rememberSaveable { mutableStateOf("50") }
    var preferredAngle by rememberSaveable { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text.autoHint, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NumberField(
                targetX,
                { targetX = it },
                "X",
                "mm",
                Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DTargetX),
            )
            NumberField(
                targetY,
                { targetY = it },
                "Y",
                "mm",
                Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DTargetY),
            )
            NumberField(
                targetZ,
                { targetZ = it },
                "Z",
                "mm",
                Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DTargetZ),
            )
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.Assembly3DTargetDirection),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text.targetDirection, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    NumberField(
                        directionX,
                        { directionX = it },
                        "dX",
                        "",
                        Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DDirectionX),
                    )
                    NumberField(
                        directionY,
                        { directionY = it },
                        "dY",
                        "",
                        Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DDirectionY),
                    )
                    NumberField(
                        directionZ,
                        { directionZ = it },
                        "dZ",
                        "",
                        Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DDirectionZ),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(minimumStraight, { minimumStraight = it }, text.minimumStraight, "mm", Modifier.weight(1f))
            NumberField(preferredAngle, { preferredAngle = it }, text.autoAngle, "°", Modifier.weight(1f))
        }
        Button(
            enabled = !state.busy,
            onClick = {
                val target = Vec3(
                    targetX.toNumberOrNull() ?: return@Button,
                    targetY.toNumberOrNull() ?: return@Button,
                    targetZ.toNumberOrNull() ?: return@Button,
                )
                val direction = Vec3(
                    directionX.toNumber(1.0),
                    directionY.toNumber(0.0),
                    directionZ.toNumber(0.0),
                )
                onSolve(target, direction, minimumStraight.toNumber(50.0), preferredAngle.toOptionalNumber())
            },
            modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.Assembly3DSolve),
        ) { Text(if (state.busy) text.solving else text.solve) }

        state.solution?.let { SolutionSummary(it, text, onAdopt) }
    }
}

@Composable
private fun SolutionSummary(
    solution: com.planruler.fabrication3d.RouteSolution3D,
    text: Workspace3DText,
    onAdopt: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.Assembly3DSolverResult),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(text.routeReady, fontWeight = FontWeight.Black)
            Text(
                "${text.topology}: ${text.topologyName(solution.topology)} · " +
                    "${text.elbows}: ${solution.elbowCount} · " +
                    "${text.error}: ${workspacePrecise(solution.targetErrorMm)} mm",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(solution.pipeCuts) { cut ->
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
                        Text(
                            "${cut.code} ${workspaceNumber(cut.lengthMm)} mm",
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = onAdopt,
                modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.Assembly3DSendToManual),
            ) { Text(text.sendToManual) }
        }
    }
}

@Composable
private fun EngineParameterControls(
    profile: AssemblyProfile3D?,
    text: Workspace3DText,
    radiusMode: String,
    onRadiusMode: (String) -> Unit,
    customRadius: String,
    onCustomRadius: (String) -> Unit,
    wallOverride: String,
    onWallOverride: (String) -> Unit,
    weldGapOverride: String,
    onWeldGapOverride: (String) -> Unit,
    minCutOverride: String,
    onMinCutOverride: (String) -> Unit,
    elbowLimit: Int,
    onElbowLimit: (Int) -> Unit,
    flangeOutside: String,
    onFlangeOutside: (String) -> Unit,
    flangeFaceToWeld: String,
    onFlangeFaceToWeld: (String) -> Unit,
    flangeBoltCircle: String,
    onFlangeBoltCircle: (String) -> Unit,
    flangeBoltCount: String,
    onFlangeBoltCount: (String) -> Unit,
    flangeOptions: List<FlangeOption3D>,
    selectedFlangeId: String?,
    onFlangeOption: (FlangeOption3D?) -> Unit,
    elbowPosition: CatalogPositionInfo3D?,
    teePosition: CatalogPositionInfo3D?,
    quality: MeshQuality3D,
    onQuality: (MeshQuality3D) -> Unit,
    maxElbowCeiling: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text.parametersHint, style = MaterialTheme.typography.bodySmall)
        Text(text.elbowRadius, fontWeight = FontWeight.Bold)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DRadiusMode),
        ) {
            items(ElbowRadiusMode3D.entries) { candidate ->
                FilterChip(
                    selected = radiusMode == candidate.name,
                    onClick = { onRadiusMode(candidate.name) },
                    label = { Text(text.radiusMode(candidate)) },
                )
            }
        }
        Text(
            "${text.effectiveRadius}: ${workspaceNumber(profile?.elbow?.centerlineRadiusMm ?: 0.0)} mm",
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DEffectiveRadius),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(customRadius, onCustomRadius, text.customRadius, "mm", Modifier.weight(1f))
            NumberField(wallOverride, onWallOverride, text.wallThickness, "mm", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                value = weldGapOverride,
                onValue = onWeldGapOverride,
                label = text.weldGap,
                suffix = "mm",
                modifier = Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DWeldGap),
            )
            NumberField(minCutOverride, onMinCutOverride, text.minimumCut, "mm", Modifier.weight(1f))
        }
        Text("${text.elbowLimit}: ${if (elbowLimit > 0) elbowLimit else profile?.rules?.maxElbows ?: 0}")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DElbowLimit),
        ) {
            items(listOf(0, 2, 5, 8, 12, maxElbowCeiling)) { candidate ->
                FilterChip(
                    selected = elbowLimit == candidate,
                    onClick = { onElbowLimit(candidate) },
                    label = { Text(if (candidate == 0) text.catalogDefault else "$candidate") },
                )
            }
        }
        Text(text.ownFlange, fontWeight = FontWeight.Bold)
        Text(
            text.flangeCatalogHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DFlangeCatalog),
        ) {
            item {
                FilterChip(
                    selected = selectedFlangeId == null,
                    onClick = { onFlangeOption(null) },
                    // Distinct from the elbow-limit "Catalog" chip that sits in the same section.
                    label = { Text(text.catalogFlange) },
                )
            }
            items(flangeOptions) { option ->
                FilterChip(
                    selected = selectedFlangeId == option.catalogId,
                    onClick = { onFlangeOption(option) },
                    label = { Text(option.shortLabel) },
                )
            }
        }
        elbowPosition?.let { position ->
            CatalogPositionLine(text.elbows, position.summary)
        }
        teePosition?.let { position ->
            CatalogPositionLine(text.tees, position.summary)
        }
        Text(
            "${text.catalogValue}: Ø ${workspaceNumber(profile?.flange?.outsideDiameterMm ?: 0.0)} · " +
                "h ${workspaceNumber(profile?.flange?.faceToWeldMm ?: 0.0)} · " +
                "${profile?.flange?.boltHoleCount ?: 0} × " +
                "Ø ${workspaceNumber(profile?.flange?.boltHoleDiameterMm ?: 0.0)} mm",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DFlangeSummary),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                value = flangeOutside,
                onValue = onFlangeOutside,
                label = text.flangeOutside,
                suffix = "mm",
                modifier = Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DFlangeOutside),
            )
            NumberField(flangeFaceToWeld, onFlangeFaceToWeld, text.flangeFaceToWeld, "mm", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(flangeBoltCircle, onFlangeBoltCircle, text.flangeBoltCircle, "mm", Modifier.weight(1f))
            NumberField(
                value = flangeBoltCount,
                onValue = onFlangeBoltCount,
                label = text.flangeBoltCount,
                suffix = "",
                modifier = Modifier.weight(1f).testTag(PipeCalculatorTags.Assembly3DFlangeBoltCount),
            )
        }
        Text(text.meshQuality, fontWeight = FontWeight.Bold)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DQuality),
        ) {
            items(MeshQuality3D.entries) { candidate ->
                FilterChip(
                    selected = quality == candidate,
                    onClick = { onQuality(candidate) },
                    label = { Text(text.quality(candidate)) },
                )
            }
        }
    }
}

@Composable
private fun CatalogPositionLine(label: String, summary: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            summary,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    suffix: String,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        suffix = { if (suffix.isNotEmpty()) Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun InlineWorkspaceMessage(message: String, error: Boolean) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.testTag(PipeCalculatorTags.Assembly3DMessage),
    ) {
        Text(
            message,
            Modifier.fillMaxWidth().padding(10.dp),
            color = if (error) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        )
    }
}

private fun String.toNumberOrNull(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

private fun String.toNumber(fallback: Double): Double = toNumberOrNull() ?: fallback

private fun String.toOptionalNumber(): Double? =
    trim().takeIf { it.isNotEmpty() }?.toNumberOrNull()

private fun workspaceNumber(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun workspacePrecise(value: Double): String = String.format(Locale.US, "%.4f", abs(value))

private class Workspace3DText(private val language: AppLanguage) {
    private fun t(pl: String, en: String, de: String, fr: String, it: String, ru: String) = when (language) {
        AppLanguage.POLISH -> pl
        AppLanguage.ENGLISH -> en
        AppLanguage.GERMAN -> de
        AppLanguage.FRENCH -> fr
        AppLanguage.ITALIAN -> it
        AppLanguage.RUSSIAN -> ru
    }

    val workspaceTitle get() = t("Warsztat trasy 3D", "3D route workshop", "3D-Routenwerkstatt", "Atelier de tracé 3D", "Officina percorso 3D", "Мастерская 3D-трассы")
    val workspaceSubtitle get() = t("Model sprawdzony, ręczna budowa, solver i parametry korzystają z jednego silnika.", "Verified model, manual building, solver and parameters share one engine.", "Geprüftes Modell, Handaufbau, Solver und Parameter nutzen dieselbe Engine.", "Le modèle vérifié, le montage manuel, le solveur et les paramètres partagent le même moteur.", "Modello verificato, costruzione manuale, solver e parametri usano lo stesso motore.", "Проверенная модель, ручная сборка, решатель и параметры используют один движок.")
    val verifiedSummary get() = t("Sprawdzone obliczenie", "Verified calculation", "Geprüfte Berechnung", "Calcul vérifié", "Calcolo verificato", "Проверенный расчёт")
    val elbows get() = t("Kolana", "Elbows", "Bögen", "Coudes", "Curve", "Отводы")
    val pipes get() = t("Rury", "Pipes", "Rohre", "Tubes", "Tubi", "Трубы")
    val tees get() = t("Trójniki", "Tees", "T-Stücke", "Tés", "Tee", "Тройники")
    val mainRun get() = t("Magistrala", "Main run", "Hauptstrang", "Ligne principale", "Linea principale", "Магистраль")
    val manualHint get() = t("Dodaj element, dotknij części w modelu i zmień jej parametry.", "Add an element, tap a part in the model and change its parameters.", "Element hinzufügen, Bauteil antippen und Parameter ändern.", "Ajoutez un élément, touchez une pièce et modifiez ses paramètres.", "Aggiungi un elemento, tocca un componente e cambia i suoi parametri.", "Добавьте элемент, нажмите на деталь в модели и измените её параметры.")
    val pipeLength get() = t("Długość rury", "Pipe cut length", "Rohrlänge", "Longueur du tube", "Lunghezza tubo", "Длина трубы")
    val roll get() = t("Obrót", "Roll", "Rollwinkel", "Roulis", "Rollio", "Крен")
    val angle get() = t("Kąt", "Angle", "Winkel", "Angle", "Angolo", "Угол")
    val positiveBend get() = t("+ kierunek", "+ bend", "+ Biegung", "+ courbe", "+ curva", "+ изгиб")
    val negativeBend get() = t("− kierunek", "− bend", "− Biegung", "− courbe", "− curva", "− изгиб")
    val addPipe get() = t("+ rura", "+ Pipe", "+ Rohr", "+ Tube", "+ Tubo", "+ труба")
    val addElbow get() = t("+ kolano", "+ Elbow", "+ Bogen", "+ Coude", "+ Curva", "+ отвод")
    val addTee get() = t("+ trójnik", "+ Tee", "+ T-Stück", "+ Té", "+ Tee", "+ тройник")
    val addReducer get() = t("+ redukcja", "+ Reducer", "+ Reduzierung", "+ Réducteur", "+ Riduzione", "+ переход")
    val addCap get() = t("+ zaślepka", "+ Cap", "+ Kappe", "+ Bouchon", "+ Tappo", "+ заглушка")
    val reducers get() = t("Redukcje", "Reducers", "Reduzierungen", "Réducteurs", "Riduzioni", "Переходы")
    val reducerHint get() = t(
        "Redukcja zmniejsza średnicę dla wszystkich elementów za nią — kolano lub trójnik za redukcją wymaga oryginalnej średnicy katalogowej.",
        "A reducer necks the diameter down for everything after it — an elbow or tee past a reducer needs the original catalog diameter.",
        "Eine Reduzierung verjüngt den Durchmesser für alles danach — ein Bogen oder T-Stück danach braucht den ursprünglichen Katalogdurchmesser.",
        "Un réducteur rétrécit le diamètre pour tout ce qui suit — un coude ou un té après nécessite le diamètre catalogue d'origine.",
        "Una riduzione restringe il diametro per tutto ciò che segue — una curva o un tee dopo richiede il diametro di catalogo originale.",
        "Переход сужает диаметр для всего, что идёт дальше — отвод или тройник после перехода требуют исходного диаметра каталога.",
    )
    val catalogReducer get() = t("Redukcja z katalogu", "Catalog reducer", "Reduzierung aus Katalog", "Réducteur du catalogue", "Riduzione a catalogo", "Переход каталога")
    val reducerSmallDn get() = t("Mniejsza DN", "Small DN", "Kleinere DN", "DN réduit", "DN piccolo", "Меньший DN")
    val reducerSmallOd get() = t("Mniejsza średnica", "Small diameter", "Kleinerer Durchmesser", "Petit diamètre", "Diametro piccolo", "Меньший диаметр")
    val reducerSmallWall get() = t("Mniejsza ścianka", "Small wall", "Kleinere Wandstärke", "Petite épaisseur", "Parete piccola", "Меньшая стенка")
    val reducerLength get() = t("Długość redukcji", "Reducer length", "Reduzierungslänge", "Longueur du réducteur", "Lunghezza riduzione", "Длина перехода")
    val finishFlange get() = t("Zamknij kołnierzem", "Finish flange", "Mit Flansch schließen", "Terminer par bride", "Chiudi con flangia", "Завершить фланцем")
    val undo get() = t("Cofnij", "Undo", "Rückgängig", "Annuler", "Annulla", "Отменить")
    val redo get() = t("Ponów", "Redo", "Wiederholen", "Rétablir", "Ripeti", "Повторить")
    val reset get() = t("Reset", "Reset", "Zurücksetzen", "Réinitialiser", "Ripristina", "Сброс")
    val editing get() = t("Edycja", "Editing", "Bearbeiten", "Édition", "Modifica", "Правка")
    val applyChange get() = t("Zastosuj", "Apply", "Übernehmen", "Appliquer", "Applica", "Применить")
    val removeStep get() = t("Usuń element", "Remove element", "Element entfernen", "Supprimer l'élément", "Rimuovi elemento", "Удалить элемент")
    val startFrame get() = t("Punkt startu montażu", "Installation start point", "Montagestartpunkt", "Point de départ du montage", "Punto di partenza del montaggio", "Точка начала монтажа")
    val applyStart get() = t("Ustaw start", "Set start", "Start setzen", "Définir le départ", "Imposta partenza", "Задать старт")
    val autoHint get() = t("Podaj współrzędne i kierunek końcowego kołnierza; osie nie muszą być równoległe.", "Enter the end flange coordinates and direction; the axes need not be parallel.", "Koordinaten und Richtung des Endflansches eingeben; die Achsen müssen nicht parallel sein.", "Saisissez les coordonnées et la direction de la bride finale ; les axes ne doivent pas être parallèles.", "Inserisci coordinate e direzione della flangia finale; gli assi non devono essere paralleli.", "Введите координаты и направление конечного фланца; оси не обязаны быть параллельными.")
    val targetDirection get() = t("Kierunek osi na końcu", "Terminal axis direction", "Richtung der Endachse", "Direction de l'axe final", "Direzione dell'asse finale", "Направление конечной оси")
    val minimumStraight get() = t("Minimalny prosty odcinek", "Minimum straight cut", "Minimale gerade Länge", "Coupe droite minimale", "Taglio diritto minimo", "Минимальный прямой рез")
    val autoAngle get() = t("Preferowany kąt", "Preferred angle", "Bevorzugter Winkel", "Angle préféré", "Angolo preferito", "Предпочтительный угол")
    val solve get() = t("Rozwiąż trasę 3D", "Solve 3D route", "3D-Route lösen", "Résoudre le tracé 3D", "Risolvi percorso 3D", "Рассчитать 3D-трассу")
    val solving get() = t("Liczenie…", "Solving…", "Berechnung…", "Calcul…", "Calcolo…", "Считаю…")
    val routeReady get() = t("Trasa zamknięta — długości do cięcia", "Route closed — fabrication cuts", "Route geschlossen — Zuschnittlängen", "Tracé fermé — longueurs de coupe", "Percorso chiuso — lunghezze di taglio", "Трасса замкнута — длины для резки")
    val sendToManual get() = t("Przenieś do edytora", "Send to the editor", "In den Editor übernehmen", "Envoyer vers l'éditeur", "Invia all'editor", "Отправить в редактор")
    val topology get() = t("Układ", "Topology", "Aufbau", "Topologie", "Topologia", "Схема")
    val error get() = t("Błąd", "Error", "Fehler", "Erreur", "Errore", "Ошибка")
    val parametersHint get() = t("Parametry silnika obowiązują w edytorze i w solverze.", "Engine parameters apply to both the editor and the solver.", "Die Engine-Parameter gelten für Editor und Solver.", "Les paramètres du moteur s'appliquent à l'éditeur et au solveur.", "I parametri del motore valgono per editor e solver.", "Параметры движка действуют и в редакторе, и в решателе.")
    val elbowRadius get() = t("Promień kolana", "Elbow radius", "Bogenradius", "Rayon du coude", "Raggio della curva", "Радиус отвода")
    val effectiveRadius get() = t("Aktywny promień", "Effective radius", "Wirksamer Radius", "Rayon effectif", "Raggio effettivo", "Действующий радиус")
    val customRadius get() = t("Własny promień", "Custom radius", "Eigener Radius", "Rayon personnalisé", "Raggio personalizzato", "Свой радиус")
    val wallThickness get() = t("Grubość ścianki", "Wall thickness", "Wandstärke", "Épaisseur de paroi", "Spessore parete", "Толщина стенки")
    val weldGap get() = t("Szczelina spawalnicza", "Weld gap", "Schweißspalt", "Jeu de soudure", "Gioco di saldatura", "Зазор под сварку")
    val minimumCut get() = t("Minimalny rez", "Minimum cut", "Minimaler Zuschnitt", "Coupe minimale", "Taglio minimo", "Минимальный рез")
    val elbowLimit get() = t("Limit kolan", "Elbow limit", "Bogengrenze", "Limite de coudes", "Limite curve", "Лимит отводов")
    val catalogDefault get() = t("Katalog", "Catalog", "Katalog", "Catalogue", "Catalogo", "Каталог")
    val meshQuality get() = t("Jakość siatki", "Mesh quality", "Netzqualität", "Qualité du maillage", "Qualità mesh", "Качество сетки")
    val ownFlange get() = t("Własny kołnierz", "Your own flange", "Eigener Flansch", "Bride personnalisée", "Flangia personalizzata", "Свой фланец")
    val catalogValue get() = t("Katalog", "Catalog", "Katalog", "Catalogue", "Catalogo", "Каталог")
    val flangeOutside get() = t("Średnica kołnierza", "Flange diameter", "Flanschdurchmesser", "Diamètre de bride", "Diametro flangia", "Диаметр фланца")
    val flangeFaceToWeld get() = t("Czoło do spoiny", "Face to weld", "Dichtfläche bis Naht", "Face à la soudure", "Faccia alla saldatura", "Торец до шва")
    val flangeBoltCircle get() = t("Rozstaw śrub", "Bolt circle", "Lochkreis", "Cercle de perçage", "Cerchio bulloni", "Окружность болтов")
    val flangeBoltCount get() = t("Liczba śrub", "Bolt count", "Schraubenzahl", "Nombre de boulons", "Numero bulloni", "Число болтов")
    val catalogFlange get() = t(
        "Kołnierz z katalogu",
        "Catalog flange",
        "Flansch aus Katalog",
        "Bride du catalogue",
        "Flangia a catalogo",
        "Фланец каталога",
    )
    val flangeCatalogHint get() = t(
        "Wybierz pozycję z katalogu albo wpisz własne wymiary poniżej.",
        "Pick a catalog position, or type your own dimensions below.",
        "Katalogposition wählen oder eigene Maße unten eintragen.",
        "Choisissez une position du catalogue ou saisissez vos propres cotes.",
        "Scegli una posizione a catalogo oppure inserisci le tue quote.",
        "Выберите позицию каталога или впишите свои размеры ниже.",
    )
    val engineUnavailable get() = t("Silnik 3D niedostępny", "The 3D engine is unavailable", "Die 3D-Engine ist nicht verfügbar", "Le moteur 3D est indisponible", "Il motore 3D non è disponibile", "3D-движок недоступен")

    fun collisionWarning(count: Int) = t(
        "Wykryto $count kolizję(-e) osi rury — sprawdź trasę przed montażem.",
        "$count pipe centreline collision(s) detected — review the route before fabrication.",
        "$count Kollision(en) der Rohrachse erkannt — Route vor der Fertigung prüfen.",
        "$count collision(s) d'axe de tube détectée(s) — vérifiez le tracé avant fabrication.",
        "$count collisione/i dell'asse del tubo rilevata/e — controlla il percorso prima della fabbricazione.",
        "Обнаружено пересечений осей трубы: $count — проверьте трассу перед изготовлением.",
    )
    val straightPipeMass get() = t(
        "Masa prostych odcinków rury",
        "Straight-pipe mass",
        "Masse der geraden Rohrabschnitte",
        "Masse des tubes droits",
        "Massa dei tratti dritti",
        "Масса прямых участков трубы",
    )
    val straightPipeMassHint get() = t(
        "Teoretyczna masa stali dla prostych odcinków; kolana, trójniki, redukcje i kołnierze nie są liczone.",
        "Theoretical steel mass of the straight cuts only; elbows, tees, reducers and flanges are not counted.",
        "Theoretische Stahlmasse nur der geraden Zuschnitte; Bögen, T-Stücke, Reduzierungen und Flansche zählen nicht.",
        "Masse théorique en acier des tronçons droits uniquement ; coudes, tés, réducteurs et brides exclus.",
        "Massa teorica in acciaio dei soli tratti dritti; curve, tee, riduzioni e flange non sono conteggiati.",
        "Теоретическая масса стали только для прямых участков; отводы, тройники, переходы и фланцы не учтены.",
    )
    fun supportSpan(meters: String) = t(
        "Rozstaw podpór ≈ $meters m",
        "Support spacing ≈ $meters m",
        "Stützenabstand ≈ $meters m",
        "Entraxe des supports ≈ $meters m",
        "Interasse supporti ≈ $meters m",
        "Шаг опор ≈ $meters м",
    )
    val supportSpanHint get() = t(
        "Empiryczna reguła (ASME B31.1: DN[in]+10 ft dla stali; 8–12 ft dla miedzi) — nie zastępuje obliczeń ugięcia.",
        "Empirical rule of thumb (ASME B31.1: DN[in]+10 ft for steel; 8-12 ft for copper) — not a sag/stress calculation.",
        "Empirische Faustregel (ASME B31.1: DN[Zoll]+10 ft für Stahl; 8–12 ft für Kupfer) — kein Durchbiegungsnachweis.",
        "Règle empirique (ASME B31.1 : DN[po]+10 ft pour l'acier ; 8-12 ft pour le cuivre) — pas un calcul de flèche.",
        "Regola empirica (ASME B31.1: DN[in]+10 ft per l'acciaio; 8-12 ft per il rame) — non è un calcolo di freccia.",
        "Эмпирическое правило (ASME B31.1: DN[дюйм]+10 фут для стали; 8–12 фут для меди) — не расчёт прогиба.",
    )

    val verifiedIsTemplate get() = t(
        "To jest wynik obliczenia. Otwórz go w edytorze, aby zmienić kolana, kołnierze i cięcia.",
        "This is the calculated result. Open it in the editor to change elbows, flanges and cuts.",
        "Das ist das Rechenergebnis. Im Editor öffnen, um Bögen, Flansche und Zuschnitte zu ändern.",
        "Voici le résultat calculé. Ouvrez-le dans l'éditeur pour modifier coudes, brides et coupes.",
        "Questo è il risultato calcolato. Aprilo nell'editor per cambiare curve, flange e tagli.",
        "Это результат расчёта. Откройте его в редакторе, чтобы менять отводы, фланцы и резы.",
    )
    val editVerified get() = t(
        "Otwórz w edytorze",
        "Open in the editor",
        "Im Editor öffnen",
        "Ouvrir dans l'éditeur",
        "Apri nell'editor",
        "Открыть в редакторе",
    )

    fun modeCaption(mode: AssemblyWorkspaceMode3D): String = when (mode) {
        AssemblyWorkspaceMode3D.VERIFIED ->
            t("Gotowy wynik obliczenia", "The finished calculation", "Das fertige Rechenergebnis", "Le résultat calculé", "Il risultato calcolato", "Готовый результат расчёта")
        AssemblyWorkspaceMode3D.MANUAL ->
            t("Buduj i zmieniaj elementy", "Build and change elements", "Elemente bauen und ändern", "Construire et modifier", "Costruisci e modifica", "Стройте и меняйте элементы")
        AssemblyWorkspaceMode3D.AUTO ->
            t("Trasa do zadanego punktu", "Route to a given point", "Route zu einem Zielpunkt", "Tracé vers un point donné", "Percorso verso un punto", "Трасса до заданной точки")
        AssemblyWorkspaceMode3D.PARAMETERS ->
            t("Reguły warsztatu i katalogu", "Shop and catalog rules", "Werkstatt- und Katalogregeln", "Règles d'atelier et catalogue", "Regole di officina e catalogo", "Правила цеха и каталога")
    }

    fun sectionTitle(mode: AssemblyWorkspaceMode3D): String = when (mode) {
        AssemblyWorkspaceMode3D.VERIFIED ->
            t("Obliczenie", "Calculation", "Berechnung", "Calcul", "Calcolo", "Расчёт")
        AssemblyWorkspaceMode3D.MANUAL ->
            t("Elementy", "Elements", "Elemente", "Éléments", "Elementi", "Элементы")
        AssemblyWorkspaceMode3D.AUTO ->
            t("Cel trasy", "Route target", "Routenziel", "Cible du tracé", "Destinazione", "Цель трассы")
        AssemblyWorkspaceMode3D.PARAMETERS ->
            t("Parametry", "Parameters", "Parameter", "Paramètres", "Parametri", "Параметры")
    }

    fun mode(mode: AssemblyWorkspaceMode3D): String = when (mode) {
        AssemblyWorkspaceMode3D.VERIFIED -> t("Sprawdzony", "Verified", "Geprüft", "Vérifié", "Verificato", "Проверенная")
        AssemblyWorkspaceMode3D.MANUAL -> t("Ręcznie", "Manual", "Manuell", "Manuel", "Manuale", "Вручную")
        AssemblyWorkspaceMode3D.AUTO -> t("Solver 3D", "3D solver", "3D-Solver", "Solveur 3D", "Solver 3D", "3D-решатель")
        // Deliberately not "Parameters": the workshop already has a section chip with that label.
        AssemblyWorkspaceMode3D.PARAMETERS -> t("Silnik", "Engine", "Engine", "Moteur", "Motore", "Движок")
    }

    fun radiusMode(mode: ElbowRadiusMode3D): String = when (mode) {
        ElbowRadiusMode3D.CATALOG -> catalogDefault
        ElbowRadiusMode3D.SHORT_1D -> "1D"
        ElbowRadiusMode3D.LONG_1_5D -> "1.5D"
        ElbowRadiusMode3D.LARGE_3D -> "3D"
        ElbowRadiusMode3D.LARGE_5D -> "5D"
        ElbowRadiusMode3D.CUSTOM -> t("Własny", "Custom", "Eigen", "Perso", "Personale", "Свой")
    }

    fun quality(quality: MeshQuality3D): String = when (quality) {
        MeshQuality3D.DRAFT -> t("Szkic", "Draft", "Entwurf", "Brouillon", "Bozza", "Черновик")
        MeshQuality3D.NORMAL -> t("Normalna", "Normal", "Normal", "Normale", "Normale", "Обычное")
        MeshQuality3D.FINE -> t("Dokładna", "Fine", "Fein", "Fine", "Fine", "Точное")
    }

    fun topologyName(topology: com.planruler.fabrication3d.RouteTopology3D): String = when (topology) {
        com.planruler.fabrication3d.RouteTopology3D.STRAIGHT ->
            t("Prosta", "Straight", "Gerade", "Droit", "Dritto", "Прямая")
        com.planruler.fabrication3d.RouteTopology3D.ROLLING_OFFSET ->
            t("Offset przestrzenny", "Rolling offset", "Raumversatz", "Décalage roulé", "Offset spaziale", "Пространственный офсет")
        com.planruler.fabrication3d.RouteTopology3D.TURN ->
            t("Zwrot", "Turn", "Richtungswechsel", "Virage", "Svolta", "Поворот")
        com.planruler.fabrication3d.RouteTopology3D.TURN_WITH_OFFSET ->
            t("Zwrot z offsetem", "Turn with offset", "Wechsel mit Versatz", "Virage avec décalage", "Svolta con offset", "Поворот со смещением")
        com.planruler.fabrication3d.RouteTopology3D.U_TURN ->
            t("Zawrót", "U-turn", "Kehre", "Demi-tour", "Inversione", "Разворот")
    }
}
