package com.example.medialog.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.medialog.R
import com.example.medialog.data.local.entity.EntryStatus
import com.example.medialog.data.local.entity.MediaType
import com.example.medialog.databinding.FragmentMediaDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MediaDetailFragment : Fragment() {

    private var _binding: FragmentMediaDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MediaDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupStatusChips()
        setupRating()
        setupProgress()
        setupNotes()
        setupDelete()
        observeUiState()
    }

    private fun setupStatusChips() {
        binding.chipPlan.setOnClickListener { viewModel.updateStatus(EntryStatus.PLAN) }
        binding.chipOngoing.setOnClickListener { viewModel.updateStatus(EntryStatus.ONGOING) }
        binding.chipCompleted.setOnClickListener { viewModel.updateStatus(EntryStatus.COMPLETED) }
        binding.chipDropped.setOnClickListener { viewModel.updateStatus(EntryStatus.DROPPED) }
    }

    private fun setupRating() {
        binding.seekBarRating.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.updateRating(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupProgress() {
        // Anime: input episode sekarang
        binding.etCurrentEpisode.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.etCurrentEpisode.text.toString()
                val episode = text.toIntOrNull() ?: 0
                viewModel.updateProgress(episode)
            }
        }

        // Book: input halaman sekarang
        binding.etCurrentPage.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.etCurrentPage.text.toString()
                val page = text.toIntOrNull() ?: 0
                viewModel.updateProgress(page)
            }
        }

        // Movie/Series: switch watched
        binding.switchWatched.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewModel.updateStatus(EntryStatus.COMPLETED)
            } else {
                viewModel.updateStatus(EntryStatus.ONGOING)
            }
        }
    }

    private fun setupNotes() {
        binding.btnSaveNotes.setOnClickListener {
            val notes = binding.etNotes.text.toString()
            viewModel.updateNotes(notes)
            Toast.makeText(requireContext(), "Notes saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDelete() {
        binding.btnDelete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Entry")
                .setMessage("Are you sure you want to delete this entry?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteEntry()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.isDeleted) {
                        findNavController().popBackStack()
                        return@collect
                    }

                    val entry = state.entry ?: return@collect

                    binding.tvTitle.text = entry.title
                    binding.tvCreator.text = entry.creator ?: ""
                    binding.tvYear.text = entry.releaseYear?.toString() ?: ""
                    binding.tvSynopsis.text = entry.synopsis ?: ""

                    val typeText = when (entry.type) {
                        MediaType.ANIME -> "Anime"
                        MediaType.MOVIE -> "Movie"
                        MediaType.SERIES -> "Series"
                        MediaType.BOOK -> "Book"
                    }
                    binding.tvTypeBadge.text = typeText

                    // Rating
                    binding.seekBarRating.progress = entry.myRating ?: 0
                    binding.tvRatingValue.text = "${entry.myRating ?: 0}/10"

                    // Show/hide berdasarkan tipe media
                    if (entry.type == MediaType.ANIME || entry.type == MediaType.SERIES) {
                        // Anime/Series: progress episode
                        binding.layoutProgressAnime.visibility = View.VISIBLE
                        binding.layoutProgressBook.visibility = View.GONE
                        binding.layoutWatched.visibility = View.GONE
                        binding.layoutWatchProviders.visibility = View.GONE

                        // Next episode countdown
                        val nextEp = state.nextEpisode
                        val nextAir = state.nextEpisodeAiringAt
                        if (nextEp != null && nextAir != null) {
                            binding.cardNextEpisode.visibility = View.VISIBLE
                            binding.tvNextEpisode.text = "Episode $nextEp"
                            val now = System.currentTimeMillis() / 1000
                            val diff = nextAir - now
                            if (diff > 0) {
                                val days = diff / 86400
                                val hours = (diff % 86400) / 3600
                                val minutes = (diff % 3600) / 60
                                binding.tvCountdown.text = "tayang dalam $days hari $hours jam $minutes menit"
                            } else {
                                binding.tvCountdown.text = "tayang sekarang!"
                            }
                        } else {
                            binding.cardNextEpisode.visibility = View.GONE
                        }

                        val totalUnits = entry.totalUnits
                        if (binding.etCurrentEpisode.text.isNullOrBlank()) {
                            binding.etCurrentEpisode.setText(entry.progress.toString())
                        }
                        binding.tvTotalEpisodes.text = totalUnits?.toString() ?: "?"
                        binding.tvUnitLabel.text = entry.unitLabel ?: ""
                        if (totalUnits != null && totalUnits > 0) {
                            binding.progressBar.max = totalUnits
                            binding.progressBar.progress = entry.progress
                        } else {
                            binding.progressBar.max = 100
                            binding.progressBar.progress = 0
                        }
                    } else if (entry.type == MediaType.BOOK) {
                        // Book: progress halaman
                        binding.layoutProgressAnime.visibility = View.GONE
                        binding.layoutProgressBook.visibility = View.VISIBLE
                        binding.layoutWatched.visibility = View.GONE
                        binding.layoutWatchProviders.visibility = View.GONE
                        binding.cardNextEpisode.visibility = View.GONE

                        val totalPages = entry.totalUnits
                        if (binding.etCurrentPage.text.isNullOrBlank()) {
                            binding.etCurrentPage.setText(entry.progress.toString())
                        }
                        binding.tvTotalPages.text = totalPages?.toString() ?: "?"
                        if (totalPages != null && totalPages > 0) {
                            binding.progressBarBook.max = totalPages
                            binding.progressBarBook.progress = entry.progress
                        } else {
                            binding.progressBarBook.max = 100
                            binding.progressBarBook.progress = 0
                        }
                    } else {
                        // Movie: switch watched
                        binding.layoutProgressAnime.visibility = View.GONE
                        binding.layoutProgressBook.visibility = View.GONE
                        binding.layoutWatched.visibility = View.VISIBLE

                        // Watch providers
                        val providers = state.watchProviders
                        if (providers.isNotEmpty()) {
                            binding.layoutWatchProviders.visibility = View.VISIBLE
                            binding.tvWatchProviders.text = providers.joinToString(", ") { it.providerName }
                        } else {
                            binding.layoutWatchProviders.visibility = View.GONE
                        }
                    }

                    // Status chips
                    binding.chipPlan.isSelected = entry.status == EntryStatus.PLAN
                    binding.chipOngoing.isSelected = entry.status == EntryStatus.ONGOING
                    binding.chipCompleted.isSelected = entry.status == EntryStatus.COMPLETED
                    binding.chipDropped.isSelected = entry.status == EntryStatus.DROPPED

                    // Notes
                    if (binding.etNotes.text.isNullOrBlank()) {
                        binding.etNotes.setText(entry.notes ?: "")
                    }

                    // Tags
                    binding.tvTags.text = if (state.tags.isEmpty()) "No tags" else state.tags.joinToString(", ")

                    // Poster
                    if (!entry.posterUrl.isNullOrBlank()) {
                        binding.ivPoster.visibility = View.VISIBLE
                        binding.tvInitial.visibility = View.GONE
                        binding.ivPoster.load(entry.posterUrl) {
                            crossfade(true)
                            placeholder(R.color.card_bg_light)
                        }
                    } else {
                        binding.ivPoster.visibility = View.GONE
                        binding.tvInitial.visibility = View.VISIBLE
                        binding.tvInitial.text = entry.title.firstOrNull()?.uppercase() ?: "M"
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
