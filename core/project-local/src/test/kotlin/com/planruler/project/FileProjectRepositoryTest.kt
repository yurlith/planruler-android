package com.planruler.project

import com.planruler.model.*
import com.planruler.project.api.ProjectError
import com.planruler.project.api.ProjectResult
import com.planruler.project.local.FileProjectRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class FileProjectRepositoryTest {
    private fun project(id: String = "p1") = PlanProject(
        id = ProjectId(id),
        name = "Test",
        createdAtEpochMs = 1,
        modifiedAtEpochMs = 2,
        documentUri = "content://sample",
        mimeType = "application/pdf",
        pages = listOf(PageMetadata(0, 612.0, 792.0, PageMetadata.CoordinateUnit.PDF_POINT)),
        calibration = Calibration.pdfRatio(
            50.0,
            CalibrationAudit(
                calibratedAtEpochMs = 10,
                calibratedBy = "Tester",
                pageIndex = 0,
                printRatio = 50.0,
                printSizeConfirmed = true,
                verification = CalibrationVerification(11, 0, 72.0, 1.27, 1.27, LengthUnit.METER),
            ),
        ),
        measurements = listOf(Measurement(MeasurementId("m1"), MeasurementType.DISTANCE, listOf(DocPoint(0.0, 0.0), DocPoint(72.0, 0.0)), createdAtEpochMs = 1)),
    )

    @Test fun `round trip preserves project geometry`() = runBlocking {
        val root = Files.createTempDirectory("planruler-test").toFile()
        val repository = FileProjectRepository(root)
        assertTrue(repository.save(project()) is ProjectResult.Ok)
        val loaded = (repository.load(ProjectId("p1")) as ProjectResult.Ok).value
        assertEquals(project(), loaded)
        assertTrue(root.resolve("p1.json").exists())
        assertEquals("Tester", loaded.calibration?.audit?.calibratedBy)
        assertEquals(1.27, loaded.calibration?.audit?.verification?.measuredLength ?: 0.0, 0.0)
    }

    @Test fun `schema two project migrates without losing its scale`() = runBlocking {
        val root = Files.createTempDirectory("planruler-schema2").toFile()
        val legacy = project().copy(schemaVersion = 2, calibration = Calibration.pdfRatio(100.0))
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val encoded = json.encodeToJsonElement(PlanProject.serializer(), legacy).jsonObject
        val withoutInstallationJobs = JsonObject(
            encoded.filterKeys { it != "installationJobs" && it != "activeInstallationJobId" },
        )
        root.resolve("p1.json").writeText(withoutInstallationJobs.toString(), Charsets.UTF_8)

        val loaded = (FileProjectRepository(root).load(ProjectId("p1")) as ProjectResult.Ok).value
        assertEquals(PlanProject.CURRENT_SCHEMA, loaded.schemaVersion)
        assertEquals(Calibration.Method.PRINT_RATIO, loaded.calibration?.method)
        assertNull(loaded.calibration?.audit)
        assertTrue(loaded.installationJobs.isEmpty())
    }

    @Test fun `corrupt project is reported`() = runBlocking {
        val root = Files.createTempDirectory("planruler-corrupt").toFile()
        root.resolve("p1.json").writeText("{not-json")
        val result = FileProjectRepository(root).load(ProjectId("p1"))
        assertTrue(result is ProjectResult.Error && result.error == ProjectError.Corrupt)
    }

    @Test fun `previous valid version is available as backup`() = runBlocking {
        val root = Files.createTempDirectory("planruler-backup").toFile()
        val repository = FileProjectRepository(root)
        repository.save(project())
        repository.save(project().copy(name = "Second"))
        root.resolve("p1.json").writeText("broken")
        val loaded = (repository.load(ProjectId("p1")) as ProjectResult.Ok).value
        assertEquals("Test", loaded.name)
    }

    @Test fun `rename updates timestamp and preserves project contents`() = runBlocking {
        val root = Files.createTempDirectory("planruler-rename").toFile()
        val repository = FileProjectRepository(root, clock = { 50L })
        repository.save(project())
        val renamed = (repository.rename(ProjectId("p1"), "  Heating plan  ") as ProjectResult.Ok).value
        assertEquals("Heating plan", renamed.name)
        assertEquals(50L, renamed.modifiedAtEpochMs)
        assertEquals(project().measurements, renamed.measurements)
        assertEquals(renamed, (repository.load(ProjectId("p1")) as ProjectResult.Ok).value)
    }

    @Test fun `duplicate has isolated identity and leaves source unchanged`() = runBlocking {
        val root = Files.createTempDirectory("planruler-duplicate").toFile()
        val repository = FileProjectRepository(root, clock = { 99L }, idGenerator = { "p2" })
        repository.save(project())
        val duplicate = (repository.duplicate(ProjectId("p1")) as ProjectResult.Ok).value
        assertEquals(ProjectId("p2"), duplicate.id)
        assertEquals("Test copy", duplicate.name)
        assertEquals(99L, duplicate.createdAtEpochMs)
        assertEquals(99L, duplicate.modifiedAtEpochMs)
        assertEquals(project().measurements, duplicate.measurements)
        assertEquals(project(), (repository.load(ProjectId("p1")) as ProjectResult.Ok).value)
        assertEquals(2, (repository.list() as ProjectResult.Ok).value.size)
    }

    @Test fun `delete moves project and backup to recycle bin and restore is lossless`() = runBlocking {
        val root = Files.createTempDirectory("planruler-trash").toFile()
        var now = 100L
        val repository = FileProjectRepository(root, clock = { now })
        repository.save(project())
        repository.save(project().copy(name = "Latest"))

        assertTrue(repository.delete(ProjectId("p1")) is ProjectResult.Ok)
        assertFalse(root.resolve("p1.json").exists())
        assertFalse(root.resolve("p1.backup.json").exists())
        val trash = (repository.listTrash() as ProjectResult.Ok).value
        assertEquals(1, trash.size)
        assertEquals("Latest", trash.single().project.name)
        assertTrue(root.resolve(".trash/p1/current.json").exists())
        assertTrue(root.resolve(".trash/p1").listFiles().orEmpty().any { it.name.startsWith("backup-") })

        now = 200L
        val restored = (repository.restore(ProjectId("p1")) as ProjectResult.Ok).value
        assertEquals("Latest", restored.name)
        assertEquals(200L, restored.modifiedAtEpochMs)
        assertTrue(root.resolve("p1.json").exists())
        assertTrue((repository.listTrash() as ProjectResult.Ok).value.isEmpty())
    }

    @Test fun `permanent deletion is explicit and limited to trash`() = runBlocking {
        val root = Files.createTempDirectory("planruler-purge").toFile()
        val repository = FileProjectRepository(root)
        repository.save(project("p1"))
        repository.save(project("p2"))
        repository.delete(ProjectId("p1"))

        assertTrue(repository.deletePermanently(ProjectId("p1")) is ProjectResult.Ok)
        assertTrue((repository.listTrash() as ProjectResult.Ok).value.isEmpty())
        assertEquals(project("p2"), (repository.load(ProjectId("p2")) as ProjectResult.Ok).value)
    }

    @Test fun `installation job autosave survives repository restart and records history`() = runBlocking {
        val root = Files.createTempDirectory("planruler-job-save").toFile()
        var now = 10L
        val repository = FileProjectRepository(
            root,
            clock = { now++ },
            installationJobIdGenerator = { "job-1" },
        )
        repository.save(project())
        val created = (repository.createInstallationJob(ProjectId("p1"), "  Узел котельной  ") as ProjectResult.Ok).value
        assertEquals("Узел котельной", created.name)

        val changed = created.copy(
            input = created.input.copy(targetOffsetMm = 725.0),
            chainRecipe = InstallationChainRecipe(encodedPlan = "recipe"),
        )
        val saved = (repository.saveInstallationJob(ProjectId("p1"), changed) as ProjectResult.Ok).value
        assertEquals(1, saved.history.size)
        assertEquals(500.0, saved.history.single().input.targetOffsetMm, 0.0)

        val reopened = FileProjectRepository(root)
        val loaded = (reopened.load(ProjectId("p1")) as ProjectResult.Ok).value
        assertEquals(InstallationJobId("job-1"), loaded.activeInstallationJobId)
        assertEquals(725.0, loaded.installationJobs.single().input.targetOffsetMm, 0.0)
        assertEquals("recipe", loaded.installationJobs.single().chainRecipe?.encodedPlan)
    }

    @Test fun `field acceptance persists and geometry changes invalidate it`() = runBlocking {
        val root = Files.createTempDirectory("planruler-job-check").toFile()
        var now = 30L
        val repository = FileProjectRepository(
            root,
            clock = { now++ },
            installationJobIdGenerator = { "job-1" },
        )
        repository.save(project())
        val created = (repository.createInstallationJob(ProjectId("p1"), "Checked spool") as ProjectResult.Ok).value
        val checked = (repository.saveInstallationJob(
            ProjectId("p1"),
            created.copy(
                status = InstallationJobStatus.CHECKED,
                checkedBy = "  Site foreman  ",
                checkedAtEpochMs = 1_234L,
            ),
        ) as ProjectResult.Ok).value

        assertEquals(InstallationJobStatus.CHECKED, checked.status)
        assertEquals("Site foreman", checked.checkedBy)
        assertEquals(1_234L, checked.checkedAtEpochMs)

        val changed = (repository.saveInstallationJob(
            ProjectId("p1"),
            checked.copy(input = checked.input.copy(alongMm = checked.input.alongMm + 5.0)),
        ) as ProjectResult.Ok).value
        assertEquals(InstallationJobStatus.DRAFT, changed.status)
        assertNull(changed.checkedBy)
        assertNull(changed.checkedAtEpochMs)
    }

    @Test fun `installation jobs support rename duplicate recycle and restore`() = runBlocking {
        val root = Files.createTempDirectory("planruler-job-actions").toFile()
        var now = 100L
        val ids = ArrayDeque(listOf("job-1", "job-2"))
        val repository = FileProjectRepository(
            root,
            clock = { now++ },
            installationJobIdGenerator = { ids.removeFirst() },
        )
        repository.save(project())
        val original = (repository.createInstallationJob(ProjectId("p1"), "Узел 1") as ProjectResult.Ok).value
        val renamed = (repository.renameInstallationJob(ProjectId("p1"), original.id, "Подача") as ProjectResult.Ok).value
        assertEquals("Подача", renamed.name)

        val duplicate = (repository.duplicateInstallationJob(ProjectId("p1"), original.id) as ProjectResult.Ok).value
        assertEquals(InstallationJobId("job-2"), duplicate.id)
        assertEquals("Подача copy", duplicate.name)
        assertTrue(duplicate.history.isEmpty())
        assertEquals(InstallationJobStatus.DRAFT, duplicate.status)
        assertNull(duplicate.checkedBy)

        val deleted = (repository.deleteInstallationJob(ProjectId("p1"), duplicate.id) as ProjectResult.Ok).value
        assertNotNull(deleted.deletedAtEpochMs)
        var loaded = (repository.load(ProjectId("p1")) as ProjectResult.Ok).value
        assertEquals(original.id, loaded.activeInstallationJobId)

        val restored = (repository.restoreInstallationJob(ProjectId("p1"), duplicate.id) as ProjectResult.Ok).value
        assertNull(restored.deletedAtEpochMs)
        loaded = (repository.load(ProjectId("p1")) as ProjectResult.Ok).value
        assertEquals(duplicate.id, loaded.activeInstallationJobId)
        assertEquals(2, loaded.installationJobs.count { it.deletedAtEpochMs == null })
    }

    @Test fun `installation calculation history is bounded`() = runBlocking {
        val root = Files.createTempDirectory("planruler-job-history").toFile()
        var now = 1L
        val repository = FileProjectRepository(
            root,
            clock = { now++ },
            installationJobIdGenerator = { "job-1" },
        )
        repository.save(project())
        var job = (repository.createInstallationJob(ProjectId("p1"), "Узел") as ProjectResult.Ok).value
        repeat(25) { revision ->
            job = (repository.saveInstallationJob(
                ProjectId("p1"),
                job.copy(input = job.input.copy(targetOffsetMm = 501.0 + revision)),
            ) as ProjectResult.Ok).value
        }
        assertEquals(20, job.history.size)
        assertEquals(525.0, job.input.targetOffsetMm, 0.0)
        assertEquals(524.0, job.history.first().input.targetOffsetMm, 0.0)
    }

    @Test fun `schema six installation dimensions migrate to installer terms`() = runBlocking {
        val root = Files.createTempDirectory("planruler-schema6-job").toFile()
        val legacyJob = InstallationJob(
            id = InstallationJobId("job-1"),
            name = "Legacy",
            input = InstallationJobInput(
                overallFaceToFaceMm = 2_500.0,
                targetOffsetMm = 800.0,
                // These fields did not exist in schema 6 and decode to their defaults.
                alongMm = 1_600.0,
                lateralOffsetMm = 500.0,
            ),
            createdAtEpochMs = 1L,
            modifiedAtEpochMs = 2L,
        )
        val legacy = project().copy(schemaVersion = 6, installationJobs = listOf(legacyJob))
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val encoded = json.encodeToJsonElement(PlanProject.serializer(), legacy).jsonObject
        val jobs = encoded.getValue("installationJobs").jsonArray.map { element ->
            JsonObject(
                element.jsonObject.toMutableMap().also { jobFields ->
                    jobFields.remove("source2D")
                    val input = jobFields.getValue("input").jsonObject
                    jobFields["input"] = JsonObject(
                        input.filterKeys {
                            it !in setOf(
                                "material", "materialName", "inputMode", "startReference", "endReference", "alongMm",
                                "lateralOffsetMm", "verticalOffsetMm", "lateralDirection", "verticalDirection",
                                "endDirection", "minimumStraightMm",
                            )
                        },
                    )
                },
            )
        }
        val legacyJson = JsonObject(encoded.toMutableMap().also { it["installationJobs"] = kotlinx.serialization.json.JsonArray(jobs) })
        root.resolve("p1.json").writeText(legacyJson.toString(), Charsets.UTF_8)

        val loaded = (FileProjectRepository(root).load(ProjectId("p1")) as ProjectResult.Ok).value
        val input = loaded.installationJobs.single().input
        assertEquals(2_500.0, input.alongMm, 0.0)
        assertEquals(800.0, input.lateralOffsetMm, 0.0)
        assertEquals(0.0, input.verticalOffsetMm, 0.0)
        assertEquals(null, loaded.installationJobs.single().source2D)
        assertEquals(PlanProject.CURRENT_SCHEMA, loaded.schemaVersion)
    }
}
