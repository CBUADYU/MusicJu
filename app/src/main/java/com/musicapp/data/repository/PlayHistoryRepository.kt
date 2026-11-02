package com.musicapp.data.repository

import com.musicapp.data.local.dao.PlayHistoryDao
import com.musicapp.data.local.entity.PlayHistoryEntity
import com.musicapp.domain.model.Song
import kotlinx.coroutines.flow.Flow

class PlayHistoryRepository(
    private val dao: PlayHistoryDao
) {
    fun getAllHistory(): Flow<List<PlayHistoryEntity>> = dao.getAllHistory()

    suspend fun addToHistory(song: Song) {
        // 先删旧记录，避免同一首歌重复出现
        dao.deleteBySongIdAndPlatform(song.songId, song.platform.name)
        dao.insert(
            PlayHistoryEntity(
                songId = song.songId,
                title = song.title,
                artist = song.artist,
                album = song.album,
                coverUrl = song.coverUrl,
                duration = song.duration,
                platform = song.platform.name
            )
        )
    }

    suspend fun clearAll() = dao.clearAll()

    suspend fun deleteById(id: Long) = dao.deleteById(id)
}