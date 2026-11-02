package com.musicapp.data.local.dao

import androidx.room.*
import com.musicapp.data.local.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    /**
     * 排序规则：置顶优先 → 自定义歌单（orderKey ASC）→ 导入歌单（按导入时间倒序）
     */
    @Query(
        """
        SELECT * FROM playlists
        ORDER BY
            CASE WHEN isPinned = 1 THEN 0 ELSE 1 END ASC,
            CASE WHEN isCustom = 1 THEN 0 ELSE 1 END ASC,
            orderKey ASC,
            importTime DESC
        """
    )
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE isCustom = 1 ORDER BY orderKey ASC, importTime DESC")
    fun getCustomPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId AND platform = :platform LIMIT 1")
    suspend fun getPlaylistBySourceId(playlistId: String, platform: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Long)

    /** 切换置顶状态 */
    @Query("UPDATE playlists SET isPinned = :pinned, updatedAt = :ts WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, ts: Long)

    /** 更新 cloudSyncId（云端对象 sid） */
    @Query("UPDATE playlists SET cloudSyncId = :syncId, updatedAt = :ts WHERE id = :id")
    suspend fun updateCloudSyncId(id: Long, syncId: String, ts: Long)

    /** 更新歌单歌曲计数 */
    @Query("UPDATE playlists SET songCount = :count, updatedAt = :ts WHERE id = :id")
    suspend fun updateSongCount(id: Long, count: Int, ts: Long)

    /** 更新歌单封面 URL */
    @Query("UPDATE playlists SET coverUrl = :coverUrl, updatedAt = :ts WHERE id = :id")
    suspend fun updateCoverUrl(id: Long, coverUrl: String, ts: Long)
}
