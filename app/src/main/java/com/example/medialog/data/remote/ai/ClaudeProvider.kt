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

class ClaudeProvider @Inject constructor(
    private val client: OkHttpClient
) : AiProvider {

    override val formatType: AiFormatType = AiFormatType.CLAUDE
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun listModels(apiKey: String, baseUrl: String): List<String> {
        return listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229")
    }

    override suspend fun generate(apiKey: String, baseUrl: String, model: String, prompt: String): AiResult = withContext(Dispatchers.IO) {
        val effectiveBase = baseUrl.ifBlank { "https://api.anthropic.com/v1" }.trimEnd('/')
        val url = "$effectiveBase/messages"
        val targetModel = model.ifBlank { "claude-3-5-sonnet-20241022" }

        val requestBodyJson = buildJsonObject {
            put("model", targetModel)
            put("max_tokens", 1024)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            }
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "HTTP ${response.code}"
                throw Exception("Claude API Error (${response.code}): $errBody")
            }
            val bodyStr = response.body?.string() ?: throw Exception("Empty response from Claude")
            val jsonObj = json.parseToJsonElement(bodyStr).jsonObject
            val contentArray = jsonObj["content"]?.jsonArray
            val firstContent = contentArray?.firstOrNull()?.jsonObject
            val text = firstContent?.get("text")?.jsonPrimitive?.content ?: ""

            val usage = jsonObj["usage"]?.jsonObject
            val inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.content?.toIntOrNull()
            val outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.content?.toIntOrNull()

            AiResult(content = text, inputTokens = inputTokens, outputTokens = outputTokens)
        }
    }
}
