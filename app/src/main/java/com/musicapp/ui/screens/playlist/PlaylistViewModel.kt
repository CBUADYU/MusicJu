package com.musicapp.ui.screens.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicapp.data.repository.PlaylistRepository
import com.musicapp.domain.model.Playlist
import com.musicapp.domain.model.Song
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlaylistViewModel(
    private val repository: PlaylistRepository,
    private val playlistId: Long
) : ViewModel() {

    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentSongIndex = MutableStateFlow(-1)
    val currentSongIndex: StateFlow<Int> = _currentSongIndex.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** 全部歌单（给「从导入歌单再加入」选择用） */
    val allPlaylists: StateFlow<List<Playlist>> = run {
        val f = MutableStateFlow<List<Playlist>>(emptyList())
        viewModelScope.launch {
            repository.observePlaylists().collect { f.value = it }
        }
        f.asStateFlow()
    }

    fun consumeToast() { _toast.value = null }

    init {
        loadPlaylist()
        loadSongs()
    }

    private fun loadPlaylist() {
        viewModelScope.launch {
            _playlist.value = repository.getPlaylistById(playlistId)
            _isLoading.value = false
        }
    }

    private fun loadSongs() {
        viewModelScope.launch {
            repository.observeSongsByPlaylistId(playlistId).collect { songList ->
                _songs.value = songList
            }
        }
    }

    fun playSong(index: Int) {
        _currentSongIndex.value = index
    }

    fun playNext() {
        val current = _currentSongIndex.value
        val size = _songs.value.size
        if (size > 0) {
            _currentSongIndex.value = (current + 1) % size
        }
    }

    fun playPrevious() {
        val current = _currentSongIndex.value
        val size = _songs.value.size
        if (size > 0) {
            _currentSongIndex.value = if (current - 1 < 0) size - 1 else current - 1
        }
    }

    /** 从其它歌单选择一批歌曲加入当前歌单（用户在歌单详情页添加歌曲入口） */
    suspend fun addSongsFromAnotherPlaylist(sourceId: Long, pickIds: Set<Long>): Int {
        val src = repository.getSongsByPlaylistId(sourceId)
        val toAdd = if (pickIds.isEmpty()) src else src.filter { it.id in pickIds }
        if (toAdd.isEmpty()) return 0
        val added = repository.addSongsToPlaylist(playlistId, toAdd)
        _toast.value = "已加入 $added 首歌曲"
        return added
    }

    /** 把当前歌单一首歌加入目标歌单（长按单曲→加入歌单） */
    suspend fun addSongToOtherPlaylist(song: Song, targetPlaylistId: Long): Int {
        val n = repository.addSongsToPlaylist(targetPlaylistId, listOf(song))
        val targetName = repository.getPlaylistById(targetPlaylistId)?.name ?: "歌单"
        _toast.value = if (n > 0) "已加入「$targetName」（+$n）" else "已在「$targetName」中"
        return n
    }

    suspend fun createCustomPlaylistAndAdd(song: Song, name: String) {
        val id = repository.createCustomPlaylist(name)
        val n = repository.addSongsToPlaylist(id, listOf(song))
        _toast.value = "已创建歌单「$name」并加入歌曲（+$n）"
    }

    fun removeSong(song: Song) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, song.id)
            _toast.value = "已移除「${song.title}」"
        }
    }

    /**
     * 收藏整歌单：在「我喜欢」之外复制一份到自定义歌单（命名为「{原歌单名} · 收藏」）。
     * 重复收藏会跳过已存在的歌曲，并 toast 提示。
     */
    fun favoriteWholePlaylist() {
        viewModelScope.launch {
            val source = _playlist.value ?: return@launch
            val srcSongs = repository.getSongsByPlaylistId(playlistId)
            if (srcSongs.isEmpty()) {
                _toast.value = "歌单为空，无需收藏"
                return@launch
            }
            val newName = "${source.name} · 收藏"
            val newId = repository.createCustomPlaylist(
                name = newName,
                coverUrl = source.coverUrl,
                description = "从「${source.name}」收藏 · ${source.platform.displayName}"
            )
            val added = repository.addSongsToPlaylist(newId, srcSongs)
            _toast.value = if (added > 0) {
                "已收藏到「$newName」（+${added}）"
            } else {
                "已收藏过「$newName」，跳过重复歌曲"
            }
        }
    }

    /** 更新歌单封面（"我喜欢"换头图） */
    fun updateCoverUrl(newCoverUrl: String) {
        viewModelScope.launch {
            repository.updateCoverUrl(playlistId, newCoverUrl)
            // 刷新 playlist
            _playlist.value = repository.getPlaylistById(playlistId)
            _toast.value = "已更新封面"
        }
    }

    class Factory(
        private val repository: PlaylistRepository,
        private val playlistId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlaylistViewModel(repository, playlistId) as T
        }
    }
}