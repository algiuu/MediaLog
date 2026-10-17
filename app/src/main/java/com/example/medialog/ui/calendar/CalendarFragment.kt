package com.example.medialog.ui.calendar

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
import com.example.medialog.databinding.FragmentCalendarBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CalendarViewModel by viewModels()
    private lateinit var adapter: CalendarAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupWeekNav()
        observeUiState()
    }

    private fun setupRecyclerView() {
        adapter = CalendarAdapter { item ->
            val mediaId = item.mediaId.toIntOrNull()
            if (mediaId != null) {
                val bundle = Bundle().apply { putInt("mediaId", mediaId) }
                findNavController().navigate(
                    R.id.action_calendarFragment_to_remoteDetailFragment,
                    bundle
                )
            }
        }
        binding.rvDays.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDays.adapter = adapter
    }

    private fun setupWeekNav() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnPrevWeek.setOnClickListener { viewModel.prevWeek() }
        binding.btnNextWeek.setOnClickListener { viewModel.nextWeek() }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
                    binding.tvWeekLabel.text =
                        "${dateFormat.format(Date(state.weekStart * 1000))} - ${dateFormat.format(Date((state.weekEnd - 86400) * 1000))}"

                    adapter.submitList(state.days)

                    binding.progressLoading.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.tvEmpty.visibility =
                        if (!state.isLoading && state.days.all { it.items.isEmpty() }) View.VISIBLE else View.GONE

                    state.error?.let { err ->
                        Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
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
