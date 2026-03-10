package dev.sift.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities  = [EventEntity::class, VectorIndexEntity::class],
    version   = 1,
    exportSchema = true,   // tracks schema for migration safety
)
@TypeConverters(Converters::class)
abstract class SiftDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun vectorIndexDao(): VectorIndexDao

    companion object {
        private const val DB_NAME = "sift_memory.db"

        /**
         * Builds the Room database with AES-256 SQLCipher encryption.
         * The passphrase is derived from the Android Keystore — never stored in plaintext.
         */
        fun build(context: Context, passphrase: ByteArray): SiftDatabase {
            val factory = SupportFactory(
                SQLiteDatabase.getBytes(String(passphrase).toCharArray())
            )
            return Room.databaseBuilder(context, SiftDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigrationFrom()  // replace with proper migrations in v2+
                .build()
        }
    }
}
