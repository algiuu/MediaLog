package com.example.medialog.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.medialog.R
import com.example.medialog.data.local.entity.EntryStatus
import com.example.medialog.data.local.entity.MediaType
import com.example.medialog.databinding.FragmentLibraryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LibraryViewModel by viewModels()
    private lateinit var adapter: MediaEntryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupChips()
        setupStatusFilter()
        setupSearch()
        setupSelectionToolbar()
        setupFab()
        observeUiState()
    }

    private fun setupRecyclerView() {
        adapter = MediaEntryAdapter(
            onItemClick = { entry ->
                val bundle = Bundle().apply { putLong("entryId", entry.id) }
                findNavController().navigate(R.id.action_libraryFragment_to_mediaDetailFragment, bundle)
            },
            onItemLongClick = { entry ->
                viewModel.enterSelectionMode(entry.id)
            }
        )
        binding.rvEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEntries.adapter = adapter
    }

    private fun setupSelectionToolbar() {
        binding.btnCloseSelection.setOnClickListener { viewModel.clearSelection() }
        binding.btnSelectAll.setOnClickListener { viewModel.selectAllVisible() }
        binding.btnSetStatus.setOnClickListener { view -> showStatusPopup(view) }
        binding.btnDeleteSelected.setOnClickListener { confirmDeleteSelected() }
    }

    private fun showStatusPopup(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 0, 0, getString(R.string.status_plan))
        popup.menu.add(0, 1, 1, getString(R.string.status_ongoing))
        popup.menu.add(0, 2, 2, getString(R.string.status_completed))
        popup.menu.add(0, 3, 3, getString(R.string.status_dropped))
        popup.setOnMenuItemClickListener { item ->
            val status = when (item.itemId) {
                0 -> EntryStatus.PLAN
                1 -> EntryStatus.ONGOING
                2 -> EntryStatus.COMPLETED
                else -> EntryStatus.DROPPED
            }
            viewModel.updateStatusSelected(status)
            Toast.makeText(requireContext(), "Status diperbarui", Toast.LENGTH_SHORT).show()
            true
        }
        popup.show()
    }

    private fun confirmDeleteSelected() {
        val count = viewModel.uiState.value.selectedCount
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Entri")
            .setMessage("Hapus $count entri yang dipilih?")
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.deleteSelected()
                Toast.makeText(requireContext(), "$count entri dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            findNavController().navigate(R.id.action_libraryFragment_to_addMediaFragment)
        }
        binding.btnCalendar.setOnClickListener {
            findNavController().navigate(R.id.action_libraryFragment_to_calendarFragment)
        }
    }

    private fun setupChips() {
        binding.chipAll.setOnClickListener { viewModel.selectType(null) }
        binding.chipAnime.setOnClickListener { viewModel.selectType(MediaType.ANIME) }
        binding.chipMovies.setOnClickListener { viewModel.selectType(MediaType.MOVIE) }
        binding.chipSeries.setOnClickListener { viewModel.selectType(MediaType.SERIES) }
        binding.chipBooks.setOnClickListener { viewModel.selectType(MediaType.BOOK) }
    }

    private fun setupStatusFilter() {
        binding.chipStatusFilter.setOnClickListener { view ->
            val popup = PopupMenu(requireContext(), view)
            val menu = popup.menu
            menu.add(android.view.Menu.NONE, 0, android.view.Menu.NONE, "All Status")
            menu.add(android.view.Menu.NONE, 1, android.view.Menu.NONE, "Plan to Watch/Read")
            menu.add(android.view.Menu.NONE, 2, android.view.Menu.NONE, "Ongoing / Watching")
            menu.add(android.view.Menu.NONE, 3, android.view.Menu.NONE, "Completed")
            menu.add(android.view.Menu.NONE, 4, android.view.Menu.NONE, "Dropped")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> {
                        viewModel.selectStatus(null)
                        binding.chipStatusFilter.text = getString(R.string.status_all)
                    }
                    1 -> {
                        viewModel.selectStatus(EntryStatus.PLAN)
                        binding.chipStatusFilter.text = getString(R.string.status_plan)
                    }
                    2 -> {
                        viewModel.selectStatus(EntryStatus.ONGOING)
                        binding.chipStatusFilter.text = getString(R.string.status_ongoing)
                    }
                    3 -> {
                        viewModel.selectStatus(EntryStatus.COMPLETED)
                        binding.chipStatusFilter.text = getString(R.string.status_completed)
                    }
                    4 -> {
                        viewModel.selectStatus(EntryStatus.DROPPED)
                        binding.chipStatusFilter.text = getString(R.string.status_dropped)
                    }
                }
                true
            }
            popup.show()
        }
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            findNavController().navigate(R.id.action_libraryFragment_to_addMediaFragment)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.entries)
                    binding.tvItemsCount.text = getString(R.string.items_count, state.entries.size)
                    binding.tvEmpty.visibility = if (state.entries.isEmpty() && !state.isLoading) View.VISIBLE else View.GONE

                    // Update chip selection visuals
                    binding.chipAll.isSelected = state.selectedType == null
                    binding.chipAnime.isSelected = state.selectedType == MediaType.ANIME
                    binding.chipMovies.isSelected = state.selectedType == MediaType.MOVIE
                    binding.chipSeries.isSelected = state.selectedType == MediaType.SERIES
                    binding.chipBooks.isSelected = state.selectedType == MediaType.BOOK

                    // Selection mode
                    adapter.setSelectionMode(state.selectionMode)
                    binding.layoutSelection.visibility = if (state.selectionMode) View.VISIBLE else View.GONE
                    android.util.Log.d("LibraryFragment", "selectionMode=${state.selectionMode} layoutSelection=${binding.layoutSelection.visibility}")
                    binding.tvSelectedCount.text = getString(R.string.items_selected, state.selectedCount)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
