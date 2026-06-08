package com.example.tenant_landlorddisputedocumenter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.tenant_landlorddisputedocumenter.data.local.dao.DisputeDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.ItemDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.NotificationDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.PhotoDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.PropertyDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.RoomDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.SignatureDao
import com.example.tenant_landlorddisputedocumenter.data.local.dao.UserDao
import com.example.tenant_landlorddisputedocumenter.data.local.entity.DisputeEntity
import com.example.tenant_landlorddisputedocumenter.data.local.entity.ItemEntity
import com.example.tenant_landlorddisputedocumenter.data.local.entity.NotificationEntity
import com.example.tenant_landlorddisputedocumenter.data.local.entity.PhotoEntity
import com.example.tenant_landlorddisputedocumenter.data.local.entity.PropertyEntity
import com.example.tenant_landlorddisputedocumenter.data.local.entity.RoomEntity
import com.example.tenant_landlorddisputedocumenter.data.local.entity.SignatureEntity
import com.example.tenant_landlorddisputedocumenter.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        PropertyEntity::class,
        RoomEntity::class,
        ItemEntity::class,
        PhotoEntity::class,
        SignatureEntity::class,
        DisputeEntity::class,
        NotificationEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ProofNestDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun propertyDao(): PropertyDao
    abstract fun roomDao(): RoomDao
    abstract fun itemDao(): ItemDao
    abstract fun photoDao(): PhotoDao
    abstract fun signatureDao(): SignatureDao
    abstract fun disputeDao(): DisputeDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        private const val NAME = "proofnest.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE properties ADD COLUMN moveInInspectionSubmittedAtMillis INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE properties ADD COLUMN moveOutInspectionSubmittedAtMillis INTEGER",
                )
                db.execSQL("ALTER TABLE signatures ADD COLUMN remoteUrl TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN photoUrl TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE disputes ADD COLUMN proposedByUid TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE disputes ADD COLUMN tenantResponseNote TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        @Volatile private var instance: ProofNestDatabase? = null

        fun get(context: Context): ProofNestDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProofNestDatabase::class.java,
                    NAME,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
