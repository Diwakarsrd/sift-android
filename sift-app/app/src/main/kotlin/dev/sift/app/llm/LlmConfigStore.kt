package dev.sift.app.llm

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sift.app.model.LlmBackend
import dev.sift.app.model.LlmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("llm_config")

@Singleton
class LlmConfigStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val BACKEND     = stringPreferencesKey("backend")
        val MODEL       = stringPreferencesKey("model")
        val BASE_URL    = stringPreferencesKey("base_url")
        val API_KEY     = stringPreferencesKey("api_key")
        val TIMEOUT_SEC = intPreferencesKey("timeout_sec")
    }

    val configFlow: Flow<LlmConfig> = context.dataStore.data.map { prefs ->
        LlmConfig(
            backend    = prefs[Keys.BACKEND]?.let { runCatching { LlmBackend.valueOf(it) }.getOrNull() }
                             ?: LlmBackend.OLLAMA,
            model      = prefs[Keys.MODEL]       ?: "gemma2:2b",
            baseUrl    = prefs[Keys.BASE_URL]    ?: "http://10.0.2.2:11434",
            apiKey     = prefs[Keys.API_KEY]     ?: "",
            timeoutSec = prefs[Keys.TIMEOUT_SEC] ?: 30,
        )
    }

    /** Blocking get for non-coroutine call sites (IntentParser init). */
    fun get(): LlmConfig = runBlocking { configFlow.first() }

    suspend fun save(config: LlmConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BACKEND]     = config.backend.name
            prefs[Keys.MODEL]       = config.model
            prefs[Keys.BASE_URL]    = config.baseUrl
            prefs[Keys.API_KEY]     = config.apiKey
            prefs[Keys.TIMEOUT_SEC] = config.timeoutSec
        }
    }
}
