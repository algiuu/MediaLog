package com.example.medialog.data.remote.media

import com.example.medialog.data.local.entity.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject

private const val TAG = "OpenLibraryProvider"

class OpenLibraryProvider @Inject constructor(
    private val client: OkHttpClient
) : MediaProvider {

    override val type: MediaType = MediaType.BOOK
    override val sourceName: String = "openlibrary"
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String): List<MediaSearchResult> = withContext(Dispatchers.IO) {
        val url = "https://openlibrary.org/search.json?q=${URLEncoder.encode(query, "UTF-8")}&limit=15"

        Log.d(TAG, "Searching Open Library for: $query")
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            Log.d(TAG, "OpenLibrary response code: ${response.code}")
            if (!response.isSuccessful) {
                Log.e(TAG, "OpenLibrary search failed: ${response.code} - ${response.body?.string()}")
                return@withContext emptyList()
            }
            val bodyStr = response.body?.string() ?: return@withContext emptyList()
            val jsonObj = json.parseToJsonElement(bodyStr).jsonObject
            val docsArray = jsonObj["docs"]?.jsonArray ?: return@withContext emptyList()
            Log.d(TAG, "OpenLibrary search returned ${docsArray.size} results")

            return@withContext docsArray.mapNotNull { item ->
                val obj = item.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val key = obj["key"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val workId = key.substringAfterLast("/")

                val authorNames = obj["author_name"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }
                val author = authorNames?.firstOrNull()

                val firstPublishYear = obj["first_publish_year"]?.jsonPrimitive?.content?.toIntOrNull()

                val coverI = obj["cover_i"]?.jsonPrimitive?.content
                val posterUrl = if (coverI != null) "https://covers.openlibrary.org/b/id/$coverI-L.jpg" else null

                val numberOfPages = obj["number_of_pages_median"]?.jsonPrimitive?.content?.toIntOrNull()

                MediaSearchResult(
                    title = title,
                    type = MediaType.BOOK,
                    source = sourceName,
                    externalId = workId,
                    posterUrl = posterUrl,
                    releaseYear = firstPublishYear,
                    creator = author,
                    totalUnits = numberOfPages,
                    unitLabel = "pages"
                )
            }
        }
    }

    override suspend fun getDetail(externalId: String): MediaSearchResult? = withContext(Dispatchers.IO) {
        val url = "https://openlibrary.org/works/$externalId.json"

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val bodyStr = response.body?.string() ?: return@withContext null
            val obj = json.parseToJsonElement(bodyStr).jsonObject

            val title = obj["title"]?.jsonPrimitive?.content ?: "Unknown Book"
            val descriptionObj = obj["description"]
            val synopsis = when {
                descriptionObj == null -> null
                descriptionObj is JsonPrimitive -> descriptionObj.content
                descriptionObj is JsonObject -> descriptionObj["value"]?.jsonPrimitive?.content
                else -> null
            }

            val coversArray = obj["covers"]?.jsonArray
            val coverId = coversArray?.firstOrNull()?.jsonPrimitive?.content
            val posterUrl = if (coverId != null) "https://covers.openlibrary.org/b/id/$coverId-L.jpg" else null

            val subjects = obj["subjects"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }?.take(5) ?: emptyList()

            MediaSearchResult(
                title = title,
                type = MediaType.BOOK,
                source = sourceName,
                externalId = externalId,
                posterUrl = posterUrl,
                synopsis = synopsis,
                genres = subjects,
                unitLabel = "pages"
            )
        }
    }
}
