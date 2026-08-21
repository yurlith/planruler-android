package com.planruler.feature.pipecalculator

import android.graphics.Paint
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.planruler.designsystem.theme.PlanRulerScenePalette
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
import com.planruler.pipecalculator.FabricationElement
import com.planruler.pipecalculator.FabricationElementKind
import com.planruler.pipecalculator.FabricationPointMm
import com.planruler.fabrication3d.Fabrication3DEngine
import com.planruler.pipecalculator.FlangedOffsetAssemblyInput
import com.planruler.pipecalculator.FlangedOffsetAssemblyResult
import com.planruler.pipecalculator.PIPE_INSTALLATION_SERIES
import com.planruler.pipecalculator.calculateFlangedOffsetAssembly
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
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
                    t("Сначала создайте проект или импортируйте план.", "Create a project or import a plan first."),
                    color = MaterialTheme.colorScheme.error,
                )
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
private fun AssemblyBlueprint(result: FlangedOffsetAssemblyResult, text: WorkshopText) {
    val palette = LocalScenePalette.current
    val transition = rememberInfiniteTransition(label = "fabrication-cut-pulse")
    val cutPulse by transition.animateFloat(
        initialValue = 0.58f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), repeatMode = RepeatMode.Reverse),
        label = "fabrication-cut-alpha",
    )
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .testTag(PipeCalculatorTags.OffsetDiagram)
            .semantics { contentDescription = text.blueprintDescription },
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(text.assemblyDrawing, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "DN ${result.pipe.dn} · PN ${result.flange.pn} · Ø ${technical(result.pipe.outsideDiameterMm)} × ${technical(result.pipe.wallThicknessMm)} mm",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(shape = RoundedCornerShape(12.dp), color = palette.blueprintCut.copy(alpha = 0.13f)) {
                    Text(
                        "P2 · ${technical(result.diagonalPipeCutMm)} mm",
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        color = palette.blueprintCut,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Text(
                "${text.cutPipe}: C = ${technical(result.diagonalPipeCutMm)} mm",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = palette.blueprintCut,
            )
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(palette.blueprintBackground, RoundedCornerShape(18.dp))
                    .padding(4.dp),
            ) {
                drawBlueprint(result, cutPulse, palette)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { BlueprintLegend(palette.blueprintPipe, text.pipe) }
                item { BlueprintLegend(palette.blueprintFitting, text.elbows) }
                item { BlueprintLegend(palette.blueprintFlange, text.flanges) }
                item { BlueprintLegend(palette.blueprintCut, text.cutMarks) }
            }
            Text(text.pointLegend, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun DrawScope.drawBlueprint(
    result: FlangedOffsetAssemblyResult,
    cutPulse: Float,
    palette: PlanRulerScenePalette,
) {
    var gx = 0f
    while (gx <= size.width) {
        drawLine(palette.blueprintGrid, Offset(gx, 0f), Offset(gx, size.height), 1f)
        gx += 24f
    }
    var gy = 0f
    while (gy <= size.height) {
        drawLine(palette.blueprintGrid, Offset(0f, gy), Offset(size.width, gy), 1f)
        gy += 24f
    }

    val flangeRadiusMm = result.flange.outsideDiameterMm / 2.0
    val logicalHeight = result.input.targetOffsetMm + result.flange.outsideDiameterMm
    val usableWidth = (size.width - 88f).coerceAtLeast(1f)
    val usableHeight = (size.height - 92f).coerceAtLeast(1f)
    val scale = min(
        usableWidth / result.input.overallFaceToFaceMm.toFloat(),
        usableHeight / logicalHeight.toFloat(),
    )
    val contentWidth = result.input.overallFaceToFaceMm.toFloat() * scale
    val contentHeight = logicalHeight.toFloat() * scale
    val originX = (size.width - contentWidth) / 2f
    val originY = (size.height - contentHeight) / 2f
    fun map(point: FabricationPointMm) = Offset(
        originX + point.x.toFloat() * scale,
        originY + (result.input.targetOffsetMm + flangeRadiusMm - point.y).toFloat() * scale,
    )

    val pipeWidth = (result.pipe.outsideDiameterMm.toFloat() * scale).coerceIn(10f, 34f)
    val boreRatio = ((result.pipe.outsideDiameterMm - 2.0 * result.pipe.wallThicknessMm) /
        result.pipe.outsideDiameterMm).toFloat()
    val boreWidth = (pipeWidth * boreRatio).coerceAtMost(pipeWidth - 3f)
    result.elements.filter { it.kind == FabricationElementKind.PIPE }.forEach { element ->
        val path = Path().apply { moveTo(map(element.start).x, map(element.start).y); lineTo(map(element.end).x, map(element.end).y) }
        val color = if (element.code == "P2") palette.blueprintCut.copy(alpha = cutPulse) else palette.blueprintPipe
        drawPath(path, Color.Black.copy(alpha = 0.55f), style = Stroke(pipeWidth + 5f, cap = StrokeCap.Butt))
        drawPath(path, color, style = Stroke(pipeWidth, cap = StrokeCap.Butt))
        drawPath(path, palette.blueprintBackground, style = Stroke(boreWidth, cap = StrokeCap.Butt))
    }
    result.elements.filter { it.kind == FabricationElementKind.ELBOW }.forEach { element ->
        val control = requireNotNull(element.control)
        val path = Path().apply {
            moveTo(map(element.start).x, map(element.start).y)
            quadraticTo(map(control).x, map(control).y, map(element.end).x, map(element.end).y)
        }
        drawPath(path, Color.Black.copy(alpha = 0.55f), style = Stroke(pipeWidth + 5f, cap = StrokeCap.Butt))
        drawPath(path, palette.blueprintFitting, style = Stroke(pipeWidth, cap = StrokeCap.Butt))
        drawPath(path, palette.blueprintBackground, style = Stroke(boreWidth, cap = StrokeCap.Butt))
    }
    drawWorkshopFlange(result, result.elements.first { it.code == "F1" }, true, scale, ::map, pipeWidth, palette)
    drawWorkshopFlange(result, result.elements.first { it.code == "F2" }, false, scale, ::map, pipeWidth, palette)

    val centerline = Path()
    result.elements.filter { it.kind != FabricationElementKind.WELD_GAP }.forEachIndexed { index, element ->
        val start = map(element.start)
        if (index == 0) centerline.moveTo(start.x, start.y) else centerline.lineTo(start.x, start.y)
        element.control?.let { control ->
            val end = map(element.end)
            centerline.quadraticTo(map(control).x, map(control).y, end.x, end.y)
        } ?: centerline.lineTo(map(element.end).x, map(element.end).y)
    }
    drawPath(
        centerline,
        palette.blueprintText.copy(alpha = 0.72f),
        style = Stroke(1.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f), 0f)),
    )

    result.elements.filter { it.kind == FabricationElementKind.WELD_GAP }.forEach { gap ->
        val a = map(gap.start)
        val b = map(gap.end)
        val vx = b.x - a.x
        val vy = b.y - a.y
        val length = kotlin.math.sqrt(vx * vx + vy * vy).coerceAtLeast(0.001f)
        val normal = Offset(-vy / length, vx / length) * (pipeWidth * 0.7f)
        val mid = (a + b) * 0.5f
        drawLine(palette.blueprintText, mid - normal, mid + normal, 2.2f)
    }
    result.elements.filter { it.kind == FabricationElementKind.PIPE }.forEach { pipe ->
        listOf(pipe.start, pipe.end).forEach { point ->
            val p = map(point)
            val other = if (point == pipe.start) map(pipe.end) else map(pipe.start)
            val vx = other.x - p.x
            val vy = other.y - p.y
            val length = kotlin.math.sqrt(vx * vx + vy * vy).coerceAtLeast(0.001f)
            val normal = Offset(-vy / length, vx / length) * (pipeWidth * 0.82f)
            drawLine(palette.blueprintCut, p - normal, p + normal, 3.2f)
        }
    }

    val start = map(result.startFace)
    val end = map(result.endFace)
    val e1Corner = requireNotNull(result.elements.single { it.code == "E1" }.control)
    val e2Corner = requireNotNull(result.elements.single { it.code == "E2" }.control)
    val corner1 = map(e1Corner)
    val corner2 = map(e2Corner)

    // Classical two-tier chain: the stations a fitter marks out first, then the overall
    // size below them. Without the inner tier the drawing only stated X and H, which is
    // not enough to set the part out.
    val stationDimensionY = size.height - 54f
    val bottomDimensionY = size.height - 26f
    val stations = listOf(
        Triple(start.x, corner1.x, e1Corner.x - result.startFace.x),
        Triple(corner1.x, corner2.x, e2Corner.x - e1Corner.x),
        Triple(corner2.x, end.x, result.endFace.x - e2Corner.x),
    )
    stations.forEach { (fromX, toX, _) ->
        blueprintDimension(
            Offset(fromX, stationDimensionY),
            Offset(toX, stationDimensionY),
            palette.blueprintDimension,
        )
    }
    listOf(start.x to start.y, corner1.x to corner1.y, corner2.x to corner2.y, end.x to end.y)
        .forEach { (x, y) ->
            drawLine(
                palette.blueprintDimension.copy(alpha = 0.5f),
                Offset(x, y),
                Offset(x, stationDimensionY + 6f),
                1.2f,
            )
        }

    blueprintDimension(Offset(start.x, bottomDimensionY), Offset(end.x, bottomDimensionY), palette.blueprintDimension)
    drawLine(palette.blueprintDimension.copy(alpha = 0.65f), start, Offset(start.x, bottomDimensionY + 6f), 1.2f)
    drawLine(palette.blueprintDimension.copy(alpha = 0.65f), end, Offset(end.x, bottomDimensionY + 6f), 1.2f)
    val heightDimensionX = 24f
    blueprintDimension(Offset(heightDimensionX, start.y), Offset(heightDimensionX, end.y), palette.blueprintDimension)
    drawLine(palette.blueprintDimension.copy(alpha = 0.65f), Offset(heightDimensionX - 6f, start.y), start, 1.2f)
    drawLine(palette.blueprintDimension.copy(alpha = 0.65f), Offset(heightDimensionX - 6f, end.y), end, 1.2f)

    val p2 = result.elements.single { it.code == "P2" }
    val p2Start = map(p2.start)
    val p2End = map(p2.end)
    val p2Mid = (p2Start + p2End) * 0.5f
    val angleOnCanvas = Math.toDegrees(atan2((p2End.y - p2Start.y).toDouble(), (p2End.x - p2Start.x).toDouble())).toFloat()
    val e1 = result.elements.single { it.code == "E1" }
    val center = map(requireNotNull(e1.control))
    drawArc(
        palette.blueprintDimension,
        startAngle = -result.input.angleDeg.toFloat(),
        sweepAngle = result.input.angleDeg.toFloat(),
        useCenter = false,
        topLeft = Offset(center.x - 32f, center.y - 32f),
        size = Size(64f, 64f),
        style = Stroke(2f),
    )

    val paint = blueprintPaint(palette.blueprintText, 10.sp.toPx(), Paint.Align.CENTER)
    val highlightPaint = blueprintPaint(palette.blueprintCut, 11.sp.toPx(), Paint.Align.CENTER, bold = true)
    val dimensionPaint = blueprintPaint(palette.blueprintDimension, 10.sp.toPx(), Paint.Align.CENTER, bold = true)
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText(
            "X = ${technical(result.input.overallFaceToFaceMm)} mm",
            (start.x + end.x) / 2f,
            bottomDimensionY - 8f,
            dimensionPaint,
        )
        canvas.nativeCanvas.save()
        canvas.nativeCanvas.rotate(-90f, heightDimensionX - 7f, (start.y + end.y) / 2f)
        canvas.nativeCanvas.drawText(
            "H = ${technical(result.input.targetOffsetMm)} mm",
            heightDimensionX - 7f,
            (start.y + end.y) / 2f - 7f,
            dimensionPaint,
        )
        canvas.nativeCanvas.restore()
        canvas.nativeCanvas.save()
        canvas.nativeCanvas.rotate(angleOnCanvas, p2Mid.x, p2Mid.y)
        canvas.nativeCanvas.drawText(
            "P2 · C ${technical(result.diagonalPipeCutMm)}",
            p2Mid.x,
            p2Mid.y - pipeWidth,
            highlightPaint,
        )
        canvas.nativeCanvas.restore()
        canvas.nativeCanvas.drawText(
            "α ${technical(result.input.angleDeg)}° · A ${technical(result.elbowTakeoutMm)} · " +
                "R ${technical(result.elbow.centerlineRadiusMm)}",
            center.x + 64f,
            center.y + 39f,
            paint,
        )
        // Station chain: the three set-out lengths that add up to X.
        stations.forEach { (fromX, toX, valueMm) ->
            canvas.nativeCanvas.drawText(
                technical(valueMm),
                (fromX + toX) / 2f,
                stationDimensionY - 8f,
                dimensionPaint,
            )
        }
        result.elements.filter { it.kind == FabricationElementKind.PIPE && it.code != "P2" }.forEach { element ->
            val mid = (map(element.start) + map(element.end)) * 0.5f
            canvas.nativeCanvas.drawText(
                "${element.code} · ${technical(requireNotNull(element.cutLengthMm))}",
                mid.x,
                mid.y - pipeWidth,
                paint,
            )
        }
        canvas.nativeCanvas.drawText("F1", start.x + 4f, start.y - flangeRadiusMm.toFloat() * scale - 8f, paint)
        canvas.nativeCanvas.drawText("F2", end.x - 4f, end.y - flangeRadiusMm.toFloat() * scale - 8f, paint)
    }
}

