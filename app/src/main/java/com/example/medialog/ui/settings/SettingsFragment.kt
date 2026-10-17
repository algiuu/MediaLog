package com.example.medialog.ui.settings

import android.content.Intent
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
import com.example.medialog.data.local.entity.AiFormatType
import com.example.medialog.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    private val providers = listOf(
        "Gemini" to AiFormatType.GEMINI,
        "OpenAI" to AiFormatType.OPENAI_COMPATIBLE,
        "OpenRouter" to AiFormatType.OPENAI_COMPATIBLE,
        "Claude (Anthropic)" to AiFormatType.CLAUDE,
        "Custom" to AiFormatType.OPENAI_COMPATIBLE
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupProviderDropdown()
        setupApiKeyInput()
        setupModelDropdown()
        setupTestConnection()
        setupTmdbKey()
        setupExportImport()
        observeUiState()
    }

    private fun setupProviderDropdown() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            providers.map { it.first }
        )
        binding.spinnerProvider.setAdapter(adapter)
        binding.spinnerProvider.setOnItemClickListener { _, _, position, _ ->
            val (name, formatType) = providers[position]
            val baseUrl = when (name) {
                "Gemini" -> "https://generativelanguage.googleapis.com/v1beta"
                "OpenAI" -> "https://api.openai.com/v1"
                "OpenRouter" -> "https://openrouter.ai/api/v1"
                "Claude (Anthropic)" -> "https://api.anthropic.com/v1"
                "Custom" -> ""
                else -> ""
            }
            binding.etBaseUrl.setText(baseUrl)
            binding.tilBaseUrl.visibility = if (name == "Custom") View.VISIBLE else View.GONE
        }
    }

    private fun setupApiKeyInput() {
        binding.btnToggleApiKey.setOnClickListener {
            val isVisible = binding.etApiKey.transformationMethod is android.text.method.PasswordTransformationMethod
            binding.etApiKey.transformationMethod = if (isVisible) {
                null
            } else {
                android.text.method.PasswordTransformationMethod.getInstance()
            }
        }
    }

    private fun setupModelDropdown() {
        binding.spinnerModel.setOnClickListener {
            val apiKey = binding.etApiKey.text.toString()
            Log.d("SettingsFragment", "Model field clicked, apiKey blank: ${apiKey.isBlank()}")
            if (apiKey.isBlank()) {
                Toast.makeText(requireContext(), "Please enter API key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.fetchModelsWithKey(apiKey)
            Toast.makeText(requireContext(), "Fetching models...", Toast.LENGTH_SHORT).show()
        }

        binding.btnChangeModel.setOnClickListener {
            val models = viewModel.uiState.value.availableModels
            Log.d("SettingsFragment", "Change model clicked, models: ${models.size}")
            if (models.isEmpty()) {
                val apiKey = binding.etApiKey.text.toString()
                if (apiKey.isBlank()) {
                    Toast.makeText(requireContext(), "Please enter API key first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                viewModel.fetchModelsWithKey(apiKey)
                Toast.makeText(requireContext(), "Fetching models...", Toast.LENGTH_SHORT).show()
            } else {
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    models
                )
                binding.spinnerModel.setAdapter(adapter)
                binding.spinnerModel.showDropDown()
            }
        }
        binding.spinnerModel.setOnItemClickListener { _, _, position, _ ->
            val models = viewModel.uiState.value.availableModels
            if (position < models.size) {
                val model = models[position]
                binding.spinnerModel.setText(model, false)
            }
        }
    }

    private fun setupTestConnection() {
        binding.btnTestConnection.setOnClickListener {
            val providerName = binding.spinnerProvider.text.toString()
            if (providerName.isBlank()) {
                Toast.makeText(requireContext(), "Please select a provider", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val apiKey = binding.etApiKey.text.toString()
            val model = binding.spinnerModel.text.toString()
            val baseUrl = binding.etBaseUrl.text.toString()

            val (name, formatType) = providers.find { it.first == providerName }
                ?: (providerName to AiFormatType.OPENAI_COMPATIBLE)

            viewModel.saveProvider(
                id = name.lowercase().replace(" ", "_").replace("(", "").replace(")", ""),
                displayName = name,
                formatType = formatType,
                baseUrl = baseUrl,
                selectedModel = model.ifBlank { null },
                apiKey = apiKey
            )
            viewModel.testConnection(model)
        }
    }

    private fun setupExportImport() {
        binding.btnExport.setOnClickListener {
            viewModel.exportData(requireContext())
        }
        binding.btnImport.setOnClickListener {
            viewModel.importData(requireContext())
        }
    }

    private fun setupTmdbKey() {
        val savedKey = viewModel.getTmdbKey()
        if (!savedKey.isNullOrBlank()) {
            binding.etTmdbKey.setText(savedKey)
        }

        binding.btnSaveTmdb.setOnClickListener {
            val key = binding.etTmdbKey.text.toString()
            if (key.isBlank()) {
                Toast.makeText(requireContext(), "Please enter TMDB API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.saveTmdbKey(key)
            Toast.makeText(requireContext(), "TMDB API key saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Provider
                    state.activeConfig?.let { config ->
                        binding.spinnerProvider.setText(config.displayName, false)
                        binding.etBaseUrl.setText(config.baseUrl)
                        binding.spinnerModel.setText(config.selectedModel ?: "", false)
                    }

                    // API Key
                    if (binding.etApiKey.text.isNullOrBlank() && state.apiKey.isNotBlank()) {
                        binding.etApiKey.setText(state.apiKey)
                    }

                    // Test result
                    state.testResult?.let { result ->
                        Toast.makeText(requireContext(), result, Toast.LENGTH_LONG).show()
                    }

                    // Export/Import results
                    state.exportResult?.let { result ->
                        Toast.makeText(requireContext(), result, Toast.LENGTH_LONG).show()
                    }
                    state.importResult?.let { result ->
                        binding.tvImportStatus.text = result
                        Toast.makeText(requireContext(), result, Toast.LENGTH_LONG).show()
                    }

                    // Loading
                    binding.progressTestConnection.visibility = if (state.isTestingConnection) View.VISIBLE else View.GONE
                    binding.progressExport.visibility = if (state.isExporting) View.VISIBLE else View.GONE
                    binding.progressImport.visibility = if (state.isImporting) View.VISIBLE else View.GONE

                    // Tampilkan model dropdown setelah models berhasil di-fetch
                    if (state.availableModels.isNotEmpty()) {
                        val adapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_dropdown_item_1line,
                            state.availableModels
                        )
                        binding.spinnerModel.setAdapter(adapter)
                        binding.spinnerModel.post {
                            binding.spinnerModel.showDropDown()
                        }
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
