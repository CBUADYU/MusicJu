package com.musicapp.data.local.dao

import androidx.room.*
import com.musicapp.data.local.entity.UserPlatformEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPlatformDao {

    @Query("SELECT * FROM user_platforms WHERE isEnabled = 1 ORDER BY name ASC")
    fun getEnabledPlatforms(): Flow<List<UserPlatformEntity>>

    @Query("SELECT * FROM user_platforms ORDER BY name ASC")
    fun getAllPlatforms(): Flow<List<UserPlatformEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(platform: UserPlatformEntity): Long

    @Update
    suspend fun update(platform: UserPlatformEntity)

    @Delete
    suspend fun delete(platform: UserPlatformEntity)

    @Query("DELETE FROM user_platforms WHERE id = :id")
    suspend fun deleteById(id: Long)
}