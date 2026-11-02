package com.musicapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.musicapp.domain.model.MusicPlatform

@Entity(
    tableName = "songs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String = "",
    val duration: Long = 0,
    val sourceUrl: String = "",
    val platform: String = MusicPlatform.UNKNOWN.name,
    val playlistId: Long = 0,
    val sortOrder: Int = 0
)