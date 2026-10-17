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
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

class GeminiProvider @Inject constructor(
    private val client: OkHttpClient
) : AiProvider {

    override val formatType: AiFormatType = AiFormatType.GEMINI
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun listModels(apiKey: String, baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        val url = if (baseUrl.isBlank()) "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey" else "$baseUrl/models?key=$apiKey"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext listOf("gemini-1.5-pro", "gemini-1.5-flash", "gemini-2.0-flash")
            val bodyStr = response.body?.string() ?: return@withContext listOf("gemini-1.5-pro", "gemini-1.5-flash", "gemini-2.0-flash")
            val jsonObj = json.parseToJsonElement(bodyStr).jsonObject
            val modelsArray = jsonObj["models"]?.jsonArray ?: return@withContext listOf("gemini-1.5-pro", "gemini-1.5-flash")
            val list = modelsArray.mapNotNull {
                val name = it.jsonObject["name"]?.jsonPrimitive?.content
                name?.removePrefix("models/")
            }
            if (list.isEmpty()) listOf("gemini-1.5-pro", "gemini-1.5-flash", "gemini-2.0-flash") else list
        }
    }

    override suspend fun generate(apiKey: String, baseUrl: String, model: String, prompt: String): AiResult = withContext(Dispatchers.IO) {
        val targetModel = model.ifBlank { "gemini-1.5-flash" }
        val effectiveBase = if (baseUrl.isBlank()) "https://generativelanguage.googleapis.com/v1beta" else baseUrl
        val url = "$effectiveBase/models/$targetModel:generateContent?key=$apiKey"

        val requestBodyJson = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    putJsonArray("parts") {
                        add(buildJsonObject {
                            put("text", prompt)
                        })
                    }
                })
            }
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "HTTP ${response.code}"
                throw Exception("Gemini API Error (${response.code}): $errBody")
            }
            val bodyStr = response.body?.string() ?: throw Exception("Empty response from Gemini")
            val jsonObj = json.parseToJsonElement(bodyStr).jsonObject
            val candidates = jsonObj["candidates"]?.jsonArray
            val firstCandidate = candidates?.firstOrNull()?.jsonObject
            val parts = firstCandidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
            val text = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""

            val usageMetadata = jsonObj["usageMetadata"]?.jsonObject
            val inputTokens = usageMetadata?.get("promptTokenCount")?.jsonPrimitive?.content?.toIntOrNull()
            val outputTokens = usageMetadata?.get("candidatesTokenCount")?.jsonPrimitive?.content?.toIntOrNull()

            AiResult(content = text, inputTokens = inputTokens, outputTokens = outputTokens)
        }
    }
}
