package com.musicapp.domain.model

data class Playlist(
    val id: Long = 0,
    val playlistId: String,
    val name: String,
    val coverUrl: String = "",
    val description: String = "",
    val songCount: Int = 0,
    val platform: MusicPlatform = MusicPlatform.UNKNOWN,
    val sourceUrl: String = "",
    val importTime: Long = System.currentTimeMillis(),
    val songs: List<Song> = emptyList(),
    val isCustom: Boolean = false,
    val isPinned: Boolean = false,
    val orderKey: Int = 0,
    val cloudSyncId: String = "",
    val updatedAt: Long = importTime
)
