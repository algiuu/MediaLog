package com.example.medialog.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "recommendation_item",
    foreignKeys = [
        ForeignKey(
            entity = RecommendationBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batch_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RecommendationItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "batch_id")
    val batchId: Long,
    val type: MediaType,
    val title: String,
    val reason: String,
    val source: String? = null,
    @ColumnInfo(name = "external_id")
    val externalId: String? = null,
    @ColumnInfo(name = "poster_url")
    val posterUrl: String? = null,
    @ColumnInfo(name = "is_hidden")
    val isHidden: Boolean = false,
    @ColumnInfo(name = "added_to_list")
    val addedToList: Boolean = false
)
