package com.planruler.feature.pipecalculator

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planruler.designsystem.localization.localizedUi
import com.planruler.designsystem.localization.UiTextKey
import com.planruler.designsystem.localization.uiText
import com.planruler.designsystem.theme.LocalScenePalette
import com.planruler.model.AppLanguage
import com.planruler.model.AppSettings
import com.planruler.model.InstallationChainRecipe
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobId
import com.planruler.model.InstallationJobInput
import com.planruler.model.InstallationInputMode
import com.planruler.model.InstallationTaskType
import com.planruler.model.InstallationWorkspaceSection
import com.planruler.model.ProjectId
import com.planruler.project.api.ProjectRepository
import com.planruler.fabrication3d.Fabrication3DEngine
import com.planruler.pipecalculator.FlangedOffsetAssemblyInput
import com.planruler.pipecalculator.FlangedOffsetAssemblyResult
import com.planruler.pipecalculator.PIPE_INSTALLATION_SERIES
import com.planruler.pipecalculator.calculateFlangedOffsetAssembly
import java.util.Locale
import kotlin.math.max
import kotlin.math.sin


private typealias WorkshopSection = InstallationWorkspaceSection

@Composable
internal fun FabricationWorkshop(
    language: AppLanguage,
    fabrication3d: Fabrication3DEngine,
    projectRepository: ProjectRepository? = null,
    onProjectsChanged: () -> Unit = {},
    settings: AppSettings = AppSettings(language = language),
    onSettings: (AppSettings) -> Unit = {},
) {
    if (projectRepository == null) {
        val scratch = remember {
            InstallationJob(
                id = InstallationJobId("temporary"),
                name = localizedUi(language, "Несохранённый расчёт", "Unsaved calculation"),
                createdAtEpochMs = 0L,
                modifiedAtEpochMs = 0L,
            )
        }
        FabricationWorkshopEditor(
            language = language,
            fabrication3d = fabrication3d,
            job = scratch,
            managerContent = {
                PersistenceNotice(language, hasProjects = false)
            },
            onAutosave = {},
            onSaveNow = {},
            settings = settings,
            onSettings = onSettings,
        )
        return
    }

    val jobsViewModel: InstallationJobsViewModel = viewModel(
        key = "installation-jobs",
        factory = InstallationJobsViewModel.factory(projectRepository),
    )
    val jobsState by jobsViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { jobsViewModel.refresh() }
    LaunchedEffect(jobsState.persistedRevision) {
        if (jobsState.persistedRevision > 0) onProjectsChanged()
    }

    val manager: @Composable () -> Unit = {
        InstallationJobManager(
            language = language,
            state = jobsState,
            onProject = jobsViewModel::selectProject,
            onJob = jobsViewModel::selectJob,
            onCreate = jobsViewModel::createJob,
            onCreateProject = jobsViewModel::createWorkshopProject,
            onRename = jobsViewModel::renameJob,
            onDuplicate = jobsViewModel::duplicateJob,
            onDelete = jobsViewModel::deleteJob,
            onRestore = jobsViewModel::restoreJob,
        )
    }

    val projectId = jobsState.selectedProjectId
    val job = jobsState.selectedJob?.takeIf { it.deletedAtEpochMs == null }
    if (projectId == null || job == null) {
        LazyColumn(
            Modifier.fillMaxSize().testTag(PipeCalculatorTags.InstallationList),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { manager() }
            if (projectId != null) {
                item { PersistenceNotice(language, hasProjects = true) }
            }
        }
    } else {
        key(job.id.value) {
            FabricationWorkshopEditor(
                language = language,
                fabrication3d = fabrication3d,
                job = job,
                managerContent = manager,
                onAutosave = { jobsViewModel.scheduleSave(projectId, it) },
                onSaveNow = { jobsViewModel.saveNow(projectId, it) },
                settings = settings,
                onSettings = onSettings,
            )
        }
    }
}

