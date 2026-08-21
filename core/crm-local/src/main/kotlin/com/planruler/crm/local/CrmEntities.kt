package com.planruler.crm.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "local_profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val displayNameEncrypted: String,
    val companyNameEncrypted: String,
    val role: String,
    val pinSaltBase64: String,
    val pinHashBase64: String,
    val pinIterations: Int,
    val createdAtEpochMs: Long,
    val lastUsedAtEpochMs: Long,
)

@Entity(
    tableName = "crm_clients",
    foreignKeys = [ForeignKey(
        entity = ProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("profileId")],
)
data class ClientEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val displayNameEncrypted: String,
    val companyNameEncrypted: String,
    val phoneEncrypted: String,
    val emailEncrypted: String,
    val noteEncrypted: String,
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
    val archivedAtEpochMs: Long?,
)

@Entity(
    tableName = "crm_sites",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId"), Index("clientId")],
)
data class SiteEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val clientId: String,
    val nameEncrypted: String,
    val addressEncrypted: String,
    val noteEncrypted: String,
    val projectId: String?,
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
    val archivedAtEpochMs: Long?,
)

@Entity(
    tableName = "work_orders",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId"), Index("clientId"), Index("siteId")],
)
data class WorkOrderEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val clientId: String,
    val siteId: String?,
    val titleEncrypted: String,
    val descriptionEncrypted: String,
    val stage: String,
    val scheduledAtEpochMs: Long?,
    val dueAtEpochMs: Long?,
    val quoteAmountMinor: Long?,
    val currencyCode: String,
    val projectId: String?,
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
    val archivedAtEpochMs: Long?,
)
