package com.planruler.crm.api

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class LocalProfileId(val value: String)

@Serializable
@JvmInline
value class ClientId(val value: String)

@Serializable
@JvmInline
value class SiteId(val value: String)

@Serializable
@JvmInline
value class WorkOrderId(val value: String)

@Serializable
enum class LocalProfileRole { OWNER, TECHNICIAN, VIEWER }

@Serializable
enum class WorkOrderStage { LEAD, SURVEY, QUOTED, APPROVED, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED }

@Serializable
data class LocalProfile(
    val id: LocalProfileId,
    val displayName: String,
    val companyName: String = "",
    val role: LocalProfileRole = LocalProfileRole.OWNER,
    val createdAtEpochMs: Long,
    val lastUsedAtEpochMs: Long,
)

@Serializable
data class CrmClient(
    val id: ClientId,
    val profileId: LocalProfileId,
    val displayName: String,
    val companyName: String = "",
    val phone: String = "",
    val email: String = "",
    val note: String = "",
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
    val archivedAtEpochMs: Long? = null,
)

@Serializable
data class CrmSite(
    val id: SiteId,
    val profileId: LocalProfileId,
    val clientId: ClientId,
    val name: String,
    val address: String = "",
    val note: String = "",
    val projectId: String? = null,
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
    val archivedAtEpochMs: Long? = null,
)

@Serializable
data class WorkOrder(
    val id: WorkOrderId,
    val profileId: LocalProfileId,
    val clientId: ClientId,
    val siteId: SiteId? = null,
    val title: String,
    val description: String = "",
    val stage: WorkOrderStage = WorkOrderStage.LEAD,
    val scheduledAtEpochMs: Long? = null,
    val dueAtEpochMs: Long? = null,
    val quoteAmountMinor: Long? = null,
    val currencyCode: String = "CHF",
    val projectId: String? = null,
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
    val archivedAtEpochMs: Long? = null,
)

@Serializable
data class CrmBackupSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val profiles: List<LocalProfile>,
    val clients: List<CrmClient>,
    val sites: List<CrmSite>,
    val workOrders: List<WorkOrder>,
    val credentials: List<ProfileCredentialBackup> = emptyList(),
) {
    companion object { const val CURRENT_SCHEMA = 1 }
}

/** A salted PIN verifier is portable; the PIN itself is never stored or exported. */
@Serializable
data class ProfileCredentialBackup(
    val profileId: LocalProfileId,
    val saltBase64: String,
    val hashBase64: String,
    val iterations: Int,
)

interface CrmRepository {
    suspend fun listProfiles(): List<LocalProfile>
    suspend fun createProfile(displayName: String, companyName: String, pin: CharArray): LocalProfile
    suspend fun verifyPin(profileId: LocalProfileId, pin: CharArray): Boolean
    suspend fun touchProfile(profileId: LocalProfileId)
    /** Irreversibly removes the profile, every client/site/work order under it, and its Keystore key. */
    suspend fun deleteProfile(profileId: LocalProfileId)

    suspend fun listClients(profileId: LocalProfileId, includeArchived: Boolean = false): List<CrmClient>
    suspend fun saveClient(client: CrmClient)
    suspend fun archiveClient(profileId: LocalProfileId, clientId: ClientId, archivedAtEpochMs: Long)
    suspend fun unarchiveClient(profileId: LocalProfileId, clientId: ClientId, modifiedAtEpochMs: Long)
    /** Irreversibly removes the client and, through the foreign key, its sites and work orders. */
    suspend fun deleteClient(profileId: LocalProfileId, clientId: ClientId)

    suspend fun listSites(profileId: LocalProfileId, includeArchived: Boolean = false): List<CrmSite>
    suspend fun saveSite(site: CrmSite)

    suspend fun listWorkOrders(profileId: LocalProfileId, includeArchived: Boolean = false): List<WorkOrder>
    suspend fun saveWorkOrder(workOrder: WorkOrder)
    suspend fun deleteWorkOrder(profileId: LocalProfileId, workOrderId: WorkOrderId)

    /** Decrypted portable data that must be wrapped in a user-password encrypted backup. */
    suspend fun exportSnapshot(): CrmBackupSnapshot
    suspend fun importSnapshot(snapshot: CrmBackupSnapshot)
}
