package com.example.medialog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.medialog.data.local.entity.EntryTagCrossRef
import com.example.medialog.data.local.entity.TagEntity

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT * FROM tag WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: EntryTagCrossRef)

    @Query("DELETE FROM entry_tag WHERE entry_id = :entryId")
    suspend fun deleteCrossRefsForEntry(entryId: Long)

    @Query("""
        SELECT t.* FROM tag t 
        INNER JOIN entry_tag et ON t.id = et.tag_id 
        WHERE et.entry_id = :entryId
    """)
    suspend fun getTagsForEntry(entryId: Long): List<TagEntity>
}
