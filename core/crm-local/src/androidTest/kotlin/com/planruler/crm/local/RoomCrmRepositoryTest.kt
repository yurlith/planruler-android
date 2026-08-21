package com.planruler.crm.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.planruler.crm.api.ClientId
import com.planruler.crm.api.CrmClient
import com.planruler.crm.api.LocalProfileId
import com.planruler.crm.api.WorkOrder
import com.planruler.crm.api.WorkOrderId
import com.planruler.crm.api.WorkOrderStage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCrmRepositoryTest {
    private lateinit var database: CrmDatabase
    private lateinit var repository: RoomCrmRepository
    private lateinit var cipher: RecordingFieldCipher
    private var id = 0
    private var now = 100L

    /** Records which profile keys were asked to be deleted, without touching the real Keystore. */
    private class RecordingFieldCipher : FieldCipher {
        val deletedKeys = mutableListOf<String>()
        override fun encrypt(profileId: String, plainText: String) = "$profileId::$plainText"
        override fun decrypt(profileId: String, encoded: String) = encoded.removePrefix("$profileId::")
        override fun deleteKey(profileId: String) { deletedKeys += profileId }
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            CrmDatabase::class.java,
        ).build()
        cipher = RecordingFieldCipher()
        repository = RoomCrmRepository(
            database = database,
            clock = { now },
            idGenerator = { "id-${++id}" },
            cipher = cipher,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun profilePinAndEncryptedCrudAreIsolated() = runBlocking {
        val first = repository.createProfile("Owner", "Heating AG", "1234".toCharArray())
        val second = repository.createProfile("Tech", "", "5678".toCharArray())
        assertTrue(repository.verifyPin(first.id, "1234".toCharArray()))
        assertFalse(repository.verifyPin(first.id, "9999".toCharArray()))

        repository.saveClient(client(first.id, "client-a", "Alice"))
        repository.saveClient(client(second.id, "client-b", "Bob"))
        assertEquals(listOf("Alice"), repository.listClients(first.id).map { it.displayName })
        assertEquals(listOf("Bob"), repository.listClients(second.id).map { it.displayName })

        repository.saveWorkOrder(
            WorkOrder(
                id = WorkOrderId("job-a"),
                profileId = first.id,
                clientId = ClientId("client-a"),
                title = "Boiler replacement",
                stage = WorkOrderStage.SCHEDULED,
                createdAtEpochMs = now,
                modifiedAtEpochMs = now,
            ),
        )
        assertEquals("Boiler replacement", repository.listWorkOrders(first.id).single().title)
        assertTrue(repository.listWorkOrders(second.id).isEmpty())
    }

    @Test
    fun snapshotContainsPortablePinVerifierButNeverThePin() = runBlocking {
        val profile = repository.createProfile("Owner", "", "2468".toCharArray())
        repository.saveClient(client(profile.id, "client-a", "Alice"))
        val snapshot = repository.exportSnapshot()
        assertEquals(1, snapshot.credentials.size)
        assertFalse(snapshot.credentials.single().hashBase64.contains("2468"))
        assertEquals("Alice", snapshot.clients.single().displayName)
    }

    @Test
    fun archivedClientIsHiddenThenRestoredByUnarchive() = runBlocking {
        val profile = repository.createProfile("Owner", "", "1111".toCharArray())
        repository.saveClient(client(profile.id, "client-a", "Alice"))

        repository.archiveClient(profile.id, ClientId("client-a"), now)
        assertTrue(repository.listClients(profile.id).isEmpty())
        assertEquals(listOf("Alice"), repository.listClients(profile.id, includeArchived = true).map { it.displayName })

        repository.unarchiveClient(profile.id, ClientId("client-a"), now)
        assertEquals(listOf("Alice"), repository.listClients(profile.id).map { it.displayName })
    }

    @Test
    fun deletingAClientCascadesToItsWorkOrders() = runBlocking {
        val profile = repository.createProfile("Owner", "", "1111".toCharArray())
        repository.saveClient(client(profile.id, "client-a", "Alice"))
        repository.saveWorkOrder(
            WorkOrder(
                id = WorkOrderId("job-a"),
                profileId = profile.id,
                clientId = ClientId("client-a"),
                title = "Boiler replacement",
                createdAtEpochMs = now,
                modifiedAtEpochMs = now,
            ),
        )

        repository.deleteClient(profile.id, ClientId("client-a"))

        assertTrue(repository.listClients(profile.id, includeArchived = true).isEmpty())
        assertTrue(
            "deleting a client must cascade to its work orders",
            repository.listWorkOrders(profile.id, includeArchived = true).isEmpty(),
        )
    }

    @Test
    fun deletingAWorkOrderLeavesTheClientAndOtherWorkOrdersIntact() = runBlocking {
        val profile = repository.createProfile("Owner", "", "1111".toCharArray())
        repository.saveClient(client(profile.id, "client-a", "Alice"))
        repository.saveWorkOrder(
            WorkOrder(WorkOrderId("job-a"), profile.id, ClientId("client-a"), title = "First", createdAtEpochMs = now, modifiedAtEpochMs = now),
        )
        repository.saveWorkOrder(
            WorkOrder(WorkOrderId("job-b"), profile.id, ClientId("client-a"), title = "Second", createdAtEpochMs = now, modifiedAtEpochMs = now),
        )

        repository.deleteWorkOrder(profile.id, WorkOrderId("job-a"))

        assertEquals(listOf("Second"), repository.listWorkOrders(profile.id).map { it.title })
        assertEquals("Alice", repository.listClients(profile.id).single().displayName)
    }

    @Test
    fun deletingAProfileCascadesAndReleasesItsKeystoreKey() = runBlocking {
        val first = repository.createProfile("Owner", "", "1111".toCharArray())
        val second = repository.createProfile("Tech", "", "2222".toCharArray())
        repository.saveClient(client(first.id, "client-a", "Alice"))
        repository.saveClient(client(second.id, "client-b", "Bob"))

        repository.deleteProfile(first.id)

        assertTrue(repository.listProfiles().none { it.id == first.id })
        assertTrue(
            "deleting a profile must cascade to its own clients only",
            repository.listClients(first.id, includeArchived = true).isEmpty(),
        )
        assertEquals("Bob", repository.listClients(second.id).single().displayName)
        assertEquals(listOf(first.id.value), cipher.deletedKeys)
    }

    @Test
    fun savingAndReloadingAWorkOrderPreservesAndClearsItsLinkedProject() = runBlocking {
        val profile = repository.createProfile("Owner", "", "1111".toCharArray())
        repository.saveClient(client(profile.id, "client-a", "Alice"))
        val workOrder = WorkOrder(
            WorkOrderId("job-a"),
            profile.id,
            ClientId("client-a"),
            title = "Boiler replacement",
            projectId = "takeoff-project-1",
            createdAtEpochMs = now,
            modifiedAtEpochMs = now,
        )
        repository.saveWorkOrder(workOrder)
        assertEquals("takeoff-project-1", repository.listWorkOrders(profile.id).single().projectId)

        repository.saveWorkOrder(workOrder.copy(projectId = null, modifiedAtEpochMs = now))
        assertEquals(null, repository.listWorkOrders(profile.id).single().projectId)
    }

    private fun client(profileId: LocalProfileId, id: String, name: String) = CrmClient(
        id = ClientId(id),
        profileId = profileId,
        displayName = name,
        createdAtEpochMs = now,
        modifiedAtEpochMs = now,
    )
}
