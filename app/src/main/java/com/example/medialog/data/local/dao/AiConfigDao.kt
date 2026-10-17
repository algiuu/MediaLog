package com.example.medialog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.medialog.data.local.entity.AiProviderConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: AiProviderConfigEntity)

    @Query("SELECT * FROM ai_provider_config WHERE id = :id LIMIT 1")
    suspend fun getConfigById(id: String): AiProviderConfigEntity?

    @Query("SELECT * FROM ai_provider_config WHERE is_active = 1 LIMIT 1")
    fun getActiveConfigFlow(): Flow<AiProviderConfigEntity?>

    @Query("SELECT * FROM ai_provider_config WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveConfig(): AiProviderConfigEntity?

    @Query("SELECT * FROM ai_provider_config")
    fun getAllConfigsFlow(): Flow<List<AiProviderConfigEntity>>

    @Query("UPDATE ai_provider_config SET is_active = 0")
    suspend fun clearActiveFlag()

    @Query("UPDATE ai_provider_config SET is_active = 1 WHERE id = :id")
    suspend fun setActiveFlag(id: String)

    @Transaction
    suspend fun setActiveProvider(id: String) {
        clearActiveFlag()
        setActiveFlag(id)
    }
}
