package com.example.medialog.ui.detail

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
import coil.load
import com.example.medialog.R
import com.example.medialog.databinding.FragmentRemoteDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RemoteDetailFragment : Fragment() {

    private var _binding: FragmentRemoteDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RemoteDetailViewModel by viewModels()

    private var addToastShown = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRemoteDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnAdd.setOnClickListener { viewModel.addToLibrary() }
        binding.tvError.setOnClickListener { viewModel.retry() }

        observeUiState()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressLoading.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.tvError.visibility =
                        if (!state.isLoading && state.detail == null && state.error != null) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    if (!state.isLoading && state.error != null && state.detail == null) {
                        binding.tvError.text = "${state.error}\n\n(Ketuk untuk coba lagi)"
                    }

                    val detail = state.detail ?: return@collect

                    binding.tvTitle.text = detail.title

                    val meta = listOfNotNull(
                        detail.creator,
                        detail.releaseYear?.toString(),
                        detail.totalUnits?.let { "$it episode" }
                    ).joinToString(" · ")
                    binding.tvMeta.text = meta.ifBlank { "AniList" }

                    binding.tvGenres.text = detail.genres.joinToString(" · ").ifBlank { "—" }

                    if (!detail.posterUrl.isNullOrBlank()) {
                        binding.ivPoster.load(detail.posterUrl) {
                            crossfade(true)
                            placeholder(R.color.card_bg_light)
                        }
                    } else {
                        binding.ivPoster.setImageResource(R.color.primary_purple_dark)
                    }

                    // Countdown episode berikutnya
                    val nextEp = detail.nextEpisode
                    val nextAir = detail.nextEpisodeAiringAt
                    if (nextEp != null && nextAir != null) {
                        binding.cardNextEpisode.visibility = View.VISIBLE
                        binding.tvNextEpisode.text = "Episode $nextEp"
                        val diff = nextAir - System.currentTimeMillis() / 1000
                        binding.tvCountdown.text = if (diff > 0) {
                            val days = diff / 86400
                            val hours = (diff % 86400) / 3600
                            val minutes = (diff % 3600) / 60
                            "tayang dalam $days hari $hours jam $minutes menit"
                        } else {
                            "tayang sekarang!"
                        }
                    } else {
                        binding.cardNextEpisode.visibility = View.GONE
                    }

                    binding.tvSynopsis.text = detail.synopsis
                        ?.takeIf { it.isNotBlank() }
                        ?: "Sinopsis tidak tersedia."

                    if (state.alreadyInLibrary) {
                        binding.btnAdd.text =
                            if (state.justAdded) "✓ Ditambahkan ke Library" else "✓ Sudah di Library"
                        binding.btnAdd.isEnabled = false
                    } else {
                        binding.btnAdd.text = "Tambahkan ke Library"
                        binding.btnAdd.isEnabled = true
                    }

                    if (state.justAdded && !addToastShown) {
                        addToastShown = true
                        Toast.makeText(
                            requireContext(),
                            "${detail.title} ditambahkan ke library",
                            Toast.LENGTH_SHORT
                        ).show()
                        findNavController().popBackStack()
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
