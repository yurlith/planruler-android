package com.planruler.project.local

import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobId
import com.planruler.model.InstallationJobRevision
import com.planruler.model.InstallationJobStatus
import com.planruler.model.PlanProject
import com.planruler.model.ProjectId
import com.planruler.project.api.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class FileProjectRepository(
    private val directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val installationJobIdGenerator: () -> String = { UUID.randomUUID().toString() },
) : ProjectRepository {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val trashDirectory = directory.resolve(".trash")
    init {
        directory.mkdirs()
        trashDirectory.mkdirs()
    }

    override suspend fun list(): ProjectResult<List<PlanProject>> = try {
        val projects = directory.listFiles { file -> file.extension == "json" && !file.name.endsWith(".backup.json") }
            .orEmpty().mapNotNull { runCatching { decode(it) }.getOrNull() }.sortedByDescending { it.modifiedAtEpochMs }
        ProjectResult.Ok(projects)
    } catch (e: Exception) { ProjectResult.Error(ProjectError.Io(e.message ?: "Cannot list projects")) }

    override suspend fun load(id: ProjectId): ProjectResult<PlanProject> {
        val file = file(id)
        if (!file.exists()) return ProjectResult.Error(ProjectError.NotFound)
        return try { ProjectResult.Ok(decode(file)) }
        catch (_: SerializationException) {
            val backup = backup(id)
            if (backup.exists()) runCatching { ProjectResult.Ok(decode(backup)) }.getOrElse { ProjectResult.Error(ProjectError.Corrupt) }
            else ProjectResult.Error(ProjectError.Corrupt)
        } catch (e: Exception) { ProjectResult.Error(ProjectError.Io(e.message ?: "Cannot load project")) }
    }

    override suspend fun save(project: PlanProject): ProjectResult<Unit> = try {
        val target = file(project.id)
        val temporary = File(directory, "${project.id.value}.tmp")
        val encoded = json.encodeToString(PlanProject.serializer(), project)
        temporary.writeText(encoded, Charsets.UTF_8)
        decode(temporary)
        if (target.exists()) Files.copy(target.toPath(), backup(project.id).toPath(), StandardCopyOption.REPLACE_EXISTING)
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        ProjectResult.Ok(Unit)
    } catch (e: Exception) { ProjectResult.Error(ProjectError.Io(e.message ?: "Cannot save project")) }

    override suspend fun delete(id: ProjectId): ProjectResult<Unit> {
        return try {
            val source = file(id)
            if (!source.exists()) {
                ProjectResult.Error(ProjectError.NotFound)
            } else {
                val folder = trashFolder(id).apply { mkdirs() }
                val current = folder.resolve("current.json")
                if (current.exists()) move(current, folder.resolve("history-${clock()}.json"))
                move(source, current)
                current.setLastModified(clock())
                val activeBackup = backup(id)
                if (activeBackup.exists()) move(activeBackup, folder.resolve("backup-${clock()}.json"))
                ProjectResult.Ok(Unit)
            }
        } catch (e: Exception) {
            ProjectResult.Error(ProjectError.Io(e.message ?: "Cannot move project to trash"))
        }
    }

    override suspend fun listTrash(): ProjectResult<List<TrashedProject>> = try {
        ProjectResult.Ok(
            trashDirectory.listFiles(File::isDirectory).orEmpty().mapNotNull { folder ->
                val current = folder.resolve("current.json")
                if (!current.exists()) null else runCatching {
                    TrashedProject(decode(current), current.lastModified())
                }.getOrNull()
            }.sortedByDescending { it.deletedAtEpochMs },
        )
    } catch (e: Exception) { ProjectResult.Error(ProjectError.Io(e.message ?: "Cannot list trash")) }

    override suspend fun restore(id: ProjectId): ProjectResult<PlanProject> {
        return try {
            val current = trashFolder(id).resolve("current.json")
            when {
                !current.exists() -> ProjectResult.Error(ProjectError.NotFound)
                file(id).exists() -> ProjectResult.Error(ProjectError.AlreadyExists)
                else -> {
                    val restored = decode(current).copy(modifiedAtEpochMs = clock())
                    val temporary = directory.resolve("${safe(id.value)}.restore.tmp")
                    temporary.writeText(json.encodeToString(PlanProject.serializer(), restored), Charsets.UTF_8)
                    decode(temporary)
                    move(temporary, file(id))
                    current.delete()
                    ProjectResult.Ok(restored)
                }
            }
        } catch (e: Exception) {
            ProjectResult.Error(ProjectError.Io(e.message ?: "Cannot restore project"))
        }
    }

    override suspend fun deletePermanently(id: ProjectId): ProjectResult<Unit> {
        return try {
            val folder = trashFolder(id)
            when {
                !folder.exists() -> ProjectResult.Error(ProjectError.NotFound)
                !folder.deleteRecursively() -> ProjectResult.Error(ProjectError.Io("Cannot permanently delete project"))
                else -> ProjectResult.Ok(Unit)
            }
        } catch (e: Exception) {
            ProjectResult.Error(ProjectError.Io(e.message ?: "Cannot permanently delete project"))
        }
    }

    override suspend fun rename(id: ProjectId, name: String): ProjectResult<PlanProject> {
        val normalized = name.trim().take(120)
        if (normalized.isEmpty()) return ProjectResult.Error(ProjectError.Io("Project name cannot be empty"))
        return when (val loaded = load(id)) {
            is ProjectResult.Error -> loaded
            is ProjectResult.Ok -> {
                val renamed = loaded.value.copy(name = normalized, modifiedAtEpochMs = clock())
                when (val saved = save(renamed)) {
                    is ProjectResult.Ok -> ProjectResult.Ok(renamed)
                    is ProjectResult.Error -> saved
                }
            }
        }
    }

    override suspend fun duplicate(id: ProjectId, name: String?): ProjectResult<PlanProject> =
        when (val loaded = load(id)) {
            is ProjectResult.Error -> loaded
            is ProjectResult.Ok -> {
                val now = clock()
                val duplicate = loaded.value.copy(
                    id = ProjectId(idGenerator()),
                    name = name?.trim()?.takeIf(String::isNotEmpty)?.take(120) ?: "${loaded.value.name} copy",
                    createdAtEpochMs = now,
                    modifiedAtEpochMs = now,
                    // Measurement IDs remain safe because every project is persisted in an isolated file.
                )
                when (val saved = save(duplicate)) {
                    is ProjectResult.Ok -> ProjectResult.Ok(duplicate)
                    is ProjectResult.Error -> saved
                }
            }
        }

    override suspend fun createInstallationJob(
        projectId: ProjectId,
        name: String,
    ): ProjectResult<InstallationJob> = mutateProject(projectId) { project, now ->
        val job = InstallationJob(
            id = InstallationJobId(installationJobIdGenerator()),
            name = normalizedJobName(name),
            createdAtEpochMs = now,
            modifiedAtEpochMs = now,
            lastOpenedAtEpochMs = now,
        )
        project.copy(
            installationJobs = project.installationJobs + job,
            activeInstallationJobId = job.id,
        ) to job
    }

    override suspend fun saveInstallationJob(
        projectId: ProjectId,
        job: InstallationJob,
    ): ProjectResult<InstallationJob> = mutateProject(projectId) { project, now ->
        val index = project.installationJobs.indexOfFirst { it.id == job.id }
        if (index < 0) throw InstallationJobMissing()
        val previous = project.installationJobs[index]
        val calculationChanged = previous.taskType != job.taskType ||
            previous.input != job.input || previous.chainRecipe != job.chainRecipe
        val history = if (calculationChanged) {
            listOf(
                InstallationJobRevision(
                    savedAtEpochMs = previous.modifiedAtEpochMs,
                    taskType = previous.taskType,
                    input = previous.input,
                    chainRecipe = previous.chainRecipe,
                ),
            ) + previous.history
        } else {
            previous.history
        }.take(MAX_INSTALLATION_JOB_HISTORY)
        val saved = job.copy(
            name = normalizedJobName(job.name),
            status = if (calculationChanged) InstallationJobStatus.DRAFT else job.status,
            checkedBy = if (calculationChanged) null else job.checkedBy?.trim()?.take(120),
            checkedAtEpochMs = if (calculationChanged) null else job.checkedAtEpochMs,
            createdAtEpochMs = previous.createdAtEpochMs,
            modifiedAtEpochMs = now,
            lastOpenedAtEpochMs = now,
            deletedAtEpochMs = previous.deletedAtEpochMs,
            history = history,
        )
        project.copy(
            installationJobs = project.installationJobs.replaceAt(index, saved),
            activeInstallationJobId = saved.id.takeIf { saved.deletedAtEpochMs == null }
                ?: project.activeInstallationJobId,
        ) to saved
    }

    override suspend fun selectInstallationJob(
        projectId: ProjectId,
        jobId: InstallationJobId,
    ): ProjectResult<InstallationJob> = mutateProject(projectId) { project, now ->
        val index = project.installationJobs.indexOfFirst { it.id == jobId && it.deletedAtEpochMs == null }
        if (index < 0) throw InstallationJobMissing()
        val selected = project.installationJobs[index].copy(lastOpenedAtEpochMs = now)
        project.copy(
            installationJobs = project.installationJobs.replaceAt(index, selected),
            activeInstallationJobId = selected.id,
        ) to selected
    }

    override suspend fun renameInstallationJob(
        projectId: ProjectId,
        jobId: InstallationJobId,
        name: String,
    ): ProjectResult<InstallationJob> = mutateProject(projectId) { project, now ->
        val index = project.installationJobs.indexOfFirst { it.id == jobId }
        if (index < 0) throw InstallationJobMissing()
        val renamed = project.installationJobs[index].copy(
            name = normalizedJobName(name),
            modifiedAtEpochMs = now,
        )
        project.copy(installationJobs = project.installationJobs.replaceAt(index, renamed)) to renamed
    }

    override suspend fun duplicateInstallationJob(
        projectId: ProjectId,
        jobId: InstallationJobId,
    ): ProjectResult<InstallationJob> = mutateProject(projectId) { project, now ->
        val source = project.installationJobs.firstOrNull { it.id == jobId && it.deletedAtEpochMs == null }
            ?: throw InstallationJobMissing()
        val duplicate = source.copy(
            id = InstallationJobId(installationJobIdGenerator()),
            name = "${source.name} copy".take(120),
            createdAtEpochMs = now,
            modifiedAtEpochMs = now,
            lastOpenedAtEpochMs = now,
            deletedAtEpochMs = null,
            status = InstallationJobStatus.DRAFT,
            checkedBy = null,
            checkedAtEpochMs = null,
            history = emptyList(),
        )
        project.copy(
            installationJobs = project.installationJobs + duplicate,
            activeInstallationJobId = duplicate.id,
        ) to duplicate
    }

    override suspend fun deleteInstallationJob(
        projectId: ProjectId,
        jobId: InstallationJobId,
    ): ProjectResult<InstallationJob> = mutateProject(projectId) { project, now ->
        val index = project.installationJobs.indexOfFirst { it.id == jobId && it.deletedAtEpochMs == null }
        if (index < 0) throw InstallationJobMissing()
        val deleted = project.installationJobs[index].copy(
            modifiedAtEpochMs = now,
            deletedAtEpochMs = now,
        )
        val jobs = project.installationJobs.replaceAt(index, deleted)
        val nextActive = if (project.activeInstallationJobId == jobId) {
            jobs.filter { it.deletedAtEpochMs == null }.maxByOrNull { it.lastOpenedAtEpochMs }?.id
        } else {
            project.activeInstallationJobId
        }
        project.copy(installationJobs = jobs, activeInstallationJobId = nextActive) to deleted
    }

    override suspend fun restoreInstallationJob(
        projectId: ProjectId,
        jobId: InstallationJobId,
    ): ProjectResult<InstallationJob> = mutateProject(projectId) { project, now ->
        val index = project.installationJobs.indexOfFirst { it.id == jobId && it.deletedAtEpochMs != null }
        if (index < 0) throw InstallationJobMissing()
        val restored = project.installationJobs[index].copy(
            modifiedAtEpochMs = now,
            lastOpenedAtEpochMs = now,
            deletedAtEpochMs = null,
        )
        project.copy(
            installationJobs = project.installationJobs.replaceAt(index, restored),
            activeInstallationJobId = restored.id,
        ) to restored
    }

    private suspend fun <T> mutateProject(
        projectId: ProjectId,
        transform: (PlanProject, Long) -> Pair<PlanProject, T>,
    ): ProjectResult<T> = when (val loaded = load(projectId)) {
        is ProjectResult.Error -> loaded
        is ProjectResult.Ok -> try {
            val now = clock()
            val (changed, value) = transform(loaded.value, now)
            val updated = changed.copy(
                schemaVersion = PlanProject.CURRENT_SCHEMA,
                modifiedAtEpochMs = now,
            )
            when (val saved = save(updated)) {
                is ProjectResult.Ok -> ProjectResult.Ok(value)
                is ProjectResult.Error -> saved
            }
        } catch (_: InstallationJobMissing) {
            ProjectResult.Error(ProjectError.NotFound)
        } catch (e: IllegalArgumentException) {
            ProjectResult.Error(ProjectError.Io(e.message ?: "Invalid installation job"))
        }
    }

    private fun normalizedJobName(value: String): String =
        value.trim().take(120).also { require(it.isNotEmpty()) { "Installation job name cannot be empty" } }

    private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
        toMutableList().also { it[index] = value }

    private fun decode(file: File): PlanProject {
        val project = json.decodeFromString(PlanProject.serializer(), file.readText(Charsets.UTF_8))
        require(project.schemaVersion in 1..PlanProject.CURRENT_SCHEMA)
        val migrated = if (project.schemaVersion < 7) {
            project.copy(
                installationJobs = project.installationJobs.map { job ->
                    job.copy(
                        input = job.input.copy(
                            alongMm = job.input.overallFaceToFaceMm,
                            lateralOffsetMm = job.input.targetOffsetMm,
                            verticalOffsetMm = 0.0,
                        ),
                    )
                },
            )
        } else {
            project
        }
        return if (migrated.schemaVersion == PlanProject.CURRENT_SCHEMA) migrated
        else migrated.copy(schemaVersion = PlanProject.CURRENT_SCHEMA)
    }
    private fun file(id: ProjectId) = File(directory, "${safe(id.value)}.json")
    private fun backup(id: ProjectId) = File(directory, "${safe(id.value)}.backup.json")
    private fun trashFolder(id: ProjectId) = trashDirectory.resolve(safe(id.value))
    private fun move(source: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
    private fun safe(value: String) = value.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private class InstallationJobMissing : RuntimeException()

    private companion object {
        const val MAX_INSTALLATION_JOB_HISTORY = 20
    }
}
