package com.example.medialog.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.medialog.R
import com.example.medialog.databinding.FragmentChatBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupInput()
        observeUiState()
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter()
        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true
        binding.rvMessages.layoutManager = layoutManager
        binding.rvMessages.adapter = adapter
    }

    private fun setupInput() {
        binding.btnSend.setOnClickListener { sendFromInput() }

        binding.etChatMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendFromInput()
                true
            } else {
                false
            }
        }

        binding.btnOpenSettingsChat.setOnClickListener {
            findNavController().navigate(R.id.action_chatFragment_to_settingsFragment)
        }
    }

    private fun sendFromInput() {
        val text = binding.etChatMessage.text.toString()
        if (text.isBlank()) return
        binding.etChatMessage.setText("")
        viewModel.sendMessage(text)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // API key check
                    val hasKey = state.hasApiKey && state.activeConfig != null
                    binding.layoutNoApiKey.visibility = if (hasKey) View.GONE else View.VISIBLE
                    binding.rvMessages.visibility = if (hasKey) View.VISIBLE else View.GONE
                    binding.tilChatMessage.visibility = if (hasKey) View.VISIBLE else View.GONE
                    binding.btnSend.visibility = if (hasKey) View.VISIBLE else View.GONE
                    binding.tvChatEmpty.visibility =
                        if (hasKey && state.messages.isEmpty()) View.VISIBLE else View.GONE

                    if (state.activeConfig != null) {
                        binding.tvChatProvider.text =
                            "${state.activeConfig.displayName} · ${state.activeConfig.selectedModel ?: "default"}"
                    }

                    // Messages
                    val oldSize = adapter.itemCount
                    adapter.submitList(state.messages) {
                        if (state.messages.size > oldSize) {
                            binding.rvMessages.smoothScrollToPosition(state.messages.size - 1)
                        }
                    }

                    // Loading
                    binding.progressSending.visibility =
                        if (state.isSending) View.VISIBLE else View.GONE
                    binding.btnSend.isEnabled = !state.isSending

                    // Error
                    state.error?.let { err ->
                        Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
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
