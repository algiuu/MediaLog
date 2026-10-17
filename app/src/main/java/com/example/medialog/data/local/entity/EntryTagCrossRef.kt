package com.example.medialog.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "entry_tag",
    primaryKeys = ["entry_id", "tag_id"]
)
data class EntryTagCrossRef(
    @ColumnInfo(name = "entry_id")
    val entryId: Long,
    @ColumnInfo(name = "tag_id")
    val tagId: Long
)
