package com.example.medialog.ui.chat

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val fromUser: Boolean
)
