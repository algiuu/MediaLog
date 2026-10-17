package com.example.medialog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.medialog.data.local.entity.RecommendationBatchEntity
import com.example.medialog.data.local.entity.RecommendationItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: RecommendationBatchEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<RecommendationItemEntity>)

    @Query("SELECT * FROM recommendation_batch ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestBatch(): RecommendationBatchEntity?

    @Query("SELECT * FROM recommendation_batch ORDER BY created_at DESC LIMIT 1")
    fun getLatestBatchFlow(): Flow<RecommendationBatchEntity?>

    @Query("SELECT * FROM recommendation_item WHERE batch_id = :batchId AND is_hidden = 0 ORDER BY id ASC")
    fun getItemsForBatchFlow(batchId: Long): Flow<List<RecommendationItemEntity>>

    @Query("SELECT * FROM recommendation_item WHERE batch_id = :batchId AND is_hidden = 0 ORDER BY id ASC")
    suspend fun getItemsForBatch(batchId: Long): List<RecommendationItemEntity>

    @Query("UPDATE recommendation_item SET is_hidden = 1 WHERE id = :itemId")
    suspend fun hideItem(itemId: Long)

    @Query("UPDATE recommendation_item SET added_to_list = 1 WHERE id = :itemId")
    suspend fun markItemAdded(itemId: Long)

    @Transaction
    suspend fun saveRecommendation(batch: RecommendationBatchEntity, items: List<RecommendationItemEntity>) {
        val batchId = insertBatch(batch)
        val itemsWithBatchId = items.map { it.copy(batchId = batchId) }
        insertItems(itemsWithBatchId)
    }
}
