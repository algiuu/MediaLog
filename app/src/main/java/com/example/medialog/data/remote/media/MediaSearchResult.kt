package com.example.medialog.data.remote.media

import com.example.medialog.data.local.entity.MediaType

data class MediaSearchResult(
    val title: String,
    val originalTitle: String? = null,
    val type: MediaType,
    val source: String,
    val externalId: String,
    val posterUrl: String? = null,
    val releaseYear: Int? = null,
    val creator: String? = null,
    val synopsis: String? = null,
    val genres: List<String> = emptyList(),
    val totalUnits: Int? = null,
    val unitLabel: String? = null,
    val nextEpisode: Int? = null,
    val nextEpisodeAiringAt: Long? = null, // epoch seconds
    val watchProviders: List<WatchProvider> = emptyList()
)

data class WatchProvider(
    val providerId: Int,
    val providerName: String,
    val logoPath: String?
)
