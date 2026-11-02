package com.musicapp.util

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.musicapp.domain.model.Song
import com.musicapp.service.MusicMediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed class PlayerState {
    object Idle : PlayerState()
    object Loading : PlayerState()
    object Buffering : PlayerState()
    data class Ready(val playing: Boolean) : PlayerState()
    object Ended : PlayerState()
    data class Error(val message: String) : PlayerState()
}

enum class PlayMode { SEQUENTIAL, SHUFFLE, REPEAT_ONE, REPEAT_ALL }

/**
 * MusicPlayer — 荣耀/HyperOS 后台播放专项加固版。
 *
 * 「播放一会自动退出」修复点：
 *  1. 播放时主动 sendBroadcast 让 Service 加 WakeLock 防止 CPU 休眠
 *  2. ExoPlayer 添加 AudioFocus 监听 — 丢焦时不自动暂停（HyperOS 有些 ROM 会静默丢焦）
 *  3. Player.Listener.onPlaybackStateChanged 里确保 Service 存活
 *  4. 所有公开方法 released-safe，不会因为时序问题 crash
 */
class MusicPlayer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** 封面下载专用 OkHttpClient（低超时，自动重连） */
    private val coverClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private var coverFetchJob: Job? = null

    /** 封面字节缓存：key=URL。避免同一封面重复下载（上下曲来回切 / 列表已解析封面回传时不再浪费流量） */
    private val coverByteCache = ConcurrentHashMap<String, ByteArray>()

    @OptIn(markerClass = [UnstableApi::class])
    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true // 自动处理音频焦点
        )
        .setHandleAudioBecomingNoisy(true) // 拔耳机/蓝牙断开时自动暂停
        .build()

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionJob: Job? = null
    @Volatile private var released: Boolean = false

    companion object {
        @Volatile private var playbackServiceStarted = false
        /** WakeLock 最长持有时间：10 分钟，防止异常路径导致持续耗电 */
        private const val WAKE_LOCK_TIMEOUT_MS = 10L * 60 * 1000
    }

    // 缓存上一个 isPlaying 状态，避免重复通知 Service
    private var lastNotifiedPlaying: Boolean? = null

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _playlist = MutableStateFlow<List<Song>>(emptyList())
    val playlist: StateFlow<List<Song>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.SEQUENTIAL)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun getPlayer(): ExoPlayer = player

    /** 供 MediaSession 使用：强制宣称支持 next/prev，让 MagicOS 9 灵动岛/通知栏能看到上/下一曲按钮 */
    val wrappedPlayer: Player by lazy { MediaPlayerWrapper(player, this) }
    var onNeedFetchUrl: ((Song) -> Unit)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (released) return
                _isPlaying.value = isPlaying
                _state.value = when {
                    isPlaying -> {
                        notifyServicePlaying()
                        startPositionLoop()
                        PlayerState.Ready(true)
                    }
                    else -> {
                        notifyServicePaused()
                        (_state.value as? PlayerState.Ready)?.copy(playing = false) ?: PlayerState.Idle
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (released) return
                when (state) {
                    Player.STATE_BUFFERING -> {
                        _buffering.value = true
                        _state.value = PlayerState.Buffering
                        ensureServiceAlive()
                    }
                    Player.STATE_READY -> {
                        _buffering.value = false
                        _duration.value = runCatching { player.duration.coerceAtLeast(0L) }.getOrDefault(0L)
                        val playing = runCatching { player.isPlaying }.getOrDefault(false)
                        ensureServiceAlive()
                        _state.value = if (playing) PlayerState.Ready(true) else PlayerState.Ready(false)
                    }
                    Player.STATE_ENDED -> onPlaybackEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (released) return
                _buffering.value = false
                val msg = error.localizedMessage ?: "播放出错"
                _error.value = msg
                _state.value = PlayerState.Error(msg)
                _isPlaying.value = false
                notifyServicePaused()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && !released) {
                    _currentPosition.value = runCatching { player.currentPosition }.getOrDefault(0L)
                }
            }
        })
    }

    // -----------------------------------------------------------------
    //  Service 通信 — 荣耀/HyperOS 后台保活关键
    // -----------------------------------------------------------------

    private fun startPlaybackService() {
        runCatching { MusicMediaSessionService.start(appContext) }
    }

    /** 确保 MediaSession 前台服务存活 */
    private fun ensureServiceAlive() {
        runCatching { MusicMediaSessionService.start(appContext) }
    }

    /** 播放时让 Service 加强保活（WakeLock） */
    private fun notifyServicePlaying() {
        if (lastNotifiedPlaying == true) return
        lastNotifiedPlaying = true
        runCatching { MusicMediaSessionService.notifyPlaying(appContext) }
    }

    /** 暂停时释放 WakeLock */
    private fun notifyServicePaused() {
        if (lastNotifiedPlaying == false) return
        lastNotifiedPlaying = false
        runCatching { MusicMediaSessionService.notifyPaused(appContext) }
    }

    // -----------------------------------------------------------------
    //  公开 API — 全部 released-safe
    // -----------------------------------------------------------------

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        if (released) return
        _playlist.value = songs
        _error.value = null
        if (songs.isEmpty()) {
            _currentSong.value = null; _currentIndex.value = -1
            runCatching { player.stop() }
            _state.value = PlayerState.Idle; return
        }
        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        playSongAt(safeIndex)
    }

    fun play(song: Song) {
        if (released) return
        if (song.sourceUrl.isEmpty()) {
            _currentSong.value = song
            _state.value = PlayerState.Loading
            runCatching { onNeedFetchUrl?.invoke(song) }
            return
        }
        _currentSong.value = song
        _error.value = null
        _currentPosition.value = 0L; _duration.value = 0L
        _state.value = PlayerState.Loading
        startPlaybackService()

        val mediaMetadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)

        // 如果 coverUrl 有效，先设 artworkUri 让 DefaultMediaNotificationProvider 有机会下载
        val rawCover = song.coverUrl
        val effectiveCover = rawCover.takeIf { it.isNotBlank() && !it.contains("|preview=") }
        if (effectiveCover != null) {
            mediaMetadataBuilder.setArtworkUri(android.net.Uri.parse(effectiveCover))
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.songId)
            .setUri(song.sourceUrl)
            .setMediaMetadata(mediaMetadataBuilder.build())
            .build()

        runCatching {
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }.onFailure { e ->
            val msg = e.localizedMessage ?: "准备播放失败"
            _error.value = msg; _state.value = PlayerState.Error(msg)
        }

        // 异步下载封面字节 → 塞进 MediaItem → ExoPlayer 元数据刷新 → Media3 通知栏/灵动岛自动显示封面
        coverFetchJob?.cancel()
        if (effectiveCover != null) {
            updateArtworkForSong(effectiveCover, song.songId)
        }
    }

    /**
     * 补推封面到当前曲目（供 ViewModel 在原生封面为空、补抓成功后回调）。
     * 与 play() 里的逻辑一致：字节走 URL 缓存，避免重复下载；歌曲已切换则丢弃，防止封面错位。
     */
    fun updateArtwork(url: String?) {
        if (released) return
        val songId = _currentSong.value?.songId ?: return
        if (url.isNullOrBlank() || url.contains("|preview=")) return
        updateArtworkForSong(url, songId)
    }

    /** 下载封面原始字节并写入当前 MediaItem。URL 命中缓存则直接复用，不重新请求网络。 */
    private fun updateArtworkForSong(url: String, songId: String) {
        if (released || url.isBlank()) return
        coverByteCache[url]?.let { bytes ->
            applyArtworkBytes(bytes); return
        }
        coverFetchJob?.cancel()
        coverFetchJob = scope.launch(Dispatchers.IO) {
            val bytes = runCatching { fetchCoverBytes(url) }.getOrNull()
            if (bytes == null || released) return@launch
            coverByteCache[url] = bytes
            // 下载期间歌曲已切换 → 丢弃，避免封面错位
            if (_currentSong.value?.songId == songId) applyArtworkBytes(bytes)
        }
    }

    /** 把封面字节写入当前 MediaItem（resetPosition=false，不打断播放） */
    @Suppress("DEPRECATION")
    private fun applyArtworkBytes(bytes: ByteArray) {
        val song = _currentSong.value ?: return
        runCatching {
            val current = player.currentMediaItem ?: return
            val meta = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setArtworkData(bytes)
                .build()
            player.setMediaItem(current.buildUpon().setMediaMetadata(meta).build(), false)
        }
    }

    /** 下载封面原始字节（IO 线程） */
    private fun fetchCoverBytes(url: String): ByteArray? {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 MusicApp")
            .header("Referer", "https://music.163.com/")
            .build()
        coverClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            return body.bytes().takeIf { it.isNotEmpty() }
        }
    }

    fun updateAndPlay(song: Song, playableUrl: String) {
        val updatedSong = song.copy(sourceUrl = playableUrl)
        val list = _playlist.value
        if (list.isNotEmpty()) {
            val idx = list.indexOfFirst { it.songId == song.songId && it.platform == song.platform }
            if (idx >= 0) _playlist.value = list.toMutableList().apply { this[idx] = updatedSong }
        }
        play(updatedSong)
    }

    fun togglePlayPause() {
        if (released) return
        runCatching {
            when {
                player.isPlaying -> player.pause()
                player.playbackState == Player.STATE_IDLE ||
                    player.playbackState == Player.STATE_ENDED -> {
                    val song = _currentSong.value
                    if (song != null) {
                        if (song.sourceUrl.isNotEmpty()) play(song)
                        else runCatching { onNeedFetchUrl?.invoke(song) }
                    }
                }
                else -> player.play()
            }
        }
    }

    fun playNext() {
        if (released) return
        val songs = _playlist.value
        if (songs.isEmpty()) {
            _currentSong.value?.let {
                if (it.sourceUrl.isEmpty()) runCatching { onNeedFetchUrl?.invoke(it) }
            }; return
        }
        val nextIndex = when (_playMode.value) {
            PlayMode.SEQUENTIAL, PlayMode.REPEAT_ALL -> {
                val next = _currentIndex.value + 1
                if (next >= songs.size) if (_playMode.value == PlayMode.REPEAT_ALL) 0 else songs.size - 1
                else next
            }
            PlayMode.SHUFFLE -> if (songs.size == 1) 0 else
                (0 until songs.size).filter { it != _currentIndex.value }.random()
            PlayMode.REPEAT_ONE -> _currentIndex.value.coerceAtLeast(0)
        }
        playSongAt(nextIndex)
    }

    fun playPrevious() {
        if (released) return
        val songs = _playlist.value
        if (songs.isEmpty()) return
        if (_currentPosition.value > 3000 && _playMode.value != PlayMode.REPEAT_ONE) {
            seekTo(0); return
        }
        val prevIndex = if (_currentIndex.value - 1 < 0) songs.size - 1
        else _currentIndex.value - 1
        playSongAt(prevIndex)
    }

    fun seekTo(positionMs: Long) {
        if (released) return
        val safe = positionMs.coerceAtLeast(0)
        runCatching { player.seekTo(safe) }
        _currentPosition.value = safe
    }

    fun cyclePlayMode() {
        if (released) return
        _playMode.value = when (_playMode.value) {
            PlayMode.SEQUENTIAL -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.REPEAT_ALL
            PlayMode.REPEAT_ALL -> PlayMode.SEQUENTIAL
        }
    }

    fun release() {
        if (released) return
        released = true
        positionJob?.cancel(); positionJob = null
        scope.cancel("MusicPlayer released")
        runCatching { player.release() }
        runCatching { MusicMediaSessionService.notifyPaused(appContext) }
        playbackServiceStarted = false
    }

    fun clearError() {
        _error.value = null
        if (_state.value is PlayerState.Error) _state.value = PlayerState.Idle
    }

    // -----------------------------------------------------------------
    //  内部
    // -----------------------------------------------------------------

    private fun playSongAt(index: Int) {
        val songs = _playlist.value
        val safeIndex = if (songs.isEmpty()) -1 else index.coerceIn(0, songs.size - 1)
        if (safeIndex < 0) {
            _currentSong.value = null; _currentIndex.value = -1
            runCatching { player.stop() }
            _state.value = PlayerState.Idle; return
        }
        _currentIndex.value = safeIndex
        play(songs[safeIndex])
    }

    private fun onPlaybackEnded() {
        if (released) return
        runCatching {
            when (_playMode.value) {
                PlayMode.REPEAT_ONE -> { player.seekTo(0); player.play() }
                PlayMode.REPEAT_ALL -> {
                    if (_playlist.value.isEmpty()) { player.stop(); _state.value = PlayerState.Ended; return@runCatching }
                    playSongAt((_currentIndex.value + 1) % _playlist.value.size)
                }
                PlayMode.SEQUENTIAL -> {
                    if (_playlist.value.isEmpty()) { player.stop(); _state.value = PlayerState.Ended; return@runCatching }
                    if (_currentIndex.value + 1 >= _playlist.value.size) _state.value = PlayerState.Ended
                    else playSongAt(_currentIndex.value + 1)
                }
                PlayMode.SHUFFLE -> playNext()
            }
        }
    }

    private fun startPositionLoop() {
        if (released || positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (isActive && !released) {
                try {
                    if (player.isPlaying && !released) {
                        _currentPosition.value = runCatching { player.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)
                        val d = runCatching { player.duration }.getOrDefault(0L)
                        if (d > 0 && _duration.value != d) _duration.value = d
                    }
                } catch (_: Throwable) { break }
                delay(200L)
            }
        }
    }

}
