package com.musicapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.musicapp.domain.model.MusicPlatform

/**
 * 歌单数据表
 *
 * 字段说明：
 *  - isCustom: 是否为「用户自建自定义歌单」（true=App 内创建，false=从各大平台导入）
 *  - isPinned: 是否置顶（用于首页排序）
 *  - orderKey: 自定义排序（置顶后再按此字段升序，其次 importTime DESC）
 *  - cloudSyncId: 云端同步用的随机 ID
 *  - cloudSnapshotJson: 上一次同步到云端的 JSON 快照（暂位）
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: String,
    val name: String,
    val coverUrl: String = "",
    val description: String = "",
    val songCount: Int = 0,
    val platform: String = MusicPlatform.UNKNOWN.name,
    val sourceUrl: String = "",
    val importTime: Long = System.currentTimeMillis(),
    val isCustom: Boolean = false,
    val isPinned: Boolean = false,
    val orderKey: Int = 0,
    val cloudSyncId: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
