package dev.sift.app.db

import androidx.room.*
import dev.sift.app.model.EventType
import kotlinx.coroutines.flow.Flow

// ── Entity ────────────────────────────────────────────────────────────────

@Entity(
    tableName = "events",
    indices = [
        Index("timestamp"),
        Index("app_package"),
        Index("contact_name"),
        Index("type"),
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")          val id:          Long      = 0,
    @ColumnInfo(name = "type")        val type:        EventType,
    @ColumnInfo(name = "timestamp")   val timestamp:   Long,
    @ColumnInfo(name = "app_package") val appPackage:  String    = "",
    @ColumnInfo(name = "app_label")   val appLabel:    String    = "",
    @ColumnInfo(name = "title")       val title:       String    = "",
    @ColumnInfo(name = "content")     val content:     String    = "",
    @ColumnInfo(name = "contact_name")val contactName: String    = "",
    @ColumnInfo(name = "contact_id")  val contactId:   String    = "",
    @ColumnInfo(name = "metadata")    val metadata:    String    = "{}",
    @ColumnInfo(name = "embedded")    val embedded:    Boolean   = false,
)

// ── Converters ────────────────────────────────────────────────────────────

class Converters {
    @TypeConverter fun fromEventType(value: EventType): String = value.name
    @TypeConverter fun toEventType(value: String): EventType   = EventType.valueOf(value)
}

// ── DAO ───────────────────────────────────────────────────────────────────

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<EventEntity>)

    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<EventEntity>

    @Query("SELECT * FROM events WHERE timestamp >= :from AND timestamp <= :to ORDER BY timestamp DESC")
    suspend fun getByTimeRange(from: Long, to: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE contact_name LIKE '%' || :name || '%' ORDER BY timestamp DESC")
    suspend fun getByContact(name: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByType(type: EventType, limit: Int = 50): List<EventEntity>

    @Query("SELECT * FROM events WHERE app_package = :pkg ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByApp(pkg: String, limit: Int = 50): List<EventEntity>

    @Query("""
        SELECT * FROM events 
        WHERE (:type IS NULL OR type = :type)
          AND (:from  IS NULL OR timestamp >= :from)
          AND (:to    IS NULL OR timestamp <= :to)
          AND (:name  IS NULL OR contact_name LIKE '%' || :name || '%')
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    suspend fun query(
        type:  EventType? = null,
        from:  Long?      = null,
        to:    Long?      = null,
        name:  String?    = null,
        limit: Int        = 50,
    ): List<EventEntity>

    @Query("SELECT * FROM events WHERE embedded = 0 ORDER BY timestamp DESC LIMIT :batch")
    suspend fun getUnembedded(batch: Int = 50): List<EventEntity>

    @Query("UPDATE events SET embedded = 1 WHERE id IN (:ids)")
    suspend fun markEmbedded(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Long

    @Query("SELECT COUNT(*) FROM events WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Long

    // Retention policy pruning
    @Query("DELETE FROM events WHERE timestamp < :olderThan AND type NOT IN (:keepTypes)")
    suspend fun pruneOld(olderThan: Long, keepTypes: List<EventType> = emptyList())

    @Query("SELECT * FROM events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT DISTINCT app_package, app_label FROM events ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecentApps(): List<AppUsageSummary>
}

data class AppUsageSummary(
    @ColumnInfo(name = "app_package") val appPackage: String,
    @ColumnInfo(name = "app_label")   val appLabel:   String,
)

// ── Vector Index Entry (FAISS companion store) ────────────────────────────

@Entity(tableName = "vector_index")
data class VectorIndexEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")   val eventId:   Long,
    @ColumnInfo(name = "faiss_idx")  val faissIndex: Int,
    @ColumnInfo(name = "created_at") val createdAt:  Long = System.currentTimeMillis(),
)

@Dao
interface VectorIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: VectorIndexEntity)

    @Query("SELECT * FROM vector_index WHERE faiss_idx IN (:indices)")
    suspend fun getByFaissIndices(indices: List<Int>): List<VectorIndexEntity>

    @Query("SELECT COUNT(*) FROM vector_index")
    suspend fun count(): Long
}
