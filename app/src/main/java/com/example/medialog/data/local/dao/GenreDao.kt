package com.example.medialog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.medialog.data.local.entity.EntryGenreCrossRef
import com.example.medialog.data.local.entity.GenreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GenreDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenre(genre: GenreEntity): Long

    @Query("SELECT * FROM genre WHERE name = :name LIMIT 1")
    suspend fun getGenreByName(name: String): GenreEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: EntryGenreCrossRef)

    @Query("DELETE FROM entry_genre WHERE entry_id = :entryId")
    suspend fun deleteCrossRefsForEntry(entryId: Long)

    @Query("""
        SELECT g.* FROM genre g 
        INNER JOIN entry_genre eg ON g.id = eg.genre_id 
        WHERE eg.entry_id = :entryId
    """)
    suspend fun getGenresForEntry(entryId: Long): List<GenreEntity>

    @Query("""
        SELECT g.name AS genre, COUNT(eg.entry_id) AS count FROM genre g 
        INNER JOIN entry_genre eg ON g.id = eg.genre_id 
        GROUP BY g.id ORDER BY count DESC LIMIT :limit
    """)
    fun getTopGenres(limit: Int = 10): Flow<List<GenreCount>>
}

data class GenreCount(
    val genre: String,
    val count: Int
)
