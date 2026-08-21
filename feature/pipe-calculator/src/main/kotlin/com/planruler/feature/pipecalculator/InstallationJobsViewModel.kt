package com.planruler.feature.pipecalculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobId
import com.planruler.model.PlanProject
import com.planruler.model.ProjectId
import com.planruler.project.api.ProjectError
import com.planruler.project.api.ProjectRepository
import com.planruler.project.api.ProjectResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class InstallationJobsState(
    val projects: List<PlanProject> = emptyList(),
    val selectedProjectId: ProjectId? = null,
    val selectedJobId: InstallationJobId? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    /** Incremented only after data has reached the project file. */
    val persistedRevision: Int = 0,
) {
    val selectedProject: PlanProject?
        get() = projects.firstOrNull { it.id == selectedProjectId }

    val selectedJob: InstallationJob?
        get() = selectedProject?.installationJobs?.firstOrNull { it.id == selectedJobId }
}

/**
 * Keeps pending autosaves alive while the user switches between the Projects tabs.
 * The repository remains the source of truth; this state only makes edits responsive.
 */
internal class InstallationJobsViewModel(
    private val repository: ProjectRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(InstallationJobsState())
    val state: StateFlow<InstallationJobsState> = _state.asStateFlow()

    private var pendingSave: Job? = null
    private var pendingJob: Pair<ProjectId, InstallationJob>? = null
    private val repositoryMutex = Mutex()

    fun refresh() {
        viewModelScope.launch {
            when (val result = repositoryCall { repository.list() }) {
                is ProjectResult.Error -> _state.update {
                    it.copy(loading = false, error = result.error.message())
                }
                is ProjectResult.Ok -> {
                    val projects = result.value.sortedByDescending { it.modifiedAtEpochMs }
                    _state.update { current ->
                        val project = projects.firstOrNull { it.id == current.selectedProjectId }
                            ?: projects.firstOrNull()
                        val activeJobs = project?.installationJobs.orEmpty()
                            .filter { it.deletedAtEpochMs == null }
                        val selectedJobId = current.selectedJobId
                            ?.takeIf { id -> activeJobs.any { it.id == id } }
                            ?: project?.activeInstallationJobId
                                ?.takeIf { id -> activeJobs.any { it.id == id } }
                            ?: activeJobs.maxByOrNull { it.lastOpenedAtEpochMs }?.id
                        current.copy(
                            projects = projects,
                            selectedProjectId = project?.id,
                            selectedJobId = selectedJobId,
                            loading = false,
                            error = null,
                        )
                    }
                }
            }
        }
    }

    fun selectProject(projectId: ProjectId) {
        flushPending()
        val project = _state.value.projects.firstOrNull { it.id == projectId } ?: return
        val activeJobs = project.installationJobs.filter { it.deletedAtEpochMs == null }
        _state.update {
            it.copy(
                selectedProjectId = projectId,
                selectedJobId = project.activeInstallationJobId
                    ?.takeIf { id -> activeJobs.any { job -> job.id == id } }
                    ?: activeJobs.maxByOrNull(InstallationJob::lastOpenedAtEpochMs)?.id,
                error = null,
            )
        }
    }

    fun selectJob(jobId: InstallationJobId) {
        flushPending()
        val projectId = _state.value.selectedProjectId ?: return
        _state.update { it.copy(selectedJobId = jobId, error = null) }
        viewModelScope.launch {
            applyResult(projectId, repositoryCall { repository.selectInstallationJob(projectId, jobId) })
        }
    }

    fun createJob(name: String) {
        flushPending()
        val projectId = _state.value.selectedProjectId ?: return
        viewModelScope.launch {
            applyResult(projectId, repositoryCall { repository.createInstallationJob(projectId, name) })
        }
    }

    fun renameJob(jobId: InstallationJobId, name: String) {
        flushPending()
        val projectId = _state.value.selectedProjectId ?: return
        viewModelScope.launch {
            applyResult(projectId, repositoryCall { repository.renameInstallationJob(projectId, jobId, name) })
        }
    }

    fun duplicateJob(jobId: InstallationJobId) {
        flushPending()
        val projectId = _state.value.selectedProjectId ?: return
        viewModelScope.launch {
            applyResult(projectId, repositoryCall { repository.duplicateInstallationJob(projectId, jobId) })
        }
    }

    fun deleteJob(jobId: InstallationJobId) {
        flushPending()
        val projectId = _state.value.selectedProjectId ?: return
        viewModelScope.launch {
            when (val result = repositoryCall { repository.deleteInstallationJob(projectId, jobId) }) {
                is ProjectResult.Error -> setError(result.error)
                is ProjectResult.Ok -> reloadProject(projectId, preferredJobId = null)
            }
        }
    }

    fun restoreJob(jobId: InstallationJobId) {
        val projectId = _state.value.selectedProjectId ?: return
        viewModelScope.launch {
            applyResult(projectId, repositoryCall { repository.restoreInstallationJob(projectId, jobId) })
        }
    }

    fun scheduleSave(projectId: ProjectId, job: InstallationJob) {
        updateLocalJob(projectId, job)
        pendingJob = projectId to job
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            pendingJob = null
            persist(projectId, job)
        }
    }

    fun saveNow(projectId: ProjectId, job: InstallationJob) {
        updateLocalJob(projectId, job)
        pendingSave?.cancel()
        pendingSave = null
        pendingJob = null
        viewModelScope.launch { persist(projectId, job) }
    }

    private fun flushPending() {
        val pending = pendingJob ?: return
        saveNow(pending.first, pending.second)
    }

    private suspend fun persist(projectId: ProjectId, job: InstallationJob) {
        _state.update { it.copy(saving = true, error = null) }
        when (val result = repositoryCall { repository.saveInstallationJob(projectId, job) }) {
            is ProjectResult.Error -> _state.update {
                it.copy(saving = false, error = result.error.message())
            }
            is ProjectResult.Ok -> {
                updateLocalJob(projectId, result.value)
                _state.update {
                    it.copy(saving = false, persistedRevision = it.persistedRevision + 1, error = null)
                }
            }
        }
    }

    private suspend fun applyResult(projectId: ProjectId, result: ProjectResult<InstallationJob>) {
        when (result) {
            is ProjectResult.Error -> setError(result.error)
            is ProjectResult.Ok -> reloadProject(projectId, preferredJobId = result.value.id)
        }
    }

    private suspend fun reloadProject(projectId: ProjectId, preferredJobId: InstallationJobId?) {
        when (val loaded = repositoryCall { repository.load(projectId) }) {
            is ProjectResult.Error -> setError(loaded.error)
            is ProjectResult.Ok -> {
                val project = loaded.value
                val active = project.installationJobs.filter { it.deletedAtEpochMs == null }
                val selected = preferredJobId?.takeIf { id -> active.any { it.id == id } }
                    ?: project.activeInstallationJobId?.takeIf { id -> active.any { it.id == id } }
                    ?: active.maxByOrNull(InstallationJob::lastOpenedAtEpochMs)?.id
                _state.update { current ->
                    current.copy(
                        projects = current.projects.replaceProject(project),
                        selectedProjectId = projectId,
                        selectedJobId = selected,
                        saving = false,
                        error = null,
                        persistedRevision = current.persistedRevision + 1,
                    )
                }
            }
        }
    }

    private fun updateLocalJob(projectId: ProjectId, job: InstallationJob) {
        _state.update { current ->
            val project = current.projects.firstOrNull { it.id == projectId } ?: return@update current
            val index = project.installationJobs.indexOfFirst { it.id == job.id }
            if (index < 0) return@update current
            val jobs = project.installationJobs.toMutableList().also { it[index] = job }
            val isActive = job.deletedAtEpochMs == null
            current.copy(
                projects = current.projects.replaceProject(
                    project.copy(
                        installationJobs = jobs,
                        activeInstallationJobId = if (isActive) job.id else project.activeInstallationJobId,
                    ),
                ),
                selectedProjectId = projectId,
                selectedJobId = if (isActive) job.id else current.selectedJobId?.takeIf { it != job.id },
            )
        }
    }

    private suspend fun <T> repositoryCall(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { repositoryMutex.withLock { block() } }

    private fun setError(error: ProjectError) {
        _state.update { it.copy(saving = false, loading = false, error = error.message()) }
    }

    companion object {
        private const val AUTOSAVE_DELAY_MS = 450L

        fun factory(repository: ProjectRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    InstallationJobsViewModel(repository) as T
            }
    }
}

private fun List<PlanProject>.replaceProject(project: PlanProject): List<PlanProject> =
    map { if (it.id == project.id) project else it }.sortedByDescending { it.modifiedAtEpochMs }

private fun ProjectError.message(): String = when (this) {
    ProjectError.NotFound -> "Project or installation job was not found"
    ProjectError.Corrupt -> "Project file is damaged"
    ProjectError.AlreadyExists -> "The item already exists"
    is ProjectError.Io -> message
}
