package dev.sift.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sift.app.llm.IntentParser
import dev.sift.app.llm.LlmConfigStore
import dev.sift.app.model.*
import dev.sift.app.search.SearchEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SearchUiState(
    val query:     String      = "",
    val queryState: QueryState = QueryState(),
    val recentEvents: List<SiftEvent> = emptyList(),
    val llmConfig: LlmConfig   = LlmConfig(),
    val showSettings: Boolean  = false,
    val onboardingDone: Boolean = false,
)

sealed class SearchEvent {
    data class QueryChanged(val text: String) : SearchEvent()
    data object Submit                         : SearchEvent()
    data object ClearResults                   : SearchEvent()
    data object ToggleSettings                 : SearchEvent()
    data class SaveConfig(val config: LlmConfig) : SearchEvent()
    data class SampleQuery(val text: String)   : SearchEvent()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val intentParser: IntentParser,
    private val searchEngine: SearchEngine,
    private val configStore:  LlmConfigStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            configStore.configFlow.collect { config ->
                _state.update { it.copy(llmConfig = config) }
            }
        }
    }

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged  -> _state.update { it.copy(query = event.text) }
            is SearchEvent.Submit        -> runSearch()
            is SearchEvent.ClearResults  -> clearResults()
            is SearchEvent.ToggleSettings-> _state.update { it.copy(showSettings = !it.showSettings) }
            is SearchEvent.SaveConfig    -> saveConfig(event.config)
            is SearchEvent.SampleQuery   -> { _state.update { it.copy(query = event.text) }; runSearch(event.text) }
        }
    }

    private fun runSearch(overrideQuery: String? = null) {
        val query = overrideQuery ?: _state.value.query
        if (query.isBlank()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val startMs = System.currentTimeMillis()

            try {
                // Step 1: Parsing intent
                updateStep(PipelineStep.PARSING_INTENT)
                val intent = intentParser.parse(query)

                // Step 2: Extracting constraints
                updateStep(PipelineStep.EXTRACTING_CONSTRAINTS, intent = intent)

                // Step 3: Graph filter
                updateStep(PipelineStep.GRAPH_FILTERING, intent = intent)

                // Step 4: Vector ranking
                updateStep(PipelineStep.VECTOR_RANKING, intent = intent)
                val results = searchEngine.search(query, intent)

                // Step 5: Done
                val duration = System.currentTimeMillis() - startMs
                _state.update { s ->
                    s.copy(
                        queryState = QueryState(
                            step       = PipelineStep.DONE,
                            intent     = intent,
                            results    = results,
                            durationMs = duration,
                        )
                    )
                }
                Timber.d("Search done in ${duration}ms — ${results.size} results")

            } catch (e: Exception) {
                Timber.e(e, "Search failed")
                _state.update { s ->
                    s.copy(
                        queryState = QueryState(
                            step  = PipelineStep.ERROR,
                            error = e.message ?: "Unknown error",
                        )
                    )
                }
            }
        }
    }

    private fun updateStep(step: PipelineStep, intent: ParsedIntent? = null) {
        _state.update { s ->
            s.copy(
                queryState = s.queryState.copy(
                    step   = step,
                    intent = intent ?: s.queryState.intent,
                )
            )
        }
    }

    private fun clearResults() {
        searchJob?.cancel()
        _state.update { it.copy(queryState = QueryState(), query = "") }
    }

    private fun saveConfig(config: LlmConfig) {
        viewModelScope.launch {
            configStore.save(config)
            _state.update { it.copy(showSettings = false) }
        }
    }
}
