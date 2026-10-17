package com.example.medialog.data.local.entity

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromMediaType(value: MediaType?): String? = value?.name

    @TypeConverter
    fun toMediaType(value: String?): MediaType? = value?.let {
        try { MediaType.valueOf(it) } catch (e: Exception) { null }
    }

    @TypeConverter
    fun fromEntryStatus(value: EntryStatus?): String? = value?.name

    @TypeConverter
    fun toEntryStatus(value: String?): EntryStatus? = value?.let {
        try { EntryStatus.valueOf(it) } catch (e: Exception) { null }
    }

    @TypeConverter
    fun fromSourceType(value: SourceType?): String? = value?.name

    @TypeConverter
    fun toSourceType(value: String?): SourceType? = value?.let {
        try { SourceType.valueOf(it) } catch (e: Exception) { null }
    }

    @TypeConverter
    fun fromAiFormatType(value: AiFormatType?): String? = value?.name

    @TypeConverter
    fun toAiFormatType(value: String?): AiFormatType? = value?.let {
        try { AiFormatType.valueOf(it) } catch (e: Exception) { null }
    }
}
