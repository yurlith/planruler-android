package com.planruler.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planruler.app.di.AppGraph
import com.planruler.backup.BackupTrashEntry
import com.planruler.backup.EncryptedBackupCodec
import com.planruler.backup.PlanRulerBackupPayload
import com.planruler.designsystem.theme.PlanRulerTheme
import com.planruler.designsystem.localization.localizedUi
import com.planruler.document.api.BlankDocument
import com.planruler.export.api.ExportFormat
import com.planruler.export.api.ExportPageSelection
import com.planruler.feature.projects.ProjectsScreen
import com.planruler.feature.projects.ProjectsTab
import com.planruler.feature.pipecalculator.PipeCalculatorScreen
import com.planruler.feature.crm.CrmScreen
import com.planruler.feature.settings.SettingsScreen
import com.planruler.feature.workspace.WorkspaceScreen
import com.planruler.feature.workspace.WorkspaceViewModel
import com.planruler.model.AppSettings
import com.planruler.model.PlanProject
import com.planruler.model.ProjectId
import com.planruler.project.api.ProjectResult
import com.planruler.project.api.TrashedProject
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        val graph = (application as PlanRulerApp).graph
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val store = remember { SettingsStore(context) }
            var settings by remember { mutableStateOf(store.load()) }
            LaunchedEffect(settings.language) {
                Locale.setDefault(settings.language.locale())
            }
            PlanRulerTheme(settings) {
                Surface {
                    PlanRulerRoot(
                        graph = graph,
                        settings = settings,
                        onSettings = { next ->
                            settings = next
                            store.save(next)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanRulerRoot(
    graph: AppGraph,
    settings: AppSettings,
    onSettings: (AppSettings) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("planruler-ui-state", android.content.Context.MODE_PRIVATE)
    }
    var projectId by remember { mutableStateOf(preferences.getString("project_id", null)) }
    var importUri by remember { mutableStateOf(preferences.getString("import_uri", null)) }
    var importMime by remember { mutableStateOf(preferences.getString("import_mime", null)) }
    var workspaceKey by remember { mutableStateOf(projectId ?: importUri) }
    var projects by remember { mutableStateOf(emptyList<PlanProject>()) }
    var trash by remember { mutableStateOf(emptyList<TrashedProject>()) }
    var refresh by remember { mutableIntStateOf(0) }
    var projectsInitialTab by remember { mutableStateOf(ProjectsTab.HOME) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var pendingExportPassword by remember { mutableStateOf<CharArray?>(null) }
    var pendingImportPassword by remember { mutableStateOf<CharArray?>(null) }
    val scope = rememberCoroutineScope()

    val backupExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val password = pendingExportPassword
        pendingExportPassword = null
        if (uri == null || password == null) {
            password?.fill('\u0000')
        } else {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val active = (graph.projects.list() as? ProjectResult.Ok)?.value.orEmpty()
                        val deleted = (graph.projects.listTrash() as? ProjectResult.Ok)?.value.orEmpty()
                        val payload = PlanRulerBackupPayload(
                            createdAtEpochMs = System.currentTimeMillis(),
                            appVersion = BuildConfig.VERSION_NAME,
                            activeProjects = active,
                            trash = deleted.map { BackupTrashEntry(it.project, it.deletedAtEpochMs) },
                            crm = graph.crm.exportSnapshot(),
                        )
                        val encoded = EncryptedBackupCodec.encode(payload, password)
                        requireNotNull(context.contentResolver.openOutputStream(uri, "w")) {
                            "Cannot open backup destination"
                        }.use { it.write(encoded) }
                        encoded.fill(0)
                    }
                }.onSuccess {
                    backupStatus = localizedUi(settings.language, "Backup сохранён на устройстве", "Backup saved on device")
                }.onFailure {
                    password.fill('\u0000')
                    backupStatus = localizedUi(settings.language, "Ошибка backup", "Backup failed") + ": ${it.message.orEmpty()}"
                }
            }
        }
    }

    val backupImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val password = pendingImportPassword
        pendingImportPassword = null
        if (uri == null || password == null) {
            password?.fill('\u0000')
        } else {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) {
                            "Cannot open backup"
                        }.use { it.readBytes() }
                        val payload = EncryptedBackupCodec.decode(bytes, password)
                        bytes.fill(0)
                        val existing = (graph.projects.list() as? ProjectResult.Ok)?.value.orEmpty()
                        val deleted = (graph.projects.listTrash() as? ProjectResult.Ok)?.value.orEmpty()
                        val usedIds = (existing.map { it.id.value } + deleted.map { it.project.id.value }).toMutableSet()
                        fun unique(project: PlanProject): PlanProject {
                            if (usedIds.add(project.id.value)) return project
                            val id = UUID.randomUUID().toString()
                            usedIds += id
                            return project.copy(
                                id = ProjectId(id),
                                name = "${project.name} (${localizedUi(settings.language, "восстановлено", "restored")})",
                            )
                        }
                        payload.activeProjects.forEach { graph.projects.save(unique(it)) }
                        payload.trash.forEach { entry ->
                            val restoredTrash = unique(entry.project)
                            graph.projects.save(restoredTrash)
                            graph.projects.delete(restoredTrash.id)
                        }
                        graph.crm.importSnapshot(payload.crm)
                    }
                }.onSuccess {
                    refresh++
                    backupStatus = localizedUi(settings.language, "Backup восстановлен", "Backup restored")
                }.onFailure {
                    password.fill('\u0000')
                    backupStatus = localizedUi(settings.language, "Ошибка восстановления", "Restore failed") + ": ${it.message.orEmpty()}"
                }
            }
        }
    }

    LaunchedEffect(projectId, importUri, importMime) {
        preferences.edit()
            .apply {
                if (projectId == null) remove("project_id") else putString("project_id", projectId)
                if (importUri == null) remove("import_uri") else putString("import_uri", importUri)
                if (importMime == null) remove("import_mime") else putString("import_mime", importMime)
            }
            .apply()
    }

    LaunchedEffect(refresh, projectId) {
        if (projectId == null) {
            projects = (graph.projects.list() as? ProjectResult.Ok)?.value.orEmpty()
            trash = (graph.projects.listTrash() as? ProjectResult.Ok)?.value.orEmpty()
        }
    }

    if (projectId == null && importUri == null) {
        val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                importUri = it.toString()
                importMime = thisMimeType(it.toString())
                workspaceKey = it.toString()
                projectsInitialTab = ProjectsTab.PROJECTS
            }
        }
        ProjectsScreen(
            projects = projects,
            trashedProjects = trash,
            settings = settings,
            onSettings = onSettings,
            onImport = { importer.launch(arrayOf("application/pdf", "image/png", "image/jpeg")) },
            onNewDrawing = {
                val uri = BlankDocument.uri(UUID.randomUUID().toString())
                importUri = uri
                importMime = BlankDocument.MIME_TYPE
                workspaceKey = uri
                projectsInitialTab = ProjectsTab.PROJECTS
            },
            onOpen = {
                projectsInitialTab = ProjectsTab.PROJECTS
                projectId = it.id.value
                workspaceKey = it.id.value
            },
            onRename = { project, name ->
                scope.launch {
                    graph.projects.rename(project.id, name)
                    refresh++
                }
            },
            onDuplicate = { project ->
                scope.launch {
                    graph.projects.duplicate(project.id)
                    refresh++
                }
            },
            onDelete = { project ->
                scope.launch {
                    graph.projects.delete(project.id)
                    refresh++
                }
            },
            onRestore = { project ->
                scope.launch {
                    graph.projects.restore(project.id)
                    refresh++
                }
            },
            onDeletePermanently = { project ->
                scope.launch {
                    graph.projects.deletePermanently(project.id)
                    refresh++
                }
            },
            settingsContent = { modifier ->
                SettingsScreen(
                    settings = settings,
                    onSettings = onSettings,
                    modifier = modifier,
                    backupStatus = backupStatus,
                    onExportBackup = { password ->
                        pendingExportPassword?.fill('\u0000')
                        pendingExportPassword = password.toCharArray()
                        backupExporter.launch("planruler-${System.currentTimeMillis()}.planruler-backup")
                    },
                    onImportBackup = { password ->
                        pendingImportPassword?.fill('\u0000')
                        pendingImportPassword = password.toCharArray()
                        backupImporter.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
                    },
                )
            },
            calculatorContent = { modifier ->
                PipeCalculatorScreen(
                    language = settings.language,
                    fabrication3d = graph.fabrication3d,
                    modifier = modifier,
                    projectRepository = graph.projects,
                    onProjectsChanged = { refresh++ },
                    settings = settings,
                    onSettings = onSettings,
                )
            },
            crmContent = { modifier ->
                CrmScreen(
                    repository = graph.crm,
                    language = settings.language,
                    modifier = modifier,
                    projects = graph.projects,
                    onOpenProject = { id -> projectId = id.value; workspaceKey = id.value },
                )
            },
            initialTab = projectsInitialTab,
        )
    } else {
        val key = workspaceKey ?: projectId ?: importUri!!
        val vm: WorkspaceViewModel = viewModel(
            key = "workspace:$key",
            factory = workspaceFactory(graph, projectId?.let(::ProjectId), importUri, importMime, settings),
        )
        var pendingPageSelection by remember { mutableStateOf(ExportPageSelection.CURRENT) }
        var pendingFirstPage by remember { mutableIntStateOf(0) }
        var pendingLastPage by remember { mutableIntStateOf(0) }
        val pdfExporter = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf"),
        ) { uri ->
            uri?.let {
                vm.export(it.toString(), ExportFormat.ANNOTATED_PDF, pendingPageSelection, pendingFirstPage, pendingLastPage)
            }
        }
        val csvExporter = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri ->
            uri?.let {
                vm.export(it.toString(), ExportFormat.CSV, pendingPageSelection, pendingFirstPage, pendingLastPage)
            }
        }
        val jsonExporter = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            uri?.let {
                vm.export(it.toString(), ExportFormat.JSON, pendingPageSelection, pendingFirstPage, pendingLastPage)
            }
        }
        val revisionImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                vm.preparePageRevision(it.toString(), thisMimeType(it.toString()))
            }
        }
        WorkspaceScreen(
            viewModel = vm,
            settings = settings,
            onSettings = onSettings,
            onProjectResolved = { id ->
                if (projectId == null) {
                    projectId = id.value
                    importUri = null
                    importMime = null
                }
            },
            onBack = {
                vm.save()
                projectId = null
                importUri = null
                importMime = null
                workspaceKey = null
                refresh++
            },
            onExport = { format, selection, first, last ->
                pendingPageSelection = selection
                pendingFirstPage = first
                pendingLastPage = last
                when (format) {
                    ExportFormat.ANNOTATED_PDF -> pdfExporter.launch("planruler-annotated.pdf")
                    ExportFormat.CSV -> csvExporter.launch("planruler-measurements.csv")
                    ExportFormat.JSON -> jsonExporter.launch("planruler-project.json")
                }
            },
            onImportRevision = {
                revisionImporter.launch(arrayOf("application/pdf", "image/png", "image/jpeg"))
            },
            onCalculateAssembly = {
                projectsInitialTab = ProjectsTab.WORKSHOP
                projectId = null
                importUri = null
                importMime = null
                workspaceKey = null
                refresh++
            },
        )
    }
}

