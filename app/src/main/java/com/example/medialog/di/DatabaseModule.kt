package com.example.medialog.di

import android.content.Context
import androidx.room.Room
import com.example.medialog.data.local.MediaLogDatabase
import com.example.medialog.data.local.dao.AiConfigDao
import com.example.medialog.data.local.dao.GenreDao
import com.example.medialog.data.local.dao.MediaEntryDao
import com.example.medialog.data.local.dao.RecommendationDao
import com.example.medialog.data.local.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMediaLogDatabase(@ApplicationContext context: Context): MediaLogDatabase {
        return Room.databaseBuilder(
            context,
            MediaLogDatabase::class.java,
            "medialog.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideMediaEntryDao(database: MediaLogDatabase): MediaEntryDao = database.mediaEntryDao()

    @Provides
    fun provideGenreDao(database: MediaLogDatabase): GenreDao = database.genreDao()

    @Provides
    fun provideTagDao(database: MediaLogDatabase): TagDao = database.tagDao()

    @Provides
    fun provideRecommendationDao(database: MediaLogDatabase): RecommendationDao = database.recommendationDao()

    @Provides
    fun provideAiConfigDao(database: MediaLogDatabase): AiConfigDao = database.aiConfigDao()
}
