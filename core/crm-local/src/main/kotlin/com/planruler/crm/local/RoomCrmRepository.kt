package com.planruler.crm.local

import com.planruler.crm.api.ClientId
import com.planruler.crm.api.CrmBackupSnapshot
import com.planruler.crm.api.CrmClient
import com.planruler.crm.api.CrmRepository
import com.planruler.crm.api.CrmSite
import com.planruler.crm.api.LocalProfile
import com.planruler.crm.api.LocalProfileId
import com.planruler.crm.api.LocalProfileRole
import com.planruler.crm.api.ProfileCredentialBackup
import com.planruler.crm.api.SiteId
import com.planruler.crm.api.WorkOrder
import com.planruler.crm.api.WorkOrderId
import com.planruler.crm.api.WorkOrderStage
import java.util.UUID

class RoomCrmRepository(
    private val database: CrmDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val cipher: FieldCipher = KeystoreFieldCipher(),
) : CrmRepository {
    private val dao = database.dao()

    override suspend fun listProfiles(): List<LocalProfile> = dao.profiles().map(::profile)

    override suspend fun createProfile(displayName: String, companyName: String, pin: CharArray): LocalProfile {
        val name = displayName.normalized("Profile name")
        val company = companyName.trim().take(160)
        val id = idGenerator()
        val now = clock()
        val verifier = PinSecurity.create(pin)
        val entity = ProfileEntity(
            id = id,
            displayNameEncrypted = cipher.encrypt(id, name),
            companyNameEncrypted = cipher.encrypt(id, company),
            role = LocalProfileRole.OWNER.name,
            pinSaltBase64 = verifier.saltBase64,
            pinHashBase64 = verifier.hashBase64,
            pinIterations = verifier.iterations,
            createdAtEpochMs = now,
            lastUsedAtEpochMs = now,
        )
        dao.saveProfile(entity)
        return profile(entity)
    }

    override suspend fun verifyPin(profileId: LocalProfileId, pin: CharArray): Boolean {
        val entity = dao.profile(profileId.value) ?: run {
            pin.fill('\u0000')
            return false
        }
        return PinSecurity.verify(
            pin,
            PinVerifier(entity.pinSaltBase64, entity.pinHashBase64, entity.pinIterations),
        )
    }

    override suspend fun touchProfile(profileId: LocalProfileId) = dao.touchProfile(profileId.value, clock())

    override suspend fun deleteProfile(profileId: LocalProfileId) {
        dao.deleteProfile(profileId.value)
        cipher.deleteKey(profileId.value)
    }

    override suspend fun listClients(profileId: LocalProfileId, includeArchived: Boolean): List<CrmClient> =
        dao.clients(profileId.value, includeArchived).map(::client)

    override suspend fun saveClient(client: CrmClient) {
        require(dao.profile(client.profileId.value) != null) { "Unknown local profile" }
        dao.saveClient(entity(client.copy(displayName = client.displayName.normalized("Client name"))))
    }

    override suspend fun archiveClient(profileId: LocalProfileId, clientId: ClientId, archivedAtEpochMs: Long) {
        dao.archiveClient(profileId.value, clientId.value, archivedAtEpochMs)
    }

    override suspend fun unarchiveClient(profileId: LocalProfileId, clientId: ClientId, modifiedAtEpochMs: Long) {
        dao.unarchiveClient(profileId.value, clientId.value, modifiedAtEpochMs)
    }

    override suspend fun deleteClient(profileId: LocalProfileId, clientId: ClientId) {
        dao.deleteClient(profileId.value, clientId.value)
    }

    override suspend fun listSites(profileId: LocalProfileId, includeArchived: Boolean): List<CrmSite> =
        dao.sites(profileId.value, includeArchived).map(::site)

    override suspend fun saveSite(site: CrmSite) {
        dao.saveSite(entity(site.copy(name = site.name.normalized("Site name"))))
    }

    override suspend fun listWorkOrders(profileId: LocalProfileId, includeArchived: Boolean): List<WorkOrder> =
        dao.workOrders(profileId.value, includeArchived).map(::workOrder)

    override suspend fun saveWorkOrder(workOrder: WorkOrder) {
        dao.saveWorkOrder(entity(workOrder.copy(title = workOrder.title.normalized("Work-order title"))))
    }

    override suspend fun deleteWorkOrder(profileId: LocalProfileId, workOrderId: WorkOrderId) {
        dao.deleteWorkOrder(profileId.value, workOrderId.value)
    }

    override suspend fun exportSnapshot(): CrmBackupSnapshot {
        val profileEntities = dao.profiles()
        return CrmBackupSnapshot(
            profiles = profileEntities.map(::profile),
            clients = dao.allClients().map(::client),
            sites = dao.allSites().map(::site),
            workOrders = dao.allWorkOrders().map(::workOrder),
            credentials = profileEntities.map {
                ProfileCredentialBackup(
                    LocalProfileId(it.id),
                    it.pinSaltBase64,
                    it.pinHashBase64,
                    it.pinIterations,
                )
            },
        )
    }

    override suspend fun importSnapshot(snapshot: CrmBackupSnapshot) {
        require(snapshot.schemaVersion in 1..CrmBackupSnapshot.CURRENT_SCHEMA) { "Unsupported CRM backup version" }
        val credentials = snapshot.credentials.associateBy { it.profileId }
        val profileEntities = snapshot.profiles.map { value ->
            val credential = requireNotNull(credentials[value.id]) { "Backup has no PIN verifier for ${value.id.value}" }
            entity(value, credential)
        }
        dao.importAll(
            profileEntities,
            snapshot.clients.map(::entity),
            snapshot.sites.map(::entity),
            snapshot.workOrders.map(::entity),
        )
    }

    private fun profile(entity: ProfileEntity) = LocalProfile(
        id = LocalProfileId(entity.id),
        displayName = cipher.decrypt(entity.id, entity.displayNameEncrypted),
        companyName = cipher.decrypt(entity.id, entity.companyNameEncrypted),
        role = enumOrDefault(entity.role, LocalProfileRole.OWNER),
        createdAtEpochMs = entity.createdAtEpochMs,
        lastUsedAtEpochMs = entity.lastUsedAtEpochMs,
    )

    private fun entity(value: LocalProfile, credential: ProfileCredentialBackup) = ProfileEntity(
        id = value.id.value,
        displayNameEncrypted = cipher.encrypt(value.id.value, value.displayName),
        companyNameEncrypted = cipher.encrypt(value.id.value, value.companyName),
        role = value.role.name,
        pinSaltBase64 = credential.saltBase64,
        pinHashBase64 = credential.hashBase64,
        pinIterations = credential.iterations,
        createdAtEpochMs = value.createdAtEpochMs,
        lastUsedAtEpochMs = value.lastUsedAtEpochMs,
    )

    private fun client(entity: ClientEntity) = CrmClient(
        id = ClientId(entity.id),
        profileId = LocalProfileId(entity.profileId),
        displayName = cipher.decrypt(entity.profileId, entity.displayNameEncrypted),
        companyName = cipher.decrypt(entity.profileId, entity.companyNameEncrypted),
        phone = cipher.decrypt(entity.profileId, entity.phoneEncrypted),
        email = cipher.decrypt(entity.profileId, entity.emailEncrypted),
        note = cipher.decrypt(entity.profileId, entity.noteEncrypted),
        createdAtEpochMs = entity.createdAtEpochMs,
        modifiedAtEpochMs = entity.modifiedAtEpochMs,
        archivedAtEpochMs = entity.archivedAtEpochMs,
    )

    private fun entity(value: CrmClient) = ClientEntity(
        id = value.id.value,
        profileId = value.profileId.value,
        displayNameEncrypted = cipher.encrypt(value.profileId.value, value.displayName),
        companyNameEncrypted = cipher.encrypt(value.profileId.value, value.companyName),
        phoneEncrypted = cipher.encrypt(value.profileId.value, value.phone),
        emailEncrypted = cipher.encrypt(value.profileId.value, value.email),
        noteEncrypted = cipher.encrypt(value.profileId.value, value.note),
        createdAtEpochMs = value.createdAtEpochMs,
        modifiedAtEpochMs = value.modifiedAtEpochMs,
        archivedAtEpochMs = value.archivedAtEpochMs,
    )

    private fun site(entity: SiteEntity) = CrmSite(
        id = SiteId(entity.id),
        profileId = LocalProfileId(entity.profileId),
        clientId = ClientId(entity.clientId),
        name = cipher.decrypt(entity.profileId, entity.nameEncrypted),
        address = cipher.decrypt(entity.profileId, entity.addressEncrypted),
        note = cipher.decrypt(entity.profileId, entity.noteEncrypted),
        projectId = entity.projectId,
        createdAtEpochMs = entity.createdAtEpochMs,
        modifiedAtEpochMs = entity.modifiedAtEpochMs,
        archivedAtEpochMs = entity.archivedAtEpochMs,
    )

    private fun entity(value: CrmSite) = SiteEntity(
        id = value.id.value,
        profileId = value.profileId.value,
        clientId = value.clientId.value,
        nameEncrypted = cipher.encrypt(value.profileId.value, value.name),
        addressEncrypted = cipher.encrypt(value.profileId.value, value.address),
        noteEncrypted = cipher.encrypt(value.profileId.value, value.note),
        projectId = value.projectId,
        createdAtEpochMs = value.createdAtEpochMs,
        modifiedAtEpochMs = value.modifiedAtEpochMs,
        archivedAtEpochMs = value.archivedAtEpochMs,
    )

    private fun workOrder(entity: WorkOrderEntity) = WorkOrder(
        id = WorkOrderId(entity.id),
        profileId = LocalProfileId(entity.profileId),
        clientId = ClientId(entity.clientId),
        siteId = entity.siteId?.let(::SiteId),
        title = cipher.decrypt(entity.profileId, entity.titleEncrypted),
        description = cipher.decrypt(entity.profileId, entity.descriptionEncrypted),
        stage = enumOrDefault(entity.stage, WorkOrderStage.LEAD),
        scheduledAtEpochMs = entity.scheduledAtEpochMs,
        dueAtEpochMs = entity.dueAtEpochMs,
        quoteAmountMinor = entity.quoteAmountMinor,
        currencyCode = entity.currencyCode,
        projectId = entity.projectId,
        createdAtEpochMs = entity.createdAtEpochMs,
        modifiedAtEpochMs = entity.modifiedAtEpochMs,
        archivedAtEpochMs = entity.archivedAtEpochMs,
    )

    private fun entity(value: WorkOrder) = WorkOrderEntity(
        id = value.id.value,
        profileId = value.profileId.value,
        clientId = value.clientId.value,
        siteId = value.siteId?.value,
        titleEncrypted = cipher.encrypt(value.profileId.value, value.title),
        descriptionEncrypted = cipher.encrypt(value.profileId.value, value.description),
        stage = value.stage.name,
        scheduledAtEpochMs = value.scheduledAtEpochMs,
        dueAtEpochMs = value.dueAtEpochMs,
        quoteAmountMinor = value.quoteAmountMinor,
        currencyCode = value.currencyCode,
        projectId = value.projectId,
        createdAtEpochMs = value.createdAtEpochMs,
        modifiedAtEpochMs = value.modifiedAtEpochMs,
        archivedAtEpochMs = value.archivedAtEpochMs,
    )

    private fun String.normalized(label: String): String = trim().take(160).also {
        require(it.isNotEmpty()) { "$label cannot be empty" }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