private fun DrawScope.drawWorkshopFlange(
    result: FlangedOffsetAssemblyResult,
    element: FabricationElement,
    isStart: Boolean,
    scale: Float,
    map: (FabricationPointMm) -> Offset,
    pipeWidth: Float,
    palette: PlanRulerScenePalette,
) {
    val face = map(if (isStart) element.start else element.end)
    val weld = map(if (isStart) element.end else element.start)
    val direction = if (isStart) 1f else -1f
    val discEndX = face.x + direction * result.flange.thicknessMm.toFloat() * scale
    val discLeft = min(face.x, discEndX)
    val outerRadius = (result.flange.outsideDiameterMm.toFloat() * scale / 2f).coerceAtLeast(pipeWidth * 0.9f)
    val boltRadius = result.flange.boltCircleDiameterMm.toFloat() * scale / 2f
    val holeRadius = (result.flange.boltHoleDiameterMm.toFloat() * scale / 2f).coerceAtLeast(2f)
    val discWidth = kotlin.math.abs(discEndX - face.x).coerceAtLeast(4f)
    drawRect(palette.blueprintFlange, Offset(discLeft, face.y - outerRadius), Size(discWidth, outerRadius * 2f))
    drawRect(Color.Black.copy(alpha = 0.5f), Offset(discLeft, face.y - outerRadius), Size(discWidth, outerRadius * 2f), style = Stroke(2f))
    val hub = Path().apply {
        moveTo(discEndX, face.y - pipeWidth * 0.82f)
        lineTo(weld.x, weld.y - pipeWidth / 2f)
        lineTo(weld.x, weld.y + pipeWidth / 2f)
        lineTo(discEndX, face.y + pipeWidth * 0.82f)
        close()
    }
    drawPath(hub, palette.blueprintFlange)
    drawPath(hub, Color.Black.copy(alpha = 0.5f), style = Stroke(2f))
    drawCircle(palette.blueprintBackground, holeRadius, Offset((face.x + discEndX) / 2f, face.y - boltRadius))
    drawCircle(palette.blueprintBackground, holeRadius, Offset((face.x + discEndX) / 2f, face.y + boltRadius))
    drawLine(palette.blueprintDimension, Offset(face.x, face.y - outerRadius), Offset(face.x, face.y + outerRadius), 2.6f)
}

