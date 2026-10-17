package com.example.medialog.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_entry",
    indices = [
        Index(value = ["type", "status"]),
        Index(value = ["source", "external_id"])
    ]
)
data class MediaEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: MediaType,
    val title: String,
    @ColumnInfo(name = "original_title")
    val originalTitle: String? = null,
    val source: String? = null,
    @ColumnInfo(name = "external_id")
    val externalId: String? = null,
    @ColumnInfo(name = "poster_url")
    val posterUrl: String? = null,
    val synopsis: String? = null,
    @ColumnInfo(name = "release_year")
    val releaseYear: Int? = null,
    val creator: String? = null,
    @ColumnInfo(name = "total_units")
    val totalUnits: Int? = null,
    @ColumnInfo(name = "unit_label")
    val unitLabel: String? = null,
    val progress: Int = 0,
    val status: EntryStatus = EntryStatus.PLAN,
    @ColumnInfo(name = "my_rating")
    val myRating: Int? = null,
    val platform: String? = null,
    @ColumnInfo(name = "started_at")
    val startedAt: Long? = null,
    @ColumnInfo(name = "finished_at")
    val finishedAt: Long? = null,
    val notes: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
