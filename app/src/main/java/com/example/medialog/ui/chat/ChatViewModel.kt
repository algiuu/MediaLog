package com.example.medialog.ui.chat

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medialog.data.local.entity.AiProviderConfigEntity
import com.example.medialog.data.repository.AiSettingsRepository
import com.example.medialog.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

private const val TAG = "ChatViewModel"

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val hasApiKey: Boolean = false,
    val activeConfig: AiProviderConfigEntity? = null,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val aiSettingsRepository: AiSettingsRepository,
    private val mediaRepository: MediaRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val prefs = context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)

    init {
        loadHistory()

        viewModelScope.launch {
            aiSettingsRepository.activeConfigFlow.collect { config ->
                val hasKey = config != null &&
                        !aiSettingsRepository.getApiKey(config.id).isNullOrBlank()
                _uiState.value = _uiState.value.copy(
                    activeConfig = config,
                    hasApiKey = hasKey
                )
            }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val config = _uiState.value.activeConfig
        val apiKey = config?.let { aiSettingsRepository.getApiKey(it.id) }
        if (config == null || apiKey.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(error = "Tambah API key di Settings dulu ya")
            return
        }
        if (_uiState.value.isSending) return

        val history = _uiState.value.messages
        val newMessages = history + ChatMessage(text = trimmed, fromUser = true)
        _uiState.value = _uiState.value.copy(messages = newMessages, isSending = true, error = null)
        saveHistory(newMessages)

        viewModelScope.launch {
            try {
                val libraryContext = buildLibraryContext()
                val prompt = buildPrompt(newMessages, libraryContext)

                val provider = aiSettingsRepository.getProviderForFormat(config.formatType)
                val result = withContext(Dispatchers.IO) {
                    provider.generate(
                        apiKey = apiKey,
                        baseUrl = config.baseUrl,
                        model = config.selectedModel ?: "",
                        prompt = prompt
                    )
                }

                val reply = result.content.trim().ifBlank { "Hmm, aku bingung jawabnya 🤔" }
                val withReply = newMessages + ChatMessage(text = reply, fromUser = false)
                _uiState.value = _uiState.value.copy(messages = withReply, isSending = false)
                saveHistory(withReply)
            } catch (e: Exception) {
                Log.e(TAG, "Chat failed", e)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    error = e.message ?: "Gagal mengirim pesan"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearHistory() {
        _uiState.value = _uiState.value.copy(messages = emptyList())
        prefs.edit().remove("messages").apply()
    }

    private suspend fun buildLibraryContext(): String = withContext(Dispatchers.Default) {
        val entries = try {
            mediaRepository.getAllEntries()
        } catch (_: Exception) {
            emptyList()
        }
        if (entries.isEmpty()) return@withContext "Library masih kosong."

        buildString {
            appendLine("Library user (${entries.size} entri):")
            entries.take(60).forEach { e ->
                append("- ${e.title} [${e.type.name}, ${e.status.name}")
                e.myRating?.let { append(", rating $it/10") }
                append("]")
                e.notes?.takeIf { it.isNotBlank() }?.let { n ->
                    append(" catatan: \"${n.take(120)}\"")
                }
                appendLine()
            }
            if (entries.size > 60) appendLine("... dan ${entries.size - 60} entri lainnya.")
        }
    }

    private fun buildPrompt(messages: List<ChatMessage>, libraryContext: String): String {
        val historyText = messages.takeLast(12).joinToString("\n") { msg ->
            (if (msg.fromUser) "User" else "Asisten") + ": " + msg.text
        }

        return """
            Kamu adalah asisten chat yang ramah di dalam aplikasi MediaLog (aplikasi catatan anime/film/series/buku).
            Jawab dengan bahasa Indonesia santai dan singkat. Gunakan konteks library di bawah jika relevan dengan pertanyaan user.
            Kalau user tanya rekomendasi, sarankan judul yang TIDAK ada di library mereka.

            KONTEKS LIBRARY:
            $libraryContext

            RIWAYAT PERCAKAPAN (akhiri dengan balasan untuk pesan "User" terakhir):
            $historyText
            Asisten:
        """.trimIndent()
    }

    private fun loadHistory() {
        try {
            val raw = prefs.getString("messages", null) ?: return
            val arr = JSONArray(raw)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ChatMessage(
                        id = obj.getLong("id"),
                        text = obj.getString("text"),
                        fromUser = obj.getBoolean("fromUser")
                    )
                )
            }
            if (list.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(messages = list)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load chat history", e)
        }
    }

    private fun saveHistory(messages: List<ChatMessage>) {
        try {
            val arr = JSONArray()
            messages.takeLast(50).forEach { m ->
                arr.put(JSONObject().apply {
                    put("id", m.id)
                    put("text", m.text)
                    put("fromUser", m.fromUser)
                })
            }
            prefs.edit().putString("messages", arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save chat history", e)
        }
    }
}
