package com.musicapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_songs")
data class LocalSongEntity(
    @PrimaryKey
    val songId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val duration: Long = 0,
    val filePath: String,
    val fileSize: Long = 0,
    val mimeType: String = "",
    val isEncrypted: Boolean = false,
    val encryptedFormat: String = "",  // ncm, qmc0, kgm 等
    val decryptedPath: String = "",     // 解密后文件路径
    val dateAdded: Long = System.currentTimeMillis()
)