package com.musicapp.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.musicapp.data.repository.MusicRepository
import com.musicapp.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 全局封面缓存。
 *  - 由 MusicApp 在 Application.onCreate 里注入 MusicRepository
 *  - 按 (platform, songId) 缓存已解析的封面 URL
 *  - 避免 SongItem / SearchResultCard 列表渲染时重复发起网络请求
 */
@Stable
object SongCoverCache {

    private val cache = ConcurrentHashMap<String, String>()

    /** 正在进行中的请求：key → Job，用来去重 */
    private val inflight = ConcurrentHashMap<String, MutableStateFlow<String?>>()

    /** 应用生命周期级作用域：替代 GlobalScope，页面销毁不中断，但可被统一取消 */
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var repo: MusicRepository? = null

    fun init(musicRepository: MusicRepository) {
        repo = musicRepository
    }

    private fun key(song: Song): String = "${song.platform.name}_${song.songId}"

    /** 直接从缓存取（可能为 null） */
    fun get(song: Song): String? = cache[key(song)]

    /** 已预带封面（非空且不是 preview 拼接）时直接返回 true */
    fun hasBuiltInCover(song: Song): Boolean =
        song.coverUrl.isNotBlank() && !song.coverUrl.contains("|preview=")

    /** 异步解析封面。返回一个 StateFlow，加载完成后 emit 一次 */
    fun resolve(song: Song): StateFlow<String?> {
        val k = key(song)

        // 1. 原始封面就够用 → 直接 emit
        if (hasBuiltInCover(song)) {
            cache[k] = song.coverUrl
            return MutableStateFlow(song.coverUrl).asStateFlow()
        }

        // 2. 缓存命中
        cache[k]?.let { return MutableStateFlow(it).asStateFlow() }

        // 3. 已有 inflight 请求 → 复用
        inflight[k]?.let { return it.asStateFlow() }

        // 4. 新建 inflight 请求
        val repoRef = repo
        val flow = MutableStateFlow<String?>(null)
        inflight[k] = flow
        cacheScope.launch {
            repoRef?.fetchSongCover(song)?.onSuccess { url ->
                cache[k] = url
                flow.value = url
            }
            inflight.remove(k)
        }
        return flow.asStateFlow()
    }

    fun clear() {
        cache.clear()
        inflight.clear()
    }
}

/**
 * Composable：根据 Song 拿封面 URL。
 *  - 原始 coverUrl 非空 → 直接返回
 *  - 为空 → 异步从 MusicRepository.fetchSongCover 补抓
 *
 * 在 SongItem / SearchResultCard / HistoryScreen 里用：
 *   val cover by rememberSongCover(song)
 *   AsyncImage(model = cover?.takeIf { it.isNotBlank() }, ...)
 */
@Composable
fun rememberSongCover(song: Song): String? {
    // 原始封面直接透传
    if (SongCoverCache.hasBuiltInCover(song)) {
        return song.coverUrl
    }
    // 有缓存直接拿
    SongCoverCache.get(song)?.let { return it }

    // 触发异步解析，返回 StateFlow 并 collect
    val flow = remember(song.platform, song.songId) { SongCoverCache.resolve(song) }
    val cover by flow.collectAsState()
    return cover
}
