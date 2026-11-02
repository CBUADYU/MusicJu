package com.musicapp.ui.screens.song_detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicapp.data.repository.PlaylistRepository
import com.musicapp.domain.model.Playlist
import com.musicapp.domain.model.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * SongDetail ViewModel
 *
 *  - song: 当前展示的歌曲（由外部传入 songId + playlistId 查）
 *  - customPlaylists: 加入歌单选择弹窗里的自定义歌单列表
 *  - showPlaylistPicker / hidePlaylistPicker
 *  - addToPlaylist
 *  - createCustomPlaylist（创建后立即把 song 加进去）
 */
class SongDetailViewModel(
    application: Application,
    private val playlistRepository: PlaylistRepository,
    private val songId: Long,          // Song.id（Room 主键），非 songId 字段
    private val playlistId: Long,      // 所在歌单 id，0 = 来自搜索页不关联
    private val rawSong: Song? = null   // 直接从外面给 Song（搜索/播放列表传入时用）
) : AndroidViewModel(application) {

    private val _song = MutableStateFlow<Song?>(rawSong)
    val song: StateFlow<Song?> = _song.asStateFlow()

    private val _pickPlaylistVisible = MutableStateFlow(false)
    val pickPlaylistVisible = _pickPlaylistVisible.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _isLiked = MutableStateFlow(false)
    val isLiked: StateFlow<Boolean> = _isLiked.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val customPlaylists: StateFlow<List<Playlist>> =
        playlistRepository.getCustomPlaylists()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            if (rawSong == null && playlistId > 0) {
                val list = playlistRepository.getSongsByPlaylistId(playlistId)
                val matched = list.firstOrNull { it.id == songId }
                _song.value = matched
            }
            checkLiked()
        }
    }

    private suspend fun checkLiked() {
        val s = _song.value ?: return
        val pid = playlistRepository.getLikedPlaylistId()
        _isLiked.value = playlistRepository.isLiked(pid, s)
    }

    /** 切换「喜欢」状态 */
    fun toggleLike() {
        val s = _song.value ?: return
        viewModelScope.launch {
            val liked = playlistRepository.toggleLike(s)
            _isLiked.value = liked
            _message.value = if (liked) "已喜欢《${s.title}》" else "已取消喜欢《${s.title}》"
        }
    }

    fun showPlaylistPicker() { _pickPlaylistVisible.value = true }
    fun hidePlaylistPicker() { _pickPlaylistVisible.value = false }

    fun consumeMessage() { _message.value = null }

    fun addToPlaylist(targetId: Long) {
        val s = _song.value ?: run {
            _message.value = "没有可加入的歌曲"
            return
        }
        viewModelScope.launch {
            val added = playlistRepository.addSongsToPlaylist(targetId, listOf(s))
            if (added > 0) {
                val target = playlistRepository.getPlaylistById(targetId)
                _message.value = "已加入「${target?.name ?: "歌单"}」（+${added} 首）"
            } else {
                _message.value = "歌曲已在歌单里"
            }
        }
    }

    fun createCustomPlaylist(name: String) {
        val s = _song.value ?: return
        viewModelScope.launch {
            val newId = playlistRepository.createCustomPlaylist(name)
            val added = playlistRepository.addSongsToPlaylist(newId, listOf(s))
            _message.value = "已创建歌单「$name」并加入歌曲（+${added}）"
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val application: Application,
        private val playlistRepository: PlaylistRepository,
        private val songId: Long,
        private val playlistId: Long,
        private val rawSong: Song? = null,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SongDetailViewModel(application, playlistRepository, songId, playlistId, rawSong) as T
        }
    }
}
