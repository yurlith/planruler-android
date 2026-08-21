package com.planruler.feature.crm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.planruler.crm.api.ClientId
import com.planruler.crm.api.CrmClient
import com.planruler.crm.api.CrmRepository
import com.planruler.crm.api.LocalProfile
import com.planruler.crm.api.LocalProfileId
import com.planruler.crm.api.WorkOrder
import com.planruler.crm.api.WorkOrderId
import com.planruler.crm.api.WorkOrderStage
import com.planruler.designsystem.component.EmptyState
import com.planruler.designsystem.component.IndicatorChip
import com.planruler.designsystem.component.IndicatorStatus
import com.planruler.designsystem.icon.PlanRulerIcons
import com.planruler.designsystem.localization.localizedUi
import com.planruler.designsystem.theme.Space
import com.planruler.model.AppLanguage
import com.planruler.model.PlanProject
import com.planruler.model.ProjectId
import com.planruler.project.api.ProjectRepository
import com.planruler.project.api.ProjectResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.min
import kotlin.math.pow

object CrmTags {
    const val Accounts = "crm_accounts"
    const val Workspace = "crm_workspace"
    const val CreateProfile = "crm_create_profile"
    const val ProfileName = "crm_profile_name"
    const val ProfileCompany = "crm_profile_company"
    const val Pin = "crm_pin"
    const val ConfirmProfile = "crm_confirm_profile"
    const val AddClient = "crm_add_client"
    const val ClientName = "crm_client_name"
    const val ConfirmClient = "crm_confirm_client"
    const val AddWorkOrder = "crm_add_work_order"
    const val WorkOrderTitle = "crm_work_order_title"
    const val ConfirmWorkOrder = "crm_confirm_work_order"
    const val Lock = "crm_lock"
    const val ShowArchived = "crm_show_archived"
    const val ConfirmDelete = "crm_confirm_delete"
    const val DeleteProfile = "crm_delete_profile"
    const val ProjectPicker = "crm_project_picker"
    fun profile(id: String) = "crm_profile:$id"
    fun deleteClient(id: String) = "crm_delete_client:$id"
    fun unarchiveClient(id: String) = "crm_unarchive_client:$id"
    fun deleteWorkOrder(id: String) = "crm_delete_work_order:$id"
    fun attachProject(id: String) = "crm_attach_project:$id"
    fun pickProject(id: String) = "crm_pick_project:$id"
}

