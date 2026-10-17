package com.example.medialog.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medialog.data.local.entity.EntryStatus
import com.example.medialog.data.local.entity.MediaEntryEntity
import com.example.medialog.data.local.entity.MediaType
import com.example.medialog.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val entries: List<MediaEntryEntity> = emptyList(),
    val selectedType: MediaType? = null,
    val selectedStatus: EntryStatus? = null,
    val isLoading: Boolean = true,
    val selectionMode: Boolean = false,
    val selectedCount: Int = 0
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _selectedType = MutableStateFlow<MediaType?>(null)
    private val _selectedStatus = MutableStateFlow<EntryStatus?>(null)
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())

    init {
        viewModelScope.launch {
            combine(
                mediaRepository.getAllEntriesFlow(),
                _selectedType,
                _selectedStatus,
                _selection
            ) { entries, type, status, selection ->
                var filtered = entries
                if (type != null) {
                    filtered = filtered.filter { it.type == type }
                }
                if (status != null) {
                    filtered = filtered.filter { it.status == status }
                }
                _uiState.value = LibraryUiState(
                    entries = filtered,
                    selectedType = type,
                    selectedStatus = status,
                    isLoading = false,
                    selectionMode = selection.isNotEmpty(),
                    selectedCount = selection.size
                )
            }.collect { }
        }
    }

    fun selectType(type: MediaType?) {
        _selectedType.value = type
    }

    fun selectStatus(status: EntryStatus?) {
        _selectedStatus.value = status
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            mediaRepository.deleteEntry(id)
        }
    }

    // ===== Batch / Selection =====

    fun enterSelectionMode(id: Long) {
        _selection.value = setOf(id)
    }

    fun toggleSelection(id: Long) {
        val current = _selection.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selection.value = current
    }

    fun selectAllVisible() {
        _selection.value = _uiState.value.entries.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selection.value.toList()
        viewModelScope.launch {
            ids.forEach { mediaRepository.deleteEntry(it) }
            _selection.value = emptySet()
        }
    }

    fun updateStatusSelected(status: EntryStatus) {
        val ids = _selection.value.toList()
        viewModelScope.launch {
            ids.forEach { id ->
                val entry = mediaRepository.getEntryById(id) ?: return@forEach
                val updated = entry.copy(
                    status = status,
                    finishedAt = if (status == EntryStatus.COMPLETED) System.currentTimeMillis() else entry.finishedAt,
                    updatedAt = System.currentTimeMillis()
                )
                mediaRepository.saveEntry(updated)
            }
            _selection.value = emptySet()
        }
    }
}
