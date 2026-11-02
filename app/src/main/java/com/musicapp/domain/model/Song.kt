package com.musicapp.domain.model

data class Song(
    val id: Long = 0,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String = "",
    val duration: Long = 0,
    val sourceUrl: String = "",
    val platform: MusicPlatform = MusicPlatform.UNKNOWN,
    val playlistId: Long = 0,
    val sortOrder: Int = 0
)