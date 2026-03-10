package dev.sift.app.search

import ai.onnxruntime.*
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device sentence embeddings using MiniLM-L6-v2 (ONNX).
 *
 * Model: all-MiniLM-L6-v2 exported to ONNX, quantized to INT8.
 * Size:  ~22MB (INT8) — stored in assets/models/minilm_int8.onnx
 * Output: 384-dimensional float embeddings, L2-normalized.
 *
 * Setup: download from
 *   https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/tree/main/onnx
 * Place in: app/src/main/assets/models/minilm_int8.onnx
 *           app/src/main/assets/models/tokenizer.json
 */
@Singleton
class Embedder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var session:   OrtSession?   = null
    private var tokenizer: SimpleTokenizer? = null
    private val env = OrtEnvironment.getEnvironment()

    private val isReady: Boolean get() = session != null && tokenizer != null

    suspend fun init() = withContext(Dispatchers.IO) {
        if (isReady) return@withContext
        try {
            val modelBytes = context.assets.open("models/minilm_int8.onnx").readBytes()
            session = env.createSession(
                modelBytes,
                OrtSession.SessionOptions().apply {
                    addNnapi()    // Use Android NNAPI (NPU/DSP acceleration)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
            )
            tokenizer = SimpleTokenizer(context)
            Timber.d("Embedder initialized: MiniLM-L6-v2 INT8")
        } catch (e: Exception) {
            Timber.e(e, "Embedder init failed — vector search disabled")
        }
    }

    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        if (!isReady) return@withContext FloatArray(EMBEDDING_DIM) { 0f }

        try {
            val tokens    = tokenizer!!.tokenize(text, maxLength = 128)
            val inputIds  = tokens.inputIds.toLongBuffer()
            val attnMask  = tokens.attentionMask.toLongBuffer()

            val inputs = mapOf(
                "input_ids"      to OnnxTensor.createTensor(env, inputIds,  longArrayOf(1, tokens.length.toLong())),
                "attention_mask" to OnnxTensor.createTensor(env, attnMask,  longArrayOf(1, tokens.length.toLong())),
            )

            session!!.run(inputs).use { result ->
                val lastHidden = result[0].value as Array<Array<FloatArray>>
                meanPool(lastHidden[0], tokens.attentionMask).also { normalize(it) }
            }
        } catch (e: Exception) {
            Timber.w(e, "Embed failed for: ${text.take(50)}")
            FloatArray(EMBEDDING_DIM) { 0f }
        }
    }

    /** Batch embed — more efficient for WorkManager background jobs. */
    suspend fun embedBatch(texts: List<String>): List<FloatArray> =
        texts.map { embed(it) }

    // ── Pooling + normalization ───────────────────────────────────────────

    private fun meanPool(hidden: Array<FloatArray>, mask: LongArray): FloatArray {
        val dim = hidden[0].size
        val out = FloatArray(dim)
        var count = 0
        hidden.forEachIndexed { i, vec ->
            if (i < mask.size && mask[i] == 1L) {
                vec.forEachIndexed { j, v -> out[j] += v }
                count++
            }
        }
        if (count > 0) out.forEachIndexed { i, _ -> out[i] /= count }
        return out
    }

    private fun normalize(vec: FloatArray) {
        val norm = Math.sqrt(vec.map { it * it }.sum().toDouble()).toFloat()
        if (norm > 1e-8f) vec.forEachIndexed { i, _ -> vec[i] /= norm }
    }

    private fun LongArray.toLongBuffer(): LongBuffer =
        LongBuffer.wrap(this)

    companion object {
        const val EMBEDDING_DIM = 384
    }
}

/**
 * Minimal BPE-style tokenizer sufficient for MiniLM.
 * For production, replace with full HuggingFace tokenizers-android binding.
 */
class SimpleTokenizer(context: Context) {
    // In a real build: load vocab.txt + tokenizer.json from assets
    // This is a placeholder skeleton — replace with:
    //   com.huggingface:tokenizers:0.13.4 (when Android build is available)
    //   OR manually port tokenizer from tokenizers.json
    data class TokenizerOutput(
        val inputIds:      LongArray,
        val attentionMask: LongArray,
        val length:        Int,
    )

    fun tokenize(text: String, maxLength: Int = 128): TokenizerOutput {
        // Placeholder: whitespace tokenize → token IDs (use real vocab in production)
        val words  = text.lowercase().split(Regex("\\s+")).take(maxLength - 2)
        val length = words.size + 2   // +2 for [CLS] + [SEP]

        val inputIds = LongArray(length).also { ids ->
            ids[0] = 101L   // [CLS]
            words.forEachIndexed { i, _ -> ids[i + 1] = (i + 1000L) }
            ids[length - 1] = 102L  // [SEP]
        }
        val mask = LongArray(length) { 1L }
        return TokenizerOutput(inputIds, mask, length)
    }
}
