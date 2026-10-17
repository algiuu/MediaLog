package com.example.medialog.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medialog.data.local.entity.EntryStatus
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

data class RemoteDetailUiState(
    val detail: MediaSearchResult? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val alreadyInLibrary: Boolean = false,
    val justAdded: Boolean = false
)

/**
 * Halaman detail untuk media yang BELUM ada di library
 * (mis. anime yang diklik dari Jadwal Tayang).
 */
@HiltViewModel
class RemoteDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val mediaId: Int = savedStateHandle.get<Int>("mediaId") ?: -1

    private val _uiState = MutableStateFlow(RemoteDetailUiState())
    val uiState: StateFlow<RemoteDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        if (mediaId <= 0) {
            _uiState.value = RemoteDetailUiState(isLoading = false, error = "Media tidak ditemukan")
            return
        }

        viewModelScope.launch {
            _uiState.value = RemoteDetailUiState(isLoading = true)
            try {
                val detail = mediaRepository.getRemoteDetail(MediaType.ANIME, mediaId.toString())
                if (detail == null) {
                    _uiState.value = RemoteDetailUiState(
                        isLoading = false,
                        error = "Gagal memuat detail dari AniList"
                    )
                    return@launch
                }

                val existing = mediaRepository.getEntryByExternalId(detail.source, detail.externalId)
                _uiState.value = RemoteDetailUiState(
                    detail = detail,
                    isLoading = false,
                    alreadyInLibrary = existing != null
                )
            } catch (e: Exception) {
                _uiState.value = RemoteDetailUiState(
                    isLoading = false,
                    error = e.message ?: "Gagal memuat detail"
                )
            }
        }
    }

    fun addToLibrary() {
        val detail = _uiState.value.detail ?: return
        if (_uiState.value.alreadyInLibrary) return

        viewModelScope.launch {
            val entity = MediaEntryEntity(
                type = MediaType.ANIME,
                title = detail.title,
                originalTitle = detail.originalTitle,
                source = detail.source,
                externalId = detail.externalId,
                posterUrl = detail.posterUrl,
                synopsis = detail.synopsis,
                releaseYear = detail.releaseYear,
                creator = detail.creator,
                totalUnits = detail.totalUnits,
                unitLabel = detail.unitLabel ?: "episodes",
                status = EntryStatus.PLAN
            )
            mediaRepository.saveEntry(entity, genres = detail.genres)
            _uiState.value = _uiState.value.copy(alreadyInLibrary = true, justAdded = true)
        }
    }

    fun retry() = load()
}
