package com.example.medialog.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medialog.data.local.entity.EntryStatus
import com.example.medialog.data.local.entity.MediaEntryEntity
import com.example.medialog.data.local.entity.MediaType
import com.example.medialog.data.remote.media.MediaSearchResult
import com.example.medialog.data.remote.media.WatchProvider
import com.example.medialog.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MediaDetailUiState(
    val entry: MediaEntryEntity? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
    val nextEpisode: Int? = null,
    val nextEpisodeAiringAt: Long? = null,
    val watchProviders: List<WatchProvider> = emptyList()
)

@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val entryId: Long = savedStateHandle.get<Long>("entryId") ?: -1L

    private val _uiState = MutableStateFlow(MediaDetailUiState())
    val uiState: StateFlow<MediaDetailUiState> = _uiState.asStateFlow()

    init {
        loadEntry()
    }

    private fun loadEntry() {
        viewModelScope.launch {
            val entry = mediaRepository.getEntryById(entryId)
            if (entry != null) {
                val genres = mediaRepository.getGenresForEntry(entryId)
                val tags = mediaRepository.getTagsForEntry(entryId)

                // Fetch remote detail for nextEpisode & watchProviders
                var nextEpisode: Int? = null
                var nextEpisodeAiringAt: Long? = null
                var watchProviders: List<WatchProvider> = emptyList()

                try {
                    val detail = mediaRepository.getRemoteDetail(entry.type, entry.externalId)
                    if (detail != null) {
                        nextEpisode = detail.nextEpisode
                        nextEpisodeAiringAt = detail.nextEpisodeAiringAt
                        watchProviders = detail.watchProviders
                    }
                } catch (_: Exception) {}

                _uiState.value = MediaDetailUiState(
                    entry = entry,
                    genres = genres,
                    tags = tags,
                    isLoading = false,
                    nextEpisode = nextEpisode,
                    nextEpisodeAiringAt = nextEpisodeAiringAt,
                    watchProviders = watchProviders
                )
            } else {
                _uiState.value = MediaDetailUiState(isLoading = false)
            }
        }
    }

    fun updateStatus(status: EntryStatus) {
        val current = _uiState.value.entry ?: return
        val updated = current.copy(
            status = status,
            finishedAt = if (status == EntryStatus.COMPLETED) System.currentTimeMillis() else current.finishedAt,
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            mediaRepository.saveEntry(updated, _uiState.value.genres, _uiState.value.tags)
            _uiState.value = _uiState.value.copy(entry = updated)
        }
    }

    fun updateRating(rating: Int) {
        val current = _uiState.value.entry ?: return
        val updated = current.copy(myRating = rating, updatedAt = System.currentTimeMillis())
        viewModelScope.launch {
            mediaRepository.saveEntry(updated, _uiState.value.genres, _uiState.value.tags)
            _uiState.value = _uiState.value.copy(entry = updated)
        }
    }

    fun updateProgress(progress: Int) {
        val current = _uiState.value.entry ?: return
        // Auto-complete kalau progress sudah mencapai total (episode/halaman)
        val newStatus = if (current.totalUnits != null && current.totalUnits > 0 && progress >= current.totalUnits) {
            EntryStatus.COMPLETED
        } else {
            current.status
        }
        val updated = current.copy(
            progress = progress,
            status = newStatus,
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            mediaRepository.saveEntry(updated, _uiState.value.genres, _uiState.value.tags)
            _uiState.value = _uiState.value.copy(entry = updated)
        }
    }

    fun updateTotalEpisodes(totalEpisodes: Int?) {
        val current = _uiState.value.entry ?: return
        val updated = current.copy(totalUnits = totalEpisodes, updatedAt = System.currentTimeMillis())
        viewModelScope.launch {
            mediaRepository.saveEntry(updated, _uiState.value.genres, _uiState.value.tags)
            _uiState.value = _uiState.value.copy(entry = updated)
        }
    }

    fun incrementProgress() {
        val current = _uiState.value.entry ?: return
        val newProgress = (current.progress + 1).coerceAtMost(current.totalUnits ?: Int.MAX_VALUE)
        updateProgress(newProgress)
    }

    fun updateNotes(notes: String) {
        val current = _uiState.value.entry ?: return
        val updated = current.copy(notes = notes, updatedAt = System.currentTimeMillis())
        viewModelScope.launch {
            mediaRepository.saveEntry(updated, _uiState.value.genres, _uiState.value.tags)
            _uiState.value = _uiState.value.copy(entry = updated)
        }
    }

    fun updateTags(tags: List<String>) {
        val current = _uiState.value.entry ?: return
        viewModelScope.launch {
            mediaRepository.saveEntry(current, _uiState.value.genres, tags)
            _uiState.value = _uiState.value.copy(tags = tags)
        }
    }

    fun deleteEntry() {
        viewModelScope.launch {
            mediaRepository.deleteEntry(entryId)
            _uiState.value = _uiState.value.copy(isDeleted = true)
        }
    }
}
