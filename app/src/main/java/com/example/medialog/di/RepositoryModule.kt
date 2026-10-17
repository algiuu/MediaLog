package com.example.medialog.di

import com.example.medialog.data.remote.ai.AiProvider
import com.example.medialog.data.remote.ai.ClaudeProvider
import com.example.medialog.data.remote.ai.GeminiProvider
import com.example.medialog.data.remote.ai.OpenAiCompatibleProvider
import com.example.medialog.data.remote.media.AniListProvider
import com.example.medialog.data.remote.media.MediaProvider
import com.example.medialog.data.remote.media.OpenLibraryProvider
import com.example.medialog.data.remote.media.TmdbProvider
import com.example.medialog.data.repository.AiSettingsRepository
import com.example.medialog.data.repository.MediaRepository
import com.example.medialog.data.repository.RecommendationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAniListProvider(client: OkHttpClient): AniListProvider = AniListProvider(client)

    @Provides
    @Singleton
    fun provideTmdbMovieProvider(
        client: OkHttpClient,
        secureKeyManager: com.example.medialog.data.security.SecureKeyManager
    ): TmdbProvider = TmdbProvider(client, secureKeyManager, com.example.medialog.data.local.entity.MediaType.MOVIE)

    @Provides
    @Singleton
    fun provideTmdbSeriesProvider(
        client: OkHttpClient,
        secureKeyManager: com.example.medialog.data.security.SecureKeyManager
    ): TmdbProvider = TmdbProvider(client, secureKeyManager, com.example.medialog.data.local.entity.MediaType.SERIES)

    @Provides
    @Singleton
    fun provideOpenLibraryProvider(client: OkHttpClient): OpenLibraryProvider =
        OpenLibraryProvider(client)

    @Provides
    @Singleton
    fun provideGeminiProvider(client: OkHttpClient): GeminiProvider = GeminiProvider(client)

    @Provides
    @Singleton
    fun provideOpenAiCompatibleProvider(client: OkHttpClient): OpenAiCompatibleProvider =
        OpenAiCompatibleProvider(client)

    @Provides
    @Singleton
    fun provideClaudeProvider(client: OkHttpClient): ClaudeProvider = ClaudeProvider(client)

    @Provides
    @Singleton
    fun provideMediaRepository(
        mediaEntryDao: com.example.medialog.data.local.dao.MediaEntryDao,
        genreDao: com.example.medialog.data.local.dao.GenreDao,
        tagDao: com.example.medialog.data.local.dao.TagDao,
        aniListProvider: AniListProvider,
        openLibraryProvider: OpenLibraryProvider,
        secureKeyManager: com.example.medialog.data.security.SecureKeyManager,
        client: OkHttpClient
    ): MediaRepository = MediaRepository(
        mediaEntryDao, genreDao, tagDao, aniListProvider, openLibraryProvider, secureKeyManager, client
    )

    @Provides
    @Singleton
    fun provideAiSettingsRepository(
        aiConfigDao: com.example.medialog.data.local.dao.AiConfigDao,
        secureKeyManager: com.example.medialog.data.security.SecureKeyManager,
        geminiProvider: GeminiProvider,
        openAiCompatibleProvider: OpenAiCompatibleProvider,
        claudeProvider: ClaudeProvider
    ): AiSettingsRepository = AiSettingsRepository(
        aiConfigDao, secureKeyManager, geminiProvider, openAiCompatibleProvider, claudeProvider
    )

    @Provides
    @Singleton
    fun provideRecommendationRepository(
        recommendationDao: com.example.medialog.data.local.dao.RecommendationDao,
        mediaEntryDao: com.example.medialog.data.local.dao.MediaEntryDao,
        mediaRepository: MediaRepository,
        aiSettingsRepository: AiSettingsRepository
    ): RecommendationRepository = RecommendationRepository(
        recommendationDao, mediaEntryDao, mediaRepository, aiSettingsRepository
    )
}
