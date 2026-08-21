package com.planruler.project.api

import com.planruler.model.PlanProject
import com.planruler.model.ProjectId
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobId

sealed interface ProjectError {
    data object NotFound : ProjectError
    data object Corrupt : ProjectError
    data object AlreadyExists : ProjectError
    data class Io(val message: String) : ProjectError
}
data class TrashedProject(val project: PlanProject, val deletedAtEpochMs: Long)
sealed interface ProjectResult<out T> {
    data class Ok<T>(val value: T) : ProjectResult<T>
    data class Error(val error: ProjectError) : ProjectResult<Nothing>
}
interface ProjectRepository {
    suspend fun list(): ProjectResult<List<PlanProject>>
    suspend fun load(id: ProjectId): ProjectResult<PlanProject>
    suspend fun save(project: PlanProject): ProjectResult<Unit>
    /** Moves the project into the app-local recycle bin. */
    suspend fun delete(id: ProjectId): ProjectResult<Unit>
    suspend fun listTrash(): ProjectResult<List<TrashedProject>>
    suspend fun restore(id: ProjectId): ProjectResult<PlanProject>
    suspend fun deletePermanently(id: ProjectId): ProjectResult<Unit>
    suspend fun rename(id: ProjectId, name: String): ProjectResult<PlanProject>
    suspend fun duplicate(id: ProjectId, name: String? = null): ProjectResult<PlanProject>

    /** Installation jobs live inside a project and share its atomic file/backup lifecycle. */
    suspend fun createInstallationJob(projectId: ProjectId, name: String): ProjectResult<InstallationJob>
    suspend fun saveInstallationJob(projectId: ProjectId, job: InstallationJob): ProjectResult<InstallationJob>
    suspend fun selectInstallationJob(projectId: ProjectId, jobId: InstallationJobId): ProjectResult<InstallationJob>
    suspend fun renameInstallationJob(projectId: ProjectId, jobId: InstallationJobId, name: String): ProjectResult<InstallationJob>
    suspend fun duplicateInstallationJob(projectId: ProjectId, jobId: InstallationJobId): ProjectResult<InstallationJob>
    suspend fun deleteInstallationJob(projectId: ProjectId, jobId: InstallationJobId): ProjectResult<InstallationJob>
    suspend fun restoreInstallationJob(projectId: ProjectId, jobId: InstallationJobId): ProjectResult<InstallationJob>
}
