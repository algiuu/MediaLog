package com.example.medialog.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recommendation_batch")
data class RecommendationBatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "provider_name")
    val providerName: String,
    val model: String,
    @ColumnInfo(name = "type_filter")
    val typeFilter: String? = null,
    @ColumnInfo(name = "mood_prompt")
    val moodPrompt: String? = null,
    @ColumnInfo(name = "input_tokens")
    val inputTokens: Int? = null,
    @ColumnInfo(name = "output_tokens")
    val outputTokens: Int? = null
)
