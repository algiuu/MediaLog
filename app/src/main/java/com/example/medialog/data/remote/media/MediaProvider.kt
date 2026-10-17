package com.example.medialog.data.remote.media

import com.example.medialog.data.local.entity.MediaType

interface MediaProvider {
    val type: MediaType
    val sourceName: String
    suspend fun search(query: String): List<MediaSearchResult>
    suspend fun getDetail(externalId: String): MediaSearchResult?
}
