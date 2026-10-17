package com.example.medialog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.medialog.data.local.dao.AiConfigDao
import com.example.medialog.data.local.dao.GenreDao
import com.example.medialog.data.local.dao.MediaEntryDao
import com.example.medialog.data.local.dao.RecommendationDao
import com.example.medialog.data.local.dao.TagDao
import com.example.medialog.data.local.entity.AiProviderConfigEntity
import com.example.medialog.data.local.entity.Converters
import com.example.medialog.data.local.entity.EntryGenreCrossRef
import com.example.medialog.data.local.entity.EntryTagCrossRef
import com.example.medialog.data.local.entity.GenreEntity
import com.example.medialog.data.local.entity.MediaEntryEntity
import com.example.medialog.data.local.entity.RecommendationBatchEntity
import com.example.medialog.data.local.entity.RecommendationItemEntity
import com.example.medialog.data.local.entity.TagEntity

@Database(
    entities = [
        MediaEntryEntity::class,
        GenreEntity::class,
        EntryGenreCrossRef::class,
        TagEntity::class,
        EntryTagCrossRef::class,
        RecommendationBatchEntity::class,
        RecommendationItemEntity::class,
        AiProviderConfigEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MediaLogDatabase : RoomDatabase() {
    abstract fun mediaEntryDao(): MediaEntryDao
    abstract fun genreDao(): GenreDao
    abstract fun tagDao(): TagDao
    abstract fun recommendationDao(): RecommendationDao
    abstract fun aiConfigDao(): AiConfigDao
}
