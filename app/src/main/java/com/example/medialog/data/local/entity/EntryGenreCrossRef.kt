package com.example.medialog.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "entry_genre",
    primaryKeys = ["entry_id", "genre_id"]
)
data class EntryGenreCrossRef(
    @ColumnInfo(name = "entry_id")
    val entryId: Long,
    @ColumnInfo(name = "genre_id")
    val genreId: Long
)
