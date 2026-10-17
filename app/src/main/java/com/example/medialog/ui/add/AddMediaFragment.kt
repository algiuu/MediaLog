package com.example.medialog.ui.add

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.medialog.R
import com.example.medialog.data.local.entity.MediaType
import com.example.medialog.databinding.FragmentAddMediaBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddMediaFragment : Fragment() {

    private var _binding: FragmentAddMediaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddMediaViewModel by viewModels()
    private lateinit var searchAdapter: SearchResultAdapter
    private lateinit var seasonalAdapter: SearchResultAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddMediaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTypeToggle()
        setupSearch()
        setupRecyclerView()
        setupSeasonal()
        observeUiState()
    }

    private fun setupTypeToggle() {
        binding.toggleButton.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btn_anime -> viewModel.selectType(MediaType.ANIME)
                R.id.btn_movie -> viewModel.selectType(MediaType.MOVIE)
                R.id.btn_series -> viewModel.selectType(MediaType.SERIES)
                R.id.btn_book -> viewModel.selectType(MediaType.BOOK)
            }
        }
    }

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            val query = binding.etSearch.text.toString()
            Log.d("AddMediaFragment", "Search button clicked, query: '$query'")
            viewModel.updateSearchQuery(query)
            viewModel.search()
        }
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            val query = binding.etSearch.text.toString()
            Log.d("AddMediaFragment", "Search IME action, query: '$query'")
            viewModel.updateSearchQuery(query)
            viewModel.search()
            true
        }
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchResultAdapter { result ->
            viewModel.saveSearchResult(result)
            Toast.makeText(requireContext(), "${result.title} added to library", Toast.LENGTH_SHORT).show()
        }
        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchResults.adapter = searchAdapter

        seasonalAdapter = SearchResultAdapter { result ->
            viewModel.saveSearchResult(result)
            Toast.makeText(requireContext(), "${result.title} added to library", Toast.LENGTH_SHORT).show()
        }
        binding.rvSeasonalResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSeasonalResults.adapter = seasonalAdapter
    }

    private fun setupSeasonal() {
        // Isi spinner season
        val seasons = listOf("Winter", "Spring", "Summer", "Fall")
        val seasonAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            seasons
        )
        seasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSeason.adapter = seasonAdapter

        // Isi spinner year (2020 - 2026)
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val years = (2020..currentYear).map { it.toString() }
        val yearAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            years
        )
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerYear.adapter = yearAdapter

        // Set default ke musim sekarang
        val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        val currentSeason = when (currentMonth) {
            in 0..2 -> "Winter"   // Jan-Mar
            in 3..5 -> "Spring"   // Apr-Jun
            in 6..8 -> "Summer"   // Jul-Sep
            else -> "Fall"         // Oct-Dec
        }
        val seasonIndex = seasons.indexOf(currentSeason)
        if (seasonIndex >= 0) {
            binding.spinnerSeason.setSelection(seasonIndex)
        }
        binding.spinnerYear.setSelection(years.indexOf(currentYear.toString()))

        binding.btnLoadSeasonal.setOnClickListener {
            val season = binding.spinnerSeason.selectedItem.toString().uppercase()
            val year = binding.spinnerYear.selectedItem.toString().toIntOrNull() ?: currentYear
            viewModel.loadSeasonalAnime(season, year)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Update type toggle
                    when (state.selectedType) {
                        MediaType.ANIME -> binding.toggleButton.check(R.id.btn_anime)
                        MediaType.MOVIE -> binding.toggleButton.check(R.id.btn_movie)
                        MediaType.SERIES -> binding.toggleButton.check(R.id.btn_series)
                        MediaType.BOOK -> binding.toggleButton.check(R.id.btn_book)
                    }

                    // Search results
                    Log.d("AddMediaFragment", "Search results: ${state.searchResults.size} items")
                    searchAdapter.submitList(state.searchResults)
                    binding.tvResultsLabel.visibility = if (state.searchResults.isNotEmpty()) View.VISIBLE else View.GONE
                    binding.rvSearchResults.visibility = if (state.searchResults.isNotEmpty()) View.VISIBLE else View.GONE

                    // Seasonal results
                    Log.d("AddMediaFragment", "Seasonal results: ${state.seasonalResults.size} items")
                    seasonalAdapter.submitList(state.seasonalResults)
                    binding.tvSeasonalLabel.visibility = if (state.seasonalResults.isNotEmpty()) View.VISIBLE else View.GONE
                    binding.rvSeasonalResults.visibility = if (state.seasonalResults.isNotEmpty()) View.VISIBLE else View.GONE

                    // Loading states
                    binding.progressSearch.visibility = if (state.isSearching) View.VISIBLE else View.GONE
                    binding.progressSeasonal.visibility = if (state.isSeasonalLoading) View.VISIBLE else View.GONE

                    // Error message
                    if (state.errorMessage != null) {
                        Toast.makeText(requireContext(), state.errorMessage, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
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
