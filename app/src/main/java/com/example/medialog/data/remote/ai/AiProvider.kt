package com.example.medialog.data.remote.ai

import com.example.medialog.data.local.entity.AiFormatType

interface AiProvider {
    val formatType: AiFormatType
    suspend fun listModels(apiKey: String, baseUrl: String): List<String>
    suspend fun generate(apiKey: String, baseUrl: String, model: String, prompt: String): AiResult
}
