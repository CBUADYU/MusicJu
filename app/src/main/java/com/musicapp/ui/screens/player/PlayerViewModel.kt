package com.musicapp.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicapp.data.local.LoginDataStore
import com.musicapp.data.repository.DownloadRepository
import com.musicapp.data.repository.MusicRepository
import com.musicapp.data.repository.PlayHistoryRepository
import com.musicapp.data.repository.PlaylistRepository
import com.musicapp.domain.model.Song
import com.musicapp.util.LrcParser
import com.musicapp.util.LrcParser.LrcLine
import com.musicapp.ui.screens.player.lyric.LyricTheme
import com.musicapp.ui.screens.player.lyric.LyricRenderEngine
import com.musicapp.util.HonorAudioKitManager
import com.musicapp.util.MusicPlayer
import com.musicapp.util.PlayerState
import com.musicapp.util.PlayMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 播放器 ViewModel — 薄包装层
 *
 * 职责：
 *  1. 转发 UI 操作到 MusicPlayer
 *  2. 协调 URL 解析 / 歌词加载（涉及 IO 异步）
 *  3. 维护歌词状态、下载状态、临时错误
 *
 * 不再持有 playlist/index/position —— 这些都由 MusicPlayer 唯一管理。
 */
class PlayerViewModel(
    private val repository: PlaylistRepository,
    private val musicRepository: MusicRepository,
    private val musicPlayer: MusicPlayer,
    private val historyRepository: PlayHistoryRepository? = null,
    private val downloadRepository: DownloadRepository? = null,
    private val loginDataStore: LoginDataStore? = null,
    private val honorAudioKit: HonorAudioKitManager? = null
) : ViewModel() {

    // ---- 直通 MusicPlayer 的状态 ----
    val currentSong: StateFlow<Song?> = musicPlayer.currentSong
    val isPlaying: StateFlow<Boolean> = musicPlayer.isPlaying
    val currentPosition: StateFlow<Long> = musicPlayer.currentPosition
    val duration: StateFlow<Long> = musicPlayer.duration
    val playlist: StateFlow<List<Song>> = musicPlayer.playlist
    val currentIndex: StateFlow<Int> = musicPlayer.currentIndex
    val buffering: StateFlow<Boolean> = musicPlayer.buffering
    val playMode: StateFlow<PlayMode> = musicPlayer.playMode
    val state: StateFlow<PlayerState> = musicPlayer.state
    val error: StateFlow<String?> = musicPlayer.error

    // ---- 荣耀 Audio Kit 状态直通 ----
    val isAudioKitSupported: StateFlow<Boolean> =
        honorAudioKit?.isDeviceSupported ?: kotlinx.coroutines.flow.MutableStateFlow(false)
    val spatialAudioAvailable: StateFlow<Boolean> =
        honorAudioKit?.spatialAudioAvailable ?: kotlinx.coroutines.flow.MutableStateFlow(false)
    val spatialAudioEnabled: StateFlow<Boolean> =
        honorAudioKit?.spatialAudioEnabled ?: kotlinx.coroutines.flow.MutableStateFlow(false)
    val isMultichannelSupported: StateFlow<Boolean> =
        honorAudioKit?.isMultichannelSupported ?: kotlinx.coroutines.flow.MutableStateFlow(false)
    val isHighSampleRateSupported: StateFlow<Boolean> =
        honorAudioKit?.isHighSampleRateSupported ?: kotlinx.coroutines.flow.MutableStateFlow(false)
    val highSampleRateEnabled: StateFlow<Boolean> =
        honorAudioKit?.highSampleRateEnabled ?: kotlinx.coroutines.flow.MutableStateFlow(false)
    val supportedChannelFormats: StateFlow<List<HonorAudioKitManager.ChannelFormatInfo>> =
        honorAudioKit?.supportedChannelFormats ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    val audioKitStatus: StateFlow<HonorAudioKitManager.ServiceStatus> =
        honorAudioKit?.serviceStatus ?: kotlinx.coroutines.flow.MutableStateFlow(HonorAudioKitManager.ServiceStatus.IDLE)

    /** 暴露底层 MusicPlayer（供需要直接读取状态的子 ViewModel，如 FavoritesViewModel） */
    fun getMusicPlayer(): MusicPlayer = musicPlayer

    // ---- ViewModel 自身维护的状态 ----
    private val _loadingUrl = MutableStateFlow(false)
    val loadingUrl: StateFlow<Boolean> = _loadingUrl.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _lrcLines = MutableStateFlow<List<LrcLine>>(emptyList())
    val lrcLines: StateFlow<List<LrcLine>> = _lrcLines.asStateFlow()

    val lyrics: StateFlow<LrcResultView> = _lrcLines.map { LrcResultView(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LrcResultView(emptyList()))

    private val _currentLrcIndex = MutableStateFlow(-1)
    val currentLrcIndex: StateFlow<Int> = _currentLrcIndex.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    /** 歌词动画主题（默认 Folia 滚动，持久化到 DataStore） */
    private val _lyricTheme = MutableStateFlow(LyricTheme.FOLIA_SCROLL)
    val lyricTheme: StateFlow<LyricTheme> = _lyricTheme.asStateFlow()

    /** 歌词渲染引擎（默认 Folia WebGL 原版，持久化到 DataStore） */
    private val _lyricRenderEngine = MutableStateFlow(LyricRenderEngine.FOLIA_WEB)
    val lyricRenderEngine: StateFlow<LyricRenderEngine> = _lyricRenderEngine.asStateFlow()

    /** 播放音质偏好（默认 320k，可由用户在播放页切换） */
    private val _quality = MutableStateFlow(PlayerQuality.STANDARD)
    val quality: StateFlow<PlayerQuality> = _quality.asStateFlow()

    /** 用户手动关联的歌词（内存缓存 + DataStore 持久化） */
    private val manualLyrics = mutableMapOf<String, String>()
    private var manualLyricsLoaded = false

    /** 当前在跑的 fetchLyric 任务：切歌时取消前一个避免歌词错位 */
    private var fetchLyricJob: Job? = null

    /** 当前在跑的封面补抓任务：切歌时取消前一个 */
    private var fetchCoverJob: Job? = null

    /** 补抓后的封面 URL（原始 coverUrl 非空时直接等于它；为空时异步填充） */
    private val _coverUrl = MutableStateFlow<String?>(null)
    val coverUrl: StateFlow<String?> = _coverUrl.asStateFlow()

    /** UI 用的"任意加载中"聚合（封面 loader 看这个） */
    fun isAnyLoading(): Boolean =
        loadingUrl.value || buffering.value || state.value is PlayerState.Loading

    init {
        // 预加载手动歌词表
        viewModelScope.launch {
            loginDataStore?.let { store ->
                val map = store.manualLyrics.first()
                manualLyrics.putAll(map)
                manualLyricsLoaded = true
                // 如果当前已有 currentSong，重新跑一遍歌词加载以应用持久化结果
                currentSong.value?.let { fetchLyric(it) }
                // 同时恢复歌词动画主题和渲染引擎
                store.lyricThemeId.first()?.let { id ->
                    _lyricTheme.value = LyricTheme.fromId(id)
                }
                store.lyricRenderEngineId.first()?.let { id ->
                    _lyricRenderEngine.value = LyricRenderEngine.fromId(id)
                }
            } ?: run { manualLyricsLoaded = true }
        }
        // 进度 → 当前歌词行
        viewModelScope.launch {
            currentPosition.collect { pos ->
                val lines = _lrcLines.value
                if (lines.isNotEmpty()) {
                    _currentLrcIndex.value = LrcParser.findCurrentLineIndex(lines, pos)
                }
            }
        }
        // 当 MusicPlayer 需要 URL 时调 fetchAndPlay
        musicPlayer.onNeedFetchUrl = { song -> fetchAndPlay(song) }
        // 当 currentSong 变化时，自动加载歌词 + 补抓封面
        viewModelScope.launch {
            currentSong.collect { song ->
                if (song != null) {
                    addHistory(song)
                    if (manualLyricsLoaded) fetchLyric(song)
                    fetchOrUseCover(song)
                } else {
                    _coverUrl.value = null
                }
            }
        }
    }

    // =================================================================
    //  播放入口
    // =================================================================

    fun loadPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val songs = repository.getSongsByPlaylistId(playlistId)
            if (songs.isNotEmpty()) musicPlayer.setPlaylist(songs, 0)
        }
    }

    /** 播放某歌单/列表中的若干首 */
    fun playSong(songs: List<Song>, index: Int) {
        val safeIndex = index.coerceAtLeast(0)
        musicPlayer.setPlaylist(songs, safeIndex)
    }

    fun togglePlayPause() = musicPlayer.togglePlayPause()

    // —— 喜欢 / 歌单 ——
    private val _isCurrentLiked = MutableStateFlow(false)
    val isCurrentLiked: StateFlow<Boolean> = _isCurrentLiked.asStateFlow()

    private val _customPlaylists = MutableStateFlow<List<com.musicapp.domain.model.Playlist>>(emptyList())
    val customPlaylists: StateFlow<List<com.musicapp.domain.model.Playlist>> = _customPlaylists.asStateFlow()

    private val _pickPlaylistVisible = MutableStateFlow(false)
    val pickPlaylistVisible: StateFlow<Boolean> = _pickPlaylistVisible.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            currentSong.collect { song ->
                if (song != null) {
                    val pid = repository.getLikedPlaylistId()
                    _isCurrentLiked.value = repository.isLiked(pid, song)
                } else {
                    _isCurrentLiked.value = false
                }
            }
        }
    }

    fun toggleLikeCurrent() {
        val song = currentSong.value ?: return
        viewModelScope.launch {
            _isCurrentLiked.value = repository.toggleLike(song)
        }
    }

    fun refreshCustomPlaylists() {
        viewModelScope.launch {
            repository.getCustomPlaylists().collect { _customPlaylists.value = it }
        }
    }

    fun showPlaylistPicker() {
        _pickPlaylistVisible.value = true
        refreshCustomPlaylists()
    }

    fun hidePlaylistPicker() { _pickPlaylistVisible.value = false }

    fun consumeMessage() { _message.value = null }

    fun addCurrentToPlaylist(playlistId: Long) {
        val song = currentSong.value ?: return
        viewModelScope.launch {
            val added = repository.addSongsToPlaylist(playlistId, listOf(song))
            val target = repository.getPlaylistById(playlistId)
            _message.value = if (added > 0) {
                "已加入「${target?.name ?: "歌单"}」"
            } else {
                "歌曲已在歌单里"
            }
            hidePlaylistPicker()
        }
    }

    fun createCustomPlaylist(name: String) {
        val song = currentSong.value ?: return
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val newId = repository.createCustomPlaylist(clean)
            val added = repository.addSongsToPlaylist(newId, listOf(song))
            _message.value = "已创建歌单「$clean」并加入歌曲"
            hidePlaylistPicker()
        }
    }

    fun playNext() = musicPlayer.playNext()
    fun playPrevious() = musicPlayer.playPrevious()
    fun seekTo(positionMs: Long) = musicPlayer.seekTo(positionMs)
    fun seekToLrcLine(timeMs: Long) = musicPlayer.seekTo(timeMs)
    fun cyclePlayMode() = musicPlayer.cyclePlayMode()
    fun clearError() = musicPlayer.clearError()

    fun toggleLyrics() {
        _showLyrics.value = !_showLyrics.value
    }

    /** 切换歌词动画主题，自动持久化 */
    fun setLyricTheme(theme: LyricTheme) {
        _lyricTheme.value = theme
        loginDataStore?.let { store ->
            viewModelScope.launch { store.setLyricThemeId(theme.id) }
        }
    }

    /** 切换歌词渲染引擎，自动持久化 */
    fun setLyricRenderEngine(engine: LyricRenderEngine) {
        _lyricRenderEngine.value = engine
        loginDataStore?.let { store ->
            viewModelScope.launch { store.setLyricRenderEngineId(engine.id) }
        }
    }

    fun downloadCurrentSong() {
        val song = currentSong.value ?: return
        if (song.sourceUrl.isEmpty()) {
            _downloadState.value = DownloadState.Error("暂无播放地址")
            return
        }
        downloadRepository?.let { repo ->
            viewModelScope.launch {
                _downloadState.value = DownloadState.Downloading
                try {
                    repo.downloadSong(song, song.sourceUrl)
                    _downloadState.value = DownloadState.Success
                } catch (e: Exception) {
                    _downloadState.value = DownloadState.Error(e.message ?: "下载失败")
                }
            }
        } ?: run {
            _downloadState.value = DownloadState.Error("下载功能未初始化")
        }
    }

    fun clearDownloadState() {
        _downloadState.value = DownloadState.Idle
    }

    // =================================================================
    //  歌词
    // =================================================================

    fun hasManualLyric(song: Song?): Boolean {
        song ?: return false
        return manualLyrics[lyricKey(song)]?.isNotBlank() == true
    }

    fun getManualLyricText(song: Song?): String {
        song ?: return ""
        return manualLyrics[lyricKey(song)] ?: ""
    }

    fun setManualLyric(lrc: String) {
        val song = currentSong.value ?: return
        val lines = toLyricLines(lrc)
        if (lines.isNotEmpty()) {
            manualLyrics[lyricKey(song)] = lrc
            persistManualLyrics()
            _lrcLines.value = lines
            _currentLrcIndex.value = -1
            _showLyrics.value = true
        }
    }

    fun clearManualLyric() {
        val song = currentSong.value ?: return
        manualLyrics.remove(lyricKey(song))
        persistManualLyrics()
        fetchLyric(song)
    }

    private fun toLyricLines(lrc: String): List<LrcLine> {
        val parsed = LrcParser.parse(lrc)
        if (parsed.lines.isNotEmpty()) return parsed.lines
        return lrc.lines().mapNotNull { it.trim() }.filter { it.isNotEmpty() }
            .mapIndexed { i, text -> LrcLine(timeMs = i * 3000L, text = text, rawTime = "") }
    }

    private fun persistManualLyrics() {
        loginDataStore?.let { store ->
            viewModelScope.launch { store.setManualLyrics(manualLyrics.toMap()) }
        }
    }

    private fun fetchLyric(song: Song) {
        // 取消上一次未完成的歌词加载，避免快速切歌时歌词错位
        fetchLyricJob?.cancel()
        fetchLyricJob = viewModelScope.launch {
            _lrcLines.value = emptyList()
            _currentLrcIndex.value = -1
            manualLyrics[lyricKey(song)]?.let { lrc ->
                _lrcLines.value = toLyricLines(lrc)
                return@launch
            }
            musicRepository.getSongLyric(song)
                .onSuccess { result -> _lrcLines.value = result.lines }
        }
    }

    /**
     * 切歌时处理封面：
     *  1. 原始 coverUrl 非空且不是 Apple 的 preview 拼接 → 直接用
     *  2. 否则 → 异步补抓，取消上一次任务
     */
    private fun fetchOrUseCover(song: Song) {
        fetchCoverJob?.cancel()
        val raw = song.coverUrl
        if (raw.isNotBlank() && !raw.contains("|preview=")) {
            _coverUrl.value = raw
            return
        }
        _coverUrl.value = null
        fetchCoverJob = viewModelScope.launch {
            musicRepository.fetchSongCover(song)
                .onSuccess { url ->
                    _coverUrl.value = url
                    // 原生封面为空时，上游 play() 不会给通知栏/灵动岛附封面；
                    // 补抓成功后把封面回传给 MusicPlayer，让 Media3 更新通知栏艺术图
                    if (url.isNotBlank() && !url.contains("|preview=")) {
                        musicPlayer.updateArtwork(url)
                    }
                }
        }
    }

    /** 用户切换音质（仅偏好保存到内存；实际音质由各平台接口决定，此处作为 UI 入口） */
    fun setQuality(quality: PlayerQuality) {
        _quality.value = quality
    }

    /** 开关荣耀高清音频播放（96~192kHz 无损） */
    fun toggleHighSampleRatePlay() {
        val kit = honorAudioKit ?: return
        kit.setHighSampleRatePlay(!kit.highSampleRateEnabled.value)
    }

    /** 主动刷新空间音频状态 */
    fun refreshSpatialAudio() {
        honorAudioKit?.refreshSpatialAudio()
    }

    /** 查询设备支持的三维声格式 */
    fun queryChannelFormats() {
        honorAudioKit?.queryMultichannelFormats()
    }

    // =================================================================
    //  URL 解析（被 MusicPlayer.onNeedFetchUrl 回调）
    // =================================================================

    private fun fetchAndPlay(song: Song) {
        viewModelScope.launch {
            _loadingUrl.value = true
            musicRepository.getSongPlayUrl(song)
                .onSuccess { url ->
                    musicPlayer.updateAndPlay(song, url)
                    _loadingUrl.value = false
                }
                .onFailure {
                    _loadingUrl.value = false
                    _downloadState.value = DownloadState.Error(it.message ?: "获取播放地址失败")
                }
        }
    }

    private fun addHistory(song: Song) {
        historyRepository?.let { repo ->
            viewModelScope.launch { repo.addToHistory(song) }
        }
    }

    private fun lyricKey(song: Song) = "${song.platform.name}|${song.songId}|${song.title}"

    data class LrcResultView(val lines: List<LrcLine>)

    /** 音质偏好（UI 选择；具体 URL 仍由平台解析接口决定） */
    enum class PlayerQuality(val label: String) {
        STANDARD("标准 128k"),
        HIGH("高品质 320k"),
        LOSSLESS("无损 FLAC")
    }

    sealed class DownloadState {
        object Idle : DownloadState()
        object Downloading : DownloadState()
        object Success : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    class Factory(
        private val repository: PlaylistRepository,
        private val musicRepository: MusicRepository,
        private val musicPlayer: MusicPlayer,
        private val historyRepository: PlayHistoryRepository? = null,
        private val downloadRepository: DownloadRepository? = null,
        private val loginDataStore: LoginDataStore? = null,
        private val honorAudioKit: HonorAudioKitManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(
                repository, musicRepository, musicPlayer,
                historyRepository, downloadRepository, loginDataStore, honorAudioKit
            ) as T
        }
    }
}
