package com.example.medialog.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.medialog.R
import com.example.medialog.data.local.entity.MediaType
import com.example.medialog.databinding.FragmentStatsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGelarButton()
        observeUiState()
    }

    private fun setupGelarButton() {
        binding.btnGetGelar.setOnClickListener {
            viewModel.generateGelar()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Summary cards
                    binding.tvTotalTracked.text = state.totalTracked.toString()
                    binding.tvCompleted.text = state.completed.toString()
                    binding.tvInProgress.text = getString(R.string.stat_in_progress, state.inProgress)
                    binding.tvAvgRating.text = String.format("%.1f", state.avgRating)
                    binding.tvTotalUnits.text = state.totalUnits.toString()

                    // By Type
                    val animeCount = state.byType[MediaType.ANIME] ?: 0
                    val movieCount = state.byType[MediaType.MOVIE] ?: 0
                    val seriesCount = state.byType[MediaType.SERIES] ?: 0
                    val bookCount = state.byType[MediaType.BOOK] ?: 0
                    val maxType = maxOf(animeCount, movieCount, seriesCount, bookCount, 1)

                    binding.tvAnimeCount.text = animeCount.toString()
                    binding.tvMovieCount.text = movieCount.toString()
                    binding.tvSeriesCount.text = seriesCount.toString()
                    binding.tvBookCount.text = bookCount.toString()

                    binding.progressAnime.progress = (animeCount * 100 / maxType)
                    binding.progressMovie.progress = (movieCount * 100 / maxType)
                    binding.progressSeries.progress = (seriesCount * 100 / maxType)
                    binding.progressBook.progress = (bookCount * 100 / maxType)

                    // Gelar AI
                    if (state.gelarTitle != null) {
                        binding.tvGelarTitle.text = state.gelarTitle
                        binding.tvGelarReason.text = state.gelarReason ?: ""
                    }
                    binding.btnGetGelar.isEnabled = !state.isGeneratingGelar
                    binding.btnGetGelar.text = if (state.isGeneratingGelar) "Generating..." else "Dapatkan Gelar"

                    // Top Genres
                    val genres = state.topGenres
                    if (genres.isNotEmpty()) {
                        binding.tvGenre1.text = genres.getOrNull(0)?.genre ?: ""
                        binding.tvGenre1Count.text = genres.getOrNull(0)?.count?.toString() ?: "0"
                        binding.progressGenre1.progress = (genres[0].count * 100 / (genres[0].count.coerceAtLeast(1)))

                        binding.tvGenre2.text = genres.getOrNull(1)?.genre ?: ""
                        binding.tvGenre2Count.text = genres.getOrNull(1)?.count?.toString() ?: "0"
                        binding.progressGenre2.progress = if (genres.size > 1) (genres[1].count * 100 / genres[0].count.coerceAtLeast(1)) else 0

                        binding.tvGenre3.text = genres.getOrNull(2)?.genre ?: ""
                        binding.tvGenre3Count.text = genres.getOrNull(2)?.count?.toString() ?: "0"
                        binding.progressGenre3.progress = if (genres.size > 2) (genres[2].count * 100 / genres[0].count.coerceAtLeast(1)) else 0

                        binding.tvGenre4.text = genres.getOrNull(3)?.genre ?: ""
                        binding.tvGenre4Count.text = genres.getOrNull(3)?.count?.toString() ?: "0"
                        binding.progressGenre4.progress = if (genres.size > 3) (genres[3].count * 100 / genres[0].count.coerceAtLeast(1)) else 0

                        binding.tvGenre5.text = genres.getOrNull(4)?.genre ?: ""
                        binding.tvGenre5Count.text = genres.getOrNull(4)?.count?.toString() ?: "0"
                        binding.progressGenre5.progress = if (genres.size > 4) (genres[4].count * 100 / genres[0].count.coerceAtLeast(1)) else 0
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