@Composable
fun CrmScreen(
    repository: CrmRepository,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    projects: ProjectRepository? = null,
    onOpenProject: (ProjectId) -> Unit = {},
) {
    val text = remember(language) { CrmText(language) }
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf(emptyList<LocalProfile>()) }
    var takeoffProjects by remember { mutableStateOf(emptyList<PlanProject>()) }
    var attachProjectFor by remember { mutableStateOf<WorkOrder?>(null) }
    var activeProfile by remember { mutableStateOf<LocalProfile?>(null) }
    var clients by remember { mutableStateOf(emptyList<CrmClient>()) }
    var workOrders by remember { mutableStateOf(emptyList<WorkOrder>()) }
    var showArchived by remember { mutableStateOf(false) }
    var createProfile by remember { mutableStateOf(false) }
    var unlockProfile by remember { mutableStateOf<LocalProfile?>(null) }
    var addClient by remember { mutableStateOf(false) }
    var addWorkOrder by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteClientPrompt by remember { mutableStateOf<CrmClient?>(null) }
    var deleteWorkOrderPrompt by remember { mutableStateOf<WorkOrder?>(null) }
    var deleteProfilePrompt by remember { mutableStateOf<LocalProfile?>(null) }
    // Keyed per profile: exhausting one profile's attempts must not lock out another.
    val pinLocks = remember { mutableStateMapOf<LocalProfileId, PinLockState>() }

    suspend fun refreshProfiles() {
        profiles = repository.listProfiles()
    }
    suspend fun refreshWorkspace(profile: LocalProfile) {
        clients = repository.listClients(profile.id, showArchived)
        workOrders = repository.listWorkOrders(profile.id)
    }

    LaunchedEffect(Unit) { refreshProfiles() }
    LaunchedEffect(activeProfile?.id, showArchived) { activeProfile?.let { refreshWorkspace(it) } }
    LaunchedEffect(projects) {
        takeoffProjects = (projects?.list() as? ProjectResult.Ok)?.value.orEmpty()
    }

    if (activeProfile == null) {
        LocalAccounts(
            profiles = profiles,
            text = text,
            modifier = modifier,
            error = error,
            onCreate = { createProfile = true },
            onUnlock = { unlockProfile = it; error = null },
            onDeleteRequest = { deleteProfilePrompt = it },
        )
    } else {
        CrmWorkspace(
            profile = requireNotNull(activeProfile),
            clients = clients,
            workOrders = workOrders,
            showArchived = showArchived,
            text = text,
            modifier = modifier,
            onLock = { activeProfile = null; clients = emptyList(); workOrders = emptyList() },
            onAddClient = { addClient = true },
            onAddWorkOrder = { addWorkOrder = true },
            onShowArchivedChange = { showArchived = it },
            onArchiveClient = { client ->
                scope.launch {
                    repository.archiveClient(client.profileId, client.id, System.currentTimeMillis())
                    refreshWorkspace(requireNotNull(activeProfile))
                }
            },
            onUnarchiveClient = { client ->
                scope.launch {
                    repository.unarchiveClient(client.profileId, client.id, System.currentTimeMillis())
                    refreshWorkspace(requireNotNull(activeProfile))
                }
            },
            onDeleteClient = { deleteClientPrompt = it },
            onDeleteWorkOrder = { deleteWorkOrderPrompt = it },
            onAdvance = { workOrder ->
                scope.launch {
                    val next = workOrder.stage.next()
                    repository.saveWorkOrder(
                        workOrder.copy(stage = next, modifiedAtEpochMs = System.currentTimeMillis()),
                    )
                    refreshWorkspace(requireNotNull(activeProfile))
                }
            },
            projectName = { id -> takeoffProjects.firstOrNull { it.id.value == id }?.name },
            onAttachProject = { attachProjectFor = it },
            onOpenProject = { id -> onOpenProject(ProjectId(id)) },
            onUnlinkProject = { workOrder ->
                scope.launch {
                    repository.saveWorkOrder(workOrder.copy(projectId = null, modifiedAtEpochMs = System.currentTimeMillis()))
                    refreshWorkspace(requireNotNull(activeProfile))
                }
            },
        )
    }

    attachProjectFor?.let { workOrder ->
        ProjectPickerDialog(
            projects = takeoffProjects,
            text = text,
            onDismiss = { attachProjectFor = null },
            onSelect = { project ->
                scope.launch {
                    repository.saveWorkOrder(
                        workOrder.copy(projectId = project.id.value, modifiedAtEpochMs = System.currentTimeMillis()),
                    )
                    attachProjectFor = null
                    refreshWorkspace(requireNotNull(activeProfile))
                }
            },
        )
    }

    if (createProfile) {
        CreateProfileDialog(
            text = text,
            onDismiss = { createProfile = false },
            onCreate = { name, company, pin ->
                scope.launch {
                    runCatching { repository.createProfile(name, company, pin.toCharArray()) }
                        .onSuccess {
                            createProfile = false
                            refreshProfiles()
                            activeProfile = it
                        }
                        .onFailure { error = it.message }
                }
            },
        )
    }

    unlockProfile?.let { profile ->
        PinDialog(
            profile = profile,
            text = text,
            lock = pinLocks[profile.id] ?: PinLockState(),
            onDismiss = { unlockProfile = null },
            onUnlock = { pin ->
                scope.launch {
                    if (repository.verifyPin(profile.id, pin.toCharArray())) {
                        repository.touchProfile(profile.id)
                        activeProfile = profile
                        unlockProfile = null
                        error = null
                        pinLocks.remove(profile.id)
                    } else {
                        pinLocks[profile.id] = (pinLocks[profile.id] ?: PinLockState()).afterFailure(System.currentTimeMillis())
                    }
                }
            },
        )
    }

    deleteClientPrompt?.let { client ->
        DeleteConfirmDialog(
            title = text.deleteClientTitle,
            body = text.deleteClientBody(client.displayName),
            text = text,
            onDismiss = { deleteClientPrompt = null },
            onConfirm = {
                scope.launch {
                    repository.deleteClient(client.profileId, client.id)
                    deleteClientPrompt = null
                    refreshWorkspace(requireNotNull(activeProfile))
                }
            },
        )
    }

    deleteWorkOrderPrompt?.let { workOrder ->
        DeleteConfirmDialog(
            title = text.deleteWorkOrderTitle,
            body = text.deleteWorkOrderBody(workOrder.title),
            text = text,
            onDismiss = { deleteWorkOrderPrompt = null },
            onConfirm = {
                scope.launch {
                    repository.deleteWorkOrder(workOrder.profileId, workOrder.id)
                    deleteWorkOrderPrompt = null
                    refreshWorkspace(requireNotNull(activeProfile))
                }
            },
        )
    }

    deleteProfilePrompt?.let { profile ->
        DeleteProfileDialog(
            profile = profile,
            text = text,
            onDismiss = { deleteProfilePrompt = null },
            onConfirm = { pin ->
                scope.launch {
                    if (repository.verifyPin(profile.id, pin.toCharArray())) {
                        repository.deleteProfile(profile.id)
                        deleteProfilePrompt = null
                        if (activeProfile?.id == profile.id) activeProfile = null
                        refreshProfiles()
                    } else {
                        error = text.wrongPin
                    }
                }
            },
        )
    }

    if (addClient) {
        ClientDialog(
            text = text,
            onDismiss = { addClient = false },
            onSave = { name, company, phone, email ->
                val profile = requireNotNull(activeProfile)
                val now = System.currentTimeMillis()
                scope.launch {
                    repository.saveClient(
                        CrmClient(
                            id = ClientId(UUID.randomUUID().toString()),
                            profileId = profile.id,
                            displayName = name,
                            companyName = company,
                            phone = phone,
                            email = email,
                            createdAtEpochMs = now,
                            modifiedAtEpochMs = now,
                        ),
                    )
                    addClient = false
                    refreshWorkspace(profile)
                }
            },
        )
    }

    if (addWorkOrder) {
        WorkOrderDialog(
            clients = clients,
            text = text,
            onDismiss = { addWorkOrder = false },
            onSave = { client, title, stage ->
                val profile = requireNotNull(activeProfile)
                val now = System.currentTimeMillis()
                scope.launch {
                    repository.saveWorkOrder(
                        WorkOrder(
                            id = WorkOrderId(UUID.randomUUID().toString()),
                            profileId = profile.id,
                            clientId = client.id,
                            title = title,
                            stage = stage,
                            createdAtEpochMs = now,
                            modifiedAtEpochMs = now,
                        ),
                    )
                    addWorkOrder = false
                    refreshWorkspace(profile)
                }
            },
        )
    }
}

