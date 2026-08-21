package com.planruler.feature.pipecalculator

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.RectF
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planruler.designsystem.localization.localizedUi
import com.planruler.fabrication3d.ChainPlan3D
import com.planruler.fabrication3d.Fabrication3DEngine
import com.planruler.fabrication3d.ParametricAssembly3D
import com.planruler.model.AppLanguage
import com.planruler.model.AppSettings
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobStatus
import com.planruler.model.ThemePreference
import com.planruler.model.TouchProfile
import com.planruler.pipecalculator.FlangedOffsetAssemblyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ParametricAssemblyDrawingCard(
    result: FlangedOffsetAssemblyResult,
    language: AppLanguage,
    engine: Fabrication3DEngine,
    jobKey: String,
    jobName: String,
    initialChainRecipe: String,
    onChainRecipeChanged: (String) -> Unit,
    installerRequest: InstallerRouteRequest?,
    job: InstallationJob,
    onJobChanged: (InstallationJob) -> Unit,
    settings: AppSettings,
    onSettings: (AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
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
    var selectedView by rememberSaveable(jobKey) { mutableStateOf(AssemblyDrawingView.ISOMETRIC) }
    var selectedLayer by rememberSaveable(jobKey) { mutableStateOf(AssemblyDrawingLayer.ALL) }
    var paper by rememberSaveable(jobKey) { mutableStateOf(AssemblyDrawingPaper.A4) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var checkedByDraft by rememberSaveable(jobKey, job.checkedBy) { mutableStateOf(job.checkedBy.orEmpty()) }

    DisposableEffect(activity, settings.keepScreenAwakeInField) {
        if (settings.keepScreenAwakeInField) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (settings.keepScreenAwakeInField) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    LaunchedEffect(result, installerRequest?.taskType) { viewModel.bind(result, savedPlan) }
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
            InstallerCalculationPath.MANUAL_TEMPLATE -> viewModel.setMode(AssemblyWorkspaceMode3D.MANUAL)
        }
    }
    LaunchedEffect(state.editor?.plan) {
        state.editor?.plan?.let {
            savedPlan = it
            onChainRecipeChanged(encodeChainPlan(it))
        }
    }
    LaunchedEffect(state.solution?.plan) {
        state.solution?.plan?.let {
            savedPlan = it
            onChainRecipeChanged(encodeChainPlan(it))
        }
    }

    val assembly = state.assemblyFor(installerRequest)
    val fileName = remember(jobName, paper) {
        val safe = jobName.replace(Regex("[^A-Za-zА-Яа-я0-9._-]+"), "_").trim('_').ifBlank { "installation" }
        "${safe}_${paper.name}.pdf"
    }
    val exportStem = remember(jobName) {
        jobName.replace(Regex("[^A-Za-zА-Яа-я0-9._-]+"), "_").trim('_').ifBlank { "installation" }
    }
    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null || assembly == null) return@rememberLauncherForActivityResult
        scope.launch {
            exportStatus = localizedUi(language, "Формируем PDF…", "Creating PDF…")
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        AssemblyDrawingPdfWriter.write(
                            output, assembly, jobName, paper, language, job.checkedBy, job.checkedAtEpochMs,
                        )
                    } ?: error("Cannot open output")
                }
            }
            exportStatus = if (result.isSuccess) {
                localizedUi(language, "PDF сохранён", "PDF saved")
            } else {
                localizedUi(language, "Не удалось сохранить PDF", "Could not save PDF")
            }
        }
    }
    val createImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri == null || assembly == null) return@rememberLauncherForActivityResult
        scope.launch {
            exportStatus = localizedUi(language, "Формируем изображение…", "Creating image…")
            val exported = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        AssemblyFieldImageWriter.writePng(
                            output, assembly, jobName, language, job.checkedBy, job.checkedAtEpochMs,
                        )
                    } ?: error("Cannot open output")
                }
            }
            exportStatus = if (exported.isSuccess) {
                localizedUi(language, "Изображение сохранено", "Image saved")
            } else localizedUi(language, "Не удалось сохранить изображение", "Could not save image")
        }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null || assembly == null) return@rememberLauncherForActivityResult
        scope.launch {
            exportStatus = localizedUi(language, "Формируем CSV…", "Creating CSV…")
            val exported = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        AssemblyFieldCsvWriter.write(
                            output, assembly, jobName, language, job.checkedBy, job.checkedAtEpochMs,
                        )
                    } ?: error("Cannot open output")
                }
            }
            exportStatus = if (exported.isSuccess) {
                localizedUi(language, "CSV сохранён", "CSV saved")
            } else localizedUi(language, "Не удалось сохранить CSV", "Could not save CSV")
        }
    }
    val createFieldPack = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null || assembly == null) return@rememberLauncherForActivityResult
        scope.launch {
            exportStatus = localizedUi(language, "Собираем монтажный пакет…", "Creating field pack…")
            val exported = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        AssemblyFieldPackageWriter.write(output, assembly, job, paper, language)
                    } ?: error("Cannot open output")
                }
            }
            exportStatus = if (exported.isSuccess) {
                localizedUi(language, "Монтажный пакет сохранён", "Field pack saved")
            } else localizedUi(language, "Не удалось сохранить пакет", "Could not save field pack")
        }
    }

    ElevatedCard(
        Modifier.fillMaxWidth().testTag(PipeCalculatorTags.AssemblyDrawing),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    localizedUi(language, "Рабочие виды и размеры", "Working views and dimensions"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    localizedUi(
                        language,
                        "Все проекции построены из текущей 3D-сборки",
                        "Every projection is generated from the current 3D assembly",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(AssemblyDrawingView.entries) { view ->
                    FilterChip(
                        selected = selectedView == view,
                        onClick = { selectedView = view },
                        label = { Text(view.uiLabel(language)) },
                        modifier = Modifier.testTag("${PipeCalculatorTags.AssemblyDrawingViewPrefix}${view.name}"),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    localizedUi(language, "Что показать", "Drawing layer"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(AssemblyDrawingLayer.entries) { layer ->
                        FilterChip(
                            selected = selectedLayer == layer,
                            onClick = { selectedLayer = layer },
                            label = { Text(layer.uiLabel(language)) },
                        )
                    }
                }
            }

            if (assembly == null) {
                Surface(
                    Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        if (state.busy) localizedUi(language, "Строим маршрут…", "Building route…")
                        else localizedUi(language, "Для этих размеров маршрут не построен", "No route could be built for these dimensions"),
                        Modifier.padding(20.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                val scheduleRows = remember(assembly, language) {
                    buildAssemblyPartFieldRows(assembly, language)
                }
                val materials = remember(assembly) { buildAssemblyMaterialFieldSummary(assembly) }
                val drawing = remember(assembly, selectedView, selectedLayer) {
                    AssemblyDrawingGenerator.generate(assembly, selectedView, selectedLayer)
                }
                val colorScheme = MaterialTheme.colorScheme
                val colors = remember(colorScheme) {
                    AssemblyDrawingColors(
                        background = colorScheme.surface.toArgb(),
                        foreground = colorScheme.onSurface.toArgb(),
                        pipe = colorScheme.primary.toArgb(),
                        fitting = colorScheme.secondary.toArgb(),
                        selected = colorScheme.tertiary.toArgb(),
                        dimension = colorScheme.primary.toArgb(),
                        muted = colorScheme.onSurfaceVariant.toArgb(),
                        weld = colorScheme.error.toArgb(),
                    )
                }
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                        .testTag(PipeCalculatorTags.AssemblyDrawingCanvas)
                        .pointerInput(drawing) {
                            detectTapGestures { tap ->
                                val padding = AssemblyDrawingRenderer.viewportPadding(
                                    drawing,
                                    size.width.toDouble(),
                                    size.height.toDouble(),
                                )
                                val viewport = DrawingViewport2D(
                                    drawing.bounds,
                                    0.0,
                                    0.0,
                                    size.width.toDouble(),
                                    size.height.toDouble(),
                                    padding,
                                )
                                viewModel.selectPart(
                                    pickDrawingPart(
                                        drawing,
                                        viewport,
                                        DrawingPoint2D(tap.x.toDouble(), tap.y.toDouble()),
                                    ),
                                )
                            }
                        },
                ) {
                    drawIntoCanvas { canvas ->
                        AssemblyDrawingRenderer.draw(
                            canvas.nativeCanvas,
                            drawing,
                            RectF(0f, 0f, size.width, size.height),
                            colors,
                            state.selectedPartId,
                        )
                    }
                }

                Column(
                    Modifier.fillMaxWidth().testTag(PipeCalculatorTags.AssemblyDrawingPartsSchedule),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        localizedUi(language, "Резы и детали", "Cuts and parts"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        localizedUi(
                            language,
                            "Нажмите карточку — эта же деталь выделится на чертеже и в 3D.",
                            "Tap a card — the same part is highlighted in the drawing and in 3D.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(
                        modifier = Modifier.testTag(PipeCalculatorTags.AssemblyDrawingPartsRow),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(scheduleRows, key = { it.partId }) { row ->
                            val selected = state.selectedPartId == row.partId
                            ElevatedCard(
                                onClick = {
                                    viewModel.selectPart(row.partId.takeUnless { selected })
                                },
                                modifier = Modifier
                                    .width(220.dp)
                                    .testTag("${PipeCalculatorTags.AssemblyDrawingPartPrefix}${row.code}"),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else MaterialTheme.colorScheme.surface,
                                ),
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(row.title, fontWeight = FontWeight.Black)
                                    Text(
                                        row.primaryValue,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                    )
                                    Text(
                                        row.specification,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                state.selectedPartId?.let { id ->
                    scheduleRows.firstOrNull { it.partId == id }?.let { row ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(PipeCalculatorTags.AssemblyDrawingSelectedPart)
                                .semantics(mergeDescendants = true) {},
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(row.title, fontWeight = FontWeight.Black)
                                    Text(
                                        row.primaryValue,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(row.specification, style = MaterialTheme.typography.bodySmall)
                                    if (row.connectedCodes.isNotEmpty()) {
                                        Text(
                                            localizedUi(
                                                language,
                                                "Соединяется с: ${row.connectedCodes.joinToString()}",
                                                "Connects to: ${row.connectedCodes.joinToString()}",
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Text(
                                        localizedUi(language, "Выделено на схеме, в списке и в 3D", "Selected in drawing, list and 3D"),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                OutlinedButton(onClick = { viewModel.selectPart(null) }) {
                                    Text(localizedUi(language, "Снять", "Clear"))
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PipeCalculatorTags.AssemblyDrawingMaterialsSummary)
                        .semantics(mergeDescendants = true) {},
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            localizedUi(language, "Материалы этого узла", "Materials for this assembly"),
                            fontWeight = FontWeight.Black,
                        )
                        Text(materials.label(language), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            localizedUi(language, "Полевой режим", "Field mode"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            localizedUi(
                                language,
                                "Быстрые настройки для солнца, перчаток и длительного просмотра чертежа.",
                                "Quick controls for glare, gloves and prolonged drawing use.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            item {
                                FilterChip(
                                    selected = settings.theme == ThemePreference.SUNLIGHT,
                                    onClick = {
                                        onSettings(
                                            settings.copy(
                                                theme = if (settings.theme == ThemePreference.SUNLIGHT) {
                                                    ThemePreference.LIGHT
                                                } else ThemePreference.SUNLIGHT,
                                                dynamicColor = false,
                                            ),
                                        )
                                    },
                                    label = { Text(localizedUi(language, "Солнце", "Sunlight")) },
                                    modifier = Modifier.testTag(PipeCalculatorTags.FieldSunlight),
                                )
                            }
                            item {
                                FilterChip(
                                    selected = settings.touchProfile == TouchProfile.GLOVE,
                                    onClick = {
                                        onSettings(
                                            settings.copy(
                                                touchProfile = if (settings.touchProfile == TouchProfile.GLOVE) {
                                                    TouchProfile.FINGER
                                                } else TouchProfile.GLOVE,
                                            ),
                                        )
                                    },
                                    label = { Text(localizedUi(language, "Перчатки", "Gloves")) },
                                    modifier = Modifier.testTag(PipeCalculatorTags.FieldGloves),
                                )
                            }
                            item {
                                FilterChip(
                                    selected = settings.keepScreenAwakeInField,
                                    onClick = {
                                        onSettings(settings.copy(keepScreenAwakeInField = !settings.keepScreenAwakeInField))
                                    },
                                    label = { Text(localizedUi(language, "Экран включён", "Keep screen on")) },
                                    modifier = Modifier.testTag(PipeCalculatorTags.FieldKeepAwake),
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (job.status == InstallationJobStatus.CHECKED) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            localizedUi(language, "Проверка перед резкой", "Check before cutting"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        if (job.status == InstallationJobStatus.CHECKED && job.checkedAtEpochMs != null) {
                            Text(
                                verificationLabel(language, job.checkedBy, job.checkedAtEpochMs),
                                modifier = Modifier.testTag(PipeCalculatorTags.AssemblyDrawingChecked),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            OutlinedButton(
                                onClick = {
                                    onJobChanged(
                                        job.copy(
                                            status = InstallationJobStatus.DRAFT,
                                            checkedBy = null,
                                            checkedAtEpochMs = null,
                                        ),
                                    )
                                },
                            ) {
                                Text(localizedUi(language, "Снять отметку", "Remove check"))
                            }
                        } else {
                            OutlinedTextField(
                                value = checkedByDraft,
                                onValueChange = { checkedByDraft = it.take(120) },
                                label = { Text(localizedUi(language, "Кто проверил", "Checked by")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.AssemblyDrawingCheckedBy),
                            )
                            Button(
                                enabled = checkedByDraft.isNotBlank(),
                                onClick = {
                                    onJobChanged(
                                        job.copy(
                                            status = InstallationJobStatus.CHECKED,
                                            checkedBy = checkedByDraft.trim(),
                                            checkedAtEpochMs = System.currentTimeMillis(),
                                        ),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.AssemblyDrawingCheck),
                            ) {
                                Text(localizedUi(language, "Проверено — сохранить", "Checked — save"))
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssemblyDrawingPaper.entries.forEach { candidate ->
                        FilterChip(
                            selected = paper == candidate,
                            onClick = { paper = candidate },
                            label = { Text(candidate.name) },
                        )
                    }
                }
                Button(
                    onClick = { createPdf.launch(fileName) },
                    modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.AssemblyDrawingPdf),
                ) {
                    Text(localizedUi(language, "Сохранить монтажный PDF ${paper.name}", "Save installation PDF ${paper.name}"))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { createImage.launch("${exportStem}_field.png") },
                        modifier = Modifier.weight(1f).testTag(PipeCalculatorTags.AssemblyDrawingImage),
                    ) {
                        Text(localizedUi(language, "Изображение PNG", "PNG image"))
                    }
                    OutlinedButton(
                        onClick = { createCsv.launch("${exportStem}_cuts.csv") },
                        modifier = Modifier.weight(1f).testTag(PipeCalculatorTags.AssemblyDrawingCsv),
                    ) {
                        Text(localizedUi(language, "Резы CSV", "Cuts CSV"))
                    }
                }
                Button(
                    onClick = { createFieldPack.launch("${exportStem}_field_pack.zip") },
                    modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.AssemblyDrawingFieldPack),
                ) {
                    Text(localizedUi(language, "Сохранить весь монтажный пакет ZIP", "Save complete field pack ZIP"))
                }
                exportStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    localizedUi(
                        language,
                        "ZIP содержит PDF, PNG, CSV и паспорт проверки. Размеры важнее масштаба.",
                        "ZIP contains PDF, PNG, CSV and the verification passport. Written dimensions override scale.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Assembly3DUiState.assemblyFor(request: InstallerRouteRequest?): ParametricAssembly3D? = when {
    request?.calculationPath == InstallerCalculationPath.AUTO_ROUTE -> solution?.assembly
    request?.calculationPath == InstallerCalculationPath.MANUAL_TEMPLATE -> editor?.assembly
    else -> shownAssembly
}

private fun AssemblyDrawingView.uiLabel(language: AppLanguage): String = when (this) {
    AssemblyDrawingView.ISOMETRIC -> localizedUi(language, "Изометрия", "Isometric")
    AssemblyDrawingView.TOP -> localizedUi(language, "Сверху", "Top")
    AssemblyDrawingView.SIDE -> localizedUi(language, "Сбоку", "Side")
    AssemblyDrawingView.RIGHT -> localizedUi(language, "Справа", "Right")
    AssemblyDrawingView.END -> localizedUi(language, "С торца", "End")
}

private fun AssemblyDrawingLayer.uiLabel(language: AppLanguage): String = when (this) {
    AssemblyDrawingLayer.INSTALLATION -> localizedUi(language, "Монтаж", "Installation")
    AssemblyDrawingLayer.CUTTING -> localizedUi(language, "Резка", "Cutting")
    AssemblyDrawingLayer.DETAILS -> localizedUi(language, "Детали", "Parts")
    AssemblyDrawingLayer.ALL -> localizedUi(language, "Все", "All")
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
