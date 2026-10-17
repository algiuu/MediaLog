package com.example.medialog.ui.foryou

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medialog.data.local.entity.AiProviderConfigEntity
import com.example.medialog.data.local.entity.MediaEntryEntity
import com.example.medialog.data.local.entity.MediaType
import com.example.medialog.data.local.entity.RecommendationBatchEntity
import com.example.medialog.data.local.entity.RecommendationItemEntity
import com.example.medialog.data.repository.AiSettingsRepository
import com.example.medialog.data.repository.MediaRepository
import com.example.medialog.data.repository.RecommendationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ForYouViewModel"

data class ForYouUiState(
    val activeConfig: AiProviderConfigEntity? = null,
    val hasApiKey: Boolean = false,
    val latestBatch: RecommendationBatchEntity? = null,
    val recommendations: List<RecommendationItemEntity> = emptyList(),
    val isGenerating: Boolean = false,
    val error: String? = null,
    val moodPrompt: String = "",
    val typeFilter: String? = null
)

@HiltViewModel
class ForYouViewModel @Inject constructor(
    private val recommendationRepository: RecommendationRepository,
    private val mediaRepository: MediaRepository,
    private val aiSettingsRepository: AiSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForYouUiState())
    val uiState: StateFlow<ForYouUiState> = _uiState.asStateFlow()

    init {
        // Cek API key & active config
        viewModelScope.launch {
            aiSettingsRepository.activeConfigFlow.collect { config ->
                Log.d(TAG, "Active config: ${config?.displayName}, hasConfig: ${config != null}")
                if (config != null) {
                    val apiKey = aiSettingsRepository.getApiKey(config.id)
                    val hasKey = !apiKey.isNullOrBlank()
                    Log.d(TAG, "API key exists: $hasKey")
                    _uiState.value = _uiState.value.copy(
                        activeConfig = config,
                        hasApiKey = hasKey
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        activeConfig = null,
                        hasApiKey = false
                    )
                }
            }
        }

        // Load recommendations terakhir
        viewModelScope.launch {
            recommendationRepository.getLatestBatchFlow().collect { batch ->
                _uiState.value = _uiState.value.copy(latestBatch = batch)
                if (batch != null) {
                    loadRecommendations(batch.id)
                }
            }
        }
    }

    private fun loadRecommendations(batchId: Long) {
        viewModelScope.launch {
            recommendationRepository.getItemsForBatchFlow(batchId).collect { items ->
                _uiState.value = _uiState.value.copy(recommendations = items)
            }
        }
    }

    fun setMoodPrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(moodPrompt = prompt)
    }

    fun setTypeFilter(filter: String?) {
        _uiState.value = _uiState.value.copy(typeFilter = filter)
    }

    fun generateRecommendations() {
        if (!_uiState.value.hasApiKey) {
            _uiState.value = _uiState.value.copy(error = "Please add an API key in Settings first")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
            Log.d(TAG, "Generating recommendations...")
            val result = recommendationRepository.generateRecommendations(
                typeFilter = _uiState.value.typeFilter,
                moodPrompt = _uiState.value.moodPrompt.ifBlank { null }
            )
            if (result.isSuccess) {
                Log.d(TAG, "Recommendations generated successfully")
                _uiState.value = _uiState.value.copy(isGenerating = false)
            } else {
                Log.e(TAG, "Failed to generate recommendations", result.exceptionOrNull())
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to generate recommendations"
                )
            }
        }
    }

    fun hideRecommendation(itemId: Long) {
        viewModelScope.launch {
            recommendationRepository.hideItem(itemId)
        }
    }

    fun addRecommendationToList(item: RecommendationItemEntity) {
        viewModelScope.launch {
            // Fetch full details from API
            val remoteDetail = try {
                mediaRepository.getRemoteDetail(item.type, item.externalId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch remote detail for ${item.title}", e)
                null
            }

            val entity = MediaEntryEntity(
                type = item.type,
                title = item.title,
                source = item.source,
                externalId = item.externalId,
                posterUrl = item.posterUrl,
                synopsis = remoteDetail?.synopsis,
                creator = remoteDetail?.creator,
                totalUnits = remoteDetail?.totalUnits,
                unitLabel = remoteDetail?.unitLabel ?: "episodes",
                status = com.example.medialog.data.local.entity.EntryStatus.PLAN
            )
            mediaRepository.saveEntry(entity, genres = remoteDetail?.genres ?: emptyList())
            recommendationRepository.markItemAdded(item.id)
        }
    }
}
