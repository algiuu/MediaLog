package com.example.medialog.data.repository

import com.example.medialog.data.local.dao.MediaEntryDao
import com.example.medialog.data.local.dao.RecommendationDao
import com.example.medialog.data.local.entity.EntryStatus
import com.example.medialog.data.local.entity.MediaType
import com.example.medialog.data.local.entity.RecommendationBatchEntity
import com.example.medialog.data.local.entity.RecommendationItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationRepository @Inject constructor(
    private val recommendationDao: RecommendationDao,
    private val mediaEntryDao: MediaEntryDao,
    private val mediaRepository: MediaRepository,
    private val aiSettingsRepository: AiSettingsRepository
) {
    fun getLatestBatchFlow(): Flow<RecommendationBatchEntity?> = recommendationDao.getLatestBatchFlow()

    fun getItemsForBatchFlow(batchId: Long): Flow<List<RecommendationItemEntity>> =
        recommendationDao.getItemsForBatchFlow(batchId)

    suspend fun getLatestBatch(): RecommendationBatchEntity? = recommendationDao.getLatestBatch()

    suspend fun hideItem(itemId: Long) = recommendationDao.hideItem(itemId)

    suspend fun markItemAdded(itemId: Long) = recommendationDao.markItemAdded(itemId)

    suspend fun generateRecommendations(
        typeFilter: String? = null,
        moodPrompt: String? = null
    ): Result<Unit> {
        val config = aiSettingsRepository.getActiveConfig()
            ?: return Result.failure(Exception("No active AI provider configured"))

        val apiKey = aiSettingsRepository.getApiKey(config.id)
        if (apiKey.isNullOrBlank()) {
            return Result.failure(Exception("API Key for ${config.displayName} is missing"))
        }

        val allEntries = mediaEntryDao.getAllEntries()
        val topRated = allEntries.filter { (it.myRating ?: 0) >= 7 }.take(15)
        val dropped = allEntries.filter { it.status == EntryStatus.DROPPED }
        val noted = allEntries.filter { !it.notes.isNullOrBlank() }.take(15)

        val summaryText = buildString {
            append("User's High Rated Items: ")
            append(topRated.joinToString(", ") { "${it.title} (${it.type.name}, ${it.myRating}/10)" })
            if (noted.isNotEmpty()) {
                append("\nUser's own notes/reviews (use to match their taste): ")
                append(noted.joinToString("; ") { "${it.title}: \"${it.notes}\"" })
            }
            if (dropped.isNotEmpty()) {
                append("\nDropped Items (DO NOT recommend similar): ")
                append(dropped.joinToString(", ") { "${it.title} (${it.type.name})" })
            }
            if (!moodPrompt.isNullOrBlank()) {
                append("\nCurrent Mood/Preference: $moodPrompt")
            }
            if (!typeFilter.isNullOrBlank()) {
                append("\nTarget Media Type: $typeFilter")
            }
        }

        val systemPrompt = """
            You are a personalized recommendation engine for MediaLog.
            Based on the user's list and mood, recommend 15 unique titles (ANIME, MOVIE, SERIES, or BOOK).
            Do NOT recommend titles the user already has in their list.
            Respond strictly in valid JSON format with a JSON array of objects:
            [
              {
                "title": "Exact Title Name",
                "type": "ANIME|MOVIE|SERIES|BOOK",
                "reason": "Short one-sentence reason why the user will enjoy this."
              }
            ]
            JSON ONLY. No markdown ticks, no extra text.
            
            $summaryText
        """.trimIndent()

        val provider = aiSettingsRepository.getProviderForFormat(config.formatType)
        val model = config.selectedModel ?: ""

        val aiResult = provider.generate(
            apiKey = apiKey,
            baseUrl = config.baseUrl,
            model = model,
            prompt = systemPrompt
        )

        val cleanJsonText = cleanJsonResponse(aiResult.content)
        val items = parseAndValidateRecommendations(cleanJsonText)

        if (items.isEmpty()) {
            return Result.failure(Exception("Failed to generate valid recommendations. Please try again."))
        }

        val batch = RecommendationBatchEntity(
            providerName = config.displayName,
            model = model.ifBlank { "default" },
            typeFilter = typeFilter,
            moodPrompt = moodPrompt,
            inputTokens = aiResult.inputTokens,
            outputTokens = aiResult.outputTokens
        )

        recommendationDao.saveRecommendation(batch, items)
        return Result.success(Unit)
    }

    private fun cleanJsonResponse(rawResponse: String): String {
        var clean = rawResponse.trim()
        if (clean.startsWith("```json")) {
            clean = clean.substringAfter("```json").substringBeforeLast("```").trim()
        } else if (clean.startsWith("```")) {
            clean = clean.substringAfter("```").substringBeforeLast("```").trim()
        }
        val firstBracket = clean.indexOf('[')
        val lastBracket = clean.lastIndexOf(']')
        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            clean = clean.substring(firstBracket, lastBracket + 1)
        }
        return clean
    }

    private suspend fun parseAndValidateRecommendations(jsonText: String): List<RecommendationItemEntity> {
        val json = Json { ignoreUnknownKeys = true }
        val resultsList = mutableListOf<RecommendationItemEntity>()

        val parsedArray = try {
            json.parseToJsonElement(jsonText).jsonArray
        } catch (_: Exception) {
            return emptyList()
        }

        for (elem in parsedArray) {
            val obj = elem.jsonObject
            val title = obj["title"]?.jsonPrimitive?.content ?: continue
            val typeStr = obj["type"]?.jsonPrimitive?.content ?: continue
            val reason = obj["reason"]?.jsonPrimitive?.content ?: "Recommended based on your preferences."

            val type = try { MediaType.valueOf(typeStr.uppercase()) } catch (_: Exception) { MediaType.ANIME }

            val searchResult = try {
                val searchList = mediaRepository.searchRemote(type, title)
                searchList.firstOrNull()
            } catch (_: Exception) {
                null
            }

            if (searchResult != null) {
                resultsList.add(
                    RecommendationItemEntity(
                        batchId = 0,
                        type = type,
                        title = searchResult.title,
                        reason = reason,
                        source = searchResult.source,
                        externalId = searchResult.externalId,
                        posterUrl = searchResult.posterUrl
                    )
                )
            } else {
                resultsList.add(
                    RecommendationItemEntity(
                        batchId = 0,
                        type = type,
                        title = title,
                        reason = reason
                    )
                )
            }
        }
        return resultsList
    }
}
