package com.musicapp.data.local.dao

import androidx.room.*
import com.musicapp.data.local.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {

    @Query("SELECT * FROM play_history ORDER BY playTime DESC")
    fun getAllHistory(): Flow<List<PlayHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PlayHistoryEntity)

    @Query("DELETE FROM play_history WHERE songId = :songId AND platform = :platform")
    suspend fun deleteBySongIdAndPlatform(songId: String, platform: String)

    @Query("DELETE FROM play_history")
    suspend fun clearAll()

    @Query("DELETE FROM play_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}