@Composable
private fun BlueprintLegend(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Spacer(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
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
private fun FlangeFrontView(
    result: FlangedOffsetAssemblyResult,
    text: WorkshopText,
    modifier: Modifier = Modifier,
) {
    val palette = LocalScenePalette.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    OutlinedCard(modifier.fillMaxWidth().testTag(PipeCalculatorTags.WorkshopFlange), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text.flangePattern, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "DN ${result.flange.dn} · PN ${result.flange.pn} · ${result.flange.type}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Canvas(Modifier.fillMaxWidth().height(190.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = min(size.width, size.height) * 0.39f
                val boltRadius = radius * (result.flange.boltCircleDiameterMm / result.flange.outsideDiameterMm).toFloat()
                val holeRadius = radius * (result.flange.boltHoleDiameterMm / result.flange.outsideDiameterMm).toFloat()
                val boreRadius = radius * (result.pipe.outsideDiameterMm / result.flange.outsideDiameterMm).toFloat() * 0.9f
                drawCircle(palette.blueprintFlange.copy(alpha = 0.28f), radius, center)
                drawCircle(palette.blueprintFlange, radius, center, style = Stroke(3f))
                drawCircle(surfaceColor, boreRadius, center)
                drawCircle(outlineColor, boreRadius, center, style = Stroke(2f))
                drawCircle(outlineVariantColor, boltRadius, center, style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))))
                repeat(result.flange.boltHoleCount) { index ->
                    val radians = 2.0 * PI * index / result.flange.boltHoleCount - PI / 2.0
                    val hole = Offset(
                        center.x + cos(radians).toFloat() * boltRadius,
                        center.y + sin(radians).toFloat() * boltRadius,
                    )
                    drawCircle(surfaceColor, holeRadius.coerceAtLeast(3.5f), hole)
                    drawCircle(palette.blueprintFlange, holeRadius.coerceAtLeast(3.5f), hole, style = Stroke(1.8f))
                }
            }
            WorkshopMetric("D / k", "${technical(result.flange.outsideDiameterMm)} / ${technical(result.flange.boltCircleDiameterMm)} mm")
            WorkshopMetric(text.boltHoles, "${result.flange.boltHoleCount} × Ø ${technical(result.flange.boltHoleDiameterMm)} mm")
            WorkshopMetric(text.flangeHeight, "h = ${technical(result.flange.faceToWeldMm)} mm")
            WorkshopMetric(text.flangeThickness, "b = ${technical(result.flange.thicknessMm)} mm")
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