@Composable
private fun FabricationWorkshopEditor(
    language: AppLanguage,
    fabrication3d: Fabrication3DEngine,
    job: InstallationJob,
    managerContent: @Composable () -> Unit,
    onAutosave: (InstallationJob) -> Unit,
    onSaveNow: (InstallationJob) -> Unit,
    settings: AppSettings,
    onSettings: (AppSettings) -> Unit,
) {
    val text = WorkshopText(language)
    var draft by remember(job.id.value) { mutableStateOf(InstallerDraft.from(job)) }
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    var section by rememberSaveable(job.id.value) { mutableStateOf(job.activeSection) }
    var chainRecipe by rememberSaveable(job.id.value) {
        mutableStateOf(
            job.chainRecipe?.encodedPlan.orEmpty().takeIf {
                job.chainRecipe?.taskType == null || job.chainRecipe?.taskType == job.taskType
            }.orEmpty(),
        )
    }
    var chainRecipeTask by remember(job.id.value) {
        mutableStateOf(job.chainRecipe?.taskType ?: job.taskType)
    }

    val input = remember(draft) { draft.toInputOrNull() }
    val calculation = remember(input, draft.taskType, refresh) {
        runCatching {
            val current = requireNotNull(input) { "Invalid number" }
            calculateFlangedOffsetAssembly(current.toProfileCalculationInput(draft.taskType))
        }
    }
    val effectiveChainRecipe = chainRecipe.takeIf { chainRecipeTask == draft.taskType }.orEmpty()
    val persistedJob = remember(job, input, draft.taskType, effectiveChainRecipe, section) {
        job.copy(
            taskType = draft.taskType,
            input = input ?: job.input,
            chainRecipe = effectiveChainRecipe.takeIf(String::isNotBlank)?.let {
                InstallationChainRecipe(encodedPlan = it, taskType = draft.taskType)
            },
            activeSection = section,
        )
    }
    val latestPersistedJob by rememberUpdatedState(persistedJob)
    LaunchedEffect(input, draft.taskType, effectiveChainRecipe, section) {
        if (input != null) onAutosave(persistedJob)
    }
    DisposableEffect(job.id) {
        onDispose { onSaveNow(latestPersistedJob) }
    }
    val installerRequest = remember(persistedJob) { installerRouteRequest(persistedJob) }
    val availableSections = remember(draft.taskType, draft.inputMode) {
        buildList {
            add(WorkshopSection.MODEL)
            add(WorkshopSection.DRAWING)
            if (draft.inputMode == InstallationInputMode.ADVANCED) add(WorkshopSection.PARAMETERS)
            if (draft.taskType == InstallationTaskType.FLANGED_OFFSET) {
                add(WorkshopSection.CUT_LIST)
            }
        }
    }
    LaunchedEffect(availableSections, section) {
        if (section !in availableSections) section = WorkshopSection.MODEL
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag(PipeCalculatorTags.InstallationList),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { managerContent() }
        item { InstallerInputWizard(language = language, draft = draft, onDraft = { draft = it }) }
        item { WorkshopHero(text, calculation.getOrNull(), draft.nominalDiameter, draft.pressureClass, draft.angle) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableSections) { candidate ->
                    FilterChip(
                        selected = section == candidate,
                        onClick = { section = candidate },
                        label = {
                            Text(
                                uiText(
                                    language,
                                    when (candidate) {
                                        WorkshopSection.MODEL -> UiTextKey.WORKSHOP_MODEL
                                        WorkshopSection.PARAMETERS -> UiTextKey.WORKSHOP_PARAMETERS
                                        WorkshopSection.DRAWING -> UiTextKey.WORKSHOP_DRAWING
                                        WorkshopSection.CUT_LIST -> UiTextKey.WORKSHOP_CUT_LIST
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
        calculation.fold(
            onSuccess = { result ->
                when (section) {
                    WorkshopSection.MODEL -> {
                        item {
                            ParametricAssembly3DCard(
                                result = result,
                                language = language,
                                engine = fabrication3d,
                                jobKey = job.id.value,
                                initialChainRecipe = effectiveChainRecipe,
                                onChainRecipeChanged = {
                                    chainRecipe = it
                                    chainRecipeTask = draft.taskType
                                },
                                installerRequest = installerRequest,
                                showAdvancedControls = draft.inputMode == InstallationInputMode.ADVANCED,
                            )
                        }
                        if (draft.taskType == InstallationTaskType.FLANGED_OFFSET) {
                            item { AssemblyMetrics(result, text) }
                        }
                    }
                    WorkshopSection.PARAMETERS -> {
                        item {
                            WorkshopControls(
                                text = text,
                                dn = draft.nominalDiameter,
                                onDn = { draft = draft.copy(nominalDiameter = it) },
                                pn = draft.pressureClass,
                                onPn = { draft = draft.copy(pressureClass = it) },
                                angle = draft.angle,
                                onAngle = { draft = draft.copy(angle = it) },
                                offset = draft.lateral,
                                onOffset = { draft = draft.copy(lateral = it, vertical = "0") },
                                overall = draft.along,
                                onOverall = { draft = draft.copy(along = it) },
                                weldGap = draft.weldGap,
                                onWeldGap = { draft = draft.copy(weldGap = it) },
                                quantity = draft.quantity,
                                onQuantity = { draft = draft.copy(quantity = it) },
                                sawKerf = draft.sawKerf,
                                onSawKerf = { draft = draft.copy(sawKerf = it) },
                                stockLength = draft.stockLengthMm,
                                onStockLength = { draft = draft.copy(stockLengthMm = it) },
                                onRefresh = { refresh++ },
                            )
                        }
                        item { WorkshopAdvisory(result, text) }
                    }
                    WorkshopSection.DRAWING -> {
                        item {
                            ParametricAssemblyDrawingCard(
                                result = result,
                                language = language,
                                engine = fabrication3d,
                                jobKey = job.id.value,
                                jobName = job.name,
                                initialChainRecipe = effectiveChainRecipe,
                                onChainRecipeChanged = {
                                    chainRecipe = it
                                    chainRecipeTask = draft.taskType
                                },
                                installerRequest = installerRequest,
                                job = persistedJob,
                                onJobChanged = onSaveNow,
                                settings = settings,
                                onSettings = onSettings,
                            )
                        }
                    }
                    WorkshopSection.CUT_LIST -> {
                        item { CutListPanel(result, text) }
                        item { StockPlanGraph(result, text) }
                        item { WorkshopAdvisory(result, text) }
                    }
                }
            },
            onFailure = { failure -> item { WorkshopError(text.invalidInput(failure.message)) } },
        )
    }
}

private fun InstallationJobInput.toProfileCalculationInput(taskType: InstallationTaskType) = FlangedOffsetAssemblyInput(
    dn = nominalDiameter,
    pn = pressureClass,
    targetOffsetMm = if (taskType == InstallationTaskType.FLANGED_OFFSET) targetOffsetMm else max(500.0, targetOffsetMm),
    overallFaceToFaceMm = if (taskType == InstallationTaskType.FLANGED_OFFSET) overallFaceToFaceMm else max(4_000.0, overallFaceToFaceMm),
    angleDeg = angleDeg,
    weldGapMm = weldGapMm,
    quantity = quantity,
    stockLengthMm = stockLengthMm.toDouble(),
    sawKerfMm = sawKerfMm,
)

@Composable
private fun InstallationJobManager(
    language: AppLanguage,
    state: InstallationJobsState,
    onProject: (ProjectId) -> Unit,
    onJob: (InstallationJobId) -> Unit,
    onCreate: (String) -> Unit,
    onCreateProject: (String, String) -> Unit,
    onRename: (InstallationJobId, String) -> Unit,
    onDuplicate: (InstallationJobId) -> Unit,
    onDelete: (InstallationJobId) -> Unit,
    onRestore: (InstallationJobId) -> Unit,
) {
    fun t(russian: String, english: String) = localizedUi(language, russian, english)
    val project = state.selectedProject
    val activeJobs = project?.installationJobs.orEmpty()
        .filter { it.deletedAtEpochMs == null }
        .sortedByDescending { it.lastOpenedAtEpochMs }
    val deletedJobs = project?.installationJobs.orEmpty()
        .filter { it.deletedAtEpochMs != null }
        .sortedByDescending { it.deletedAtEpochMs }
    val selected = state.selectedJob?.takeIf { it.deletedAtEpochMs == null }
    var renameTarget by remember { mutableStateOf<InstallationJob?>(null) }
    var renameValue by remember { mutableStateOf("") }

    ElevatedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t("Проект и монтажный узел", "Project and installation job"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    t(
                        "Расчёт автоматически сохраняется в выбранном проекте.",
                        "The calculation is saved automatically in the selected project.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.projects.isEmpty()) {
                Text(
                    t(
                        "Проектов пока нет. Создайте проект мастерской — пустой лист, в котором сохраняются узлы и 3D.",
                        "No projects yet. Create a workshop project: a blank sheet that stores jobs and 3D models.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        onCreateProject(
                            t("Мастерская", "Workshop"),
                            t("Монтажный узел", "Installation job") + " 1",
                        )
                    },
                ) { Text(t("Создать проект мастерской", "Create workshop project")) }
            } else {
                Text(t("Проект", "Project"), style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.projects, key = { it.id.value }) { candidate ->
                        FilterChip(
                            selected = candidate.id == state.selectedProjectId,
                            onClick = { onProject(candidate.id) },
                            label = { Text(candidate.name) },
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t("Последние расчёты", "Recent calculations"), style = MaterialTheme.typography.labelLarge)
                    Text(
                        when {
                            state.saving -> t("Сохранение…", "Saving…")
                            state.persistedRevision > 0 -> t("Сохранено", "Saved")
                            else -> t("Автосохранение", "Autosave")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (activeJobs.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(activeJobs, key = { it.id.value }) { candidate ->
                            FilterChip(
                                selected = candidate.id == state.selectedJobId,
                                onClick = { onJob(candidate.id) },
                                label = { Text(candidate.name) },
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        onCreate(t("Монтажный узел", "Installation job") + " ${activeJobs.size + 1}")
                    },
                ) {
                    Text(t("Новый узел", "New job"))
                }

                if (selected != null) {
                    Text(
                        t("История расчёта", "Calculation history") + ": ${selected.history.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            OutlinedButton(onClick = {
                                renameTarget = selected
                                renameValue = selected.name
                            }) { Text(t("Переименовать", "Rename")) }
                        }
                        item {
                            OutlinedButton(onClick = { onDuplicate(selected.id) }) {
                                Text(t("Копировать", "Duplicate"))
                            }
                        }
                        item {
                            TextButton(onClick = { onDelete(selected.id) }) {
                                Text(t("В корзину", "Move to bin"), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                if (deletedJobs.isNotEmpty()) {
                    Text(t("Корзина узлов", "Job recycle bin"), style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(deletedJobs, key = { it.id.value }) { deleted ->
                            OutlinedButton(onClick = { onRestore(deleted.id) }) {
                                Text(t("Восстановить", "Restore") + ": ${deleted.name}")
                            }
                        }
                    }
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(t("Название узла", "Job name")) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it.take(120) },
                    singleLine = true,
                    label = { Text(t("Название", "Name")) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameValue.isNotBlank(),
                    onClick = {
                        onRename(target.id, renameValue)
                        renameTarget = null
                    },
                ) { Text(t("Сохранить", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(t("Отмена", "Cancel")) }
            },
        )
    }
}

@Composable
private fun PersistenceNotice(language: AppLanguage, hasProjects: Boolean) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Text(
            localizedUi(
                language,
                if (hasProjects) "Создайте первый монтажный узел — параметры и 3D-рецепт будут сохранены в проекте."
                else "Без проекта расчёт остаётся временным. Создайте проект, чтобы включить автосохранение.",
                if (hasProjects) "Create the first installation job to save its inputs and 3D recipe in the project."
                else "Without a project this calculation is temporary. Create a project to enable autosave.",
            ),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun WorkshopHero(
    text: WorkshopText,
    result: FlangedOffsetAssemblyResult?,
    dn: Int,
    pn: Int,
    angle: String,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(text.subtitle, style = MaterialTheme.typography.bodyMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { WorkshopPill("DN $dn") }
                item { WorkshopPill("PN $pn") }
                item { WorkshopPill("$angle°") }
                item { WorkshopPill(result?.let { "${it.cuts.size} ${text.pipeCutsShort}" } ?: text.checkInput) }
                item { WorkshopPill(result?.let { "${it.weldCount} ${text.weldsShort}" } ?: "—") }
            }
        }
    }
}

@Composable
private fun WorkshopPill(value: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)) {
        Text(value, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WorkshopControls(
    text: WorkshopText,
    dn: Int,
    onDn: (Int) -> Unit,
    pn: Int,
    onPn: (Int) -> Unit,
    angle: String,
    onAngle: (String) -> Unit,
    offset: String,
    onOffset: (String) -> Unit,
    overall: String,
    onOverall: (String) -> Unit,
    weldGap: String,
    onWeldGap: (String) -> Unit,
    quantity: String,
    onQuantity: (String) -> Unit,
    sawKerf: String,
    onSawKerf: (String) -> Unit,
    stockLength: Int,
    onStockLength: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text.inputGeometry, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text.diameter, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(PIPE_INSTALLATION_SERIES, key = { it.dn }) { pipe ->
                    FilterChip(selected = dn == pipe.dn, onClick = { onDn(pipe.dn) }, label = { Text("DN ${pipe.dn}") })
                }
            }
            Text(text.pressureClass, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(listOf(6, 10, 16, 25, 40)) { value ->
                    FilterChip(selected = pn == value, onClick = { onPn(value) }, label = { Text("PN $value") })
                }
            }
            Text(text.elbowAngle, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(listOf("30", "45", "60", "90")) { value ->
                    FilterChip(selected = angle == value, onClick = { onAngle(value) }, label = { Text("$value°") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WorkshopNumericField(offset, onOffset, text.axisOffset, Modifier.weight(1f))
                WorkshopNumericField(overall, onOverall, text.faceToFaceOverall, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WorkshopNumericField(weldGap, onWeldGap, text.weldGap, Modifier.weight(1f))
                WorkshopNumericField(quantity, onQuantity, text.quantity, Modifier.weight(1f))
            }
            Text(text.stockBar, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(listOf(3_000, 6_000, 12_000)) { value ->
                    FilterChip(
                        selected = stockLength == value,
                        onClick = { onStockLength(value) },
                        label = { Text("${value / 1_000} m") },
                    )
                }
            }
            WorkshopNumericField(sawKerf, onSawKerf, text.sawKerf, Modifier.fillMaxWidth())
            Text(text.liveCalculation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.CalculateOffsetAssembly),
                shape = RoundedCornerShape(16.dp),
            ) { Text(text.refreshDrawing) }
        }
    }
}

@Composable
private fun WorkshopNumericField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun CutListPanel(result: FlangedOffsetAssemblyResult, text: WorkshopText) {
    val palette = LocalScenePalette.current
    ElevatedCard(
        Modifier.fillMaxWidth().testTag(PipeCalculatorTags.OffsetAssemblyResults),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text.cutList, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(text.cutListHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            result.cuts.forEachIndexed { index, cut ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = when (index) {
                        1 -> palette.blueprintCut.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(cut.code, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                            Text("${cut.quantity} × · ${technical(cut.startCutDeg)}° / ${technical(cut.endCutDeg)}°", style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            "${technical(cut.lengthMm)} mm",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (index == 1) palette.blueprintCut else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            WorkshopMetric(text.betweenWeldFaces, "F = ${technical(result.diagonalFaceToFaceMm)} mm")
            WorkshopMetric(text.insertCutLength, "C = ${technical(result.diagonalPipeCutMm)} mm")
        }
    }
}

@Composable
private fun StockPlanGraph(
    result: FlangedOffsetAssemblyResult,
    text: WorkshopText,
    modifier: Modifier = Modifier,
) {
    val palette = LocalScenePalette.current
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    OutlinedCard(modifier.fillMaxWidth().testTag(PipeCalculatorTags.WorkshopStockPlan), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text.stockPlan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${result.stockBarsRequired} × ${technical(result.input.stockLengthMm / 1_000.0)} m · ${text.firstFitPlan}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val shownBars = result.stockBars.take(4)
            Canvas(Modifier.fillMaxWidth().height((shownBars.size * 54 + 16).dp)) {
                val barWidth = size.width - 12f
                val colors = listOf(palette.blueprintPipe, palette.blueprintCut, palette.blueprintFitting)
                shownBars.forEachIndexed { barIndex, bar ->
                    val y = 8f + barIndex * 54f
                    drawRoundRect(
                        surfaceVariantColor,
                        Offset(6f, y),
                        Size(barWidth, 34f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                    )
                    var x = 6f
                    bar.cutsMm.forEachIndexed { cutIndex, length ->
                        val width = (barWidth * (length / result.input.stockLengthMm)).toFloat()
                        drawRect(colors[cutIndex % colors.size].copy(alpha = 0.82f), Offset(x, y), Size(width, 34f))
                        if (width > 46f) {
                            val paint = blueprintPaint(Color.White, 9.sp.toPx(), Paint.Align.CENTER, bold = true)
                            drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(technical(length), x + width / 2f, y + 22f, paint) }
                        }
                        x += width + (barWidth * result.input.sawKerfMm / result.input.stockLengthMm).toFloat()
                    }
                    val numberPaint = blueprintPaint(onSurfaceColor, 9.sp.toPx(), Paint.Align.LEFT, bold = true)
                    drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText("#${bar.number}", 8f, y + 48f, numberPaint) }
                }
            }
            if (result.stockBars.size > shownBars.size) {
                Text("+${result.stockBars.size - shownBars.size} ${text.moreBars}", style = MaterialTheme.typography.labelMedium)
            }
            WorkshopMetric(text.netPipe, "${technical(result.totalNetPipeLengthMm / 1_000.0)} m")
            WorkshopMetric(text.kerfLoss, "${technical(result.totalKerfLossMm)} mm")
            WorkshopMetric(text.offcut, "${technical(result.totalOffcutMm)} mm")
        }
    }
}

@Composable
private fun AssemblyMetrics(result: FlangedOffsetAssemblyResult, text: WorkshopText) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text.assemblyPassport, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            WorkshopMetric(text.centerTravel, "L = ${technical(result.diagonalCenterTravelMm)} mm")
            WorkshopMetric(text.centerAdvance, "Xa = ${technical(result.horizontalCenterAdvanceMm)} mm")
            WorkshopMetric(text.elbowTakeout, "A = ${technical(result.elbowTakeoutMm)} mm")
            WorkshopMetric(text.betweenWeldFaces, "F = ${technical(result.diagonalFaceToFaceMm)} mm")
            WorkshopMetric(text.pipeSpecification, "DN ${result.pipe.dn} · Ø ${technical(result.pipe.outsideDiameterMm)} × ${technical(result.pipe.wallThicknessMm)} mm")
            WorkshopMetric(text.fittings, "2 × ${technical(result.input.angleDeg)}° · R ${technical(result.elbow.centerlineRadiusMm)} mm")
            WorkshopMetric(text.flanges, "2 × DN ${result.flange.dn} PN ${result.flange.pn} · Type 11")
            WorkshopMetric(text.welds, result.weldCount.toString())
            WorkshopMetric(text.bolts, result.flangeBoltCount.toString())
            WorkshopMetric(text.pipeMass, "${technical(result.totalPipeMassKg, 3)} kg")
        }
    }
}

@Composable
private fun WorkshopMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun WorkshopAdvisory(result: FlangedOffsetAssemblyResult, text: WorkshopText) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text.fabricationCheck, fontWeight = FontWeight.Black)
            Text(text.formula, style = MaterialTheme.typography.bodySmall)
            result.warnings.forEach { warning -> Text("• ${text.warning(warning)}", style = MaterialTheme.typography.bodySmall) }
            Text(
                "${text.source}: ${result.elbow.source.organisation} · ${result.flange.source.organisation}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun WorkshopError(message: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
    }
}

private fun DrawScope.blueprintDimension(start: Offset, end: Offset, color: Color) {
    drawLine(color, start, end, 2f)
    val vertical = kotlin.math.abs(start.x - end.x) < kotlin.math.abs(start.y - end.y)
    if (vertical) {
        drawLine(color, Offset(start.x - 7f, start.y), Offset(start.x + 7f, start.y), 2f)
        drawLine(color, Offset(end.x - 7f, end.y), Offset(end.x + 7f, end.y), 2f)
    } else {
        drawLine(color, Offset(start.x, start.y - 7f), Offset(start.x, start.y + 7f), 2f)
        drawLine(color, Offset(end.x, end.y - 7f), Offset(end.x, end.y + 7f), 2f)
    }
}

private fun blueprintPaint(color: Color, sizePx: Float, align: Paint.Align, bold: Boolean = false) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textSize = sizePx
        textAlign = align
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

private operator fun Offset.times(scale: Float) = Offset(x * scale, y * scale)

private fun technical(value: Double, decimals: Int = 1): String = String.format(Locale.US, "%.${decimals}f", value)

private class WorkshopText(private val language: AppLanguage) {
    private fun t(russian: String, english: String) = localizedUi(language, russian, english)
    val title get() = t("Монтажная мастерская", "Fabrication workshop")
    val subtitle get() = t(
        "Один узел, один экран: вводите габарит — получаете контур, три длины реза, фланцы, сварные стыки и раскрой.",
        "One assembly, one screen: enter the envelope and get the contour, three cut lengths, flanges, welds and stock plan.",
    )
    val inputGeometry get() = t("Исходная геометрия", "Input geometry")
    val diameter get() = t("Диаметр трубы", "Pipe diameter")
    val pressureClass get() = t("Давление фланца", "Flange pressure class")
    val elbowAngle get() = t("Угол отвода", "Elbow angle")
    val axisOffset get() = t("Смещение осей H, mm", "Axis offset H, mm")
    val faceToFaceOverall get() = t("Между фланцами X, mm", "Flange face-to-face X, mm")
    val weldGap get() = t("Сварочный зазор g, mm", "Weld gap g, mm")
    val quantity get() = t("Количество узлов", "Assembly quantity")
    val stockBar get() = t("Исходный хлыст", "Stock bar")
    val sawKerf get() = t("Ширина реза пилы, mm", "Saw kerf, mm")
    val liveCalculation get() = t("Схема и длины обновляются сразу при каждом изменении.", "The drawing and lengths update immediately after every change.")
    val refreshDrawing get() = t("Обновить чертёж и раскрой", "Refresh drawing and cut plan")
    val pipe get() = t("Труба", "Pipe")
    val elbows get() = t("Отводы", "Elbows")
    val flanges get() = t("Фланцы", "Flanges")
    val cutList get() = t("Ведомость резов", "Pipe cut list")
    val cutListHint get() = t("P1 и P3 — прямые участки у фланцев; P2 — диагональная вставка между отводами.", "P1 and P3 are flange tails; P2 is the diagonal insert between elbows.")
    val betweenWeldFaces get() = t("Между сварными торцами F", "Between weld faces F")
    val insertCutLength get() = t("Длина вставки C", "Insert cut length C")
    val flangeThickness get() = t("Толщина диска", "Flange thickness")
    val stockPlan get() = t("График раскроя хлыстов", "Stock cutting chart")
    val firstFitPlan get() = t("практический раскрой", "practical first-fit plan")
    val moreBars get() = t("ещё хлыстов", "more bars")
    val netPipe get() = t("Чистая длина трубы", "Net pipe length")
    val kerfLoss get() = t("Потери на рез", "Saw kerf loss")
    val offcut get() = t("Остаток", "Offcut")
    val assemblyPassport get() = t("Паспорт сборки", "Assembly passport")
    val centerTravel get() = t("Между центрами отводов", "Elbow center travel")
    val centerAdvance get() = t("Продвижение центров", "Center advance")
    val elbowTakeout get() = t("Монтажный размер отвода", "Elbow take-out")
    val pipeSpecification get() = t("Труба", "Pipe specification")
    val fittings get() = t("Отводы", "Elbow fittings")
    val welds get() = t("Сварные стыки", "Butt-weld joints")
    val bolts get() = t("Болты для двух соединений", "Bolts for two connections")
    val pipeMass get() = t("Масса трубных заготовок", "Pipe-cut mass")
    val fabricationCheck get() = t("Проверка перед изготовлением", "Fabrication check")
    val formula get() = t(
        "P2 = H / sin(α) − 2A − 2g; P1 + P3 = X − 2h − 4g − 2A − H / tan(α).",
        "P2 = H / sin(α) − 2A − 2g; P1 + P3 = X − 2h − 4g − 2A − H / tan(α).",
    )
    val source get() = t("Источники размеров", "Dimension sources")
    val pipeCutsShort get() = t("реза", "cuts")
    val weldsShort get() = t("стыков", "welds")
    val checkInput get() = t("проверьте ввод", "check input")
    fun invalidInput(message: String?) = t("Проверьте габариты узла", "Check the assembly envelope") + message?.let { ": $it" }.orEmpty()
    fun warning(english: String) = when (english) {
        "Custom angle uses A = R × tan(α/2); verify the actual manufactured or trimmed elbow" -> t(
            "Для нестандартного угла A = R × tan(α/2); проверьте фактический изготовленный или подрезанный отвод.",
            english,
        )
        else -> t(
            "Перед резкой проверьте фактические монтажные размеры, исполнение уплотнительной поверхности и технологию сварки.",
            english,
        )
    }
}
