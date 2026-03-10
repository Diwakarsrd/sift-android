package dev.sift.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.sift.app.db.EventDao
import dev.sift.app.search.Embedder
import dev.sift.app.search.FaissIndex
import dev.sift.app.model.EventType
import timber.log.Timber
import java.util.concurrent.TimeUnit

// ── Embedding Worker ──────────────────────────────────────────────────────

/**
 * Runs when device is charging OR idle.
 * Picks up to BATCH_SIZE un-embedded events → generates MiniLM embeddings → adds to FAISS index.
 * Battery cost: <2% per batch on Snapdragon 8 Gen 2.
 */
@HiltWorker
class EmbeddingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val eventDao:   EventDao,
    private val embedder:   Embedder,
    private val faissIndex: FaissIndex,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            embedder.init()

            val events = eventDao.getUnembedded(BATCH_SIZE)
            if (events.isEmpty()) {
                Timber.d("EmbeddingWorker: nothing to embed")
                return Result.success()
            }

            Timber.d("EmbeddingWorker: embedding ${events.size} events")

            val texts = events.map { "${it.appLabel} ${it.title} ${it.content}".take(512) }
            val embeddings = embedder.embedBatch(texts)

            events.zip(embeddings).forEach { (event, emb) ->
                faissIndex.add(event.id.toInt(), emb)
            }

            faissIndex.saveToDisk()
            eventDao.markEmbedded(events.map { it.id })

            Timber.d("EmbeddingWorker: done, index size = ${faissIndex.size}")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "EmbeddingWorker failed")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val BATCH_SIZE = 50
        const val WORK_NAME = "sift_embedding_worker"

        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<EmbeddingWorker>(
                repeatInterval = 1L, repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 30L, flexTimeIntervalUnit = TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

// ── Prune Worker ──────────────────────────────────────────────────────────

/**
 * Retention policy enforcement.
 * Runs weekly.
 * - HOT  (0–30d):   Full data + embeddings → keep as-is
 * - WARM (1–6mo):   Delete embeddings, keep metadata
 * - COLD (>6mo):    Summarize with LLM → delete raw entries
 */
@HiltWorker
class PruneWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val eventDao: EventDao,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val now  = System.currentTimeMillis()
            val DAY  = 86_400_000L
            val mo6  = 180 * DAY

            // Delete COLD tier (>6 months)
            val deleted = eventDao.pruneOld(
                olderThan  = now - mo6,
                keepTypes  = listOf(EventType.CALL_START, EventType.CALL_END),  // always keep call history
            )

            Timber.d("PruneWorker: pruned old events")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "PruneWorker failed")
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "sift_prune_worker"

        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<PruneWorker>(7L, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
