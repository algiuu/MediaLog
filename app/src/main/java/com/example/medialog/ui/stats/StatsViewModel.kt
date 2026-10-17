package com.example.medialog.ui.stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medialog.data.local.dao.GenreCount
import com.example.medialog.data.local.entity.EntryStatus
import com.example.medialog.data.local.entity.MediaType
import com.example.medialog.data.repository.AiSettingsRepository
import com.example.medialog.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private const val TAG = "StatsViewModel"

data class StatsUiState(
    val totalTracked: Int = 0,
    val completed: Int = 0,
    val inProgress: Int = 0,
    val avgRating: Float = 0f,
    val totalUnits: Int = 0,
    val byType: Map<MediaType, Int> = emptyMap(),
    val topGenres: List<GenreCount> = emptyList(),
    val isLoading: Boolean = true,
    val gelarTitle: String? = null,
    val gelarReason: String? = null,
    val isGeneratingGelar: Boolean = false
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val aiSettingsRepository: AiSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            mediaRepository.getAllEntriesFlow().collect { entries ->
                val totalTracked = entries.size
                val completed = entries.count { it.status == EntryStatus.COMPLETED }
                val inProgress = entries.count { it.status == EntryStatus.ONGOING }
                val ratedEntries = entries.filter { it.myRating != null }
                val avgRating = if (ratedEntries.isNotEmpty()) {
                    ratedEntries.map { it.myRating!! }.average().toFloat()
                } else 0f
                val totalUnits = entries.filter { it.status == EntryStatus.ONGOING || it.status == EntryStatus.COMPLETED }
                    .sumOf { it.progress }

                val byType = entries.groupingBy { it.type }.eachCount()

                _uiState.value = _uiState.value.copy(
                    totalTracked = totalTracked,
                    completed = completed,
                    inProgress = inProgress,
                    avgRating = avgRating,
                    totalUnits = totalUnits,
                    byType = byType,
                    isLoading = false
                )
            }
        }
        viewModelScope.launch {
            mediaRepository.getTopGenres().collect { genres ->
                _uiState.value = _uiState.value.copy(topGenres = genres)
            }
        }
    }

    fun generateGelar() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingGelar = true)

            val config = aiSettingsRepository.getActiveConfig()
            if (config == null) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingGelar = false,
                    gelarTitle = "AI belum dikonfigurasi",
                    gelarReason = "Isi API key di Settings dulu ya"
                )
                return@launch
            }

            val apiKey = aiSettingsRepository.getApiKey(config.id)
            if (apiKey.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingGelar = false,
                    gelarTitle = "API key kosong",
                    gelarReason = "Isi API key di Settings dulu ya"
                )
                return@launch
            }

            val entries = mediaRepository.getAllEntries()
            if (entries.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingGelar = false,
                    gelarTitle = "Library masih kosong",
                    gelarReason = "Tambah beberapa entri dulu ya"
                )
                return@launch
            }

            val topRated = entries.filter { (it.myRating ?: 0) >= 7 }.take(10)
            val dropped = entries.filter { it.status == EntryStatus.DROPPED }
            val completed = entries.filter { it.status == EntryStatus.COMPLETED }
            val noted = entries.filter { !it.notes.isNullOrBlank() }.take(10)

            val summaryText = buildString {
                append("Total entries: ${entries.size}\n")
                append("Completed: ${completed.size}\n")
                append("Dropped: ${dropped.size}\n")
                append("Average rating: ${_uiState.value.avgRating}\n")
                append("Top rated: ${topRated.joinToString(", ") { "${it.title} (${it.myRating}/10)" }}\n")
                if (dropped.isNotEmpty()) {
                    append("Dropped: ${dropped.joinToString(", ") { it.title }}\n")
                }
                if (noted.isNotEmpty()) {
                    append("User's own notes/reviews:\n")
                    noted.forEach { append("- ${it.title}: \"${it.notes}\"\n") }
                }
            }

            val systemPrompt = """
                You are a fun title-giving AI for MediaLog, a personal media tracking app.
                Based on the user's library data, give them ONE creative, fun Indonesian title/gelar.
                Examples: "Si Paling Elitis", "Marathoner Santai", "Completionist Sejuti", "Mood Reader", "Si Paling Picky"
                Also give a short, funny reason (1 sentence in Indonesian) why they got this title.
                Respond strictly in JSON format:
                {"title": "Gelar di sini", "reason": "Alasan di sini"}
                JSON ONLY. No markdown ticks, no extra text.

                User's library data:
                $summaryText
            """.trimIndent()

            try {
                val provider = aiSettingsRepository.getProviderForFormat(config.formatType)
                val result = provider.generate(
                    apiKey = apiKey,
                    baseUrl = config.baseUrl,
                    model = config.selectedModel ?: "",
                    prompt = systemPrompt
                )

                val cleanJson = result.content.trim()
                    .removePrefix("```json").removePrefix("```")
                    .removeSuffix("```").trim()

                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val obj = json.parseToJsonElement(cleanJson).jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: "Si Mystery"
                val reason = obj["reason"]?.jsonPrimitive?.content ?: "Karena kamu unik!"

                _uiState.value = _uiState.value.copy(
                    isGeneratingGelar = false,
                    gelarTitle = title,
                    gelarReason = reason
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate gelar", e)
                _uiState.value = _uiState.value.copy(
                    isGeneratingGelar = false,
                    gelarTitle = "Gagal generate",
                    gelarReason = e.message ?: "Coba lagi nanti"
                )
            }
        }
    }
}
