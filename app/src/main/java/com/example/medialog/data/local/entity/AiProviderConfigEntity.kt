package com.example.medialog.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_provider_config")
data class AiProviderConfigEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "format_type")
    val formatType: AiFormatType,
    @ColumnInfo(name = "base_url")
    val baseUrl: String,
    @ColumnInfo(name = "selected_model")
    val selectedModel: String? = null,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false
)
