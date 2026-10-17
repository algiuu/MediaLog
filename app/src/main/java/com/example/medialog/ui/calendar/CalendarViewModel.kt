package com.example.medialog.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medialog.data.remote.media.AiringItem
import com.example.medialog.data.remote.media.AniListProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class AiringDay(
    val dayLabel: String,
    val items: List<AiringItem>
)

data class CalendarUiState(
    val days: List<AiringDay> = emptyList(),
    val weekStart: Long = 0L,
    val weekEnd: Long = 0L,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val aniListProvider: AniListProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var rangeStart: Long = startOfWeek(System.currentTimeMillis() / 1000)
    private val weekSeconds = 7L * 24 * 3600

    init {
        loadWeek(rangeStart)
    }

    fun loadWeek(start: Long) {
        rangeStart = start
        val end = start + weekSeconds
        _uiState.value = CalendarUiState(
            weekStart = start,
            weekEnd = end,
            isLoading = true
        )
        viewModelScope.launch {
            try {
                val items = aniListProvider.getAiringSchedule(start, end)
                _uiState.value = CalendarUiState(
                    days = groupByDay(items, start),
                    weekStart = start,
                    weekEnd = end,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = CalendarUiState(
                    weekStart = start,
                    weekEnd = end,
                    isLoading = false,
                    error = e.message ?: "Gagal memuat jadwal tayang"
                )
            }
        }
    }

    fun nextWeek() = loadWeek(rangeStart + weekSeconds)
    fun prevWeek() = loadWeek(rangeStart - weekSeconds)

    private fun groupByDay(items: List<AiringItem>, start: Long): List<AiringDay> {
        val cal = Calendar.getInstance()
        val dayLabels = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
        val result = mutableListOf<AiringDay>()
        for (i in 0 until 7) {
            val dayStart = start + i.toLong() * 24 * 3600
            val dayEnd = dayStart + 24 * 3600
            val dayItems = items
                .filter { it.airingAt in dayStart until dayEnd }
                .sortedBy { it.airingAt }
            cal.timeInMillis = dayStart * 1000
            val dayIndex = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Senin = 0
            result.add(AiringDay(dayLabels[dayIndex], dayItems))
        }
        return result
    }

    private fun startOfWeek(nowSeconds: Long): Long {
        // Hitung dalam waktu lokal (WIB) supaya bucket hari = hari kalender user.
        // airingAt AniList adalah Unix timestamp (momen absolut), jadi aman
        // dikonversi ke batas hari lokal.
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowSeconds * 1000
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        // Mundur ke Senin
        val dow = cal.get(Calendar.DAY_OF_WEEK) // Minggu = 1
        val diffToMonday = (dow + 5) % 7
        cal.add(Calendar.DAY_OF_MONTH, -diffToMonday)
        return cal.timeInMillis / 1000
    }
}
