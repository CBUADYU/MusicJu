package com.musicapp.domain.model

/** 歌词搜索的命中结果：歌曲 + 命中的歌词片段 */
data class LyricHit(
    val song: Song,
    /** 命中的歌词行，用于在搜索结果卡片上显示 */
    val matchedLine: String
)

/** 歌手搜索的轻量结果 */
data class ArtistHit(
    val artistId: String,
    val name: String,
    val coverUrl: String,
    /** 热门歌曲数 */
    val songCount: Int = 0
)

/** 专辑搜索的轻量结果 */
data class AlbumHit(
    val albumId: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val publishTime: String = ""
)