private fun workshopNumber(value: String): Double =
    value.trim().replace(',', '.').toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number")

private fun workshopWholeNumber(value: String): Int {
    val number = workshopNumber(value)
    if (number % 1.0 != 0.0) throw IllegalArgumentException("Quantity must be a whole number")
    return number.toInt()
}

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
    val assemblyDrawing get() = t("Рабочий чертёж узла", "Assembly working drawing")
    val blueprintDescription get() = t("Размерная схема фланцевого смещения с тремя трубными заготовками.", "Dimensioned flanged-offset drawing with three pipe cuts.")
    val cutPipe get() = t("ОТРЕЗАТЬ ТРУБУ", "CUT PIPE")
    val pipe get() = t("Труба", "Pipe")
    val elbows get() = t("Отводы", "Elbows")
    val flanges get() = t("Фланцы", "Flanges")
    val cutMarks get() = t("Метки реза", "Cut marks")
    val pointLegend get() = t(
        "1 — вход отвода 1; Т1 — его сварной торец; 2–3 — труба C для резки; Т2 — сварной торец отвода 2; 4 — конец отвода 2.",
        "1 — elbow 1 inlet; F1 — its weld face; 2–3 — pipe C to cut; F2 — elbow 2 weld face; 4 — elbow 2 outlet.",
    )
    val cutList get() = t("Ведомость резов", "Pipe cut list")
    val cutListHint get() = t("P1 и P3 — прямые участки у фланцев; P2 — диагональная вставка между отводами.", "P1 and P3 are flange tails; P2 is the diagonal insert between elbows.")
    val betweenWeldFaces get() = t("Между сварными торцами F", "Between weld faces F")
    val insertCutLength get() = t("Длина вставки C", "Insert cut length C")
    val flangePattern get() = t("Фланец и болтовой круг", "Flange and bolt pattern")
    val boltHoles get() = t("Отверстия", "Bolt holes")
    val flangeHeight get() = t("Монтажная высота фланца", "Flange mounting height")
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
