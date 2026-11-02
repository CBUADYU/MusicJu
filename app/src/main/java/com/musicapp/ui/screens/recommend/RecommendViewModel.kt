package com.musicapp.ui.screens.recommend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicapp.data.repository.MusicRepository
import com.musicapp.data.repository.PlaylistRepository
import com.musicapp.domain.model.Song
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 推荐首屏 ViewModel —— 网易云随机热歌无限滚动。
 *
 * 数据源：MusicRepository.fetchNeteaseSongsFromPlaylist()
 *  10 个热门榜单循环用，每次 loadMore 换一个榜单，
 *  一个榜单取 50 首，分多页展示（2 列 × N 行）。
 */
class RecommendViewModel(
    private val playlistRepository: PlaylistRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    /** 当前已用过的榜单索引，loadMore 时换下一个 */
    private var currentPlaylistIndex = 0
    private val playlistIds = musicRepository.getAllHotPlaylistIds()

    init {
        viewModelScope.launch {
            loadFirstPage()
        }
    }

    /** 首次加载 / 顶栏刷新 */
    fun refresh() {
        currentPlaylistIndex = 0
        viewModelScope.launch { loadFirstPage() }
    }

    /** 无限滚动 —— 滑到底触发加载更多 */
    fun loadMore() {
        if (_isLoadingMore.value) return
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            val id = playlistIds[currentPlaylistIndex % playlistIds.size]
            currentPlaylistIndex++

            musicRepository.fetchNeteaseSongsFromPlaylist(playlistId = id, count = 50)
                .onSuccess { newSongs ->
                    // 去重（按 songId + platform）
                    val existingKeys = _songs.value.map { it.songId to it.platform }.toSet()
                    val dedup = newSongs.filter { (it.songId to it.platform) !in existingKeys }
                    _songs.value = _songs.value + dedup
                }
                .onFailure {
                    // 失败就跳过这个榜单，换下一个
                }
            _isLoadingMore.value = false
        }
    }

    private suspend fun loadFirstPage() {
        _isLoading.value = true
        _songs.value = emptyList()
        currentPlaylistIndex = 0
        val id = playlistIds.first()
        musicRepository.fetchNeteaseSongsFromPlaylist(playlistId = id, count = 50)
            .onSuccess { songs ->
                _songs.value = songs
            }
            .onFailure {
                // fallback：本地歌单
                val fallback = runCatching {
                    playlistRepository.getAllPlaylists().first().firstOrNull()
                        ?.let { playlistRepository.getSongsByPlaylistId(it.id) }
                        ?.shuffled()?.take(24) ?: emptyList()
                }.getOrDefault(emptyList())
                _songs.value = fallback
            }
        _isLoading.value = false
    }

    class Factory(
        private val playlistRepository: PlaylistRepository,
        private val musicRepository: MusicRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecommendViewModel(playlistRepository, musicRepository) as T
        }
    }
}
