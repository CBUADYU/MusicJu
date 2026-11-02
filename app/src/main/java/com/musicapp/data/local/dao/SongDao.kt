package com.musicapp.data.local.dao

import androidx.room.*
import com.musicapp.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    fun getSongsByPlaylistId(playlistId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    suspend fun getSongsByPlaylistIdOnce(playlistId: Long): List<SongEntity>

    /** 取某歌单里的歌曲数量（用于去重判断） */
    @Query("SELECT COUNT(1) FROM songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun countSongInPlaylist(playlistId: Long, songId: String): Int

    /** 从某歌单中删除一首歌 */
    @Query("DELETE FROM songs WHERE id = :id AND playlistId = :playlistId")
    suspend fun deleteByIdAndPlaylist(id: Long, playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity): Long

    @Delete
    suspend fun deleteSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE playlistId = :playlistId")
    suspend fun deleteSongsByPlaylistId(playlistId: Long)
}
