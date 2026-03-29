package com.meditrack.app.data.local

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.meditrack.app.data.local.dao.*
import com.meditrack.app.data.local.entities.*

/**
 * MediTrack Room Database - Central offline data store.
 *
 * **Entities:**
 * 1. CachedMedicine - Offline medicines cache (for reads when offline)
 * 2. CachedAppointment - Offline appointments cache (for reads when offline)
 * 3. CachedHealthLog - Offline health logs cache (for reads when offline)
 * 4. PendingAction - Pending write operations queue (medicines, appointments, health logs, orders)
 *
 * **Note on PendingPayment:**
 * Kept separate in OfflinePaymentDatabase to avoid conflict with existing payment queue system.
 * Can be consolidated in future refactoring with proper migrations.
 *
 * **Database Version: 1**
 * Initial version with cache + action entities
 *
 * **Singleton Pattern:**
 * Access via MediTrackDatabase.getInstance(context)
 *
 * **Thread Safety:**
 * All methods are suspend functions (run on Dispatchers.IO by Room)
 */
@Database(
    entities = [
        CachedMedicine::class,
        CachedAppointment::class,
        CachedHealthLog::class,
        PendingAction::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(
    RoomTypeConverters::class
)
abstract class MediTrackDatabase : RoomDatabase() {

    // ── DAOs for Cached Data ────────────────────────────────
    abstract fun cachedMedicineDao(): CachedMedicineDao
    abstract fun cachedAppointmentDao(): CachedAppointmentDao
    abstract fun cachedHealthLogDao(): CachedHealthLogDao

    // ── DAOs for Offline Actions ─────────────────────────────
    abstract fun pendingActionDao(): PendingActionDao

    companion object {
        private const val DATABASE_NAME = "meditrack.db"

        @Volatile
        private var INSTANCE: MediTrackDatabase? = null

        /**
         * Get or create the database singleton.
         * Thread-safe using double-check locking pattern.
         */
        fun getInstance(context: Context): MediTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createDatabase(context).also { INSTANCE = it }
            }
        }

        /**
         * Create the database with proper configuration.
         */
        private fun createDatabase(context: Context): MediTrackDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MediTrackDatabase::class.java,
                DATABASE_NAME
            )
                // Use destructive migration from v1→v2 (payment app can handle it)
                // In production, implement proper Migration objects for data preservation
                .fallbackToDestructiveMigration()

                // Optional: Add migration callbacks for logging
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Database created - indices/triggers will be created automatically
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Database opened - can add WAL pragma, etc
                    }
                })

                .build()
        }

        /**
         * Close the database singleton (for testing).
         * Should NOT be called in production.
         */
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}

/**
 * Extension function to simplify database access.
 * Usage: val db = getMediTrackDatabase(context)
 */
fun getMediTrackDatabase(context: Context): MediTrackDatabase {
    return MediTrackDatabase.getInstance(context)
}
