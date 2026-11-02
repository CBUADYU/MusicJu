package com.musicapp.data.repository

import android.util.Base64
import com.musicapp.data.remote.api.NeteaseApi
import com.musicapp.data.remote.api.NetworkConfig
import com.musicapp.data.remote.dto.ParsedPlaylistDto
import com.musicapp.data.remote.dto.ParsedSongDto
import com.musicapp.domain.model.LyricHit
import com.musicapp.domain.model.ArtistHit
import com.musicapp.domain.model.AlbumHit
import com.musicapp.domain.model.MusicPlatform
import com.musicapp.domain.model.Playlist
import com.musicapp.domain.model.Song
import com.musicapp.util.LrcParser
import com.musicapp.util.ShareLinkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 歌单导入服务 - 负责从各平台获取歌单数据和歌曲播放地址
 */
class MusicRepository {

    private var neteaseApi: NeteaseApi? = null

    fun setNeteaseApiBaseUrl(baseUrl: String) {
        neteaseApi = NetworkConfig.createNeteaseApi(baseUrl)
    }

    /**
     * 网易云 API 返回的 CDN 封面图普遍是 http://（music.126.net），
     * Android 9+ 禁止明文 HTTP，加载会被系统网络层直接拦截 → 封面空白。
     * 统一替换成 https://，一次修干净。
     */
    private fun String?.asHttps(): String {
        val raw = this ?: return ""
        // Apple Music 的 coverUrl 里可能拼了 "|preview=" 分隔符，只改 scheme 部分
        val pipeIdx = raw.indexOf("|preview=")
        return if (pipeIdx >= 0) {
            raw.substring(0, pipeIdx).replace("http://", "https://") + raw.substring(pipeIdx)
        } else {
            raw.replace("http://", "https://")
        }
    }

