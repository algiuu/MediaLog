package com.example.medialog.data.repository

import com.example.medialog.data.local.dao.AiConfigDao
import com.example.medialog.data.local.entity.AiFormatType
import com.example.medialog.data.local.entity.AiProviderConfigEntity
import com.example.medialog.data.remote.ai.AiProvider
import com.example.medialog.data.remote.ai.ClaudeProvider
import com.example.medialog.data.remote.ai.GeminiProvider
import com.example.medialog.data.remote.ai.OpenAiCompatibleProvider
import com.example.medialog.data.security.SecureKeyManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiSettingsRepository @Inject constructor(
    private val aiConfigDao: AiConfigDao,
    private val secureKeyManager: SecureKeyManager,
    private val geminiProvider: GeminiProvider,
    private val openAiCompatibleProvider: OpenAiCompatibleProvider,
    private val claudeProvider: ClaudeProvider
) {
    val activeConfigFlow: Flow<AiProviderConfigEntity?> = aiConfigDao.getActiveConfigFlow()
    val allConfigsFlow: Flow<List<AiProviderConfigEntity>> = aiConfigDao.getAllConfigsFlow()

    suspend fun getActiveConfig(): AiProviderConfigEntity? = aiConfigDao.getActiveConfig()

    fun getApiKey(providerId: String): String? {
        return secureKeyManager.getApiKey(providerId)
    }

    suspend fun saveConfig(
        id: String,
        displayName: String,
        formatType: AiFormatType,
        baseUrl: String,
        selectedModel: String?,
        apiKey: String,
        isActive: Boolean = true
    ) {
        val config = AiProviderConfigEntity(
            id = id,
            displayName = displayName,
            formatType = formatType,
            baseUrl = baseUrl,
            selectedModel = selectedModel,
            isActive = isActive
        )
        aiConfigDao.insertOrUpdate(config)
        if (isActive) {
            aiConfigDao.setActiveProvider(id)
        }
        if (apiKey.isNotBlank()) {
            secureKeyManager.saveApiKey(id, apiKey)
        }
    }

    fun getProviderForFormat(formatType: AiFormatType): AiProvider {
        return when (formatType) {
            AiFormatType.GEMINI -> geminiProvider
            AiFormatType.OPENAI_COMPATIBLE -> openAiCompatibleProvider
            AiFormatType.CLAUDE -> claudeProvider
        }
    }

    suspend fun fetchModelsForConfig(config: AiProviderConfigEntity, apiKey: String): List<String> {
        val provider = getProviderForFormat(config.formatType)
        return provider.listModels(apiKey, config.baseUrl)
    }

    suspend fun testConnection(config: AiProviderConfigEntity, apiKey: String, model: String): Boolean {
        return try {
            val provider = getProviderForFormat(config.formatType)
            val result = provider.generate(
                apiKey = apiKey,
                baseUrl = config.baseUrl,
                model = model,
                prompt = "Respond with JSON: {\"status\": \"ok\"}"
            )
            result.content.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }
}
