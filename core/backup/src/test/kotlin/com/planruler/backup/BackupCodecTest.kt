package com.planruler.backup

import com.planruler.crm.api.CrmBackupSnapshot
import com.planruler.model.PageMetadata
import com.planruler.model.PlanProject
import com.planruler.model.ProjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupCodecTest {
    private val project = PlanProject(
        id = ProjectId("p1"),
        name = "Heating Zürich",
        createdAtEpochMs = 1,
        modifiedAtEpochMs = 2,
        documentUri = "content://local/plan.pdf",
        mimeType = "application/pdf",
        pages = listOf(PageMetadata(0, 100.0, 200.0, PageMetadata.CoordinateUnit.PDF_POINT)),
    )
    private val payload = PlanRulerBackupPayload(
        createdAtEpochMs = 3,
        appVersion = "test",
        activeProjects = listOf(project),
        trash = listOf(BackupTrashEntry(project.copy(id = ProjectId("trash")), 2)),
        crm = CrmBackupSnapshot(
            profiles = emptyList(),
            clients = emptyList(),
            sites = emptyList(),
            workOrders = emptyList(),
        ),
    )

    @Test
    fun `password encrypted backup round trips without exposing project text`() {
        val encoded = EncryptedBackupCodec.encode(payload, "strong password".toCharArray())
        assertFalse(encoded.toString(Charsets.UTF_8).contains("Heating Zürich"))
        assertEquals(payload, EncryptedBackupCodec.decode(encoded, "strong password".toCharArray()))
    }

    @Test(expected = BackupException::class)
    fun `wrong password is rejected`() {
        val encoded = EncryptedBackupCodec.encode(payload, "strong password".toCharArray())
        EncryptedBackupCodec.decode(encoded, "wrong password".toCharArray())
    }

    @Test(expected = BackupException::class)
    fun `corrupted envelope is rejected`() {
        val encoded = EncryptedBackupCodec.encode(payload, "strong password".toCharArray())
        encoded[encoded.lastIndex / 2] = (encoded[encoded.lastIndex / 2].toInt() xor 1).toByte()
        EncryptedBackupCodec.decode(encoded, "strong password".toCharArray())
    }
}
