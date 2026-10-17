package com.example.medialog.ui.add

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medialog.data.local.entity.MediaEntryEntity
import com.example.medialog.data.local.entity.MediaType
import com.example.medialog.data.remote.media.MediaSearchResult
import com.example.medialog.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AddMediaViewModel"

data class AddMediaUiState(
    val selectedType: MediaType = MediaType.ANIME,
    val searchQuery: String = "",
    val searchResults: List<MediaSearchResult> = emptyList(),
    val seasonalResults: List<MediaSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val isSeasonalLoading: Boolean = false,
    val selectedSeason: String = "WINTER",
    val selectedYear: Int = 2026,
    val errorMessage: String? = null
)

@HiltViewModel
class AddMediaViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddMediaUiState())
    val uiState: StateFlow<AddMediaUiState> = _uiState.asStateFlow()

    fun selectType(type: MediaType) {
        _uiState.value = _uiState.value.copy(
            selectedType = type,
            searchResults = emptyList(),
            seasonalResults = emptyList(),
            errorMessage = null
        )
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query, errorMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun search() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, errorMessage = null)
            try {
                Log.d(TAG, "Searching for '$query' in ${_uiState.value.selectedType}")
                val results = mediaRepository.searchRemote(_uiState.value.selectedType, query)
                Log.d(TAG, "Search returned ${results.size} results")
                _uiState.value = _uiState.value.copy(
                    searchResults = results,
                    isSearching = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Search failed for '$query'", e)
                _uiState.value = _uiState.value.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    errorMessage = "Search failed: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun loadSeasonalAnime(season: String, year: Int) {
        _uiState.value = _uiState.value.copy(
            selectedSeason = season,
            selectedYear = year,
            isSeasonalLoading = true,
            errorMessage = null
        )
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading seasonal anime: $season $year")
                val results = mediaRepository.getSeasonalAnime(season, year)
                Log.d(TAG, "Seasonal anime returned ${results.size} results")
                _uiState.value = _uiState.value.copy(
                    seasonalResults = results,
                    isSeasonalLoading = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Seasonal anime failed for $season $year", e)
                _uiState.value = _uiState.value.copy(
                    seasonalResults = emptyList(),
                    isSeasonalLoading = false,
                    errorMessage = "Failed to load seasonal anime: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun saveSearchResult(result: MediaSearchResult) {
        viewModelScope.launch {
            try {
                val entity = MediaEntryEntity(
                    type = result.type,
                    title = result.title,
                    originalTitle = result.originalTitle,
                    source = result.source,
                    externalId = result.externalId,
                    posterUrl = result.posterUrl,
                    synopsis = result.synopsis,
                    releaseYear = result.releaseYear,
                    creator = result.creator,
                    totalUnits = result.totalUnits,
                    unitLabel = result.unitLabel
                )
                mediaRepository.saveEntry(entity, result.genres, emptyList())
                Log.d(TAG, "Saved search result: ${result.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save search result: ${result.title}", e)
            }
        }
    }

    fun saveManualEntry(
        title: String,
        creator: String,
        year: Int?,
        totalUnits: Int?,
        unitLabel: String?,
        genres: List<String>,
        platform: String?
    ) {
        viewModelScope.launch {
            try {
                val entity = MediaEntryEntity(
                    type = _uiState.value.selectedType,
                    title = title,
                    creator = creator.ifBlank { null },
                    releaseYear = year,
                    totalUnits = totalUnits,
                    unitLabel = unitLabel,
                    platform = platform,
                    source = null,
                    externalId = null
                )
                mediaRepository.saveEntry(entity, genres, emptyList())
                Log.d(TAG, "Saved manual entry: $title")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save manual entry: $title", e)
            }
        }
    }
}
