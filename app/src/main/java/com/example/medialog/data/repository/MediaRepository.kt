package com.example.medialog.data.repository

import com.example.medialog.data.local.dao.GenreCount
import com.example.medialog.data.local.dao.GenreDao
import com.example.medialog.data.local.dao.MediaEntryDao
import com.example.medialog.data.local.dao.TagDao
import com.example.medialog.data.local.entity.EntryGenreCrossRef
import com.example.medialog.data.local.entity.EntryStatus
import com.example.medialog.data.local.entity.EntryTagCrossRef
import com.example.medialog.data.local.entity.GenreEntity
import com.example.medialog.data.local.entity.MediaEntryEntity
import com.example.medialog.data.local.entity.MediaType
import com.example.medialog.data.local.entity.TagEntity
import com.example.medialog.data.remote.media.AniListProvider
import com.example.medialog.data.remote.media.MediaSearchResult
import com.example.medialog.data.remote.media.OpenLibraryProvider
import com.example.medialog.data.remote.media.TmdbProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val mediaEntryDao: MediaEntryDao,
    private val genreDao: GenreDao,
    private val tagDao: TagDao,
    private val aniListProvider: AniListProvider,
    private val openLibraryProvider: OpenLibraryProvider,
    private val secureKeyManager: com.example.medialog.data.security.SecureKeyManager,
    okHttpClient: OkHttpClient
) {
    private val tmdbMovieProvider = TmdbProvider(okHttpClient, secureKeyManager, MediaType.MOVIE)
    private val tmdbSeriesProvider = TmdbProvider(okHttpClient, secureKeyManager, MediaType.SERIES)

    fun getAllEntriesFlow(): Flow<List<MediaEntryEntity>> = mediaEntryDao.getAllEntriesFlow()

    fun getEntriesByTypeFlow(type: MediaType): Flow<List<MediaEntryEntity>> =
        mediaEntryDao.getEntriesByTypeFlow(type)

    suspend fun getEntryById(id: Long): MediaEntryEntity? = mediaEntryDao.getById(id)

    suspend fun getEntryByExternalId(source: String, externalId: String): MediaEntryEntity? =
        mediaEntryDao.getBySourceAndExternalId(source, externalId)

    suspend fun saveEntry(
        entry: MediaEntryEntity,
        genres: List<String> = emptyList(),
        tags: List<String> = emptyList()
    ): Long {
        val entryId = if (entry.id == 0L) {
            mediaEntryDao.insert(entry)
        } else {
            mediaEntryDao.update(entry)
            entry.id
        }

        if (genres.isNotEmpty()) {
            genreDao.deleteCrossRefsForEntry(entryId)
            for (genreName in genres) {
                var genre = genreDao.getGenreByName(genreName)
                if (genre == null) {
                    val newId = genreDao.insertGenre(GenreEntity(name = genreName))
                    genre = GenreEntity(id = newId, name = genreName)
                }
                genreDao.insertCrossRef(EntryGenreCrossRef(entryId = entryId, genreId = genre.id))
            }
        }

        if (tags.isNotEmpty()) {
            tagDao.deleteCrossRefsForEntry(entryId)
            for (tagName in tags) {
                var tag = tagDao.getTagByName(tagName)
                if (tag == null) {
                    val newId = tagDao.insertTag(TagEntity(name = tagName))
                    tag = TagEntity(id = newId, name = tagName)
                }
                tagDao.insertCrossRef(EntryTagCrossRef(entryId = entryId, tagId = tag.id))
            }
        }

        return entryId
    }

    suspend fun deleteEntry(id: Long) {
        genreDao.deleteCrossRefsForEntry(id)
        tagDao.deleteCrossRefsForEntry(id)
        mediaEntryDao.deleteById(id)
    }

    suspend fun getGenresForEntry(entryId: Long): List<String> {
        return genreDao.getGenresForEntry(entryId).map { it.name }
    }

    suspend fun getTagsForEntry(entryId: Long): List<String> {
        return tagDao.getTagsForEntry(entryId).map { it.name }
    }

    suspend fun searchRemote(type: MediaType, query: String): List<MediaSearchResult> {
        return when (type) {
            MediaType.ANIME -> aniListProvider.search(query)
            MediaType.MOVIE -> tmdbMovieProvider.search(query)
            MediaType.SERIES -> tmdbSeriesProvider.search(query)
            MediaType.BOOK -> openLibraryProvider.search(query)
        }
    }

    suspend fun getSeasonalAnime(season: String, year: Int): List<MediaSearchResult> {
        return aniListProvider.getSeasonalAnime(season, year)
    }

    suspend fun getRemoteDetail(type: MediaType, externalId: String?): MediaSearchResult? {
        if (externalId == null) return null
        return when (type) {
            MediaType.ANIME -> aniListProvider.getDetail(externalId)
            MediaType.MOVIE -> tmdbMovieProvider.getDetail(externalId)
            MediaType.SERIES -> tmdbSeriesProvider.getDetail(externalId)
            MediaType.BOOK -> openLibraryProvider.getDetail(externalId)
        }
    }

    suspend fun getAllEntries(): List<MediaEntryEntity> {
        return mediaEntryDao.getAllEntries()
    }

    fun getTopGenres(): Flow<List<GenreCount>> {
        return genreDao.getTopGenres(10)
    }

    suspend fun exportToJson(): String {
        val entries = mediaEntryDao.getAllEntries()
        val entryDetails = entries.map { e ->
            val genres = getGenresForEntry(e.id)
            val tags = getTagsForEntry(e.id)
            Triple(e, genres, tags)
        }

        val json = Json { prettyPrint = true }

        val root = buildJsonObject {
            put("version", 1)
            put("exported_at", System.currentTimeMillis())
            putJsonArray("entries") {
                for ((e, genres, tags) in entryDetails) {
                    addJsonObject {
                        put("type", e.type.name)
                        put("title", e.title)
                        put("original_title", e.originalTitle)
                        put("source", e.source)
                        put("external_id", e.externalId)
                        put("poster_url", e.posterUrl)
                        put("synopsis", e.synopsis)
                        put("release_year", e.releaseYear)
                        put("creator", e.creator)
                        put("total_units", e.totalUnits)
                        put("unit_label", e.unitLabel)
                        put("progress", e.progress)
                        put("status", e.status.name)
                        put("my_rating", e.myRating)
                        put("notes", e.notes)
                        putJsonArray("genres") { genres.forEach { add(JsonPrimitive(it)) } }
                        putJsonArray("tags") { tags.forEach { add(JsonPrimitive(it)) } }
                    }
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    suspend fun importFromJson(jsonString: String): Int {
        val json = Json { ignoreUnknownKeys = true }
        val rootObj = json.parseToJsonElement(jsonString).jsonObject
        val entriesArray = rootObj["entries"]?.jsonArray ?: return 0
        var count = 0

        for (item in entriesArray) {
            val obj = item.jsonObject
            val title = obj["title"]?.jsonPrimitive?.content ?: continue
            val typeStr = obj["type"]?.jsonPrimitive?.content ?: continue
            val type = try { MediaType.valueOf(typeStr) } catch (_: Exception) { continue }
            val statusStr = obj["status"]?.jsonPrimitive?.content ?: "PLAN"
            val status = try { EntryStatus.valueOf(statusStr) } catch (_: Exception) { EntryStatus.PLAN }

            val source = obj["source"]?.jsonPrimitive?.content
            val externalId = obj["external_id"]?.jsonPrimitive?.content

            if (source != null && externalId != null) {
                val existing = mediaEntryDao.getBySourceAndExternalId(source, externalId)
                if (existing != null) continue
            }

            val entity = MediaEntryEntity(
                title = title,
                type = type,
                originalTitle = obj["original_title"]?.jsonPrimitive?.content,
                source = source,
                externalId = externalId,
                posterUrl = obj["poster_url"]?.jsonPrimitive?.content,
                synopsis = obj["synopsis"]?.jsonPrimitive?.content,
                releaseYear = obj["release_year"]?.jsonPrimitive?.content?.toIntOrNull(),
                creator = obj["creator"]?.jsonPrimitive?.content,
                totalUnits = obj["total_units"]?.jsonPrimitive?.content?.toIntOrNull(),
                unitLabel = obj["unit_label"]?.jsonPrimitive?.content,
                progress = obj["progress"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                status = status,
                myRating = obj["my_rating"]?.jsonPrimitive?.content?.toIntOrNull(),
                notes = obj["notes"]?.jsonPrimitive?.content
            )

            val genres = obj["genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
            val tags = obj["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()

            saveEntry(entity, genres, tags)
            count++
        }
        return count
    }
}
