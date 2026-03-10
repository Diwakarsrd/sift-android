package dev.sift.app.search

import dev.sift.app.db.EventDao
import dev.sift.app.db.EventEntity
import dev.sift.app.model.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Two-stage search:
 * 1. Graph/relational filter  — SQLite queries with time/person/type constraints
 * 2. Vector re-ranking        — FAISS ANN cosine similarity (when index is ready)
 *
 * Falls back gracefully to pure relational if the FAISS index has < 10 entries.
 */
@Singleton
class SearchEngine @Inject constructor(
    private val eventDao:    EventDao,
    private val faissIndex:  FaissIndex,
    private val embedder:    Embedder,
) {
    suspend fun search(query: String, intent: ParsedIntent, limit: Int = 20): List<SearchResult> {
        Timber.d("Searching with intent: ${intent.summary}")

        // ── Stage 1: Graph Filter ─────────────────────────────────────────
        val candidates = graphFilter(intent, limit * 3)
        Timber.d("Graph filter: ${candidates.size} candidates")

        if (candidates.isEmpty()) return emptyList()

        // ── Stage 2: Vector Ranking (if index ready) ──────────────────────
        return if (faissIndex.size >= MIN_VECTOR_ENTRIES) {
            vectorRank(query, candidates, limit)
        } else {
            // Not enough embeddings yet — return by recency + score
            candidates.take(limit).map { entity ->
                SearchResult(
                    event       = entity.toModel(),
                    score       = 1.0f,
                    matchReason = buildMatchReason(entity, intent),
                )
            }
        }
    }

    // ── Stage 1: Graph/Relational Filter ─────────────────────────────────

    private suspend fun graphFilter(intent: ParsedIntent, limit: Int): List<EventEntity> {
        val now = System.currentTimeMillis()
        val DAY = 86_400_000L

        val from: Long? = intent.timeConstraint?.let { tc ->
            when {
                tc.fromTs  != null    -> tc.fromTs
                tc.daysAgo != null    -> now - ((tc.daysAgo + 1.5) * DAY).toLong()
                else                  -> null
            }
        }

        val to: Long? = intent.timeConstraint?.let { tc ->
            when {
                tc.toTs    != null    -> tc.toTs
                tc.daysAgo != null    -> now - ((tc.daysAgo - 1.5).coerceAtLeast(0.0) * DAY).toLong()
                else                  -> null
            }
        }

        val eventType: EventType? = when (intent.action) {
            "find_call"       -> EventType.CALL_START
            "find_file"       -> EventType.FILE_OPEN
            "find_app"        -> EventType.APP_OPEN
            "find_screenshot" -> EventType.SCREENSHOT
            else              -> null
        }

        val results = eventDao.query(
            type  = eventType,
            from  = from,
            to    = to,
            name  = intent.personConstraint?.name?.takeIf { it.isNotBlank() },
            limit = limit,
        ).toMutableList()

        // File type sub-filter (pdf, xlsx, docx)
        if (intent.fileTypeConstraint != "any" && intent.fileTypeConstraint.isNotBlank()) {
            results.retainAll { it.title.contains(intent.fileTypeConstraint, ignoreCase = true) }
        }

        // App constraint sub-filter
        intent.appConstraint?.let { appName ->
            if (appName.isNotBlank()) {
                results.retainAll {
                    it.appLabel.contains(appName, ignoreCase = true) ||
                    it.appPackage.contains(appName, ignoreCase = true)
                }
            }
        }

        return results
    }

    // ── Stage 2: Vector Re-ranking ────────────────────────────────────────

    private suspend fun vectorRank(
        query:      String,
        candidates: List<EventEntity>,
        limit:      Int,
    ): List<SearchResult> {
        return try {
            val queryEmbedding = embedder.embed(query)
            val candidateIds   = candidates.map { it.id.toInt() }
            val scores         = faissIndex.score(queryEmbedding, candidateIds)

            candidates
                .zip(scores)
                .sortedByDescending { (_, score) -> score }
                .take(limit)
                .map { (entity, score) ->
                    SearchResult(
                        event       = entity.toModel(),
                        score       = score,
                        matchReason = buildMatchReason(entity, null),
                    )
                }
        } catch (e: Exception) {
            Timber.w(e, "Vector ranking failed — falling back to recency")
            candidates.take(limit).map { SearchResult(event = it.toModel(), score = 0.5f) }
        }
    }

    private fun buildMatchReason(entity: EventEntity, intent: ParsedIntent?): String {
        val parts = mutableListOf<String>()
        if (entity.contactName.isNotBlank()) parts.add("contact: ${entity.contactName}")
        if (entity.appLabel.isNotBlank())    parts.add("app: ${entity.appLabel}")
        return parts.joinToString(" · ")
    }

    companion object {
        private const val MIN_VECTOR_ENTRIES = 10
    }
}

// ── Entity → Model mapper ─────────────────────────────────────────────────

fun EventEntity.toModel() = SiftEvent(
    id          = id,
    type        = type,
    timestamp   = timestamp,
    appPackage  = appPackage,
    appLabel    = appLabel,
    title       = title,
    content     = content,
    contactName = contactName,
    contactId   = contactId,
    metadata    = metadata,
)
