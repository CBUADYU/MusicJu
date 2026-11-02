package com.musicapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "play_history",
    indices = [androidx.room.Index(value = ["songId", "platform"], unique = true)]
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String = "",
    val duration: Long = 0,
    val platform: String,
    val playTime: Long = System.currentTimeMillis()
)