private fun workspaceFactory(
    graph: AppGraph,
    id: ProjectId?,
    uri: String?,
    mime: String?,
    settings: AppSettings,
) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkspaceViewModel(
        engine = graph.newEngine(),
        snapEngine = graph.newSnapEngine(),
        documents = graph.documents,
        projects = graph.projects,
        exports = graph.exports,
        projectId = id,
        importUri = uri,
        mimeType = mime,
        // A project created right after import must already use the preferred unit.
        initialSettings = settings,
    ) as T
}

private fun thisMimeType(uri: String): String = when {
    uri.endsWith(".pdf", true) -> "application/pdf"
    uri.endsWith(".png", true) -> "image/png"
    else -> "image/jpeg"
}

private fun com.planruler.model.AppLanguage.locale(): Locale = when (this) {
    com.planruler.model.AppLanguage.POLISH -> Locale.forLanguageTag("pl-PL")
    com.planruler.model.AppLanguage.ENGLISH -> Locale.forLanguageTag("en-GB")
    com.planruler.model.AppLanguage.GERMAN -> Locale.forLanguageTag("de-CH")
    com.planruler.model.AppLanguage.FRENCH -> Locale.forLanguageTag("fr-CH")
    com.planruler.model.AppLanguage.ITALIAN -> Locale.forLanguageTag("it-CH")
    com.planruler.model.AppLanguage.RUSSIAN -> Locale.forLanguageTag("ru-RU")
}
