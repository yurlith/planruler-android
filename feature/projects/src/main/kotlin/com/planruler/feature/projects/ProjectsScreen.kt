package com.planruler.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planruler.designsystem.PlanRulerTestTags
import com.planruler.designsystem.component.EmptyState
import com.planruler.designsystem.component.IndicatorStatus
import com.planruler.designsystem.component.PlanRulerIconButton
import com.planruler.designsystem.component.PlanRulerHeroCard
import com.planruler.designsystem.component.PlanRulerMenuRow
import com.planruler.designsystem.component.PlanRulerToolTile
import com.planruler.designsystem.icon.PlanRulerIcons
import com.planruler.designsystem.layout.NavigationStyle
import com.planruler.designsystem.layout.rememberPlanRulerLayout
import com.planruler.designsystem.localization.localizedUi
import com.planruler.designsystem.localization.UiTextKey
import com.planruler.designsystem.localization.formatProjectCount
import com.planruler.designsystem.localization.uiText
import com.planruler.designsystem.theme.Space
import com.planruler.model.AppLanguage
import com.planruler.model.AppSettings
import com.planruler.model.Calibration
import com.planruler.model.PlanProject
import com.planruler.model.ProjectSort
import com.planruler.model.ProjectView
import com.planruler.project.api.TrashedProject
import java.text.DateFormat
import java.util.Date

enum class ProjectsTab { HOME, PROJECTS, WORKSHOP, CRM, MENU }

