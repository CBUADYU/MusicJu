package com.musicapp.data.remote.api

import com.musicapp.data.remote.dto.*
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 网易云音乐 API（需要配合 NeteaseCloudMusicApi 服务使用）
 * 使用方式：部署一个 NeteaseCloudMusicApi 服务，将 BASE_URL 指向该服务
 */
interface NeteaseApi {

    // ========== 歌单 ==========
    @GET("playlist/detail")
    suspend fun getPlaylistDetail(
        @Query("id") playlistId: Long
    ): NeteasePlaylistResponse

    @GET("user/playlist")
    suspend fun getUserPlaylists(
        @Query("uid") userId: Long
    ): NeteaseUserPlaylistResponse

    // ========== 登录 ==========
    @GET("login/qr/key")
    suspend fun getLoginQRKey(): NeteaseQRKeyResponse

    @GET("login/qr/create")
    suspend fun getQRImage(
        @Query("key") unikey: String,
        @Query("qrimg") qrimg: Boolean = true
    ): okhttp3.ResponseBody

    @GET("login/qr/check")
    suspend fun checkQRStatus(
        @Query("key") unikey: String
    ): NeteaseQRCheckResponse

    @GET("login/status")
    suspend fun getLoginStatus(): NeteaseLoginStatusResponse

    @GET("user/account")
    suspend fun getUserAccount(): NeteaseUserAccountResponse

    // ========== 歌曲播放 ==========
    @GET("song/url/v1")
    suspend fun getSongUrl(
        @Query("id") songId: Long,
        @Query("level") level: String = "standard"
    ): NeteaseSongUrlResponse

    @GET("song/detail")
    suspend fun getSongDetail(
        @Query("ids") songIds: String
    ): NeteaseSongDetailResponse

    @GET("check/music")
    suspend fun checkMusic(
        @Query("id") songId: Long
    ): NeteaseCheckMusicResponse

    @GET("search")
    suspend fun search(
        @Query("keywords") keywords: String,
        @Query("type") type: Int = 1,
        @Query("limit") limit: Int = 20
    ): NeteaseSearchResponse

    // ========== 歌词 ==========
    @GET("lyric")
    suspend fun getLyric(
        @Query("id") songId: Long
    ): NeteaseLyricResponse

    companion object {
        const val DEFAULT_BASE_URL = "https://ncm-api.prod.gbclstudio.cn/"
        const val PUBLIC_BASE_URL = "https://ncm-api.prod.gbclstudio.cn/"
    }
}