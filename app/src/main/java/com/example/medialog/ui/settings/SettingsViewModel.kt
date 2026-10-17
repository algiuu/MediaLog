package com.example.medialog.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medialog.data.local.entity.AiFormatType
import com.example.medialog.data.local.entity.AiProviderConfigEntity
import com.example.medialog.data.repository.AiSettingsRepository
import com.example.medialog.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

data class SettingsUiState(
    val activeConfig: AiProviderConfigEntity? = null,
    val allConfigs: List<AiProviderConfigEntity> = emptyList(),
    val apiKey: String = "",
    val availableModels: List<String> = emptyList(),
    val isTestingConnection: Boolean = false,
    val testResult: String? = null,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val exportResult: String? = null,
    val importResult: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val aiSettingsRepository: AiSettingsRepository,
    private val mediaRepository: MediaRepository,
    private val secureKeyManager: com.example.medialog.data.security.SecureKeyManager
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
        private const val TMDB_KEY_ID = "tmdb"
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            aiSettingsRepository.activeConfigFlow.collect { config ->
                _uiState.value = _uiState.value.copy(activeConfig = config)
                if (config != null) {
                    loadApiKey(config.id)
                }
            }
        }
        viewModelScope.launch {
            aiSettingsRepository.allConfigsFlow.collect { configs ->
                _uiState.value = _uiState.value.copy(allConfigs = configs)
            }
        }
    }

    private fun loadApiKey(providerId: String) {
        val key = aiSettingsRepository.getApiKey(providerId) ?: ""
        _uiState.value = _uiState.value.copy(apiKey = key)
    }

    fun saveProvider(
        id: String,
        displayName: String,
        formatType: AiFormatType,
        baseUrl: String,
        selectedModel: String?,
        apiKey: String
    ) {
        viewModelScope.launch {
            aiSettingsRepository.saveConfig(
                id = id,
                displayName = displayName,
                formatType = formatType,
                baseUrl = baseUrl,
                selectedModel = selectedModel,
                apiKey = apiKey,
                isActive = true
            )
            _uiState.value = _uiState.value.copy(apiKey = apiKey)
        }
    }

    fun fetchModels() {
        val config = _uiState.value.activeConfig ?: return
        val apiKey = _uiState.value.apiKey
        if (apiKey.isBlank()) return

        viewModelScope.launch {
            try {
                val models = aiSettingsRepository.fetchModelsForConfig(config, apiKey)
                _uiState.value = _uiState.value.copy(availableModels = models)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(availableModels = emptyList())
            }
        }
    }

    fun fetchModelsWithKey(apiKey: String) {
        val config = _uiState.value.activeConfig
        Log.d(TAG, "fetchModelsWithKey: config=${config?.displayName}, apiKey blank=${apiKey.isBlank()}")
        if (config == null) {
            Log.e(TAG, "No active config!")
            return
        }
        if (apiKey.isBlank()) return

        viewModelScope.launch {
            try {
                Log.d(TAG, "Fetching models for ${config.displayName}...")
                val models = aiSettingsRepository.fetchModelsForConfig(config, apiKey)
                Log.d(TAG, "Fetched ${models.size} models")
                _uiState.value = _uiState.value.copy(availableModels = models)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch models", e)
                _uiState.value = _uiState.value.copy(availableModels = emptyList())
            }
        }
    }

    fun testConnection(model: String) {
        val config = _uiState.value.activeConfig ?: return
        val apiKey = _uiState.value.apiKey
        if (apiKey.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingConnection = true, testResult = null)
            val success = aiSettingsRepository.testConnection(config, apiKey, model)
            _uiState.value = _uiState.value.copy(
                isTestingConnection = false,
                testResult = if (success) "Connection successful!" else "Connection failed. Check your API key and model."
            )
        }
    }

    fun exportData(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)
            try {
                val json = mediaRepository.exportToJson()
                val file = java.io.File(context.getExternalFilesDir(null), "medialog_export.json")
                file.writeText(json)
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportResult = "Exported to ${file.absolutePath}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportResult = "Export failed: ${e.message}"
                )
            }
        }
    }

    fun importData(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            try {
                val file = java.io.File(context.getExternalFilesDir(null), "medialog_export.json")
                if (!file.exists()) {
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        importResult = "No export file found"
                    )
                    return@launch
                }
                val json = file.readText()
                val count = mediaRepository.importFromJson(json)
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importResult = "Imported $count entries"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importResult = "Import failed: ${e.message}"
                )
            }
        }
    }

    fun saveTmdbKey(apiKey: String) {
        secureKeyManager.saveApiKey(TMDB_KEY_ID, apiKey)
    }

    fun getTmdbKey(): String? {
        return secureKeyManager.getApiKey(TMDB_KEY_ID)
    }
}
