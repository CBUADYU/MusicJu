package com.musicapp.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicapp.data.repository.PlaylistRepository
import com.musicapp.domain.model.Song
import com.musicapp.util.MusicPlayer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FavoritesViewModel(
    private val repository: PlaylistRepository,
    private val musicPlayer: MusicPlayer? = null
) : ViewModel() {

    private val likedId = MutableStateFlow<Long?>(null)

    val songs: StateFlow<List<Song>> = likedId.flatMapLatest { id ->
        if (id == null || id <= 0L) flowOf(emptyList())
        else repository.observeLikedSongs(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isLoading = MutableStateFlow(true)

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** 透传 MusicPlayer 状态，使 UI 能正确高亮正在播放的歌曲 */
    val currentSong: StateFlow<Song?> = musicPlayer?.currentSong
        ?: MutableStateFlow(null)
    val isPlaying: StateFlow<Boolean> = musicPlayer?.isPlaying
        ?: MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val id = repository.getLikedPlaylistId()
            likedId.value = id
            isLoading.value = false
        }
    }

    fun consumeToast() { _toast.value = null }

    /** 取消喜欢 / 移除 */
    fun removeFromLiked(song: Song) {
        viewModelScope.launch {
            val pid = likedId.value ?: return@launch
            repository.removeSongFromPlaylist(pid, song.id)
            _toast.value = "已取消喜欢「${song.title}」"
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            val liked = repository.toggleLike(song)
            _toast.value = if (liked) "已喜欢「${song.title}」" else "已取消喜欢「${song.title}」"
        }
    }

    class Factory(
        private val repository: PlaylistRepository,
        private val musicPlayer: MusicPlayer? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FavoritesViewModel(repository, musicPlayer) as T
        }
    }
}
