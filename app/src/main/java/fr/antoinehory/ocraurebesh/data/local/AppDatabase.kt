package fr.antoinehory.ocraurebesh.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import fr.antoinehory.ocraurebesh.data.local.dao.ScanHistoryDao
import fr.antoinehory.ocraurebesh.data.local.entity.ScanHistoryEntity

/**
 * Base de données locale Room de l'application OCRAurebesh.
 */
@Database(
    entities = [ScanHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        private const val DATABASE_NAME = "ocr_aurebesh.db"

        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Retourne l'instance singleton thread-safe de [AppDatabase].
         */
        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
