package com.musicapp.data.repository

import com.musicapp.data.local.dao.PlaylistDao
import com.musicapp.data.local.dao.SongDao
import com.musicapp.data.local.entity.PlaylistEntity
import com.musicapp.data.local.entity.SongEntity
import com.musicapp.domain.model.MusicPlatform
import com.musicapp.domain.model.Playlist
import com.musicapp.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao
) {
    /** 「喜欢」歌单在数据库中的固定 playlistId key（platform 固定为 CUSTOM） */
    private val LIKED_PLAYLIST_KEY = "custom_liked_musicapp"
    /**
     * 首页歌单列表：置顶 → 自定义 → 导入（倒序）
     */
    fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /** 与 getAllPlaylists 同义（给 VM 语义化命名） */
    fun observePlaylists(): Flow<List<Playlist>> = getAllPlaylists()

    /** 只有自定义歌单（用于加入歌单选择弹窗） */
    fun getCustomPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getCustomPlaylists().map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getPlaylistById(id: Long): Playlist? {
        return playlistDao.getPlaylistById(id)?.toDomain()
    }

    suspend fun getSongsByPlaylistId(playlistId: Long): List<Song> {
        return songDao.getSongsByPlaylistIdOnce(playlistId).map { it.toDomain() }
    }

    fun observeSongsByPlaylistId(playlistId: Long): Flow<List<Song>> {
        return songDao.getSongsByPlaylistId(playlistId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * 通用保存歌单（导入 & 新建自定义通用）。
     *
     * 若 playlist.id=0 则 INSERT；否则更新歌单元信息；songs 非空则全量写入 songs 表。
     */
    suspend fun savePlaylist(playlist: Playlist): Long {
        val entity = playlist.toEntity(updatedAt = System.currentTimeMillis())
        val playlistId = playlistDao.insertPlaylist(entity)

        if (playlist.songs.isNotEmpty()) {
            val songEntities = playlist.songs.mapIndexed { index, song ->
                song.toEntity().copy(
                    playlistId = playlistId,
                    sortOrder = index
                )
            }
            songDao.insertSongs(songEntities)
            playlistDao.updateSongCount(playlistId, playlist.songs.size, System.currentTimeMillis())
        }

        return playlistId
    }

    // ========== 自定义歌单 ==========

    /**
     * 创建一个空的自定义歌单。
     */
    suspend fun createCustomPlaylist(
        name: String,
        description: String = "",
        coverUrl: String = ""
    ): Long {
        val ts = System.currentTimeMillis()
        val entity = PlaylistEntity(
            playlistId = "custom_${UUID.randomUUID().toString().replace("-", "")}",
            name = name.trim().ifBlank { "新建歌单" },
            coverUrl = coverUrl,
            description = description,
            songCount = 0,
            platform = MusicPlatform.CUSTOM.name,
            sourceUrl = "",
            importTime = ts,
            isCustom = true,
            isPinned = false,
            orderKey = 0,
            cloudSyncId = "",
            updatedAt = ts
        )
        return playlistDao.insertPlaylist(entity)
    }

    /**
     * 给歌单追加一首或多首歌曲（去重）。
     *
     * 跳过同歌单里已存在的 songId（避免重复加入）。
     */
    suspend fun addSongsToPlaylist(playlistId: Long, songs: List<Song>): Int {
        if (songs.isEmpty()) return 0
        val existing = songDao.getSongsByPlaylistIdOnce(playlistId)
        val existingIds = existing.mapTo(HashSet()) { it.songId }
        val baseSortOrder = existing.size

        val toInsert = songs
            .distinctBy { it.songId }
            .filterNot { existingIds.contains(it.songId) }
            .mapIndexed { idx, song ->
                song.toEntity().copy(
                    id = 0, // 强制插入新行
                    playlistId = playlistId,
                    sortOrder = baseSortOrder + idx
                )
            }

        if (toInsert.isEmpty()) return 0
        songDao.insertSongs(toInsert)
        val total = existing.size + toInsert.size
        playlistDao.updateSongCount(playlistId, total, System.currentTimeMillis())
        return toInsert.size
    }

    /** 从歌单移除一首歌曲 */
    suspend fun removeSongFromPlaylist(playlistId: Long, songRowId: Long) {
        songDao.deleteByIdAndPlaylist(songRowId, playlistId)
        val remaining = songDao.getSongsByPlaylistIdOnce(playlistId).size
        playlistDao.updateSongCount(playlistId, remaining, System.currentTimeMillis())
    }

    // ========== 置顶 / 删除 ==========

    suspend fun togglePinned(id: Long): Boolean {
        val current = playlistDao.getPlaylistById(id) ?: return false
        val next = !current.isPinned
        playlistDao.setPinned(id, next, System.currentTimeMillis())
        return next
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        playlistDao.setPinned(id, pinned, System.currentTimeMillis())
    }

    suspend fun deletePlaylist(id: Long) {
        playlistDao.deletePlaylistById(id)
    }

    suspend fun updateCoverUrl(id: Long, coverUrl: String) {
        playlistDao.updateCoverUrl(id, coverUrl, System.currentTimeMillis())
    }

    suspend fun isPlaylistExists(playlistId: String, platform: MusicPlatform): Boolean {
        return playlistDao.getPlaylistBySourceId(playlistId, platform.name) != null
    }

    // ========== 喜欢 / 收藏 ==========

    /**
     * 获取「我喜欢」歌单的本地 id；不存在则自动创建。
     */
    suspend fun getLikedPlaylistId(): Long {
        val existing = playlistDao.getPlaylistBySourceId(LIKED_PLAYLIST_KEY, MusicPlatform.CUSTOM.name)
        if (existing != null) return existing.id
        val ts = System.currentTimeMillis()
        val entity = PlaylistEntity(
            playlistId = LIKED_PLAYLIST_KEY,
            name = "我喜欢",
            description = "我收藏喜欢的歌曲",
            songCount = 0,
            platform = MusicPlatform.CUSTOM.name,
            importTime = ts,
            isCustom = true,
            isPinned = true,
            orderKey = -1,
            cloudSyncId = "",
            updatedAt = ts
        )
        return playlistDao.insertPlaylist(entity)
    }

    /** 观察「我喜欢」歌单内的歌曲 */
    fun observeLikedSongs(playlistId: Long): Flow<List<Song>> {
        return songDao.getSongsByPlaylistId(playlistId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /** 某首歌是否已被喜欢 */
    suspend fun isLiked(playlistId: Long, song: Song): Boolean {
        return songDao.countSongInPlaylist(playlistId, song.songId) > 0
    }

    /**
     * 切换喜欢状态，返回切换后是否喜欢。
     */
    suspend fun toggleLike(song: Song): Boolean {
        val pid = getLikedPlaylistId()
        return if (isLiked(pid, song)) {
            songDao.getSongsByPlaylistIdOnce(pid)
                .firstOrNull { it.songId == song.songId }
                ?.let { removeSongFromPlaylist(pid, it.id) }
            false
        } else {
            addSongsToPlaylist(pid, listOf(song))
            true
        }
    }

    // ========== 云同步 ==========

    suspend fun setCloudSyncId(id: Long, syncId: String) {
        playlistDao.updateCloudSyncId(id, syncId, System.currentTimeMillis())
    }

    // ================= 映射 =================

    private fun PlaylistEntity.toDomain() = Playlist(
        id = id,
        playlistId = playlistId,
        name = name,
        coverUrl = coverUrl,
        description = description,
        songCount = songCount,
        platform = MusicPlatform.valueOf(platform),
        sourceUrl = sourceUrl,
        importTime = importTime,
        songs = emptyList(),
        isCustom = isCustom,
        isPinned = isPinned,
        orderKey = orderKey,
        cloudSyncId = cloudSyncId,
        updatedAt = updatedAt
    )

    private fun Playlist.toEntity(updatedAt: Long = this.updatedAt) = PlaylistEntity(
        id = id,
        playlistId = playlistId,
        name = name,
        coverUrl = coverUrl,
        description = description,
        songCount = songCount,
        platform = platform.name,
        sourceUrl = sourceUrl,
        importTime = importTime,
        isCustom = isCustom,
        isPinned = isPinned,
        orderKey = orderKey,
        cloudSyncId = cloudSyncId,
        updatedAt = updatedAt
    )

    internal fun SongEntity.toDomain() = Song(
        id = id,
        songId = songId,
        title = title,
        artist = artist,
        album = album,
        coverUrl = coverUrl,
        duration = duration,
        sourceUrl = sourceUrl,
        platform = runCatching { MusicPlatform.valueOf(platform) }.getOrDefault(MusicPlatform.UNKNOWN),
        playlistId = playlistId,
        sortOrder = sortOrder
    )

    internal fun Song.toEntity() = SongEntity(
        id = id,
        songId = songId,
        title = title,
        artist = artist,
        album = album,
        coverUrl = coverUrl,
        duration = duration,
        sourceUrl = sourceUrl,
        platform = platform.name,
        playlistId = playlistId,
        sortOrder = sortOrder
    )
}
