package dev.sift.app.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-process FAISS flat index for on-device vector search.
 *
 * For v1 MVP we use a pure-Kotlin brute-force cosine similarity
 * (sufficient up to ~50K vectors @ <100ms).
 *
 * For production scale (>100K events), swap to:
 *   - faiss-android NDK build: github.com/facebookresearch/faiss (see NDK branch)
 *   - Index type: IndexIVFFlat (nlist=100) for ~5ms search at 1M vectors
 *
 * Index is persisted to disk so it survives process restarts.
 */
@Singleton
class FaissIndex @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dim   = Embedder.EMBEDDING_DIM
    private val index = mutableListOf<Pair<Int, FloatArray>>()  // (eventId → embedding)
    private val indexFile = File(context.filesDir, "sift_index.bin")

    var size: Int = 0
        private set

    init {
        loadFromDisk()
    }

    /** Add a vector to the index. */
    fun add(eventId: Int, embedding: FloatArray) {
        require(embedding.size == dim) { "Embedding size mismatch: ${embedding.size} != $dim" }
        // Remove existing entry for this ID to avoid duplicates
        index.removeAll { it.first == eventId }
        index.add(Pair(eventId, embedding))
        size = index.size
    }

    /**
     * Score a query embedding against a specific set of candidate event IDs.
     * Returns cosine similarity scores in the same order as [candidateIds].
     */
    fun score(queryEmb: FloatArray, candidateIds: List<Int>): List<Float> {
        val idSet = candidateIds.toHashSet()
        val lookup = index.filter { it.first in idSet }.associateBy { it.first }
        return candidateIds.map { id ->
            lookup[id]?.let { (_, emb) -> cosine(queryEmb, emb) } ?: 0f
        }
    }

    /**
     * Full ANN search — returns top-k event IDs by similarity.
     * Used when there's no pre-filter from the graph layer.
     */
    fun search(queryEmb: FloatArray, topK: Int = 20): List<Pair<Int, Float>> {
        if (index.isEmpty()) return emptyList()
        return index
            .map { (id, emb) -> Pair(id, cosine(queryEmb, emb)) }
            .sortedByDescending { it.second }
            .take(topK)
    }

    /** Persist index to disk (called by EmbeddingWorker after batch add). */
    fun saveToDisk() {
        try {
            indexFile.outputStream().buffered().use { out ->
                out.write(int2bytes(index.size))
                index.forEach { (id, emb) ->
                    out.write(int2bytes(id))
                    emb.forEach { out.write(float2bytes(it)) }
                }
            }
            Timber.d("FAISS index saved: ${index.size} vectors")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save FAISS index")
        }
    }

    private fun loadFromDisk() {
        if (!indexFile.exists()) return
        try {
            indexFile.inputStream().buffered().use { inp ->
                val count = bytes2int(inp.readNBytes(4))
                repeat(count) {
                    val id  = bytes2int(inp.readNBytes(4))
                    val emb = FloatArray(dim) { bytes2float(inp.readNBytes(4)) }
                    index.add(Pair(id, emb))
                }
            }
            size = index.size
            Timber.d("FAISS index loaded: $size vectors")
        } catch (e: Exception) {
            Timber.w(e, "FAISS index load failed — starting fresh")
            index.clear()
            size = 0
        }
    }

    // ── Math ──────────────────────────────────────────────────────────────

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val denom = Math.sqrt((na * nb).toDouble()).toFloat()
        return if (denom > 1e-8f) dot / denom else 0f
    }

    // ── Serialization helpers ─────────────────────────────────────────────

    private fun int2bytes(v: Int)   = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
    private fun float2bytes(v: Float) = int2bytes(java.lang.Float.floatToIntBits(v))
    private fun bytes2int(b: ByteArray)   = (b[0].toInt() and 0xFF shl 24) or (b[1].toInt() and 0xFF shl 16) or (b[2].toInt() and 0xFF shl 8) or (b[3].toInt() and 0xFF)
    private fun bytes2float(b: ByteArray) = java.lang.Float.intBitsToFloat(bytes2int(b))
}
