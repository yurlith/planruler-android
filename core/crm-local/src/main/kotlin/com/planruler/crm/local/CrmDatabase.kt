package com.planruler.crm.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProfileEntity::class, ClientEntity::class, SiteEntity::class, WorkOrderEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CrmDatabase : RoomDatabase() {
    abstract fun dao(): CrmDao

    companion object {
        fun create(context: Context): CrmDatabase = Room.databaseBuilder(
            context.applicationContext,
            CrmDatabase::class.java,
            "planruler-local-crm.db",
        ).build()
    }
}