private enum class ProjectFilter { ACTIVE, RECENT, TRASH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    projects: List<PlanProject>,
    trashedProjects: List<TrashedProject>,
    settings: AppSettings,
    onSettings: (AppSettings) -> Unit,
    onImport: () -> Unit,
    onNewDrawing: () -> Unit,
    onOpen: (PlanProject) -> Unit,
    onRename: (PlanProject, String) -> Unit,
    onDuplicate: (PlanProject) -> Unit,
    onDelete: (PlanProject) -> Unit,
    onRestore: (PlanProject) -> Unit,
    onDeletePermanently: (PlanProject) -> Unit,
    settingsContent: @Composable (Modifier) -> Unit,
    calculatorContent: @Composable (Modifier) -> Unit,
    crmContent: @Composable (Modifier) -> Unit,
    initialTab: ProjectsTab = ProjectsTab.HOME,
) {
    val text = remember(settings.language) { Pt(settings.language) }
    val layout = rememberPlanRulerLayout(settings, focusMode = false)
    var tab by remember(initialTab) { mutableStateOf(initialTab) }
    var projectFilter by remember { mutableStateOf(ProjectFilter.ACTIVE) }
    var menuSettingsOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var renameProject by remember { mutableStateOf<PlanProject?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteProject by remember { mutableStateOf<PlanProject?>(null) }
    val projectTab = tab == ProjectsTab.PROJECTS
    val activeProjects = if (projectFilter == ProjectFilter.TRASH) trashedProjects.map { it.project } else projects

    val visible = remember(activeProjects, query, settings.projectSort, projectFilter) {
        activeProjects
            .filter { project ->
                query.isBlank() ||
                    project.name.contains(query, true) ||
                    project.documentUri.substringAfterLast('/').contains(query, true) ||
                    project.measurements.any { it.label.orEmpty().contains(query, true) }
            }
            .sortedWith(
                when (settings.projectSort) {
                    ProjectSort.MODIFIED -> compareByDescending { it.modifiedAtEpochMs }
                    ProjectSort.CREATED -> compareByDescending { it.createdAtEpochMs }
                    ProjectSort.NAME -> compareBy { it.name.lowercase() }
                    ProjectSort.MEASUREMENTS -> compareByDescending { it.measurements.size }
                },
            )
            .let {
                if (projectFilter == ProjectFilter.RECENT) {
                    it.sortedByDescending { project -> project.modifiedAtEpochMs }.take(10)
                } else {
                    it
                }
            }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (searching && projectTab) {
                        OutlinedTextField(
                            query,
                            { query = it.take(80) },
                            placeholder = { Text(text.search) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag(PlanRulerTestTags.ProjectsSearch),
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(topTitle(tab, menuSettingsOpen, settings.language), style = MaterialTheme.typography.titleLarge)
                            topSubtitle(tab, settings.language).takeIf(String::isNotBlank)?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (tab == ProjectsTab.MENU && menuSettingsOpen) {
                        PlanRulerIconButton(
                            PlanRulerIcons.Back,
                            uiText(settings.language, UiTextKey.BACK_TO_MENU),
                            { menuSettingsOpen = false },
                            Modifier.padding(start = Space.x1),
                        )
                    } else if (projectTab) {
                        PlanRulerIconButton(
                            if (searching) PlanRulerIcons.Close else PlanRulerIcons.Search,
                            text.search,
                            { searching = !searching; if (!searching) query = "" },
                            Modifier.padding(start = Space.x1),
                        )
                    }
                },
                actions = {
                    if (projectTab) {
                        Box {
                            PlanRulerIconButton(PlanRulerIcons.Sort, text.sort, { sortMenu = true })
                            DropdownMenu(sortMenu, { sortMenu = false }) {
                                ProjectSort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = { Text(text.sortLabel(sort)) },
                                        onClick = {
                                            sortMenu = false
                                            onSettings(settings.copy(projectSort = sort))
                                        },
                                    )
                                }
                            }
                        }
                        PlanRulerIconButton(
                            if (settings.projectView == ProjectView.GRID) PlanRulerIcons.ListView else PlanRulerIcons.Grid,
                            text.view,
                            {
                                onSettings(
                                    settings.copy(
                                        projectView = if (settings.projectView == ProjectView.GRID) {
                                            ProjectView.LIST
                                        } else {
                                            ProjectView.GRID
                                        },
                                    ),
                                )
                            },
                            Modifier.padding(end = Space.x1),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (layout.navigation == NavigationStyle.BOTTOM_BAR) {
                NavigationBar {
                    ProjectsTab.entries.forEach { candidate ->
                        NavigationBarItem(
                            selected = tab == candidate,
                            onClick = {
                                tab = candidate
                                if (candidate != ProjectsTab.MENU) menuSettingsOpen = false
                            },
                            modifier = Modifier.testTag(PlanRulerTestTags.navigation(candidate.name)),
                            icon = { Icon(tabIcon(candidate), null) },
                            label = { Text(text.tab(candidate)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (tab == ProjectsTab.PROJECTS && projectFilter != ProjectFilter.TRASH && projects.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onImport,
                    modifier = Modifier.testTag(PlanRulerTestTags.ProjectsFab),
                    icon = { Icon(PlanRulerIcons.Plus, null) },
                    text = { Text(text.importPlan) },
                )
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (layout.navigation != NavigationStyle.BOTTOM_BAR) {
                NavigationRail {
                    ProjectsTab.entries.forEach { candidate ->
                        NavigationRailItem(
                            selected = tab == candidate,
                            onClick = {
                                tab = candidate
                                if (candidate != ProjectsTab.MENU) menuSettingsOpen = false
                            },
                            modifier = Modifier.testTag(PlanRulerTestTags.navigation(candidate.name)),
                            icon = { Icon(tabIcon(candidate), null) },
                            label = { Text(text.tab(candidate)) },
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    tab == ProjectsTab.HOME -> HomeDashboard(
                        projects = projects,
                        language = settings.language,
                        onImport = onImport,
                        onNewDrawing = onNewDrawing,
                        onOpen = onOpen,
                        onProjects = { tab = ProjectsTab.PROJECTS },
                        onWorkshop = { tab = ProjectsTab.WORKSHOP },
                        onCrm = { tab = ProjectsTab.CRM },
                    )
                    tab == ProjectsTab.MENU && menuSettingsOpen -> Box(
                        Modifier.fillMaxSize().testTag(PlanRulerTestTags.MenuSettings),
                    ) {
                        settingsContent(Modifier.fillMaxSize())
                    }
                    tab == ProjectsTab.MENU -> AppMenu(
                        language = settings.language,
                        onProfile = { tab = ProjectsTab.CRM },
                        onSettings = { menuSettingsOpen = true },
                        onBackup = { menuSettingsOpen = true },
                        onCatalogues = { tab = ProjectsTab.WORKSHOP },
                        onPrivacy = { menuSettingsOpen = true },
                    )
                    tab == ProjectsTab.WORKSHOP -> calculatorContent(Modifier.fillMaxSize())
                    tab == ProjectsTab.CRM -> crmContent(Modifier.fillMaxSize())
                    tab == ProjectsTab.PROJECTS -> ProjectsContent(
                        filter = projectFilter,
                        onFilter = { projectFilter = it },
                        activeProjects = activeProjects,
                        visible = visible,
                        text = text,
                        language = settings.language,
                        view = settings.projectView,
                        onImport = onImport,
                        onNewDrawing = onNewDrawing,
                        onOpen = onOpen,
                        onRename = { project -> renameProject = project; renameText = project.name },
                        onDuplicate = onDuplicate,
                        onDelete = { deleteProject = it },
                        onRestore = onRestore,
                        onResetQuery = { query = "" },
                    )
                    else -> Unit
                }
            }
        }
    }

    renameProject?.let { project ->
        AlertDialog(
            onDismissRequest = { renameProject = null },
            title = { Text(text.renameProject) },
            text = {
                OutlinedTextField(
                    renameText,
                    { renameText = it.take(120) },
                    label = { Text(text.projectName) },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    enabled = renameText.isNotBlank(),
                    onClick = { onRename(project, renameText); renameProject = null },
                ) { Text(text.rename) }
            },
            dismissButton = { TextButton({ renameProject = null }) { Text(text.cancel) } },
        )
    }
    deleteProject?.let { project ->
        AlertDialog(
            onDismissRequest = { deleteProject = null },
            title = { Text(if (projectFilter == ProjectFilter.TRASH) text.deletePermanently else text.deleteProject) },
            text = {
                Text(
                    if (projectFilter == ProjectFilter.TRASH) text.deletePermanentlyMessage(project.name)
                    else text.deleteMessage(project.name),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (projectFilter == ProjectFilter.TRASH) onDeletePermanently(project) else onDelete(project)
                        deleteProject = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(text.delete) }
            },
            dismissButton = { TextButton({ deleteProject = null }) { Text(text.cancel) } },
        )
    }
}

private fun tabIcon(tab: ProjectsTab) = when (tab) {
    ProjectsTab.HOME -> PlanRulerIcons.Home
    ProjectsTab.PROJECTS -> PlanRulerIcons.Folder
    ProjectsTab.WORKSHOP -> PlanRulerIcons.Ruler
    ProjectsTab.CRM -> PlanRulerIcons.Schedule
    ProjectsTab.MENU -> PlanRulerIcons.Menu
}

private fun topTitle(tab: ProjectsTab, menuSettingsOpen: Boolean, language: AppLanguage): String = when (tab) {
    ProjectsTab.HOME -> uiText(language, UiTextKey.APP_NAME)
    ProjectsTab.PROJECTS -> uiText(language, UiTextKey.PROJECTS_TITLE)
    ProjectsTab.WORKSHOP -> uiText(language, UiTextKey.WORKSHOP_TITLE)
    ProjectsTab.CRM -> uiText(language, UiTextKey.NAV_CRM)
    ProjectsTab.MENU -> uiText(
        language,
        if (menuSettingsOpen) UiTextKey.MENU_SETTINGS else UiTextKey.MENU_TITLE,
    )
}

private fun topSubtitle(tab: ProjectsTab, language: AppLanguage): String = when (tab) {
    ProjectsTab.HOME -> uiText(language, UiTextKey.HOME_TITLE)
    ProjectsTab.PROJECTS -> uiText(language, UiTextKey.PROJECTS_SUBTITLE)
    ProjectsTab.WORKSHOP -> uiText(language, UiTextKey.WORKSHOP_SUBTITLE)
    ProjectsTab.CRM -> uiText(language, UiTextKey.LOCAL_ONLY)
    ProjectsTab.MENU -> uiText(language, UiTextKey.MENU_SUBTITLE)
}

@Composable
private fun HomeDashboard(
    projects: List<PlanProject>,
    language: AppLanguage,
    onImport: () -> Unit,
    onNewDrawing: () -> Unit,
    onOpen: (PlanProject) -> Unit,
    onProjects: () -> Unit,
    onWorkshop: () -> Unit,
    onCrm: () -> Unit,
) {
    val recent = remember(projects) { projects.sortedByDescending(PlanProject::modifiedAtEpochMs).take(3) }
    val latest = recent.firstOrNull()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(260.dp),
        modifier = Modifier.fillMaxSize().testTag(PlanRulerTestTags.HomeRoot),
        contentPadding = PaddingValues(start = Space.x4, end = Space.x4, top = Space.x4, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            PlanRulerHeroCard(
                title = latest?.name ?: uiText(language, UiTextKey.HOME_TITLE),
                body = if (latest == null) {
                    uiText(language, UiTextKey.HOME_SUBTITLE)
                } else {
                    uiText(language, UiTextKey.PROJECTS_SUBTITLE)
                },
                actionLabel = uiText(
                    language,
                    if (latest == null) UiTextKey.IMPORT_PLAN else UiTextKey.CONTINUE_PROJECT,
                ),
                onAction = { if (latest == null) onImport() else onOpen(latest) },
                icon = if (latest == null) PlanRulerIcons.Home else PlanRulerIcons.Folder,
                supportingText = formatProjectCount(language, projects.size),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(uiText(language, UiTextKey.QUICK_ACTIONS), style = MaterialTheme.typography.titleLarge)
        }
        item {
            PlanRulerToolTile(
                icon = PlanRulerIcons.Plus,
                title = newDrawingTitle(language),
                body = newDrawingBody(language),
                onClick = onNewDrawing,
                modifier = Modifier.testTag(PlanRulerTestTags.NewDrawing),
                accent = MaterialTheme.colorScheme.primary,
            )
        }
        item {
            PlanRulerToolTile(
                icon = PlanRulerIcons.Document,
                title = uiText(language, UiTextKey.IMPORT_PLAN),
                body = uiText(language, UiTextKey.PROJECTS_SUBTITLE),
                onClick = onImport,
                modifier = Modifier.testTag(PlanRulerTestTags.ProjectsFab),
            )
        }
        item {
            PlanRulerToolTile(
                icon = PlanRulerIcons.Ruler,
                title = uiText(language, UiTextKey.OPEN_WORKSHOP),
                body = uiText(language, UiTextKey.WORKSHOP_SUBTITLE),
                onClick = onWorkshop,
                accent = MaterialTheme.colorScheme.secondary,
            )
        }
        item {
            PlanRulerToolTile(
                icon = PlanRulerIcons.Schedule,
                title = uiText(language, UiTextKey.OPEN_CRM),
                body = uiText(language, UiTextKey.MENU_PROFILE_BODY),
                onClick = onCrm,
                accent = MaterialTheme.colorScheme.tertiary,
            )
        }
        item {
            PlanRulerToolTile(
                icon = PlanRulerIcons.Folder,
                title = uiText(language, UiTextKey.NAV_PROJECTS),
                body = formatProjectCount(language, projects.size),
                onClick = onProjects,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(
                    Modifier.fillMaxWidth().padding(Space.x4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.x3),
                ) {
                    Icon(PlanRulerIcons.Check, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Column {
                        Text(
                            uiText(language, UiTextKey.LOCAL_ONLY),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            uiText(language, UiTextKey.LOCAL_ONLY_BODY),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(uiText(language, UiTextKey.RECENT_PROJECTS), style = MaterialTheme.typography.titleLarge)
        }
        if (recent.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    uiText(language, UiTextKey.NO_RECENT_PROJECTS),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(recent, key = { "home-${it.id.value}" }) { project ->
                ElevatedCard(
                    Modifier.fillMaxWidth().clickable { onOpen(project) },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(Space.x4),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.x3),
                    ) {
                        Icon(PlanRulerIcons.Document, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(project.modifiedAtEpochMs)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(PlanRulerIcons.Forward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectsContent(
    filter: ProjectFilter,
    onFilter: (ProjectFilter) -> Unit,
    activeProjects: List<PlanProject>,
    visible: List<PlanProject>,
    text: Pt,
    language: AppLanguage,
    view: ProjectView,
    onImport: () -> Unit,
    onNewDrawing: () -> Unit,
    onOpen: (PlanProject) -> Unit,
    onRename: (PlanProject) -> Unit,
    onDuplicate: (PlanProject) -> Unit,
    onDelete: (PlanProject) -> Unit,
    onRestore: (PlanProject) -> Unit,
    onResetQuery: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.x4, vertical = Space.x2),
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            ProjectFilter.entries.forEach { candidate ->
                FilterChip(
                    selected = filter == candidate,
                    onClick = { onFilter(candidate) },
                    label = {
                        Text(
                            uiText(
                                language,
                                when (candidate) {
                                    ProjectFilter.ACTIVE -> UiTextKey.FILTER_ACTIVE
                                    ProjectFilter.RECENT -> UiTextKey.FILTER_RECENT
                                    ProjectFilter.TRASH -> UiTextKey.FILTER_TRASH
                                },
                            ),
                        )
                    },
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                activeProjects.isEmpty() -> EmptyState(
                    icon = if (filter == ProjectFilter.TRASH) PlanRulerIcons.Delete else PlanRulerIcons.Ruler,
                    title = if (filter == ProjectFilter.TRASH) text.trashEmpty else text.emptyTitle,
                    body = if (filter == ProjectFilter.TRASH) text.trashEmptyBody else text.empty,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    if (filter != ProjectFilter.TRASH) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Space.x2),
                        ) {
                            Button(onNewDrawing, Modifier.testTag(PlanRulerTestTags.NewDrawing)) {
                                Text(text.newDrawing)
                            }
                            TextButton(onImport, Modifier.testTag(PlanRulerTestTags.ProjectsFab)) {
                                Text(text.importPlan)
                            }
                        }
                    }
                }
                visible.isEmpty() -> EmptyState(
                    icon = PlanRulerIcons.Search,
                    title = text.nothingFound,
                    body = text.nothingFoundBody,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    TextButton(onResetQuery) { Text(text.resetFilters) }
                }
                else -> LazyVerticalGrid(
                    columns = if (view == ProjectView.GRID) GridCells.Adaptive(180.dp) else GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize().testTag(PlanRulerTestTags.ProjectsList),
                    contentPadding = PaddingValues(start = Space.x4, end = Space.x4, top = Space.x2, bottom = 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.x3),
                    verticalArrangement = Arrangement.spacedBy(Space.x3),
                ) {
                    items(visible, key = { it.id.value }) { project ->
                        ProjectCard(
                            project = project,
                            text = text,
                            grid = view == ProjectView.GRID,
                            onOpen = { onOpen(project) },
                            onRename = { onRename(project) },
                            onDuplicate = { onDuplicate(project) },
                            onDelete = { onDelete(project) },
                            trashed = filter == ProjectFilter.TRASH,
                            onRestore = { onRestore(project) },
                            onDeletePermanently = { onDelete(project) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppMenu(
    language: AppLanguage,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onBackup: () -> Unit,
    onCatalogues: () -> Unit,
    onPrivacy: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.x4).testTag(PlanRulerTestTags.MenuRoot),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        PlanRulerMenuRow(
            PlanRulerIcons.Schedule,
            uiText(language, UiTextKey.MENU_PROFILE),
            uiText(language, UiTextKey.MENU_PROFILE_BODY),
            onProfile,
            trailing = PlanRulerIcons.Forward,
        )
        PlanRulerMenuRow(
            PlanRulerIcons.Settings,
            uiText(language, UiTextKey.MENU_SETTINGS),
            uiText(language, UiTextKey.MENU_SETTINGS_BODY),
            onSettings,
            modifier = Modifier.testTag(PlanRulerTestTags.MenuSettingsEntry),
            trailing = PlanRulerIcons.Forward,
        )
        PlanRulerMenuRow(
            PlanRulerIcons.Export,
            uiText(language, UiTextKey.MENU_BACKUP),
            uiText(language, UiTextKey.MENU_BACKUP_BODY),
            onBackup,
            trailing = PlanRulerIcons.Forward,
        )
        PlanRulerMenuRow(
            PlanRulerIcons.Document,
            uiText(language, UiTextKey.MENU_NORMS),
            uiText(language, UiTextKey.MENU_NORMS_BODY),
            onCatalogues,
            trailing = PlanRulerIcons.Forward,
        )
        PlanRulerMenuRow(
            PlanRulerIcons.Check,
            uiText(language, UiTextKey.MENU_PRIVACY),
            uiText(language, UiTextKey.MENU_PRIVACY_BODY),
            onPrivacy,
            trailing = PlanRulerIcons.Forward,
        )
    }
}

@Composable
private fun ProjectCard(
    project: PlanProject,
    text: Pt,
    grid: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    trashed: Boolean,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val pdf = project.mimeType.contains("pdf", true)
    val calibrationStatus = when {
        project.calibration == null -> IndicatorStatus.ERROR
        project.calibration?.method == Calibration.Method.REFERENCE -> IndicatorStatus.OK
        else -> IndicatorStatus.WARNING
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag(PlanRulerTestTags.project(project.id.value)),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp, pressedElevation = 8.dp),
    ) {
        if (grid) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (pdf) PlanRulerIcons.Document else PlanRulerIcons.Image,
                        null,
                        Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        Modifier.align(Alignment.TopEnd).padding(Space.x2),
                        shape = CircleShape,
                        color = statusColor(calibrationStatus),
                    ) {
                        Text(
                            text.calibrationBadge(calibrationStatus),
                            Modifier.padding(horizontal = Space.x2, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
                CardBody(
                    project, text, onOpen, onRename, onDuplicate, onDelete,
                    trashed, onRestore, onDeletePermanently, menu,
                ) { menu = it }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .padding(Space.x3)
                        .size(56.dp, 72.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (pdf) PlanRulerIcons.Document else PlanRulerIcons.Image,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.weight(1f)) {
                    CardBody(
                        project, text, onOpen, onRename, onDuplicate, onDelete,
                        trashed, onRestore, onDeletePermanently, menu,
                    ) { menu = it }
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: IndicatorStatus) = when (status) {
    IndicatorStatus.OK -> MaterialTheme.colorScheme.secondary
    IndicatorStatus.WARNING -> MaterialTheme.colorScheme.tertiary
    IndicatorStatus.ERROR -> MaterialTheme.colorScheme.error
    IndicatorStatus.NEUTRAL -> MaterialTheme.colorScheme.outline
}

@Composable
private fun CardBody(
    project: PlanProject,
    text: Pt,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    trashed: Boolean,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    menu: Boolean,
    onMenu: (Boolean) -> Unit,
) {
    Row(Modifier.padding(Space.x3), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                project.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${project.pages.size} ${text.pages} · ${project.measurements.size} ${text.objects}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "${text.updated} ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(project.modifiedAtEpochMs))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            PlanRulerIconButton(PlanRulerIcons.More, text.menu, { onMenu(true) })
            DropdownMenu(menu, { onMenu(false) }) {
                if (trashed) {
                    DropdownMenuItem({ Text(text.restore) }, { onMenu(false); onRestore() })
                    DropdownMenuItem(
                        { Text(text.deletePermanently, color = MaterialTheme.colorScheme.error) },
                        { onMenu(false); onDeletePermanently() },
                    )
                } else {
                    DropdownMenuItem({ Text(text.open) }, { onMenu(false); onOpen() })
                    DropdownMenuItem({ Text(text.rename) }, { onMenu(false); onRename() })
                    DropdownMenuItem({ Text(text.duplicate) }, { onMenu(false); onDuplicate() })
                    DropdownMenuItem(
                        { Text(text.moveToTrash, color = MaterialTheme.colorScheme.error) },
                        { onMenu(false); onDelete() },
                    )
                }
            }
        }
    }
}

class Pt(private val language: AppLanguage) {
    private fun t(russian: String, english: String) = localizedUi(language, russian, english)
    val title get() = t("Проекты PlanRuler", "PlanRuler projects")
    val subtitle get() = t("Планы, измерения и ведомости", "Plans, measurements and takeoffs")
    val settings get() = t("Настройки", "Settings")
    val calculator get() = t("Монтажный калькулятор", "Pipe calculator")
    val crm get() = t("Клиенты и работы", "Clients and jobs")
    val search get() = t("Поиск", "Search")
    val sort get() = t("Сортировка", "Sort")
    val view get() = t("Вид", "View")
    val menu get() = t("Меню проекта", "Project menu")
    val importPlan get() = t("Импортировать план", "Import plan")
    val newDrawing get() = newDrawingTitle(language)
    val emptyTitle get() = t("Начните с чертежа или плана", "Start with a drawing or plan")
    val empty
        get() = t(
            "Откройте пустой лист или импортируйте PDF/изображение.",
            "Open a blank sheet or import a PDF/image.",
        )
    val nothingFound get() = t("Ничего не найдено", "Nothing found")
    val nothingFoundBody get() = t("Измените запрос или сбросьте фильтры.", "Change the query or reset the filters.")
    val resetFilters get() = t("Сбросить", "Reset")
    val updated get() = t("Изменён", "Updated")
    val pages get() = t("стр.", "pages")
    val objects get() = t("объектов", "objects")
    val open get() = t("Открыть", "Open")
    val rename get() = t("Переименовать", "Rename")
    val duplicate get() = t("Дублировать", "Duplicate")
    val delete get() = t("Удалить", "Delete")
    val moveToTrash get() = t("Переместить в корзину", "Move to trash")
    val trash get() = t("Корзина", "Trash")
    val trashEmpty get() = t("Корзина пуста", "Trash is empty")
    val trashEmptyBody get() = t("Удалённые проекты остаются здесь, пока вы не удалите их окончательно.", "Deleted projects stay here until you remove them permanently.")
    val restore get() = t("Восстановить", "Restore")
    val deletePermanently get() = t("Удалить окончательно", "Delete permanently")
    val cancel get() = t("Отмена", "Cancel")
    val renameProject get() = t("Переименовать проект", "Rename project")
    val projectName get() = t("Название проекта", "Project name")
    val deleteProject get() = t("Удалить проект?", "Delete project?")

    fun deleteMessage(name: String) = t(
        "Проект «$name» будет перемещён в корзину. Его можно восстановить.",
        "“$name” will be moved to the recycle bin and can be restored.",
    )

    fun deletePermanentlyMessage(name: String) = t(
        "Проект «$name» и сохранённые локальные версии будут уничтожены без возможности восстановления. Внешний backup не удаляется.",
        "“$name” and its local saved versions will be destroyed and cannot be restored. External backups are not deleted.",
    )

    fun tab(tab: ProjectsTab) = when (tab) {
        ProjectsTab.HOME -> uiText(language, UiTextKey.NAV_HOME)
        ProjectsTab.PROJECTS -> uiText(language, UiTextKey.NAV_PROJECTS)
        ProjectsTab.WORKSHOP -> uiText(language, UiTextKey.NAV_WORKSHOP)
        ProjectsTab.CRM -> uiText(language, UiTextKey.NAV_CRM)
        ProjectsTab.MENU -> uiText(language, UiTextKey.NAV_MENU)
    }

    fun sortLabel(sort: ProjectSort) = when (sort) {
        ProjectSort.MODIFIED -> t("Последние изменённые", "Recently modified")
        ProjectSort.CREATED -> t("Последние созданные", "Recently created")
        ProjectSort.NAME -> t("По названию", "By name")
        ProjectSort.MEASUREMENTS -> t("По количеству измерений", "By measurement count")
    }

    fun calibrationBadge(status: IndicatorStatus) = when (status) {
        IndicatorStatus.OK -> t("масштаб", "scale")
        IndicatorStatus.WARNING -> t("проверить", "check")
        IndicatorStatus.ERROR -> t("нет масштаба", "no scale")
        IndicatorStatus.NEUTRAL -> ""
    }
}

private fun newDrawingTitle(language: AppLanguage): String = when (language) {
    AppLanguage.POLISH -> "Nowy rysunek"
    AppLanguage.ENGLISH -> "New drawing"
    AppLanguage.GERMAN -> "Neue Zeichnung"
    AppLanguage.FRENCH -> "Nouveau dessin"
    AppLanguage.ITALIAN -> "Nuovo disegno"
    AppLanguage.RUSSIAN -> "Новый чертёж"
}

private fun newDrawingBody(language: AppLanguage): String = when (language) {
    AppLanguage.POLISH -> "Zacznij od pustego arkusza A4 bez konfiguracji projektu."
    AppLanguage.ENGLISH -> "Start on a blank A4 sheet without project setup."
    AppLanguage.GERMAN -> "Ohne Projekteinrichtung auf einem leeren A4-Blatt beginnen."
    AppLanguage.FRENCH -> "Commencez sur une feuille A4 vierge sans configurer de projet."
    AppLanguage.ITALIAN -> "Inizia su un foglio A4 vuoto senza configurare un progetto."
    AppLanguage.RUSSIAN -> "Начните на пустом листе A4 без настройки проекта."
}
