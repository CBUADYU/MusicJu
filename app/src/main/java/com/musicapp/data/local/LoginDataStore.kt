package com.musicapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.musicapp.domain.model.MusicPlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "login_state")

/**
 * 登录态持久化存储
 * 存储各平台的登录凭证（Cookie、Token、用户ID等）
 */
class LoginDataStore(private val context: Context) {

    companion object {
        private val KEY_COOKIE = stringPreferencesKey("netease_cookie")
        private val KEY_USER_ID = longPreferencesKey("netease_user_id")
        private val KEY_NICKNAME = stringPreferencesKey("netease_nickname")
        private val KEY_AVATAR = stringPreferencesKey("netease_avatar")
        private val KEY_IS_LOGGED_IN = booleanPreferencesKey("netease_logged_in")
        private val KEY_LOGIN_TIME = longPreferencesKey("netease_login_time")

        // QQ音乐
        private val KEY_QQ_COOKIE = stringPreferencesKey("qq_cookie")
        private val KEY_QQ_LOGGED_IN = booleanPreferencesKey("qq_logged_in")
        private val KEY_QQ_OPEN_ID = stringPreferencesKey("qq_open_id")

        // 酷狗
        private val KEY_KG_COOKIE = stringPreferencesKey("kg_cookie")
        private val KEY_KG_LOGGED_IN = booleanPreferencesKey("kg_logged_in")

        // 酷我
        private val KEY_KW_COOKIE = stringPreferencesKey("kw_cookie")
        private val KEY_KW_LOGGED_IN = booleanPreferencesKey("kw_logged_in")

        // 咪咕
        private val KEY_MG_COOKIE = stringPreferencesKey("mg_cookie")
        private val KEY_MG_LOGGED_IN = booleanPreferencesKey("mg_logged_in")

        // 汽水
        private val KEY_QS_COOKIE = stringPreferencesKey("qs_cookie")
        private val KEY_QS_LOGGED_IN = booleanPreferencesKey("qs_logged_in")

        // 主题模式（SYSTEM / LIGHT / DARK）
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

        // 歌词动画主题（folia_scroll / word_sweep / word_pop / glow_pulse / fade_slide / plain）
        private val KEY_LYRIC_THEME = stringPreferencesKey("lyric_theme")
        private val KEY_LYRIC_RENDER_ENGINE = stringPreferencesKey("lyric_render_engine")

        // 用户手动关联的歌词（JSON 序列化：{key: lrcText}）
        private val KEY_MANUAL_LYRICS = stringPreferencesKey("manual_lyrics")
    }

