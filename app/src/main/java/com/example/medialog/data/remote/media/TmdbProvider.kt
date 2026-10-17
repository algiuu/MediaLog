package com.example.medialog.data.remote.media

import com.example.medialog.data.local.entity.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import android.util.Log
import com.example.medialog.data.security.SecureKeyManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject

private const val TAG = "TmdbProvider"

class TmdbProvider @Inject constructor(
    private val client: OkHttpClient,
    private val secureKeyManager: SecureKeyManager,
    override val type: MediaType
) : MediaProvider {

    override val sourceName: String = "tmdb"
    private val json = Json { ignoreUnknownKeys = true }

    private fun getApiKey(): String = secureKeyManager.getApiKey("tmdb") ?: ""

    override suspend fun search(query: String): List<MediaSearchResult> = withContext(Dispatchers.IO) {
        val endpoint = if (type == MediaType.MOVIE) "search/movie" else "search/tv"
        val url = "https://api.themoviedb.org/3/$endpoint?api_key=${getApiKey()}&query=${URLEncoder.encode(query, "UTF-8")}"

        Log.d(TAG, "Searching TMDB: $url")
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            Log.d(TAG, "TMDB response code: ${response.code}")
            if (!response.isSuccessful) {
                Log.e(TAG, "TMDB search failed: ${response.code} - ${response.body?.string()}")
                return@withContext emptyList()
            }
            val bodyStr = response.body?.string() ?: return@withContext emptyList()
            val jsonObj = json.parseToJsonElement(bodyStr).jsonObject
            val resultsArray = jsonObj["results"]?.jsonArray ?: return@withContext emptyList()
            Log.d(TAG, "TMDB search returned ${resultsArray.size} results")

            return@withContext resultsArray.mapNotNull { item ->
                val obj = item.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val title = if (type == MediaType.MOVIE) {
                    obj["title"]?.jsonPrimitive?.content
                } else {
                    obj["name"]?.jsonPrimitive?.content
                } ?: "Unknown Title"

                val originalTitle = if (type == MediaType.MOVIE) {
                    obj["original_title"]?.jsonPrimitive?.content
                } else {
                    obj["original_name"]?.jsonPrimitive?.content
                }

                val posterPath = obj["poster_path"]?.jsonPrimitive?.content
                val posterUrl = if (posterPath != null) "https://image.tmdb.org/t/p/w500$posterPath" else null
                val overview = obj["overview"]?.jsonPrimitive?.content ?: ""

                val releaseDate = if (type == MediaType.MOVIE) {
                    obj["release_date"]?.jsonPrimitive?.content
                } else {
                    obj["first_air_date"]?.jsonPrimitive?.content
                }
                val year = releaseDate?.take(4)?.toIntOrNull()

                MediaSearchResult(
                    title = title,
                    originalTitle = originalTitle,
                    type = type,
                    source = sourceName,
                    externalId = "${type.name.lowercase()}_$id",
                    posterUrl = posterUrl,
                    releaseYear = year,
                    synopsis = overview,
                    unitLabel = if (type == MediaType.MOVIE) "minutes" else "episodes"
                )
            }
        }
    }

    override suspend fun getDetail(externalId: String): MediaSearchResult? = withContext(Dispatchers.IO) {
        val rawId = externalId.substringAfter("_")
        val endpoint = if (type == MediaType.MOVIE) "movie/$rawId" else "tv/$rawId"
        val url = "https://api.themoviedb.org/3/$endpoint?api_key=${getApiKey()}&append_to_response=watch/providers"

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val bodyStr = response.body?.string() ?: return@withContext null
            val obj = json.parseToJsonElement(bodyStr).jsonObject

            val title = if (type == MediaType.MOVIE) {
                obj["title"]?.jsonPrimitive?.content
            } else {
                obj["name"]?.jsonPrimitive?.content
            } ?: "Unknown Title"

            val originalTitle = if (type == MediaType.MOVIE) {
                obj["original_title"]?.jsonPrimitive?.content
            } else {
                obj["original_name"]?.jsonPrimitive?.content
            }

            val posterPath = obj["poster_path"]?.jsonPrimitive?.content
            val posterUrl = if (posterPath != null) "https://image.tmdb.org/t/p/w500$posterPath" else null
            val overview = obj["overview"]?.jsonPrimitive?.content ?: ""

            val releaseDate = if (type == MediaType.MOVIE) {
                obj["release_date"]?.jsonPrimitive?.content
            } else {
                obj["first_air_date"]?.jsonPrimitive?.content
            }
            val year = releaseDate?.take(4)?.toIntOrNull()

            val genres = obj["genres"]?.jsonArray?.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.content
            } ?: emptyList()

            val totalUnits = if (type == MediaType.MOVIE) {
                obj["runtime"]?.jsonPrimitive?.content?.toIntOrNull()
            } else {
                obj["number_of_episodes"]?.jsonPrimitive?.content?.toIntOrNull()
            }

            // Parse watch providers (region ID)
            val watchProviders = parseWatchProviders(obj["watch/providers"]?.jsonObject)

            MediaSearchResult(
                title = title,
                originalTitle = originalTitle,
                type = type,
                source = sourceName,
                externalId = externalId,
                posterUrl = posterUrl,
                releaseYear = year,
                synopsis = overview,
                genres = genres,
                totalUnits = totalUnits,
                unitLabel = if (type == MediaType.MOVIE) "minutes" else "episodes",
                watchProviders = watchProviders
            )
        }
    }

    private fun parseWatchProviders(watchProvidersObj: JsonObject?): List<WatchProvider> {
        if (watchProvidersObj == null) return emptyList()
        val results = watchProvidersObj["results"]?.jsonObject ?: return emptyList()
        val idRegion = results["ID"]?.jsonObject ?: return emptyList()
        val flatrate = idRegion["flatrate"]?.jsonArray ?: return emptyList()
        return flatrate.mapNotNull { item ->
            val obj = item.jsonObject
            val providerId = obj["provider_id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
            val providerName = obj["provider_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val logoPath = obj["logo_path"]?.jsonPrimitive?.content
            WatchProvider(providerId, providerName, logoPath)
        }
    }
}
