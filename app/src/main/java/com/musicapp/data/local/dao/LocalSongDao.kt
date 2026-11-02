package com.musicapp.data.local.dao

import androidx.room.*
import com.musicapp.data.local.entity.LocalSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalSongDao {

    @Query("SELECT * FROM local_songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<LocalSongEntity>>

    @Query("SELECT * FROM local_songs WHERE isEncrypted = 0 ORDER BY title ASC")
    fun getPlayableSongs(): Flow<List<LocalSongEntity>>

    @Query("SELECT * FROM local_songs WHERE isEncrypted = 1 ORDER BY title ASC")
    fun getEncryptedSongs(): Flow<List<LocalSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<LocalSongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: LocalSongEntity)

    @Query("DELETE FROM local_songs")
    suspend fun clearAll()

    @Query("DELETE FROM local_songs WHERE songId = :songId")
    suspend fun deleteById(songId: String)

    @Query("UPDATE local_songs SET decryptedPath = :path WHERE songId = :songId")
    suspend fun updateDecryptedPath(songId: String, path: String)

    @Query("SELECT COUNT(*) FROM local_songs")
    suspend fun count(): Int
}