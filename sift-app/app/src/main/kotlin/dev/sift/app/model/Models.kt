package dev.sift.app.model

import kotlinx.serialization.Serializable

// ── Event (what the Accessibility Service captures) ──────────────────────

enum class EventType {
    APP_OPEN, APP_CLOSE, FILE_OPEN, CALL_START, CALL_END,
    NOTIFICATION, SCREENSHOT, WEB_VISIT, CONTACT_VIEW
}

data class SiftEvent(
    val id:          Long        = 0,
    val type:        EventType,
    val timestamp:   Long        = System.currentTimeMillis(),
    val appPackage:  String      = "",
    val appLabel:    String      = "",
    val title:       String      = "",
    val content:     String      = "",
    val contactName: String      = "",
    val contactId:   String      = "",
    val metadata:    String      = "{}", // JSON blob for extra fields
    val embedding:   FloatArray? = null,
)

// ── Parsed intent from the LLM ────────────────────────────────────────────

@Serializable
data class ParsedIntent(
    val timeConstraint:     TimeConstraint?   = null,
    val personConstraint:   PersonConstraint? = null,
    val fileTypeConstraint: String            = "any",
    val appConstraint:      String?           = null,
    val action:             String            = "general",
    val confidence:         Float             = 0.5f,
    val summary:            String            = "",
)

@Serializable
data class TimeConstraint(
    val description: String = "",
    val daysAgo:     Double? = null,
    val fromTs:      Long?   = null,
    val toTs:        Long?   = null,
)

@Serializable
data class PersonConstraint(
    val name: String? = null,
)

// ── Search result ─────────────────────────────────────────────────────────

data class SearchResult(
    val event:       SiftEvent,
    val score:       Float  = 1.0f,   // vector similarity score
    val matchReason: String = "",     // human-readable why this matched
)

// ── Pipeline step (for UI animation) ─────────────────────────────────────

enum class PipelineStep {
    IDLE, PARSING_INTENT, EXTRACTING_CONSTRAINTS,
    GRAPH_FILTERING, VECTOR_RANKING, ASSEMBLING_RESULTS, DONE, ERROR
}

data class QueryState(
    val step:    PipelineStep   = PipelineStep.IDLE,
    val intent:  ParsedIntent?  = null,
    val results: List<SearchResult> = emptyList(),
    val error:   String?        = null,
    val durationMs: Long        = 0L,
)

// ── Backend config ────────────────────────────────────────────────────────

enum class LlmBackend { OLLAMA, HUGGING_FACE, LM_STUDIO }

data class LlmConfig(
    val backend:    LlmBackend = LlmBackend.OLLAMA,
    val model:      String     = "gemma2:2b",
    val baseUrl:    String     = "http://10.0.2.2:11434",  // 10.0.2.2 = host machine from emulator
    val apiKey:     String     = "",
    val timeoutSec: Int        = 30,
)
