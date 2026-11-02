package com.musicapp.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 网易云音乐 API 响应 DTO
 */
data class NeteasePlaylistResponse(
    val code: Int,
    val playlist: NeteasePlaylist? = null,
    val message: String? = null
)

data class NeteasePlaylist(
    val id: Long,
    val name: String,
    val coverImgUrl: String? = null,
    val description: String? = null,
    val trackCount: Int = 0,
    val tracks: List<NeteaseTrack> = emptyList()
)

data class NeteaseTrack(
    val id: Long,
    val name: String,
    val ar: List<NeteaseArtist> = emptyList(),
    val al: NeteaseAlbum? = null,
    val dt: Long = 0
)

data class NeteaseArtist(
    val id: Long,
    val name: String
)

data class NeteaseAlbum(
    val id: Long,
    val name: String,
    val picUrl: String? = null
)

/**
 * 通用歌单解析结果
 */
data class ParsedPlaylistDto(
    val playlistId: String,
    val name: String,
    val coverUrl: String = "",
    val description: String = "",
    val songCount: Int = 0,
    val songs: List<ParsedSongDto> = emptyList()
)

data class ParsedSongDto(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String = "",
    val duration: Long = 0,
    val sourceUrl: String = ""
)

// ========== 登录相关 DTO ==========

data class NeteaseQRKeyResponse(
    val code: Int,
    val data: NeteaseQRKeyData? = null,
    val message: String? = null
)

data class NeteaseQRKeyData(
    val unikey: String
)

data class NeteaseQRCheckResponse(
    val code: Int,
    val cookie: String? = null,
    val message: String? = null
)

data class NeteaseLoginStatusResponse(
    val code: Int,
    val data: NeteaseLoginStatusData? = null
)

data class NeteaseLoginStatusData(
    val code: Int,
    val account: NeteaseAccountInfo? = null,
    val profile: NeteaseProfileInfo? = null
)

data class NeteaseUserAccountResponse(
    val code: Int,
    val account: NeteaseAccountInfo? = null,
    val profile: NeteaseProfileInfo? = null
)

data class NeteaseAccountInfo(
    val id: Long,
    val userName: String = "",
    val type: Int = 0
)

data class NeteaseProfileInfo(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String = "",
    val signature: String = ""
)

// ========== 歌曲 URL 相关 DTO ==========

data class NeteaseSongUrlResponse(
    val code: Int,
    val data: List<NeteaseSongUrlData> = emptyList()
)

data class NeteaseSongUrlData(
    val id: Long,
    val url: String? = null,
    val br: Int = 0,
    val size: Long = 0,
    val type: String = "",
    val level: String = "",
    val freeTrialInfo: Any? = null
)

data class NeteaseSongDetailResponse(
    val code: Int,
    val songs: List<NeteaseTrack> = emptyList()
)

data class NeteaseCheckMusicResponse(
    val success: Boolean,
    val message: String = ""
)

data class NeteaseSearchResponse(
    val code: Int,
    val result: NeteaseSearchResult? = null,
    val message: String? = null
)

data class NeteaseSearchResult(
    val songs: List<NeteaseTrack> = emptyList(),
    val songCount: Int = 0
)

// ========== 用户歌单 ==========

data class NeteaseUserPlaylistResponse(
    val code: Int,
    val playlist: List<NeteaseUserPlaylist> = emptyList()
)

data class NeteaseUserPlaylist(
    val id: Long,
    val name: String,
    val coverImgUrl: String? = null,
    val trackCount: Int = 0,
    val description: String? = null
)

// ========== 歌词相关 DTO ==========

data class NeteaseLyricResponse(
    val code: Int,
    val lrc: NeteaseLrc? = null,
    val tlyric: NeteaseLrc? = null,  // 翻译歌词
    val klyric: NeteaseLrc? = null,  // 逐字歌词（卡拉OK）
    val romalrc: NeteaseLrc? = null  // 罗马音
)

data class NeteaseLrc(
    val version: Int = 0,
    val lyric: String = ""
)