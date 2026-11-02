package com.musicapp.data.repository

import com.musicapp.data.local.LoginDataStore
import com.musicapp.data.remote.api.NeteaseApi
import com.musicapp.data.remote.api.NetworkConfig
import com.musicapp.domain.model.LoginState
import com.musicapp.domain.model.MusicPlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 认证仓库 - 管理各平台登录状态
 */
class AuthRepository(
    private val loginDataStore: LoginDataStore
) {
    private var neteaseApi: NeteaseApi? = null

    fun setNeteaseApiBaseUrl(baseUrl: String) {
        neteaseApi = NetworkConfig.createNeteaseApi(baseUrl)
    }

    /**
     * 获取所有平台的登录状态 Flow
     */
    fun getAllLoginStates(): Flow<List<LoginState>> {
        return combine(
            listOf(
                loginDataStore.neteaseLoggedIn,
                loginDataStore.qqLoggedIn,
                loginDataStore.kugouLoggedIn,
                loginDataStore.kuwoLoggedIn,
                loginDataStore.miguLoggedIn,
                loginDataStore.qishuiLoggedIn
            )
        ) { values ->
            val states = values.mapIndexed { index, loggedIn ->
                val platform = when (index) {
                    0 -> MusicPlatform.NETEASE
                    1 -> MusicPlatform.QQ
                    2 -> MusicPlatform.KUGOU
                    3 -> MusicPlatform.KUWO
                    4 -> MusicPlatform.MIGU
                    5 -> MusicPlatform.QISHUI
                    else -> MusicPlatform.UNKNOWN
                }
                LoginState(platform, loggedIn)
            }
            states
        }
    }

    /**
     * 获取网易云用户信息（通过已登录的 Cookie）
     */
    suspend fun fetchNeteaseUserInfo(): Result<LoginState> {
        return try {
            val cookie = loginDataStore.getNeteaseCookieSync()
            if (cookie.isEmpty()) {
                return Result.failure(Exception("未登录网易云"))
            }

            val api = neteaseApi ?: NetworkConfig.createNeteaseApi()
            val response = api.getUserAccount()

            if (response.code == 200 && response.account != null) {
                val state = LoginState(
                    platform = MusicPlatform.NETEASE,
                    isLoggedIn = true,
                    userId = response.account.id.toString(),
                    nickname = response.profile?.nickname ?: "",
                    avatarUrl = response.profile?.avatarUrl ?: "",
                    cookie = cookie
                )
                loginDataStore.saveNeteaseLogin(
                    cookie = cookie,
                    userId = response.account.id,
                    nickname = response.profile?.nickname ?: "",
                    avatarUrl = response.profile?.avatarUrl ?: ""
                )
                Result.success(state)
            } else {
                // Cookie 可能过期，清除登录态
                loginDataStore.clearNeteaseLogin()
                Result.failure(Exception("登录已过期，请重新登录"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 通过扫码登录获取 Cookie
     * 返回二维码 key，用于生成二维码图片
     */
    suspend fun getNeteaseQRKey(): Result<String> {
        return try {
            val api = neteaseApi ?: NetworkConfig.createNeteaseApi()
            val response = api.getLoginQRKey()
            if (response.code == 200 && response.data != null) {
                Result.success(response.data.unikey)
            } else {
                Result.failure(Exception("获取二维码失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取二维码图片（Base64编码）
     */
    suspend fun getNeteaseQRImage(unikey: String): Result<String> {
        return try {
            val api = neteaseApi ?: NetworkConfig.createNeteaseApi()
            val response = api.getQRImage(unikey)
            val bytes = response.bytes()
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            Result.success("data:image/png;base64,$base64")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 检查扫码登录状态
     */
    suspend fun checkNeteaseQRStatus(unikey: String): Result<String> {
        return try {
            val api = neteaseApi ?: NetworkConfig.createNeteaseApi()
            val response = api.checkQRStatus(unikey)
            when (response.code) {
                800 -> Result.failure(Exception("二维码已过期"))
                801 -> Result.failure(Exception("等待扫码"))
                802 -> Result.failure(Exception("等待确认"))
                803 -> {
                    // 登录成功，获取 cookie
                    val cookie = response.cookie ?: ""
                    if (cookie.isNotEmpty()) {
                        loginDataStore.saveNeteaseLogin(
                            cookie = cookie,
                            userId = 0L,
                            nickname = "",
                            avatarUrl = ""
                        )
                        // 获取用户信息
                        fetchNeteaseUserInfo()
                        Result.success(cookie)
                    } else {
                        Result.failure(Exception("获取登录凭证失败"))
                    }
                }
                else -> Result.failure(Exception("未知状态: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 模拟登录 - 通过 Cookie 字符串直接登录
     */
    suspend fun loginWithCookie(platform: MusicPlatform, cookie: String): Result<Unit> {
        return try {
            when (platform) {
                MusicPlatform.NETEASE -> {
                    loginDataStore.saveNeteaseLogin(cookie, 0L, "", "")
                    fetchNeteaseUserInfo()
                }
                MusicPlatform.QQ -> loginDataStore.saveQQLogin(cookie, "")
                MusicPlatform.KUGOU -> loginDataStore.saveKugouLogin(cookie)
                MusicPlatform.KUWO -> loginDataStore.saveKuwoLogin(cookie)
                MusicPlatform.MIGU -> loginDataStore.saveMiguLogin(cookie)
                MusicPlatform.QISHUI -> loginDataStore.saveQishuiLogin(cookie)
                MusicPlatform.FIVE_SING -> { /* 暂不支持 */ }
                MusicPlatform.APPLE_MUSIC -> { /* 暂不支持 */ }
                MusicPlatform.KKBOX -> { /* 暂不支持 */ }
                MusicPlatform.GEQUBAO -> { /* 无需登录 */ }
                MusicPlatform.CUSTOM -> { /* 暂不支持 */ }
                MusicPlatform.LOCAL -> { /* 不支持 */ }
                MusicPlatform.UNKNOWN -> throw IllegalArgumentException("未知平台")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 退出登录
     */
    suspend fun logout(platform: MusicPlatform) {
        when (platform) {
            MusicPlatform.NETEASE -> loginDataStore.clearNeteaseLogin()
            else -> loginWithCookie(platform, "") // 清空 cookie
        }
    }

    /**
     * 获取平台 Cookie（用于 API 请求）
     */
    suspend fun getCookie(platform: MusicPlatform): String = when (platform) {
        MusicPlatform.NETEASE -> loginDataStore.getNeteaseCookieSync()
        MusicPlatform.QQ -> loginDataStore.getQQCookieSync()
        MusicPlatform.KUGOU -> loginDataStore.getKugouCookieSync()
        else -> ""
    }
}