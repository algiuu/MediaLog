package com.example.medialog.data.remote.media

data class AiringItem(
    val mediaId: String,
    val title: String,
    val episode: Int,
    val airingAt: Long,
    val posterUrl: String?
)
