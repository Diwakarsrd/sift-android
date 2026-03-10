package dev.sift.app.llm

import dev.sift.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentParser @Inject constructor(
    private val configStore: LlmConfigStore,
) {
    private val json = Json {
        ignoreUnknownKeys    = true
        isLenient            = true
        coerceInputValues    = true
    }

    private fun buildClient(config: LlmConfig): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(config.timeoutSec.toLong(), TimeUnit.SECONDS)
            .readTimeout(config.timeoutSec.toLong(),    TimeUnit.SECONDS)
            .writeTimeout(10L, TimeUnit.SECONDS)
            .build()

    /**
     * Parses a natural-language query into a structured [ParsedIntent].
     * Tries the configured backend; falls back to rule-based parsing on failure.
     */
    suspend fun parse(query: String): ParsedIntent = withContext(Dispatchers.IO) {
        val config = configStore.get()
        Timber.d("Parsing intent with ${config.backend}: $query")

        return@withContext try {
            val raw = when (config.backend) {
                LlmBackend.OLLAMA      -> callOllama(query, config)
                LlmBackend.HUGGING_FACE -> callHuggingFace(query, config)
                LlmBackend.LM_STUDIO   -> callLmStudio(query, config)
            }
            parseJson(raw) ?: fallback(query)
        } catch (e: Exception) {
            Timber.w(e, "LLM parse failed — using rule-based fallback")
            fallback(query)
        }
    }

    // ── Ollama ────────────────────────────────────────────────────────────

    private fun callOllama(query: String, config: LlmConfig): String {
        val body = """
            {
              "model": "${config.model}",
              "stream": false,
              "messages": [
                {"role": "system", "content": ${SYSTEM_PROMPT.json()}},
                {"role": "user",   "content": ${query.json()}}
              ]
            }
        """.trimIndent()

        return post("${config.baseUrl}/api/chat", body, headers(config))
            .let { extractOllamaContent(it) }
    }

    // ── HuggingFace Inference API ─────────────────────────────────────────

    private fun callHuggingFace(query: String, config: LlmConfig): String {
        val prompt = "<s>[INST] <<SYS>>\n$SYSTEM_PROMPT\n<</SYS>>\n\n$query [/INST]"
        val body   = """{"inputs":${prompt.json()},"parameters":{"max_new_tokens":400,"return_full_text":false}}"""
        val url    = "${config.baseUrl}/models/${config.model}"

        return post(url, body, headers(config, bearer = config.apiKey))
            .let { extractHfContent(it) }
    }

    // ── LM Studio (OpenAI-compatible) ─────────────────────────────────────

    private fun callLmStudio(query: String, config: LlmConfig): String {
        val body = """
            {
              "model": "${config.model}",
              "stream": false,
              "messages": [
                {"role": "system", "content": ${SYSTEM_PROMPT.json()}},
                {"role": "user",   "content": ${query.json()}}
              ]
            }
        """.trimIndent()

        return post("${config.baseUrl}/v1/chat/completions", body, headers(config))
            .let { extractOpenAiContent(it) }
    }

    // ── HTTP helper ───────────────────────────────────────────────────────

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val client  = buildClient(configStore.get())
        val reqBody = body.toRequestBody("application/json".toMediaType())
        val builder = Request.Builder().url(url).post(reqBody)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.body?.string()?.take(200)}")
            }
            return response.body?.string() ?: throw IOException("Empty response body")
        }
    }

    private fun headers(config: LlmConfig, bearer: String = ""): Map<String, String> =
        buildMap {
            put("Content-Type", "application/json")
            if (bearer.isNotBlank()) put("Authorization", "Bearer $bearer")
        }

    // ── JSON extractors ───────────────────────────────────────────────────

    private fun extractOllamaContent(raw: String): String =
        json.parseToJsonElement(raw)
            .jsonObject["message"]
            ?.jsonObject?.get("content")
            ?.toString()?.trim('"') ?: raw

    private fun extractHfContent(raw: String): String =
        runCatching {
            json.parseToJsonElement(raw)
                .jsonArray[0]
                .jsonObject["generated_text"]
                ?.toString()?.trim('"') ?: raw
        }.getOrDefault(raw)

    private fun extractOpenAiContent(raw: String): String =
        json.parseToJsonElement(raw)
            .jsonObject["choices"]
            ?.jsonArray?.get(0)
            ?.jsonObject?.get("message")
            ?.jsonObject?.get("content")
            ?.toString()?.trim('"') ?: raw

    private fun parseJson(raw: String): ParsedIntent? {
        val cleaned = raw.replace(Regex("```json|```"), "").trim()
        val jsonStr = Regex("\\{[\\s\\S]*\\}").find(cleaned)?.value ?: return null
        return runCatching { json.decodeFromString<ParsedIntent>(jsonStr) }.getOrNull()
    }

    // ── Rule-based fallback ───────────────────────────────────────────────

    private fun fallback(query: String): ParsedIntent {
        val q = query.lowercase()
        return ParsedIntent(
            timeConstraint = when {
                q.contains("today")      -> TimeConstraint("today", 0.0)
                q.contains("yesterday")  -> TimeConstraint("yesterday", 1.0)
                q.contains("3 day")      -> TimeConstraint("3 days ago", 3.0)
                q.contains("week")       -> TimeConstraint("last week", 7.0)
                q.contains("month")      -> TimeConstraint("last month", 30.0)
                else                     -> null
            },
            personConstraint = COMMON_NAMES
                .firstOrNull { q.contains(it.lowercase()) }
                ?.let { PersonConstraint(it) },
            fileTypeConstraint = when {
                q.contains("pdf")        -> "pdf"
                q.contains("excel") || q.contains("xlsx") -> "xlsx"
                q.contains("doc")        -> "docx"
                q.contains("screenshot") -> "screenshot"
                else                     -> "any"
            },
            action = when {
                q.contains("call")       -> "find_call"
                q.contains("file") || q.contains("pdf") || q.contains("doc") -> "find_file"
                q.contains("app") || q.contains("open") -> "find_app"
                q.contains("screenshot") -> "find_screenshot"
                else                     -> "general"
            },
            confidence = 0.4f,
            summary    = "Rule-based parse: $query",
        )
    }

    companion object {
        private val COMMON_NAMES = listOf("Rahul", "Priya", "Amit", "Neha", "Raj", "Ananya")

        private val SYSTEM_PROMPT = """
            You are the intent parser for SIFT, an on-device AI memory system for Android.
            Parse the user's natural language query about their phone activity history.
            Return ONLY valid compact JSON, no markdown, no explanation, no preamble.
            JSON schema:
            {
              "timeConstraint":{"description":"string","daysAgo":number_or_null},
              "personConstraint":{"name":"string_or_null"},
              "fileTypeConstraint":"pdf|xlsx|docx|screenshot|any",
              "appConstraint":"string_or_null",
              "action":"find_file|find_call|find_app|find_screenshot|general",
              "confidence":0.0_to_1.0,
              "summary":"one-sentence summary of what the user wants"
            }
        """.trimIndent()

        // Escape a string for JSON embedding
        private fun String.json(): String =
            "\"${replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
    }
}

// Bring in JSON element helpers
private val kotlinx.serialization.json.JsonElement.jsonObject
    get() = this as kotlinx.serialization.json.JsonObject
private val kotlinx.serialization.json.JsonElement.jsonArray
    get() = this as kotlinx.serialization.json.JsonArray
