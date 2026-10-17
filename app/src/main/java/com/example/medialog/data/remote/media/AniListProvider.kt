package com.example.medialog.data.remote.media

import com.example.medialog.data.local.entity.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

private const val TAG = "AniListProvider"

class AniListProvider @Inject constructor(
    private val client: OkHttpClient
) : MediaProvider {

    override val type: MediaType = MediaType.ANIME
    override val sourceName: String = "anilist"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String): List<MediaSearchResult> = withContext(Dispatchers.IO) {
        val graphql = """
            query (${'$'}search: String) {
              Page(page: 1, perPage: 15) {
                media(search: ${'$'}search, type: ANIME, sort: POPULARITY_DESC) {
                  id
                  title { romaji english native }
                  coverImage { extraLarge large }
                  description
                  startDate { year }
                  episodes
                  genres
                  studios(isMain: true) { nodes { name } }
                  nextAiringEpisode { episode airingAt timeUntilAiring }
                }
              }
            }
        """.trimIndent()

        val payload = buildGraphqlBody(graphql, mapOf("search" to query))
        Log.d(TAG, "Searching AniList for: $query")
        val results = sendRequest(payload)
        Log.d(TAG, "AniList search returned ${results.size} results")
        results
    }

    override suspend fun getDetail(externalId: String): MediaSearchResult? = withContext(Dispatchers.IO) {
        val idInt = externalId.toIntOrNull() ?: return@withContext null
        val graphql = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                id
                title { romaji english native }
                coverImage { extraLarge large }
                description
                startDate { year }
                episodes
                genres
                studios(isMain: true) { nodes { name } }
                nextAiringEpisode { episode airingAt timeUntilAiring }
              }
            }
        """.trimIndent()

        val payload = buildGraphqlBody(graphql, mapOf("id" to idInt))
        sendRequestForSingleMedia(payload)
    }

    suspend fun getSeasonalAnime(season: String, year: Int): List<MediaSearchResult> = withContext(Dispatchers.IO) {
        val graphql = """
            query (${'$'}season: MediaSeason, ${'$'}year: Int) {
              Page(page: 1, perPage: 20) {
                media(season: ${'$'}season, seasonYear: ${'$'}year, type: ANIME, sort: POPULARITY_DESC) {
                  id
                  title { romaji english native }
                  coverImage { extraLarge large }
                  description
                  startDate { year }
                  episodes
                  genres
                  studios(isMain: true) { nodes { name } }
                }
              }
            }
        """.trimIndent()

        val payload = buildGraphqlBody(graphql, mapOf("season" to season.uppercase(), "year" to year))
        sendRequest(payload)
    }

    suspend fun getAiringSchedule(start: Long, end: Long): List<AiringItem> = withContext(Dispatchers.IO) {
        val allItems = mutableListOf<AiringItem>()
        var page = 1
        var hasNext = true

        while (hasNext) {
            val graphql = """
                query (${'$'}start: Int, ${'$'}end: Int, ${'$'}page: Int) {
                  Page(page: ${'$'}page, perPage: 50) {
                    pageInfo { hasNextPage }
                    airingSchedules(airingAt_greater: ${'$'}start, airingAt_lesser: ${'$'}end, sort: TIME) {
                      episode
                      airingAt
                      media {
                        id
                        title { romaji english native }
                        coverImage { extraLarge large }
                      }
                    }
                  }
                }
            """.trimIndent()

            val payload = buildGraphqlBody(graphql, mapOf("start" to start, "end" to end, "page" to page))

            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val result = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Airing schedule request failed: ${response.code}")
                    return@use null
                }
                val bodyStr = response.body?.string() ?: return@use null
                val jsonObj = json.parseToJsonElement(bodyStr).jsonObject
                val pageObj = jsonObj["data"]?.jsonObject?.get("Page")?.jsonObject ?: return@use null
                val schedules = pageObj["airingSchedules"]?.jsonArray ?: return@use null
                val hasNextPage = pageObj["pageInfo"]?.jsonObject
                    ?.get("hasNextPage")?.jsonPrimitive?.content == "true"

                val items = schedules.mapNotNull { sched ->
                    val mediaObj = sched.jsonObject["media"]?.jsonObject ?: return@mapNotNull null
                    val titleObj = mediaObj["title"]?.jsonObject
                    val title = listOf("english", "romaji", "native")
                        .mapNotNull { key -> titleObj?.get(key) }
                        .filterNot { it is JsonNull }
                        .mapNotNull { it.jsonPrimitive.content }
                        .firstOrNull() ?: return@mapNotNull null
                    val coverObj = mediaObj["coverImage"] as? JsonObject
                    val posterUrl = coverObj?.get("extraLarge")?.jsonPrimitive?.content
                        ?: coverObj?.get("large")?.jsonPrimitive?.content
                    AiringItem(
                        mediaId = mediaObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        title = title,
                        episode = sched.jsonObject["episode"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        airingAt = sched.jsonObject["airingAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        posterUrl = posterUrl
                    )
                }
                Pair(items, hasNextPage)
            }

            if (result != null) {
                allItems.addAll(result.first)
                hasNext = result.second
                page++
            } else {
                hasNext = false
            }

            if (page > 5) break // Safety: max 250 anime
        }

        allItems
    }

    private fun buildGraphqlBody(query: String, variables: Map<String, Any?>): String {
        val varsJson = variables.entries.joinToString(",") { (k, v) ->
            when (v) {
                is Number -> "\"$k\": $v"
                else -> "\"$k\": \"$v\""
            }
        }
        return "{\"query\": ${json.encodeToString(serializer(), query)}, \"variables\": {$varsJson}}"
    }

    private fun sendRequest(jsonPayload: String): List<MediaSearchResult> {
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            Log.d(TAG, "AniList response code: ${response.code}")
            if (!response.isSuccessful) {
                Log.e(TAG, "AniList request failed: ${response.code} - ${response.body?.string()}")
                return emptyList()
            }
            val bodyStr = response.body?.string() ?: return emptyList()
            val jsonObj = json.parseToJsonElement(bodyStr).jsonObject
            val mediaArray = jsonObj["data"]?.jsonObject
                ?.get("Page")?.jsonObject
                ?.get("media")?.jsonArray ?: return emptyList()

            return mediaArray.mapNotNull { parseMediaItem(it.jsonObject) }
        }
    }

    private fun sendRequestForSingleMedia(jsonPayload: String): MediaSearchResult? {
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyStr = response.body?.string() ?: return null
            val jsonObj = json.parseToJsonElement(bodyStr).jsonObject
            val mediaObj = jsonObj["data"]?.jsonObject?.get("Media")?.jsonObject ?: return null
            return parseMediaItem(mediaObj)
        }
    }

    private fun parseMediaItem(obj: JsonObject): MediaSearchResult? {
        val id = obj["id"]?.jsonPrimitive?.content ?: return null
        val titleObj = obj["title"]?.jsonObject
        val romaji = titleObj?.get("romaji")?.jsonPrimitive?.content
        val english = titleObj?.get("english")?.jsonPrimitive?.content
        val native = titleObj?.get("native")?.jsonPrimitive?.content
        val title = english ?: romaji ?: native ?: "Unknown Anime"

        val coverImageObj = obj["coverImage"] as? JsonObject
        val posterUrl = coverImageObj?.get("extraLarge")?.jsonPrimitive?.content
            ?: coverImageObj?.get("large")?.jsonPrimitive?.content

        val rawDescription = obj["description"]?.jsonPrimitive?.content ?: ""
        val synopsis = rawDescription.replace(Regex("<[^>]*>"), "")

        val year = (obj["startDate"] as? JsonObject)?.get("year")?.jsonPrimitive?.content?.toIntOrNull()
        val episodes = obj["episodes"]?.jsonPrimitive?.content?.toIntOrNull()

        val genres = (obj["genres"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()

        val studios = (obj["studios"] as? JsonObject)?.get("nodes")?.jsonArray?.firstOrNull()?.jsonObject
        val studioName = studios?.get("name")?.jsonPrimitive?.content

        val nextAiringEpisode = obj["nextAiringEpisode"] as? JsonObject
        val nextEpisode = nextAiringEpisode?.get("episode")?.jsonPrimitive?.content?.toIntOrNull()
        val nextEpisodeAiringAt = nextAiringEpisode?.get("airingAt")?.jsonPrimitive?.content?.toLongOrNull()

        return MediaSearchResult(
            title = title,
            originalTitle = native ?: romaji,
            type = MediaType.ANIME,
            source = sourceName,
            externalId = id,
            posterUrl = posterUrl,
            releaseYear = year,
            creator = studioName,
            synopsis = synopsis,
            genres = genres,
            totalUnits = episodes,
            unitLabel = "episodes",
            nextEpisode = nextEpisode,
            nextEpisodeAiringAt = nextEpisodeAiringAt
        )
    }
}