    // -----------------------------------------------------------------
    //  共用 HTTP 客户端
    // -----------------------------------------------------------------
    private val sharedClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private fun httpGetText(url: String, headers: Map<String, String> = emptyMap()): String? {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
            header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
        }.build()
        return runCatching {
            sharedClient.newCall(req).execute().use { resp ->
                resp.body?.string()
            }
        }.getOrNull()
    }

    /** GET 返回标准 JSON。失败 / 非 JSON 返回 null。 */
    private fun httpGetJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject? {
        val text = httpGetText(url, headers) ?: return null
        val t = text.trim()
        if (t.isEmpty() || (!t.startsWith("{") && !t.startsWith("["))) return null
        return runCatching { JSONObject(t) }.getOrNull()
    }

    /** 酷我接口返回的是单引号 JSON。 */
    private fun httpGetKuwoJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject? {
        val text = httpGetText(url, headers) ?: return null
        // 简单把 ' 替换为 " 并尝试解析
        val fixed = text.trim().replace("'", "\"")
        return runCatching { JSONObject(fixed) }.getOrNull()
    }

    /** POST 表单并返回文本。 */
    private fun httpPostForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): String? {
        val body = form.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val req = Request.Builder()
            .url(url)
            .post(body.toRequestBody(null))
            .apply {
                headers.forEach { (k, v) -> header(k, v) }
                header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                header("Referer", "https://www.gequbao.net/")
            }
            .build()
        return runCatching {
            sharedClient.newCall(req).execute().use { resp -> resp.body?.string() }
        }.getOrNull()
    }

    /** POST 表单返回 JSON。 */
    private fun httpPostFormJson(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): JSONObject? {
        val text = httpPostForm(url, form, headers) ?: return null
        val t = text.trim()
        if (t.isEmpty() || (!t.startsWith("{") && !t.startsWith("["))) return null
        return runCatching { JSONObject(t) }.getOrNull()
    }

    // -----------------------------------------------------------------
    //  导入歌单
    // -----------------------------------------------------------------
    suspend fun importPlaylist(shareUrl: String): Result<Playlist> {
        return try {
            val parsed = ShareLinkParser.parse(shareUrl)
                ?: return Result.failure(Exception("无法识别该链接，请确认是有效的歌单分享链接"))

            if (parsed.isSong) {
                return Result.failure(Exception("这是单曲链接，请使用「解析单曲」功能"))
            }

            val dto = fetchPlaylistData(parsed.platform, parsed.playlistId)
            val playlist = dto.toDomainModel(parsed.platform, shareUrl)

            Result.success(playlist)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -----------------------------------------------------------------
    //  解析单曲
    // -----------------------------------------------------------------
    /**
     * 从单曲分享链接解析出 Song 对象
     *  策略：
     *   - 网易云 → 走 NeteaseCloudMusicApi /song/detail
     *   - 其它平台 → 走对应平台搜索/解析
     */
    suspend fun parseSingleSong(shareUrl: String): Result<Song> {
        return try {
            val parsed = ShareLinkParser.parse(shareUrl)
                ?: return Result.failure(Exception("无法识别该链接，请确认是有效的单曲分享链接"))
            if (!parsed.isSong || parsed.songId.isNullOrBlank()) {
                return Result.failure(Exception("该链接不是单曲链接，请使用「解析歌单」功能"))
            }
            parseSingleSongInternal(parsed.platform, parsed.songId, shareUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun parseSingleSongInternal(
        platform: MusicPlatform,
        songId: String,
        sourceUrl: String
    ): Result<Song> {
        return when (platform) {
            MusicPlatform.NETEASE -> parseNeteaseSingleSong(songId, sourceUrl)
            MusicPlatform.APPLE_MUSIC -> parseAppleMusicSingleSong(songId, sourceUrl)
            MusicPlatform.QQ -> fallbackSingleByName(platform, songId, sourceUrl, "QQ")
            MusicPlatform.KUGOU -> fallbackSingleByName(platform, songId, sourceUrl, "KUGOU")
            MusicPlatform.KUWO -> parseKuwoSingleSong(songId, sourceUrl)
            MusicPlatform.GEQUBAO -> parseGequbaoSingleSong(songId, sourceUrl)
            else -> fallbackSingleByName(platform, songId, sourceUrl, platform.displayName)
        }
    }

    /** 网易云单曲：用 NeteaseCloudMusicApi 查 /song/detail */
    private suspend fun parseNeteaseSingleSong(songId: String, sourceUrl: String): Result<Song> {
        return try {
            val api = neteaseApi ?: NetworkConfig.createNeteaseApi()
            val response = api.getSongDetail(songId)
            if (response.code == 200) {
                val info = response.songs.firstOrNull()
                    ?: return Result.failure(Exception("未找到该歌曲"))
                Song(
                    songId = info.id.toString(),
                    title = info.name,
                    artist = info.ar.joinToString(" / ") { it.name },
                    album = info.al?.name ?: "",
                    coverUrl = info.al?.picUrl?.asHttps() ?: "",
                    duration = info.dt,
                    sourceUrl = sourceUrl,
                    platform = MusicPlatform.NETEASE
                ).let { Result.success(it) }
            } else {
                Result.failure(Exception("获取歌曲信息失败：${response.code}"))
            }
        } catch (e: Exception) {
            // fallback: 网易云搜索（按 ID 搜）
            fallbackSingleByName(MusicPlatform.NETEASE, songId, sourceUrl, "网易云")
        }
    }

    /** 酷我单曲：search.kuwo.cn 用关键字搜，再用 rid 拿到详情 */
    private suspend fun parseKuwoSingleSong(songId: String, sourceUrl: String): Result<Song> {
        return try {
            // 尝试拿详情（如果 songId 是数字）
            val r = httpGetKuwoJson("http://www.kuwo.cn/api/www/music/musicInfo?mid=$songId&httpsStatus=1&reqId=test")
            if (r != null) {
                val song = Song(
                    songId = songId,
                    title = r.optString("name").ifBlank { return Result.failure(Exception("无法解析酷我单曲")) },
                    artist = r.optString("artist").replace("&nbsp;", " ").trim(),
                    album = r.optString("album").replace("&nbsp;", " ").trim(),
                    coverUrl = r.optString("pic"),
                    duration = r.optLong("duration", 0L) * 1000L,
                    sourceUrl = sourceUrl,
                    platform = MusicPlatform.KUWO
                )
                Result.success(song)
            } else {
                fallbackSingleByName(MusicPlatform.KUWO, songId, sourceUrl, "酷我")
            }
        } catch (e: Exception) {
            fallbackSingleByName(MusicPlatform.KUWO, songId, sourceUrl, "酷我")
        }
    }

    /** 歌曲宝单曲：从 /music/{id} 页面提取 mp3_title/mp3_author/mp3_cover */
    private suspend fun parseGequbaoSingleSong(songId: String, sourceUrl: String): Result<Song> {
        return try {
            val html = httpGetText("https://www.gequbao.net/music/$songId", mapOf(
                "Referer" to "https://www.gequbao.net/"
            )) ?: return Result.failure(Exception("歌曲宝页面获取失败"))
            val title = Regex("""mp3_title["']?\s*[:=]\s*["']([^"']+)["']""").find(html)?.groupValues?.get(1)
                ?: return Result.failure(Exception("无法解析歌曲宝单曲"))
            val author = Regex("""mp3_author["']?\s*[:=]\s*["']([^"']+)["']""").find(html)?.groupValues?.get(1).orEmpty()
            val cover = Regex("""mp3_cover\s*=\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1).orEmpty()
            Result.success(
                Song(
                    songId = songId,
                    title = title,
                    artist = author,
                    album = "",
                    coverUrl = cover,
                    duration = 0,
                    sourceUrl = sourceUrl,
                    platform = MusicPlatform.GEQUBAO
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("歌曲宝单曲解析失败: ${e.message}"))
        }
    }

    /** Apple Music 单曲：用 iTunes lookup API */
    private suspend fun parseAppleMusicSingleSong(songId: String, sourceUrl: String): Result<Song> {
        return try {
            val json = httpGetJson("https://itunes.apple.com/lookup?id=$songId&country=US")
                ?: return Result.failure(Exception("iTunes lookup 失败"))
            val res = json.optJSONArray("results") ?: return Result.failure(Exception("无结果"))
            val s = res.optJSONObject(0) ?: return Result.failure(Exception("无结果"))
            val preview = s.optString("previewUrl")
            val cover = s.optString("artworkUrl100").ifBlank { s.optString("artworkUrl60") }
            Result.success(
                Song(
                    songId = songId,
                    title = s.optString("trackName").ifBlank { return Result.failure(Exception("无标题")) },
                    artist = s.optString("artistName"),
                    album = s.optString("collectionName"),
                    coverUrl = if (preview.isNotEmpty()) "$cover|preview=$preview" else cover,
                    duration = s.optLong("trackTimeMillis"),
                    sourceUrl = sourceUrl,
                    platform = MusicPlatform.APPLE_MUSIC
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 兜底：用网易云搜索匹配项（粗略） */
    private suspend fun fallbackSingleByName(
        platform: MusicPlatform,
        rawId: String,
        sourceUrl: String,
        keywordHint: String
    ): Result<Song> {
        return try {
            val api = neteaseApi ?: NetworkConfig.createNeteaseApi()
            val r = api.search(rawId, limit = 3)
            if (r.code == 200 && r.result != null) {
                val s = r.result.songs.firstOrNull()
                    ?: return Result.failure(Exception("$keywordHint 单曲暂不支持直接解析，请粘贴歌单链接"))
                Result.success(
                    Song(
                        songId = s.id.toString(),
                        title = s.name,
                        artist = s.ar.joinToString(" / ") { it.name },
                        album = s.al?.name ?: "",
                        coverUrl = s.al?.picUrl?.asHttps() ?: "",
                        duration = s.dt,
                        sourceUrl = sourceUrl,
                        platform = platform
                    )
                )
            } else {
                Result.failure(Exception("$keywordHint 单曲暂不支持直接解析"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("$keywordHint 单曲暂不支持直接解析: ${e.message}"))
        }
    }

    // -----------------------------------------------------------------
    //  单曲播放地址解析
    // -----------------------------------------------------------------
    /**
     * 获取歌曲播放地址。
     *
     * 解析策略：
     *  1) 如果 Song 已带 sourceUrl（非空）→ 直接使用
     *  2) Apple Music → previewUrl（30s 试听，已在搜索时记录到 coverUrl 旁的字段）
     *  3) 网易云 → gdstudio 聚合（已验证可用）
     *  4) 其它平台 → gdstudio 聚合 + fallback 网易云搜索同名
     */
    suspend fun getSongPlayUrl(song: Song): Result<String> = withContext(Dispatchers.IO) {
        if (song.sourceUrl.isNotEmpty()) {
            return@withContext Result.success(song.sourceUrl)
        }
        try {
            when (song.platform) {
                MusicPlatform.APPLE_MUSIC -> getAppleMusicPreview(song)
                MusicPlatform.NETEASE -> getNeteaseUrlViaGd(song.songId)
                MusicPlatform.QQ -> getQQUrlViaGd(song)
                MusicPlatform.KUGOU -> getKugouUrlViaGd(song)
                MusicPlatform.KUWO -> getKuwoUrlViaGd(song)
                MusicPlatform.GEQUBAO -> getGequbaoPlayUrl(song.songId)
                else -> fallbackNeteaseUrlBySearch(song)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 歌曲宝播放地址：POST /api/play-url，id = base64(song_id) */
    private suspend fun getGequbaoPlayUrl(songId: String): Result<String> {
        val playId = runCatching {
            String(Base64.encode(songId.toByteArray(), Base64.NO_WRAP))
        }.getOrDefault(songId)
        val json = httpPostFormJson(
            "https://www.gequbao.net/api/play-url",
            mapOf("id" to playId)
        ) ?: return Result.failure(Exception("歌曲宝播放地址获取失败"))
        val url = json.optJSONObject("data")?.optString("url").orEmpty()
        return if (url.isNotBlank()) Result.success(url)
        else Result.failure(Exception("歌曲宝暂无可用播放源"))
    }

    private fun getAppleMusicPreview(song: Song): Result<String> {
        val preview = song.coverUrl
        // iTunes 搜索结果中 previewUrl 字段我们单独存到 Song 的 coverUrl 之外的逻辑会再处理
        // 这里我们用一个约定字段 _previewUrl（外部搜索时拼到 coverUrl 之后的分隔）
        // 为了简单起见：搜索时把 previewUrl 拼到 coverUrl 后面用 "|preview=" 分隔
        if (preview.contains("|preview=")) {
            val url = preview.substringAfter("|preview=")
            return if (url.isNotBlank()) Result.success(url)
            else Result.failure(Exception("Apple Music 无 preview"))
        }
        return Result.failure(Exception("Apple Music 无 preview"))
    }

    /** gdstudio 解析 - source=netease */
    private suspend fun getNeteaseUrlViaGd(songId: String): Result<String> {
        val json = httpGetJson(
            "https://music-api.gdstudio.xyz/api.php?types=url&source=netease&id=${URLEncoder.encode(songId, "UTF-8")}&br=320"
        ) ?: return fallbackNeteaseSongUrl(songId)
        val url = json.optString("url")
        return if (url.isNotBlank()) Result.success(url)
        else fallbackNeteaseSongUrl(songId)
    }

    /** 网易云备用（走已部署的 NeteaseCloudMusicApi） */
    private suspend fun fallbackNeteaseSongUrl(songId: String): Result<String> {
        return try {
            val api = neteaseApi ?: NetworkConfig.createNeteaseApi()
            val response = api.getSongUrl(songId.toLong())
            val url = response.data.firstOrNull()?.url
            if (!url.isNullOrEmpty()) Result.success(url)
            else Result.failure(Exception("该歌曲暂无版权或需要VIP"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** gdstudio 解析 - QQ（当前 gdstudio 不支持 tencent，但留接口） */
    private suspend fun getQQUrlViaGd(song: Song): Result<String> {
        // gdstudio 不支持 tencent，直接 fallback 网易云搜同名
        return fallbackNeteaseUrlBySearch(song)
    }

    private suspend fun getKugouUrlViaGd(song: Song): Result<String> {
        return fallbackNeteaseUrlBySearch(song)
    }

    private suspend fun getKuwoUrlViaGd(song: Song): Result<String> {
        return fallbackNeteaseUrlBySearch(song)
    }

    private suspend fun fallbackNeteaseUrlBySearch(song: Song): Result<String> {
        return try {
            val api = neteaseApi ?: NetworkConfig.createNeteaseApi()
            val kw = "${song.title} ${song.artist}".trim()
            val searchResponse = api.search(kw, limit = 5)
            if (searchResponse.code == 200 && searchResponse.result != null) {
                for (s in searchResponse.result.songs) {
                    val r = api.getSongUrl(s.id)
                    val url = r.data.firstOrNull()?.url
                    if (!url.isNullOrEmpty()) return Result.success(url)
                }
            }
            Result.failure(Exception("未找到可播放的音频源"))
        } catch (e: Exception) {
            Result.failure(Exception("搜索播放源失败: ${e.message}"))
        }
    }

    // -----------------------------------------------------------------
    //  网易云用户歌单
    // -----------------------------------------------------------------
    suspend fun importNeteaseUserPlaylists(userId: Long): Result<List<Playlist>> {
        return try {
            val api = neteaseApi ?: NetworkConfig.createNeteaseApi()
            val response = api.getUserPlaylists(userId)
            if (response.code == 200) {
                val playlists = response.playlist.map { userPlaylist ->
                    Playlist(
                        playlistId = userPlaylist.id.toString(),
                        name = userPlaylist.name,
                        coverUrl = userPlaylist.coverImgUrl.asHttps() ?: "",
                        description = userPlaylist.description ?: "",
                        songCount = userPlaylist.trackCount,
                        platform = MusicPlatform.NETEASE,
                        sourceUrl = "https://music.163.com/playlist?id=${userPlaylist.id}"
                    )
                }
                Result.success(playlists)
            } else {
                Result.failure(Exception("获取用户歌单失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -----------------------------------------------------------------
    //  多平台搜索
    // -----------------------------------------------------------------
    suspend fun searchSongs(keyword: String, platform: MusicPlatform, limit: Int = 20): Result<List<Song>> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext Result.success(emptyList())
        try {
            val primary = when (platform) {
                MusicPlatform.NETEASE -> searchNetease(keyword, limit)
                MusicPlatform.QQ -> searchQQ(keyword, limit)
                MusicPlatform.KUGOU -> searchKuGou(keyword, limit)
                MusicPlatform.KUWO -> searchKuwo(keyword, limit)
                MusicPlatform.APPLE_MUSIC -> searchAppleMusic(keyword, limit)
                MusicPlatform.GEQUBAO -> searchGequbao(keyword, limit)
                else -> null
            }
            primary?.getOrNull()?.let { return@withContext Result.success(it) }

            // fallback：用网易云搜，结果打上目标平台标签
            fallbackSearchWithPlatform(keyword, platform, limit)
        } catch (e: Exception) {
            runCatching { fallbackSearchWithPlatform(keyword, platform, limit) }
                .fold(onSuccess = { it }, onFailure = { Result.failure(e) })
        }
    }

    //  -----------------------------------------------------------------
    //  网易云推荐：热歌榜随机歌曲
    //  -----------------------------------------------------------------
    private val hotPlaylistIds = listOf(
        "3778678",   // 热歌榜
        "21845217",  // 飙升榜
        "60198",     // 新歌榜
        "60131",     // 原创榜
        "180106",    // 欧美流行
        "60197",     // 云音乐ACG
        "180107",    // 欧美新歌
        "60191",     // 华语金曲
        "60126",     // 韩语榜
        "60128",     // 日语榜
    )

    /** 随机选一个榜单 ID（可指定排除某些 ID） */
    fun randomHotPlaylistId(exclude: Set<String> = emptySet()): String {
        val pool = hotPlaylistIds.filter { it !in exclude }
        return (if (pool.isEmpty()) hotPlaylistIds else pool).random()
    }

    /** 获取所有热门榜单 ID（无限滚动时逐个用） */
    fun getAllHotPlaylistIds(): List<String> = hotPlaylistIds.toList()

    /**
     * 从指定网易云榜单抓取 [count] 首歌曲。
     * 使用 NetworkConfig.neteaseOkHttpClient 自动带 Referer 拦截器，防止网易云拦截。
     *
     * @param playlistId 网易云榜单 ID（不传则随机选一个）
     * @param count      抓取数量（默认 50，够 2 页 24 首）
     */
    suspend fun fetchNeteaseSongsFromPlaylist(
        playlistId: String = hotPlaylistIds.random(),
        count: Int = 50
    ): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://music.163.com/api/playlist/detail?id=$playlistId"
            val json = networkRequestJson(url)
                ?: return@withContext Result.failure(Exception("网易云榜单请求失败(playlist=$playlistId)"))

            val playlist = json.optJSONObject("result") ?: json
            val tracks = playlist.optJSONArray("tracks")
                ?: return@withContext Result.failure(Exception("榜单无曲目"))

            val total = tracks.length()
            if (total == 0) return@withContext Result.failure(Exception("榜单为空"))

            val takeCount = minOf(count, total)
            val songs = (0 until takeCount).mapNotNull { idx ->
                parseNeteaseTrackToSong(tracks.optJSONObject(idx))
            }
            Result.success(songs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从网易云随机抓取 [count] 首歌曲（随机榜单 + 随机抽取）。
     */
    suspend fun fetchRandomNeteaseSongs(count: Int = 24): Result<List<Song>> =
        withContext(Dispatchers.IO) {
            try {
                val id = hotPlaylistIds.random()
                fetchNeteaseSongsFromPlaylist(id, count)
                    .map { it.shuffled().take(count) }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** ============ 网易云专用请求（带 Referer 拦截器） ============ */
    private fun networkRequestJson(url: String): JSONObject? {
        return runCatching {
            val req = okhttp3.Request.Builder()
                .url(url)
                .build()
            NetworkConfig.neteaseOkHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@use null
                val t = body.trim()
                if (t.isEmpty() || (!t.startsWith("{") && !t.startsWith("["))) return@use null
                JSONObject(t)
            }
        }.getOrNull()
    }

    private fun parseNeteaseTrackToSong(s: org.json.JSONObject?): Song? {
        s ?: return null
        val artists = s.optJSONArray("artists")
        val artist = (0 until (artists?.length() ?: 0))
            .joinToString(" / ") { artists?.optJSONObject(it)?.optString("name").orEmpty() }
        val album = s.optJSONObject("album")?.optString("name") ?: ""
        val pic = s.optJSONObject("album")?.optString("picUrl").asHttps() ?: ""
        return Song(
            songId = s.optLong("id").toString(),
            title = s.optString("name"),
            artist = artist,
            album = album,
            coverUrl = pic,
            duration = s.optLong("duration"),
            platform = MusicPlatform.NETEASE
        )
    }

    // -------- 网易云：旧版公开搜索接口（明文） --------
    private suspend fun searchNetease(keyword: String, limit: Int): Result<List<Song>> =
        searchNeteaseByType(keyword, 1, limit).let { it as Result<List<Song>> }

    /** 网易云搜索通用：type=1 单曲, 10 专辑, 100 歌手, 18 歌词 */
    private suspend fun searchNeteaseByType(keyword: String, type: Int, limit: Int): Result<List<Song>> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://music.163.com/api/search/get/web?s=$encoded&type=$type&limit=$limit&offset=0"
        val json = httpGetJson(url, mapOf("Referer" to "https://music.163.com/"))
            ?: return Result.failure(Exception("网易云搜索失败"))
        val result = json.optJSONObject("result") ?: return Result.success(emptyList())
        val songs = result.optJSONArray("songs") ?: return Result.success(emptyList())
        val list = (0 until songs.length()).mapNotNull { i ->
            val s = songs.optJSONObject(i) ?: return@mapNotNull null
            val artists = s.optJSONArray("artists")
            val artist = (0 until (artists?.length() ?: 0)).joinToString(" / ") { j ->
                artists?.optJSONObject(j)?.optString("name").orEmpty()
            }
            val album = s.optJSONObject("album")?.optString("name") ?: ""
            val pic = s.optJSONObject("album")?.optString("picUrl").asHttps() ?: ""
            Song(
                songId = s.optLong("id").toString(),
                title = s.optString("name"),
                artist = artist,
                album = album,
                coverUrl = pic,
                duration = s.optLong("duration"),
                platform = MusicPlatform.NETEASE
            )
        }
        return Result.success(list)
    }

    // -------- 综合搜索：歌词 / 歌手 / 专辑 --------

    /** 按歌词内容搜索（网易云 type=18），返回歌曲列表；同时返回匹配的歌词片段 */
    suspend fun searchByLyric(keyword: String, limit: Int = 20): Result<List<LyricHit>> = withContext(Dispatchers.IO) {
        runCatching {
            val songs = searchNeteaseByType(keyword, 18, limit).getOrNull().orEmpty()
            // 再走一次 type=1（歌曲）作为补充 —— type=18 可能返回的结果量更少
            if (songs.size < 8) {
                val more = searchNeteaseByType(keyword, 1, limit).getOrNull().orEmpty()
                val ids = songs.map { it.songId }.toSet()
                val combined = songs + more.filter { it.songId !in ids }
                // 去重
                Result.success(combined.map { LyricHit(it, keyword) })
            } else {
                Result.success(songs.map { LyricHit(it, keyword) })
            }
        }.getOrElse { Result.failure(it) }
    }

    /** 按歌手搜索（网易云 type=100） */
    suspend fun searchArtists(keyword: String, limit: Int = 15): Result<List<ArtistHit>> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://music.163.com/api/search/get/web?s=$encoded&type=100&limit=$limit&offset=0"
            val json = httpGetJson(url, mapOf("Referer" to "https://music.163.com/"))
                ?: return@runCatching Result.failure(Exception("歌手搜索失败"))
            val result = json.optJSONObject("result") ?: return@runCatching Result.success(emptyList())
            val artists = result.optJSONArray("artists") ?: return@runCatching Result.success(emptyList())
            val hits = (0 until artists.length()).mapNotNull { i ->
                val a = artists.optJSONObject(i) ?: return@mapNotNull null
                ArtistHit(
                    artistId = a.optLong("id").toString(),
                    name = a.optString("name"),
                    coverUrl = a.optString("picUrl").asHttps(),
                    songCount = a.optLong("songSize").toInt()
                )
            }
            Result.success(hits)
        }.getOrElse { Result.failure(it) }
    }

    /** 按专辑搜索（网易云 type=10） */
    suspend fun searchAlbums(keyword: String, limit: Int = 15): Result<List<AlbumHit>> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://music.163.com/api/search/get/web?s=$encoded&type=10&limit=$limit&offset=0"
            val json = httpGetJson(url, mapOf("Referer" to "https://music.163.com/"))
                ?: return@runCatching Result.failure(Exception("专辑搜索失败"))
            val result = json.optJSONObject("result") ?: return@runCatching Result.success(emptyList())
            val albums = result.optJSONArray("albums") ?: return@runCatching Result.success(emptyList())
            val hits = (0 until albums.length()).mapNotNull { i ->
                val a = albums.optJSONObject(i) ?: return@mapNotNull null
                val ar = a.optJSONObject("artist")
                AlbumHit(
                    albumId = a.optLong("id").toString(),
                    title = a.optString("name"),
                    artist = ar?.optString("name").orEmpty(),
                    coverUrl = a.optString("picUrl").asHttps(),
                    publishTime = runCatching {
                        val ts = a.optLong("publishTime")
                        if (ts > 0) java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                            .format(java.util.Date(ts)) else ""
                    }.getOrDefault("")
                )
            }
            Result.success(hits)
        }.getOrElse { Result.failure(it) }
    }

    /** 根据歌手 ID 取热门 Top 10 歌曲 */
    suspend fun fetchArtistTopSongs(artistId: String, limit: Int = 10): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://music.163.com/api/artist/top/song?id=$artistId"
            val json = httpGetJson(url, mapOf("Referer" to "https://music.163.com/"))
                ?: return@runCatching Result.failure(Exception("歌手热门歌曲获取失败"))
            val songs = json.optJSONArray("songs") ?: return@runCatching Result.success(emptyList())
            val list = (0 until minOf(songs.length(), limit)).mapNotNull { i ->
                val s = songs.optJSONObject(i) ?: return@mapNotNull null
                val artists = s.optJSONArray("artists")
                val artist = (0 until (artists?.length() ?: 0)).joinToString(" / ") { j ->
                    artists?.optJSONObject(j)?.optString("name").orEmpty()
                }
                val album = s.optJSONObject("album")?.optString("name") ?: ""
                val pic = s.optJSONObject("album")?.optString("picUrl").asHttps() ?: ""
                Song(
                    songId = s.optLong("id").toString(),
                    title = s.optString("name"),
                    artist = artist,
                    album = album,
                    coverUrl = pic,
                    duration = s.optLong("duration"),
                    platform = MusicPlatform.NETEASE
                )
            }
            Result.success(list)
        }.getOrElse { Result.failure(it) }
    }

    /** 根据专辑 ID 取专辑歌曲 */
    suspend fun fetchAlbumSongs(albumId: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://music.163.com/api/album/$albumId"
            val json = httpGetJson(url, mapOf("Referer" to "https://music.163.com/"))
                ?: return@runCatching Result.failure(Exception("专辑歌曲获取失败"))
            val songs = json.optJSONObject("album")?.optJSONArray("songs")
                ?: json.optJSONArray("songs") ?: return@runCatching Result.success(emptyList())
            val list = (0 until songs.length()).mapNotNull { i ->
                val s = songs.optJSONObject(i) ?: return@mapNotNull null
                val artists = s.optJSONArray("ar")
                val artist = (0 until (artists?.length() ?: 0)).joinToString(" / ") { j ->
                    artists?.optJSONObject(j)?.optString("name").orEmpty()
                }
                val album = s.optJSONObject("al")?.optString("name") ?: ""
                val pic = s.optJSONObject("al")?.optString("picUrl").asHttps() ?: ""
                Song(
                    songId = s.optLong("id").toString(),
                    title = s.optString("name"),
                    artist = artist,
                    album = album,
                    coverUrl = pic,
                    duration = s.optLong("dt"),
                    platform = MusicPlatform.NETEASE
                )
            }
            Result.success(list)
        }.getOrElse { Result.failure(it) }
    }

    // -------- QQ 音乐：DoSearchForQQMusicDesktop --------
    private suspend fun searchQQ(keyword: String, limit: Int): Result<List<Song>> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val data = URLEncoder.encode(
            "{\"search\":{\"method\":\"DoSearchForQQMusicDesktop\",\"module\":\"music.search.SearchCgiService\",\"param\":{\"remoteplace\":\"txt.mqq.search\",\"searchid\":\"\",\"search_type\":\"song\",\"query\":\"$encoded\",\"page_num\":1,\"num_per_page\":$limit}}}",
            "UTF-8"
        )
        val url = "https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data=$data"
        val json = httpGetJson(url, mapOf("Referer" to "https://y.qq.com/"))
            ?: return Result.failure(Exception("QQ 搜索失败"))
        val list = json.optJSONObject("search")
            ?.optJSONObject("data")
            ?.optJSONObject("body")
            ?.optJSONObject("song")
            ?.optJSONArray("list")
            ?: return Result.failure(Exception("QQ 搜索无数据"))
        val songs = (0 until list.length()).mapNotNull { i ->
            val s = list.optJSONObject(i) ?: return@mapNotNull null
            val singers = s.optJSONArray("singer")
            val artist = (0 until (singers?.length() ?: 0)).joinToString(" / ") { j ->
                singers?.optJSONObject(j)?.optString("name").orEmpty()
            }
            Song(
                songId = s.optString("mid").ifBlank { s.optLong("id").toString() },
                title = s.optString("name"),
                artist = artist,
                album = s.optJSONObject("album")?.optString("name") ?: "",
                coverUrl = "",
                duration = s.optLong("interval") * 1000,
                platform = MusicPlatform.QQ
            )
        }
        return Result.success(songs)
    }

    // -------- 酷狗 mobilecdn / song_search_v2 --------
    private suspend fun searchKuGou(keyword: String, limit: Int): Result<List<Song>> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url1 = "http://mobilecdn.kugou.com/api/v3/search/song?keyword=$encoded&page=1&pagesize=$limit"
        val json = httpGetJson(url1) ?: run {
            // 退到 song_search_v2
            val url2 = "https://songsearch.kugou.com/song_search_v2?keyword=$encoded&page=1&pagesize=$limit&platform=WebFilter&format=json"
            httpGetJson(url2)
        } ?: return Result.failure(Exception("酷狗搜索失败"))

        val arr = json.optJSONObject("data")?.optJSONArray("info")
            ?: json.optJSONObject("data")?.optJSONArray("lists")
            ?: return Result.failure(Exception("酷狗搜索无数据"))
        val songs = (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            val hash = s.optString("hash")
                .ifBlank { s.optString("FileHash") }
                .ifBlank { s.optString("SQFileHash") }
            val title = s.optString("songname")
                .ifBlank { s.optString("SongName") }
                .ifBlank { return@mapNotNull null }
            val artist = s.optString("singername")
                .ifBlank { s.optString("SingerName") }
            val album = s.optString("album_name").ifBlank { s.optString("AlbumName") }
            val cover = s.optString("img").ifBlank { s.optString("Img") }
                .ifBlank { s.optString("album_img") }
            val durationMs = when {
                s.has("duration") -> (s.optDouble("duration", 0.0) * 1000).toLong()
                s.has("Duration") -> (s.optDouble("Duration", 0.0) * 1000).toLong()
                else -> 0L
            }
            Song(
                songId = hash.ifBlank { "kugou_$i" },
                title = title,
                artist = artist,
                album = album,
                coverUrl = cover,
                duration = durationMs,
                platform = MusicPlatform.KUGOU
            )
        }
        return Result.success(songs)
    }

    // -------- 酷我：search.kuwo.cn（单引号 JSON） --------
    private suspend fun searchKuwo(keyword: String, limit: Int): Result<List<Song>> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://search.kuwo.cn/r.s?all=$encoded&ft=music&pn=0&rn=$limit&rformat=json&encoding=utf8&client=kt"
        val json = httpGetKuwoJson(url) ?: return Result.failure(Exception("酷我搜索失败"))
        val arr = json.optJSONArray("abslist") ?: return Result.failure(Exception("酷我搜索无数据"))
        val songs = (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            val rid = s.optString("MUSICRID").ifBlank { "MUSIC_${s.optString("DC_TARGETID")}" }
            val title = s.optString("SONGNAME").replace("&nbsp;", " ").trim()
                .ifBlank { return@mapNotNull null }
            Song(
                songId = rid.removePrefix("MUSIC_"),
                title = title,
                artist = s.optString("ARTIST"),
                album = s.optString("ALBUM"),
                coverUrl = "",
                duration = s.optLong("DURATION") * 1000,
                platform = MusicPlatform.KUWO
            )
        }
        return Result.success(songs)
    }

    // -------- Apple Music：itunes.com/search --------
    private suspend fun searchAppleMusic(keyword: String, limit: Int): Result<List<Song>> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$encoded&media=music&entity=song&limit=$limit&country=US"
        val text = httpGetText(url) ?: return Result.failure(Exception("iTunes 搜索失败"))
        val json = runCatching { JSONObject(text) }.getOrNull()
            ?: return Result.failure(Exception("iTunes 响应解析失败"))
        val arr = json.optJSONArray("results") ?: return Result.success(emptyList())
        val songs = (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            val preview = s.optString("previewUrl")
            // 把 previewUrl 通过分隔符塞到 coverUrl 字段，解析播放地址时再剥离
            val cover = s.optString("artworkUrl100").ifBlank { s.optString("artworkUrl60") }
            Song(
                songId = s.optLong("trackId").toString(),
                title = s.optString("trackName").ifBlank { return@mapNotNull null },
                artist = s.optString("artistName"),
                album = s.optString("collectionName"),
                coverUrl = if (preview.isNotEmpty()) "$cover|preview=$preview" else cover,
                duration = s.optLong("trackTimeMillis"),
                platform = MusicPlatform.APPLE_MUSIC
            )
        }
        return Result.success(songs)
    }

    // -------- 歌曲宝 gequbao.net：搜索页 HTML 解析 --------
    private suspend fun searchGequbao(keyword: String, limit: Int): Result<List<Song>> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val html = httpGetText("https://www.gequbao.net/s/$encoded", mapOf(
            "Referer" to "https://www.gequbao.net/"
        )) ?: return Result.failure(Exception("歌曲宝搜索失败"))

        // 每条结果的链接：/search_music?song_id=xxx&kwd=...&title=...&singer=...&page=1
        val linkRegex = Regex("""href="/search_music\?song_id=(\d+)&kwd=[^"]*&title=([^&"]+)&singer=([^&"]+)[^"]*"""")
        val titleRegex = Regex("""<span\s+class="text-primary[^"]*music-title[^"]*"[^>]*>\s*<span>([^<]+)</span>""")
        val singerRegex = Regex("""<small\s+class="text-jade[^"]*"[^>]*>\s*([^<]+?)\s*</small>""")

        val songs = mutableListOf<Song>()
        val seenIds = mutableSetOf<String>()
        val linkMatches = linkRegex.findAll(html).toList()
        for (m in linkMatches) {
            if (songs.size >= limit) break
            val songId = m.groupValues[1]
            if (!seenIds.add(songId)) continue
            val title = runCatching { java.net.URLDecoder.decode(m.groupValues[2], "UTF-8") }.getOrDefault(m.groupValues[2])
            val singer = runCatching { java.net.URLDecoder.decode(m.groupValues[3], "UTF-8") }.getOrDefault(m.groupValues[3])
            songs.add(
                Song(
                    songId = songId,
                    title = title,
                    artist = singer,
                    album = "",
                    coverUrl = "",
                    duration = 0,
                    platform = MusicPlatform.GEQUBAO
                )
            )
        }
        // 兜底：如果正则没匹配到链接，用 title/singer 正则辅助
        if (songs.isEmpty()) {
            val titles = titleRegex.findAll(html).map { it.groupValues[1].trim() }.toList()
            val singers = singerRegex.findAll(html).map { it.groupValues[1].trim() }.toList()
            val ids = Regex("""song_id=(\d+)""").findAll(html).map { it.groupValues[1] }.toList()
            for (i in titles.indices) {
                if (songs.size >= limit) break
                songs.add(
                    Song(
                        songId = ids.getOrNull(i) ?: "gqb_$i",
                        title = titles[i],
                        artist = singers.getOrNull(i) ?: "",
                        album = "",
                        coverUrl = "",
                        duration = 0,
                        platform = MusicPlatform.GEQUBAO
                    )
                )
            }
        }
        return Result.success(songs)
    }

    // -------- 兜底：用网易云搜索结果并改标签 --------
    private suspend fun fallbackSearchWithPlatform(
        keyword: String,
        platform: MusicPlatform,
        limit: Int
    ): Result<List<Song>> {
        val base = searchNetease(keyword, limit).getOrElse { return Result.failure(it) }
        return Result.success(base.map { it.copy(platform = platform) })
    }

    // -----------------------------------------------------------------
    //  歌词
    // -----------------------------------------------------------------
    suspend fun getSongLyric(song: Song): Result<LrcParser.LrcResult> = withContext(Dispatchers.IO) {
        try {
            when (song.platform) {
                MusicPlatform.NETEASE -> getNeteaseLyric(song.songId)
                MusicPlatform.GEQUBAO -> getGequbaoLyric(song)
                else -> getLyricByGdOrSearch(song)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 歌曲宝歌词：从 /search_music 页面的 #content-lrc 提取 LRC */
    private suspend fun getGequbaoLyric(song: Song): Result<LrcParser.LrcResult> {
        val title = URLEncoder.encode(song.title, "UTF-8")
        val singer = URLEncoder.encode(song.artist, "UTF-8")
        val url = "https://www.gequbao.net/search_music?song_id=${song.songId}&title=$title&singer=$singer&page=1"
        val html = httpGetText(url, mapOf("Referer" to "https://www.gequbao.net/"))
            ?: return Result.failure(Exception("歌曲宝歌词获取失败"))
        val lrcMatch = Regex("""id="content-lrc"[^>]*>([\s\S]*?)</(?:div|pre|textarea)>""").find(html)
        val lrcText = lrcMatch?.groupValues?.get(1)?.trim().orEmpty()
        if (lrcText.isBlank()) return Result.failure(Exception("歌曲宝暂无歌词"))
        // 解码 HTML 实体
        val decoded = lrcText
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("<br>", "\n")
            .replace("<br/>", "\n")
            .replace("<br />", "\n")
        return Result.success(LrcParser.parse(decoded))
    }

    private suspend fun getNeteaseLyric(songId: String): Result<LrcParser.LrcResult> {
        // 优先 gdstudio（避免 NeteaseCloudMusicApi 加密）
        val gd = httpGetJson(
            "https://music-api.gdstudio.xyz/api.php?types=lyric&source=netease&id=${URLEncoder.encode(songId, "UTF-8")}"
        )
        val lyric = gd?.optString("lyric").orEmpty()
        if (lyric.isNotBlank()) return Result.success(LrcParser.parse(lyric))

        // fallback: 已部署的 NeteaseCloudMusicApi
        return try {
            val api = neteaseApi ?: NetworkConfig.createNeteaseApi()
            val response = api.getLyric(songId.toLong())
            val text = response.lrc?.lyric ?: ""
            if (text.isNotBlank()) Result.success(LrcParser.parse(text))
            else Result.failure(Exception("暂无歌词"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getLyricByGdOrSearch(song: Song): Result<LrcParser.LrcResult> {
        // 直接用网易云搜索同名，再走 getNeteaseLyric
        return try {
            val api = neteaseApi ?: NetworkConfig.createNeteaseApi()
            val r = api.search("${song.title} ${song.artist}", limit = 3)
            if (r.code == 200 && r.result != null) {
                for (m in r.result.songs) {
                    val l = getNeteaseLyric(m.id.toString())
                    if (l.isSuccess) return l
                }
            }
            Result.failure(Exception("暂无歌词"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -----------------------------------------------------------------
    //  封面补抓（搜索结果 / 歌单导入时某些平台没给封面）
    // -----------------------------------------------------------------
    /**
     * 当 Song.coverUrl 为空或无效时，按平台策略补抓一张封面。
     * 策略：
     *  - NETEASE → /song/detail 拿 al.picUrl
     *  - GEQUBAO → 爬 /music/{id} 拿 mp3_cover
     *  - QQ / KUGOU / KUWO → 网易云搜索同名 + getSongDetail 取封面
     *  - APPLE_MUSIC → 已在搜索时给过封面，直接返回失败
     */
    suspend fun fetchSongCover(song: Song): Result<String> = withContext(Dispatchers.IO) {
        if (song.coverUrl.isNotBlank() && !song.coverUrl.contains("|preview=")) {
            return@withContext Result.success(song.coverUrl)
        }
        try {
            when (song.platform) {
                MusicPlatform.NETEASE -> fetchNeteaseCover(song.songId)
                MusicPlatform.GEQUBAO -> fetchGequbaoCover(song.songId)
                MusicPlatform.QQ, MusicPlatform.KUGOU, MusicPlatform.KUWO -> {
                    fetchCoverViaNeteaseSearch("${song.title} ${song.artist}")
                }
                else -> fetchCoverViaNeteaseSearch("${song.title} ${song.artist}")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchNeteaseCover(songId: String): Result<String> {
        return try {
            val api = neteaseApi ?: NetworkConfig.createNeteaseApi()
            val response = api.getSongDetail(songId)
            val pic = response.songs.firstOrNull()?.al?.picUrl?.asHttps().orEmpty()
            if (pic.isNotBlank()) Result.success(pic)
            else Result.failure(Exception("网易云无封面"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchGequbaoCover(songId: String): Result<String> {
        val html = httpGetText("https://www.gequbao.net/music/$songId", mapOf(
            "Referer" to "https://www.gequbao.net/"
        )) ?: return Result.failure(Exception("歌曲宝页面获取失败"))
        val cover = Regex("""mp3_cover\s*=\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1).orEmpty()
        return if (cover.isNotBlank()) Result.success(cover)
        else Result.failure(Exception("歌曲宝无封面"))
    }

    private suspend fun fetchCoverViaNeteaseSearch(keyword: String): Result<String> {
        val api = neteaseApi ?: NetworkConfig.createNeteaseApi()
        val r = api.search(keyword, limit = 3)
        if (r.code == 200 && r.result != null) {
            for (s in r.result.songs) {
                val pic = s.al?.picUrl?.asHttps().orEmpty()
                if (pic.isNotBlank()) return Result.success(pic)
            }
        }
        return Result.failure(Exception("全网搜索无封面"))
    }

    // -----------------------------------------------------------------
    //  歌单导入（按平台分发）
    // -----------------------------------------------------------------
    private suspend fun fetchPlaylistData(platform: MusicPlatform, playlistId: String): ParsedPlaylistDto {
        return when (platform) {
            MusicPlatform.NETEASE -> fetchNeteasePlaylist(playlistId)
            MusicPlatform.QISHUI -> fetchQishuiPlaylist(playlistId)
            else -> fetchGenericPlaylist(platform, playlistId)
        }
    }

    private suspend fun fetchNeteasePlaylist(playlistId: String): ParsedPlaylistDto {
        val api = neteaseApi ?: NetworkConfig.createNeteaseApi()
        return try {
            val response = api.getPlaylistDetail(playlistId.toLong())
            if (response.code == 200 && response.playlist != null) {
                val playlist = response.playlist
                ParsedPlaylistDto(
                    playlistId = playlist.id.toString(),
                    name = playlist.name,
                    coverUrl = playlist.coverImgUrl.asHttps() ?: "",
                    description = playlist.description ?: "",
                    songCount = playlist.trackCount,
                    songs = playlist.tracks.map { track ->
                        ParsedSongDto(
                            songId = track.id.toString(),
                            title = track.name,
                            artist = track.ar.joinToString(" / ") { it.name },
                            album = track.al?.name ?: "",
                            coverUrl = track.al?.picUrl?.asHttps() ?: playlist.coverImgUrl.asHttps() ?: "",
                            duration = track.dt
                        )
                    }
                )
            } else {
                throw Exception(response.message ?: "获取歌单失败")
            }
        } catch (e: Exception) {
            throw Exception("网易云API服务不可用。请确保已部署 NeteaseCloudMusicApi 服务。\n" +
                    "GitHub: https://github.com/Binaryify/NeteaseCloudMusicApi\n" +
                    "或使用其他平台的分享链接导入。")
        }
    }

    private suspend fun fetchQishuiPlaylist(shareKey: String): ParsedPlaylistDto {
        try {
            val shareUrl = "https://qishui.douyin.com/s/$shareKey"
            val body = httpGetText(shareUrl) ?: return emptyResult(shareKey)
            val songListPattern = Regex("""songList\s*[:=]\s*(\[[^\]]*\])""")
            val songListMatch = songListPattern.find(body)
            if (songListMatch != null) {
                val songsJson = JSONObject("{\"songs\":${songListMatch.groupValues[1]}}")
                val songsArray = songsJson.optJSONArray("songs") ?: return emptyResult(shareKey)
                val songs = mutableListOf<ParsedSongDto>()
                for (i in 0 until songsArray.length()) {
                    val song = songsArray.getJSONObject(i)
                    songs.add(
                        ParsedSongDto(
                            songId = song.optString("id", "qishui_$i"),
                            title = song.optString("title", "未知歌曲"),
                            artist = song.optString("author", "未知艺术家"),
                            album = song.optString("album", ""),
                            coverUrl = song.optString("cover", ""),
                            duration = song.optLong("duration", 0) * 1000
                        )
                    )
                }
                val title = Regex(""""title"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "汽水音乐歌单"
                return ParsedPlaylistDto(
                    playlistId = shareKey, name = title, coverUrl = "",
                    description = "来自汽水音乐的分享歌单", songCount = songs.size, songs = songs
                )
            }
            val jsonDataPattern = Regex("""<script[^>]*id="__NEXT_DATA__"[^>]*>(.*?)</script>""")
            val jsonDataMatch = jsonDataPattern.find(body)
            if (jsonDataMatch != null) {
                val jsonData = JSONObject(jsonDataMatch.groupValues[1])
                val props = jsonData.optJSONObject("props")?.optJSONObject("pageProps")
                if (props != null) {
                    val title = props.optString("title", "汽水音乐歌单")
                    val songsData = props.optJSONArray("songList") ?: props.optJSONArray("songs")
                    val songs = mutableListOf<ParsedSongDto>()
                    if (songsData != null) {
                        for (i in 0 until songsData.length()) {
                            val song = songsData.getJSONObject(i)
                            songs.add(
                                ParsedSongDto(
                                    songId = song.optString("songId", song.optString("id", "qishui_$i")),
                                    title = song.optString("songName", song.optString("title", "未知歌曲")),
                                    artist = song.optString("singerName", song.optString("author", "未知艺术家")),
                                    album = song.optString("albumName", ""),
                                    coverUrl = song.optString("coverUrl", song.optString("cover", "")),
                                    duration = song.optLong("duration", 0) * 1000
                                )
                            )
                        }
                    }
                    return ParsedPlaylistDto(
                        playlistId = shareKey, name = title, coverUrl = "",
                        description = "来自汽水音乐的分享歌单", songCount = songs.size, songs = songs
                    )
                }
            }
            return emptyResult(shareKey)
        } catch (e: Exception) {
            return emptyResult(shareKey)
        }
    }

    private fun emptyResult(shareKey: String) = ParsedPlaylistDto(
        playlistId = shareKey, name = "汽水音乐歌单", coverUrl = "",
        description = "来自汽水音乐的分享歌单\n点击歌曲可搜索试听", songCount = 0, songs = emptyList()
    )

    private suspend fun fetchGenericPlaylist(platform: MusicPlatform, playlistId: String): ParsedPlaylistDto {
        return ParsedPlaylistDto(
            playlistId = playlistId, name = "${platform.displayName}歌单", coverUrl = "",
            description = "来自${platform.displayName}的歌单\n点击歌曲可搜索试听",
            songCount = 0, songs = emptyList()
        )
    }

    private fun ParsedPlaylistDto.toDomainModel(platform: MusicPlatform, sourceUrl: String): Playlist {
        return Playlist(
            playlistId = playlistId, name = name, coverUrl = coverUrl, description = description,
            songCount = songCount, platform = platform, sourceUrl = sourceUrl,
            songs = songs.map { it.toDomainModel(platform) }
        )
    }

    private fun ParsedSongDto.toDomainModel(platform: MusicPlatform): Song {
        return Song(
            songId = songId, title = title, artist = artist, album = album,
            coverUrl = coverUrl, duration = duration, sourceUrl = sourceUrl, platform = platform
        )
    }

    @Suppress("UNUSED")
    private fun unusedToHold() { Retrofit::class.java; GsonConverterFactory::class.java }
}
