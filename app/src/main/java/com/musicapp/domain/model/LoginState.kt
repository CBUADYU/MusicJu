package com.musicapp.domain.model

/**
 * 平台登录状态
 */
data class LoginState(
    val platform: MusicPlatform,
    val isLoggedIn: Boolean,
    val userId: String = "",
    val nickname: String = "",
    val avatarUrl: String = "",
    val cookie: String = ""
)