@Composable
private fun LocalAccounts(
    profiles: List<LocalProfile>,
    text: CrmText,
    modifier: Modifier,
    error: String?,
    onCreate: () -> Unit,
    onUnlock: (LocalProfile) -> Unit,
    onDeleteRequest: (LocalProfile) -> Unit,
) {
    LazyColumn(
        modifier.fillMaxSize().testTag(CrmTags.Accounts),
        contentPadding = PaddingValues(Space.x4),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        item {
            Text(text.localAccounts, style = MaterialTheme.typography.headlineSmall)
            Text(text.localAccountsBody, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        items(profiles, key = { it.id.value }) { profile ->
            ElevatedCard(
                onClick = { onUnlock(profile) },
                modifier = Modifier.testTag(CrmTags.profile(profile.id.value)),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(Space.x4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.x3),
                ) {
                    IndicatorChip(PlanRulerIcons.Check, text.protected, IndicatorStatus.OK, {})
                    Column(Modifier.weight(1f)) {
                        Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                        if (profile.companyName.isNotBlank()) Text(profile.companyName)
                        Text(text.offline, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { onDeleteRequest(profile) },
                        modifier = Modifier.testTag(CrmTags.DeleteProfile + ":" + profile.id.value),
                    ) { Icon(PlanRulerIcons.Delete, contentDescription = text.deleteAccount) }
                }
            }
        }
        item { Button(onCreate, Modifier.fillMaxWidth().testTag(CrmTags.CreateProfile)) { Text(text.createAccount) } }
    }
}

@Composable
private fun CrmWorkspace(
    profile: LocalProfile,
    clients: List<CrmClient>,
    workOrders: List<WorkOrder>,
    showArchived: Boolean,
    text: CrmText,
    modifier: Modifier,
    onLock: () -> Unit,
    onAddClient: () -> Unit,
    onAddWorkOrder: () -> Unit,
    onShowArchivedChange: (Boolean) -> Unit,
    onArchiveClient: (CrmClient) -> Unit,
    onUnarchiveClient: (CrmClient) -> Unit,
    onDeleteClient: (CrmClient) -> Unit,
    onDeleteWorkOrder: (WorkOrder) -> Unit,
    onAdvance: (WorkOrder) -> Unit,
    projectName: (String) -> String?,
    onAttachProject: (WorkOrder) -> Unit,
    onOpenProject: (String) -> Unit,
    onUnlinkProject: (WorkOrder) -> Unit,
) {
    LazyColumn(
        modifier.fillMaxSize().testTag(CrmTags.Workspace),
        contentPadding = PaddingValues(Space.x4),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(profile.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text(profile.companyName.ifBlank { text.localWorkspace }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onLock, Modifier.testTag(CrmTags.Lock)) { Text(text.lock) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                IndicatorChip(PlanRulerIcons.Folder, "${text.clients}: ${clients.size}", IndicatorStatus.OK, {})
                IndicatorChip(
                    PlanRulerIcons.Schedule,
                    "${text.activeJobs}: ${workOrders.count { it.stage !in setOf(WorkOrderStage.COMPLETED, WorkOrderStage.CANCELLED) }}",
                    if (workOrders.isEmpty()) IndicatorStatus.NEUTRAL else IndicatorStatus.WARNING,
                    {},
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                Button(onAddClient, Modifier.weight(1f).testTag(CrmTags.AddClient)) { Text(text.addClient) }
                FilledTonalButton(
                    onAddWorkOrder,
                    Modifier.weight(1f).testTag(CrmTags.AddWorkOrder),
                    enabled = clients.isNotEmpty(),
                ) {
                    Text(text.addJob)
                }
            }
        }
        item { Text(text.jobs, style = MaterialTheme.typography.titleLarge) }
        if (workOrders.isEmpty()) {
            item { Text(text.noJobs, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(workOrders, key = { it.id.value }) { job ->
                ElevatedCard(shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(job.title, style = MaterialTheme.typography.titleMedium)
                                Text(clients.firstOrNull { it.id == job.clientId }?.displayName.orEmpty())
                            }
                            IndicatorChip(
                                PlanRulerIcons.Clock,
                                text.stage(job.stage),
                                job.stage.indicator(),
                                {},
                            )
                            IconButton(
                                onClick = { onDeleteWorkOrder(job) },
                                modifier = Modifier.testTag(CrmTags.deleteWorkOrder(job.id.value)),
                            ) { Icon(PlanRulerIcons.Delete, contentDescription = text.delete) }
                        }
                        if (job.stage !in setOf(WorkOrderStage.COMPLETED, WorkOrderStage.CANCELLED)) {
                            TextButton({ onAdvance(job) }) { Text(text.nextStage) }
                        }
                        val linkedProjectId = job.projectId
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (linkedProjectId != null) {
                                val linkedName = projectName(linkedProjectId)
                                Column(Modifier.weight(1f)) {
                                    Text(text.linkedProject, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    // A missing name means the project was deleted from the app after linking.
                                    Text(linkedName ?: text.projectRemoved, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (linkedName != null) {
                                    TextButton({ onOpenProject(linkedProjectId) }) { Text(text.openProject) }
                                }
                                TextButton({ onUnlinkProject(job) }) { Text(text.unlinkProject) }
                            } else {
                                OutlinedButton(
                                    { onAttachProject(job) },
                                    Modifier.testTag(CrmTags.attachProject(job.id.value)),
                                ) { Text(text.attachProject) }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text.clients, style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text.showArchived, style = MaterialTheme.typography.bodySmall)
                    Switch(showArchived, onShowArchivedChange, modifier = Modifier.testTag(CrmTags.ShowArchived))
                }
            }
        }
        if (clients.isEmpty()) {
            item { Text(text.noClients, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(clients, key = { it.id.value }) { client ->
                val archived = client.archivedAtEpochMs != null
                ElevatedCard(shape = MaterialTheme.shapes.large) {
                    Row(Modifier.fillMaxWidth().padding(Space.x4), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(client.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(listOf(client.companyName, client.phone, client.email).filter(String::isNotBlank).joinToString(" · "))
                            if (archived) {
                                Text(text.archived, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (archived) {
                            TextButton(
                                { onUnarchiveClient(client) },
                                Modifier.testTag(CrmTags.unarchiveClient(client.id.value)),
                            ) { Text(text.unarchive) }
                        } else {
                            TextButton({ onArchiveClient(client) }) { Text(text.archive) }
                        }
                        IconButton(
                            onClick = { onDeleteClient(client) },
                            modifier = Modifier.testTag(CrmTags.deleteClient(client.id.value)),
                        ) { Icon(PlanRulerIcons.Delete, contentDescription = text.delete) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateProfileDialog(text: CrmText, onDismiss: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.createAccount) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text(text.name) }, singleLine = true, modifier = Modifier.testTag(CrmTags.ProfileName))
                OutlinedTextField(company, { company = it.take(120) }, label = { Text(text.company) }, singleLine = true, modifier = Modifier.testTag(CrmTags.ProfileCompany))
                SecretField(pin, { pin = it.take(12) }, text.pin)
                Text(text.pinHint, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button({ onCreate(name, company, pin) }, Modifier.testTag(CrmTags.ConfirmProfile), enabled = name.isNotBlank() && pin.length >= 4) { Text(text.create) } },
        dismissButton = { TextButton(onDismiss) { Text(text.cancel) } },
    )
}

/**
 * Escalating lockout after wrong PINs: the first two mistakes are free (typos happen),
 * every one after doubles the wait, capped at five minutes. Kept as plain data so it is
 * unit-testable without Compose or Android.
 */
data class PinLockState(val failures: Int = 0, val lockedUntilEpochMs: Long = 0L) {
    fun isLocked(nowEpochMs: Long): Boolean = nowEpochMs < lockedUntilEpochMs
    fun remainingSeconds(nowEpochMs: Long): Int =
        ((lockedUntilEpochMs - nowEpochMs + 999) / 1000).coerceAtLeast(0).toInt()

    fun afterFailure(nowEpochMs: Long): PinLockState {
        val nextFailures = failures + 1
        val freeAttempts = 2
        val lockSeconds = if (nextFailures <= freeAttempts) {
            0L
        } else {
            min(300.0, 5.0 * 2.0.pow(nextFailures - freeAttempts - 1)).toLong()
        }
        return PinLockState(nextFailures, nowEpochMs + lockSeconds * 1000L)
    }
}

@Composable
private fun PinDialog(profile: LocalProfile, text: CrmText, lock: PinLockState, onDismiss: () -> Unit, onUnlock: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var now by remember(lock) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lock) {
        while (lock.isLocked(now)) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }
    val locked = lock.isLocked(now)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(profile.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                SecretField(pin, { pin = it.take(12) }, text.pin)
                if (locked) {
                    Text(
                        text.pinLocked(lock.remainingSeconds(now)),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (lock.failures > 0) {
                    Text(text.wrongPin, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button({ onUnlock(pin); pin = "" }, enabled = !locked && pin.length >= 4) { Text(text.unlock) }
        },
        dismissButton = { TextButton(onDismiss) { Text(text.cancel) } },
    )
}

@Composable
private fun ProjectPickerDialog(
    projects: List<PlanProject>,
    text: CrmText,
    onDismiss: () -> Unit,
    onSelect: (PlanProject) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.pickProjectTitle) },
        text = {
            if (projects.isEmpty()) {
                Text(text.noProjectsYet, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.widthIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                    items(projects, key = { it.id.value }) { project ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(project) }
                                .testTag(CrmTags.pickProject(project.id.value))
                                .padding(vertical = Space.x2),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.x2),
                        ) {
                            Icon(PlanRulerIcons.Folder, contentDescription = null)
                            Text(project.name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(text.cancel) } },
    )
}

@Composable
private fun DeleteConfirmDialog(title: String, body: String, text: CrmText, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(
                onConfirm,
                Modifier.testTag(CrmTags.ConfirmDelete),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(text.delete) }
        },
        dismissButton = { TextButton(onDismiss) { Text(text.cancel) } },
    )
}

/** Deleting a profile removes every client and work order under it, so it re-asks for the PIN. */
@Composable
private fun DeleteProfileDialog(profile: LocalProfile, text: CrmText, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.deleteProfileTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                Text(text.deleteProfileBody(profile.displayName))
                SecretField(pin, { pin = it.take(12) }, text.pin)
            }
        },
        confirmButton = {
            Button(
                { onConfirm(pin) },
                Modifier.testTag(CrmTags.ConfirmDelete),
                enabled = pin.length >= 4,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(text.delete) }
        },
        dismissButton = { TextButton(onDismiss) { Text(text.cancel) } },
    )
}

@Composable
private fun SecretField(value: String, onValue: (String) -> Unit, label: String) {
    OutlinedTextField(
        value,
        { onValue(it.filter(Char::isDigit)) },
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = Modifier.testTag(CrmTags.Pin),
    )
}

@Composable
private fun ClientDialog(text: CrmText, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.addClient) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                OutlinedTextField(name, { name = it.take(100) }, label = { Text(text.name) }, modifier = Modifier.testTag(CrmTags.ClientName))
                OutlinedTextField(company, { company = it.take(120) }, label = { Text(text.company) })
                OutlinedTextField(phone, { phone = it.take(40) }, label = { Text(text.phone) })
                OutlinedTextField(email, { email = it.take(120) }, label = { Text(text.email) })
            }
        },
        confirmButton = { Button({ onSave(name, company, phone, email) }, Modifier.testTag(CrmTags.ConfirmClient), enabled = name.isNotBlank()) { Text(text.save) } },
        dismissButton = { TextButton(onDismiss) { Text(text.cancel) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkOrderDialog(
    clients: List<CrmClient>,
    text: CrmText,
    onDismiss: () -> Unit,
    onSave: (CrmClient, String, WorkOrderStage) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var client by remember { mutableStateOf(clients.first()) }
    var stage by remember { mutableStateOf(WorkOrderStage.LEAD) }
    var clientMenu by remember { mutableStateOf(false) }
    var stageMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.addJob) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                OutlinedTextField(title, { title = it.take(120) }, label = { Text(text.title) }, modifier = Modifier.testTag(CrmTags.WorkOrderTitle))
                ExposedDropdownMenuBox(clientMenu, { clientMenu = !clientMenu }) {
                    OutlinedTextField(
                        client.displayName,
                        {},
                        readOnly = true,
                        label = { Text(text.client) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(clientMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    DropdownMenu(clientMenu, { clientMenu = false }) {
                        clients.forEach { candidate ->
                            DropdownMenuItem({ Text(candidate.displayName) }, { client = candidate; clientMenu = false })
                        }
                    }
                }
                ExposedDropdownMenuBox(stageMenu, { stageMenu = !stageMenu }) {
                    OutlinedTextField(
                        text.stage(stage),
                        {},
                        readOnly = true,
                        label = { Text(text.status) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(stageMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    DropdownMenu(stageMenu, { stageMenu = false }) {
                        WorkOrderStage.entries.forEach { candidate ->
                            DropdownMenuItem({ Text(text.stage(candidate)) }, { stage = candidate; stageMenu = false })
                        }
                    }
                }
            }
        },
        confirmButton = { Button({ onSave(client, title, stage) }, Modifier.testTag(CrmTags.ConfirmWorkOrder), enabled = title.isNotBlank()) { Text(text.save) } },
        dismissButton = { TextButton(onDismiss) { Text(text.cancel) } },
    )
}

private fun WorkOrderStage.next(): WorkOrderStage = when (this) {
    WorkOrderStage.LEAD -> WorkOrderStage.SURVEY
    WorkOrderStage.SURVEY -> WorkOrderStage.QUOTED
    WorkOrderStage.QUOTED -> WorkOrderStage.APPROVED
    WorkOrderStage.APPROVED -> WorkOrderStage.SCHEDULED
    WorkOrderStage.SCHEDULED -> WorkOrderStage.IN_PROGRESS
    WorkOrderStage.IN_PROGRESS -> WorkOrderStage.COMPLETED
    WorkOrderStage.COMPLETED, WorkOrderStage.CANCELLED -> this
}

private fun WorkOrderStage.indicator(): IndicatorStatus = when (this) {
    WorkOrderStage.COMPLETED -> IndicatorStatus.OK
    WorkOrderStage.CANCELLED -> IndicatorStatus.ERROR
    WorkOrderStage.LEAD, WorkOrderStage.SURVEY, WorkOrderStage.QUOTED -> IndicatorStatus.NEUTRAL
    else -> IndicatorStatus.WARNING
}

private class CrmText(private val language: AppLanguage) {
    private fun t(ru: String, en: String) = localizedUi(language, ru, en)
    val localAccounts get() = t("Локальные аккаунты", "Local accounts")
    val localAccountsBody get() = t("Данные хранятся только на этом устройстве. Сервер, электронная почта и облачная синхронизация не используются.", "Data stays on this device. No server, email account, or cloud sync is used.")
    val localWorkspace get() = t("Локальное рабочее пространство", "Local workspace")
    val protected get() = t("PIN", "PIN")
    val offline get() = t("Только на устройстве", "On device only")
    val createAccount get() = t("Создать локальный аккаунт", "Create local account")
    val create get() = t("Создать", "Create")
    val unlock get() = t("Открыть", "Unlock")
    val lock get() = t("Заблокировать", "Lock")
    val name get() = t("Имя", "Name")
    val company get() = t("Компания", "Company")
    val pin get() = t("PIN", "PIN")
    val pinHint get() = t("Минимум четыре цифры. PIN не отправляется с устройства.", "At least four digits. The PIN never leaves the device.")
    val wrongPin get() = t("Неверный PIN", "Incorrect PIN")
    val cancel get() = t("Отмена", "Cancel")
    val save get() = t("Сохранить", "Save")
    val clients get() = t("Клиенты", "Clients")
    val activeJobs get() = t("Активные", "Active")
    val jobs get() = t("Заявки и работы", "Jobs and work orders")
    val addClient get() = t("Добавить клиента", "Add client")
    val addJob get() = t("Добавить работу", "Add job")
    val noClients get() = t("Клиентов пока нет.", "No clients yet.")
    val noJobs get() = t("Работ пока нет.", "No work orders yet.")
    val archive get() = t("Архив", "Archive")
    val unarchive get() = t("Восстановить", "Restore")
    val archived get() = t("В архиве", "Archived")
    val showArchived get() = t("Показывать архив", "Show archived")
    val delete get() = t("Удалить", "Delete")
    val deleteAccount get() = t("Удалить аккаунт", "Delete account")
    val phone get() = t("Телефон", "Phone")
    val email get() = t("E-mail", "Email")
    val title get() = t("Название работы", "Job title")
    val client get() = t("Клиент", "Client")
    val status get() = t("Статус", "Status")
    val nextStage get() = t("Следующий этап", "Next stage")
    val deleteClientTitle get() = t("Удалить клиента?", "Delete client?")
    fun deleteClientBody(name: String) = t(
        "«$name» и все его наряды будут удалены без возможности восстановления.",
        "\"$name\" and all of its work orders will be permanently removed.",
    )
    val deleteWorkOrderTitle get() = t("Удалить наряд?", "Delete work order?")
    fun deleteWorkOrderBody(title: String) = t(
        "«$title» будет удалён без возможности восстановления.",
        "\"$title\" will be permanently removed.",
    )
    val deleteProfileTitle get() = t("Удалить аккаунт?", "Delete account?")
    fun deleteProfileBody(name: String) = t(
        "«$name» и все его клиенты и наряды будут удалены без возможности восстановления. Введите PIN для подтверждения.",
        "\"$name\" and all of its clients and work orders will be permanently removed. Enter the PIN to confirm.",
    )
    fun pinLocked(seconds: Int) = t(
        "Слишком много неверных попыток. Повторите через $seconds с.",
        "Too many incorrect attempts. Try again in ${seconds}s.",
    )
    val linkedProject get() = t("Проект замеров", "Takeoff project")
    val projectRemoved get() = t("Проект удалён", "Project removed")
    val attachProject get() = t("Привязать проект", "Attach project")
    val openProject get() = t("Открыть", "Open")
    val unlinkProject get() = t("Отвязать", "Unlink")
    val pickProjectTitle get() = t("Выберите проект", "Select a project")
    val noProjectsYet get() = t("Проектов пока нет.", "No projects yet.")
    fun stage(stage: WorkOrderStage) = when (stage) {
        WorkOrderStage.LEAD -> t("Запрос", "Lead")
        WorkOrderStage.SURVEY -> t("Обследование", "Survey")
        WorkOrderStage.QUOTED -> t("Предложение", "Quoted")
        WorkOrderStage.APPROVED -> t("Согласовано", "Approved")
        WorkOrderStage.SCHEDULED -> t("Запланировано", "Scheduled")
        WorkOrderStage.IN_PROGRESS -> t("В работе", "In progress")
        WorkOrderStage.COMPLETED -> t("Выполнено", "Completed")
        WorkOrderStage.CANCELLED -> t("Отменено", "Cancelled")
    }
}
