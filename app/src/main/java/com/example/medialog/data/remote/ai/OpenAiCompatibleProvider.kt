package com.example.medialog.data.remote.ai

import com.example.medialog.data.local.entity.AiFormatType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

class OpenAiCompatibleProvider @Inject constructor(
    private val client: OkHttpClient
) : AiProvider {

    override val formatType: AiFormatType = AiFormatType.OPENAI_COMPATIBLE
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun listModels(apiKey: String, baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        val effectiveBase = baseUrl.ifBlank { "https://api.openai.com/v1" }.trimEnd('/')
        val url = "$effectiveBase/models"

        val requestBuilder = Request.Builder().url(url).get()
        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return@withContext listOf("gpt-4o-mini", "gpt-4o")
            val bodyStr = response.body?.string() ?: return@withContext listOf("gpt-4o-mini", "gpt-4o")
            val jsonObj = json.parseToJsonElement(bodyStr).jsonObject
            val dataArray = jsonObj["data"]?.jsonArray ?: return@withContext listOf("gpt-4o-mini", "gpt-4o")
            val list = dataArray.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
            if (list.isEmpty()) listOf("gpt-4o-mini", "gpt-4o") else list
        }
    }

    override suspend fun generate(apiKey: String, baseUrl: String, model: String, prompt: String): AiResult = withContext(Dispatchers.IO) {
        val effectiveBase = baseUrl.ifBlank { "https://api.openai.com/v1" }.trimEnd('/')
        val url = "$effectiveBase/chat/completions"
        val targetModel = model.ifBlank { "gpt-4o-mini" }

        val requestBodyJson = buildJsonObject {
            put("model", targetModel)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            }
        }.toString()

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "HTTP ${response.code}"
                throw Exception("OpenAI Compatible API Error (${response.code}): $errBody")
            }
            val bodyStr = response.body?.string() ?: throw Exception("Empty response from OpenAI Compatible provider")
            val jsonObj = json.parseToJsonElement(bodyStr).jsonObject
            val choices = jsonObj["choices"]?.jsonArray
            val firstChoice = choices?.firstOrNull()?.jsonObject
            val message = firstChoice?.get("message")?.jsonObject
            val content = message?.get("content")?.jsonPrimitive?.content ?: ""

            val usage = jsonObj["usage"]?.jsonObject
            val inputTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.content?.toIntOrNull()
            val outputTokens = usage?.get("completion_tokens")?.jsonPrimitive?.content?.toIntOrNull()

            AiResult(content = content, inputTokens = inputTokens, outputTokens = outputTokens)
        }
    }
}
