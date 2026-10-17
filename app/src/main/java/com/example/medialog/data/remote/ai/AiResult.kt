package com.example.medialog.data.remote.ai

data class AiResult(
    val content: String,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null
)
