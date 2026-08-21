package com.planruler.crm.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CrmDao {
    @Query("SELECT * FROM local_profiles ORDER BY lastUsedAtEpochMs DESC")
    suspend fun profiles(): List<ProfileEntity>

    @Query("SELECT * FROM local_profiles WHERE id = :id LIMIT 1")
    suspend fun profile(id: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: ProfileEntity)

    @Query("UPDATE local_profiles SET lastUsedAtEpochMs = :time WHERE id = :id")
    suspend fun touchProfile(id: String, time: Long)

    /** Room's `ON DELETE CASCADE` on client/site/work-order foreign keys removes everything under it. */
    @Query("DELETE FROM local_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("SELECT * FROM crm_clients WHERE profileId = :profileId AND (:includeArchived OR archivedAtEpochMs IS NULL) ORDER BY modifiedAtEpochMs DESC")
    suspend fun clients(profileId: String, includeArchived: Boolean): List<ClientEntity>

    @Query("SELECT * FROM crm_clients")
    suspend fun allClients(): List<ClientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveClient(client: ClientEntity)

    @Query("UPDATE crm_clients SET archivedAtEpochMs = :time, modifiedAtEpochMs = :time WHERE profileId = :profileId AND id = :clientId")
    suspend fun archiveClient(profileId: String, clientId: String, time: Long)

    @Query("UPDATE crm_clients SET archivedAtEpochMs = NULL, modifiedAtEpochMs = :time WHERE profileId = :profileId AND id = :clientId")
    suspend fun unarchiveClient(profileId: String, clientId: String, time: Long)

    /** Cascades to the client's sites and work orders through their foreign keys. */
    @Query("DELETE FROM crm_clients WHERE profileId = :profileId AND id = :clientId")
    suspend fun deleteClient(profileId: String, clientId: String)

    @Query("SELECT * FROM crm_sites WHERE profileId = :profileId AND (:includeArchived OR archivedAtEpochMs IS NULL) ORDER BY modifiedAtEpochMs DESC")
    suspend fun sites(profileId: String, includeArchived: Boolean): List<SiteEntity>

    @Query("SELECT * FROM crm_sites")
    suspend fun allSites(): List<SiteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSite(site: SiteEntity)

    @Query("SELECT * FROM work_orders WHERE profileId = :profileId AND (:includeArchived OR archivedAtEpochMs IS NULL) ORDER BY modifiedAtEpochMs DESC")
    suspend fun workOrders(profileId: String, includeArchived: Boolean): List<WorkOrderEntity>

    @Query("SELECT * FROM work_orders")
    suspend fun allWorkOrders(): List<WorkOrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWorkOrder(workOrder: WorkOrderEntity)

    @Query("DELETE FROM work_orders WHERE profileId = :profileId AND id = :workOrderId")
    suspend fun deleteWorkOrder(profileId: String, workOrderId: String)

    @Transaction
    suspend fun importAll(
        profiles: List<ProfileEntity>,
        clients: List<ClientEntity>,
        sites: List<SiteEntity>,
        workOrders: List<WorkOrderEntity>,
    ) {
        profiles.forEach { saveProfile(it) }
        clients.forEach { saveClient(it) }
        sites.forEach { saveSite(it) }
        workOrders.forEach { saveWorkOrder(it) }
    }
}