    // ========== 手动歌词持久化 ==========
    /**
     * 用户手动关联的歌词表。key = "platform|songId|title"，value = LRC 文本
     * 进程被杀或重启后仍保留
     */
    val manualLyrics: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        parseLyricsMap(prefs[KEY_MANUAL_LYRICS])
    }

    suspend fun setManualLyrics(map: Map<String, String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MANUAL_LYRICS] = serializeLyricsMap(map)
        }
    }

    private fun serializeLyricsMap(map: Map<String, String>): String {
        // 轻量自定义分隔，避免引入 JSON 库；title/key 内的换行与 |
        // 由 base64 包一层，键与值都用 URL-safe base64
        return map.entries.joinToString("") { (k, v) ->
            android.util.Base64.encodeToString(k.toByteArray(), android.util.Base64.NO_WRAP) +
                "" +
                android.util.Base64.encodeToString(v.toByteArray(), android.util.Base64.NO_WRAP)
        }
    }

    private fun parseLyricsMap(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return try {
            raw.split('').mapNotNull { entry ->
                val parts = entry.split('', limit = 2)
                if (parts.size == 2) {
                    val k = String(android.util.Base64.decode(parts[0], android.util.Base64.DEFAULT))
                    val v = String(android.util.Base64.decode(parts[1], android.util.Base64.DEFAULT))
                    k to v
                } else null
            }.toMap()
        } catch (_: Throwable) { emptyMap() }
    }

    // ========== 主题 ==========
    val themeMode: Flow<String?> = context.dataStore.data.map { it[KEY_THEME_MODE] }
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    val lyricThemeId: Flow<String?> = context.dataStore.data.map { it[KEY_LYRIC_THEME] }
    suspend fun setLyricThemeId(id: String) {
        context.dataStore.edit { it[KEY_LYRIC_THEME] = id }
    }

    val lyricRenderEngineId: Flow<String?> = context.dataStore.data.map { it[KEY_LYRIC_RENDER_ENGINE] }
    suspend fun setLyricRenderEngineId(id: String) {
        context.dataStore.edit { it[KEY_LYRIC_RENDER_ENGINE] = id }
    }

    // ========== 网易云 ==========
    suspend fun saveNeteaseLogin(cookie: String, userId: Long, nickname: String, avatarUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_COOKIE] = cookie
            prefs[KEY_USER_ID] = userId
            prefs[KEY_NICKNAME] = nickname
            prefs[KEY_AVATAR] = avatarUrl
            prefs[KEY_IS_LOGGED_IN] = true
            prefs[KEY_LOGIN_TIME] = System.currentTimeMillis()
        }
    }

    suspend fun clearNeteaseLogin() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_COOKIE)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_NICKNAME)
            prefs.remove(KEY_AVATAR)
            prefs[KEY_IS_LOGGED_IN] = false
            prefs.remove(KEY_LOGIN_TIME)
        }
    }

    val neteaseCookie: Flow<String> = context.dataStore.data.map { it[KEY_COOKIE] ?: "" }
    val neteaseUserId: Flow<Long> = context.dataStore.data.map { it[KEY_USER_ID] ?: 0L }
    val neteaseNickname: Flow<String> = context.dataStore.data.map { it[KEY_NICKNAME] ?: "" }
    val neteaseAvatar: Flow<String> = context.dataStore.data.map { it[KEY_AVATAR] ?: "" }
    val neteaseLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[KEY_IS_LOGGED_IN] ?: false }

    suspend fun getNeteaseCookieSync(): String = context.dataStore.data.first()[KEY_COOKIE] ?: ""

    // ========== QQ音乐 ==========
    suspend fun saveQQLogin(cookie: String, openId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QQ_COOKIE] = cookie
            prefs[KEY_QQ_OPEN_ID] = openId
            prefs[KEY_QQ_LOGGED_IN] = true
        }
    }

    val qqLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[KEY_QQ_LOGGED_IN] ?: false }
    suspend fun getQQCookieSync(): String = context.dataStore.data.first()[KEY_QQ_COOKIE] ?: ""

    // ========== 酷狗 ==========
    suspend fun saveKugouLogin(cookie: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_KG_COOKIE] = cookie
            prefs[KEY_KG_LOGGED_IN] = true
        }
    }

    val kugouLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[KEY_KG_LOGGED_IN] ?: false }
    suspend fun getKugouCookieSync(): String = context.dataStore.data.first()[KEY_KG_COOKIE] ?: ""

    // ========== 酷我 ==========
    suspend fun saveKuwoLogin(cookie: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_KW_COOKIE] = cookie
            prefs[KEY_KW_LOGGED_IN] = true
        }
    }

    val kuwoLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[KEY_KW_LOGGED_IN] ?: false }

    // ========== 咪咕 ==========
    suspend fun saveMiguLogin(cookie: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MG_COOKIE] = cookie
            prefs[KEY_MG_LOGGED_IN] = true
        }
    }

    val miguLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[KEY_MG_LOGGED_IN] ?: false }

    // ========== 汽水 ==========
    suspend fun saveQishuiLogin(cookie: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QS_COOKIE] = cookie
            prefs[KEY_QS_LOGGED_IN] = true
        }
    }

    val qishuiLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[KEY_QS_LOGGED_IN] ?: false }

    /**
     * 获取指定平台的登录状态
     */
    fun isPlatformLoggedIn(platform: MusicPlatform): Flow<Boolean> = when (platform) {
        MusicPlatform.NETEASE -> neteaseLoggedIn
        MusicPlatform.QQ -> qqLoggedIn
        MusicPlatform.KUGOU -> kugouLoggedIn
        MusicPlatform.KUWO -> kuwoLoggedIn
        MusicPlatform.MIGU -> miguLoggedIn
        MusicPlatform.QISHUI -> qishuiLoggedIn
        MusicPlatform.FIVE_SING -> kotlinx.coroutines.flow.flowOf(false)
        MusicPlatform.APPLE_MUSIC -> kotlinx.coroutines.flow.flowOf(false)
        MusicPlatform.KKBOX -> kotlinx.coroutines.flow.flowOf(false)
        MusicPlatform.GEQUBAO -> kotlinx.coroutines.flow.flowOf(false)
        MusicPlatform.CUSTOM -> kotlinx.coroutines.flow.flowOf(false)
        MusicPlatform.LOCAL -> kotlinx.coroutines.flow.flowOf(false)
        MusicPlatform.UNKNOWN -> kotlinx.coroutines.flow.flowOf(false)
    }

    /**
     * 获取所有已登录的平台
     */
    suspend fun getLoggedInPlatforms(): List<MusicPlatform> {
        val prefs = context.dataStore.data.first()
        return MusicPlatform.entries.filter { platform ->
            when (platform) {
                MusicPlatform.NETEASE -> prefs[KEY_IS_LOGGED_IN] == true
                MusicPlatform.QQ -> prefs[KEY_QQ_LOGGED_IN] == true
                MusicPlatform.KUGOU -> prefs[KEY_KG_LOGGED_IN] == true
                MusicPlatform.KUWO -> prefs[KEY_KW_LOGGED_IN] == true
                MusicPlatform.MIGU -> prefs[KEY_MG_LOGGED_IN] == true
                MusicPlatform.QISHUI -> prefs[KEY_QS_LOGGED_IN] == true
                MusicPlatform.FIVE_SING -> false
                MusicPlatform.APPLE_MUSIC -> false
                MusicPlatform.KKBOX -> false
                MusicPlatform.GEQUBAO -> false
                MusicPlatform.CUSTOM -> false
                MusicPlatform.LOCAL -> false
                MusicPlatform.UNKNOWN -> false
            }
        }
    }
}