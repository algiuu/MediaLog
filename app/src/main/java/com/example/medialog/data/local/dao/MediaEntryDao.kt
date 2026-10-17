package com.example.medialog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.medialog.data.local.entity.MediaEntryEntity
import com.example.medialog.data.local.entity.MediaType
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MediaEntryEntity): Long

    @Update
    suspend fun update(entry: MediaEntryEntity)

    @Query("DELETE FROM media_entry WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM media_entry WHERE id = :id")
    suspend fun getById(id: Long): MediaEntryEntity?

    @Query("SELECT * FROM media_entry WHERE source = :source AND external_id = :externalId LIMIT 1")
    suspend fun getBySourceAndExternalId(source: String, externalId: String): MediaEntryEntity?

    @Query("SELECT * FROM media_entry ORDER BY updated_at DESC")
    fun getAllEntriesFlow(): Flow<List<MediaEntryEntity>>

    @Query("SELECT * FROM media_entry WHERE type = :type ORDER BY updated_at DESC")
    fun getEntriesByTypeFlow(type: MediaType): Flow<List<MediaEntryEntity>>

    @Query("SELECT * FROM media_entry")
    suspend fun getAllEntries(): List<MediaEntryEntity>
}
