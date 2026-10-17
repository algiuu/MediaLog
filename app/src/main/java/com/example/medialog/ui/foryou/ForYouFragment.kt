package com.example.medialog.ui.foryou

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.medialog.R
import com.example.medialog.databinding.FragmentForYouBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ForYouFragment : Fragment() {

    private var _binding: FragmentForYouBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ForYouViewModel by viewModels()
    private lateinit var adapter: RecommendationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForYouBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupMoodChips()
        setupRefreshButton()
        setupTypeFilter()
        observeUiState()
    }

    private fun setupRecyclerView() {
        adapter = RecommendationAdapter(
            onAddClick = { item ->
                viewModel.addRecommendationToList(item)
                Toast.makeText(requireContext(), "${item.title} added to Plan", Toast.LENGTH_SHORT).show()
            },
            onHideClick = { item ->
                viewModel.hideRecommendation(item.id)
            }
        )
        binding.rvRecommendations.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecommendations.adapter = adapter
    }

    private fun setupMoodChips() {
        binding.chipDark.setOnClickListener {
            binding.etMoodPrompt.setText(getString(R.string.mood_chip_dark))
        }
        binding.chipFeelGood.setOnClickListener {
            binding.etMoodPrompt.setText(getString(R.string.mood_chip_feel_good))
        }
        binding.chipAction.setOnClickListener {
            binding.etMoodPrompt.setText(getString(R.string.mood_chip_action))
        }
    }

    private fun setupRefreshButton() {
        binding.btnRefresh.setOnClickListener {
            val mood = binding.etMoodPrompt.text.toString()
            viewModel.setMoodPrompt(mood)
            viewModel.generateRecommendations()
        }
    }

    private fun setupTypeFilter() {
        binding.chipAll.setOnClickListener { viewModel.setTypeFilter(null) }
        binding.chipAnime.setOnClickListener { viewModel.setTypeFilter("ANIME") }
        binding.chipMovies.setOnClickListener { viewModel.setTypeFilter("MOVIE") }
        binding.chipSeries.setOnClickListener { viewModel.setTypeFilter("SERIES") }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // API key missing notice
                    val hasKey = state.hasApiKey && state.activeConfig != null
                    binding.layoutNoApiKey.visibility = if (!hasKey) View.VISIBLE else View.GONE
                    binding.layoutContent.visibility = if (hasKey) View.VISIBLE else View.GONE

                    if (state.activeConfig != null) {
                        binding.tvProviderInfo.text = "${state.activeConfig.displayName} · ${state.activeConfig.selectedModel ?: "default"}"
                    }

                    // Recommendations
                    adapter.submitList(state.recommendations)
                    binding.tvEmptyRecommendations.visibility = if (state.recommendations.isEmpty() && !state.isGenerating) View.VISIBLE else View.GONE

                    // Loading
                    binding.progressGenerating.visibility = if (state.isGenerating) View.VISIBLE else View.GONE
                    binding.btnRefresh.isEnabled = !state.isGenerating

                    // Error
                    if (state.error != null) {
                        Toast.makeText(requireContext(), state.error, Toast.LENGTH_LONG).show()
                    }

                    // Type filter chips
                    binding.chipAll.isSelected = state.typeFilter == null
                    binding.chipAnime.isSelected = state.typeFilter == "ANIME"
                    binding.chipMovies.isSelected = state.typeFilter == "MOVIE"
                    binding.chipSeries.isSelected = state.typeFilter == "SERIES"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
