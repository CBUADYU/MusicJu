package com.musicapp.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicapp.data.repository.MusicRepository
import com.musicapp.data.repository.PlaylistRepository
import com.musicapp.domain.model.AlbumHit
import com.musicapp.domain.model.ArtistHit
import com.musicapp.domain.model.LyricHit
import com.musicapp.domain.model.MusicPlatform
import com.musicapp.domain.model.Song
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchTab(val label: String) {
    ALL("综合"),
    SONG("歌曲"),
    ARTIST("歌手"),
    ALBUM("专辑"),
    LYRIC("歌词")
}

data class SearchUiState(
    val query: String = "",
    val tab: SearchTab = SearchTab.ALL,

    // 综合模式分组结果（ALL tab）
    val songResults: List<Song> = emptyList(),
    val artistResults: List<ArtistHit> = emptyList(),
    val albumResults: List<AlbumHit> = emptyList(),
    val lyricResults: List<LyricHit> = emptyList(),

    // 单 tab 结果
    val songs: List<Song> = emptyList(),
    val artists: List<ArtistHit> = emptyList(),
    val albums: List<AlbumHit> = emptyList(),
    val lyrics: List<LyricHit> = emptyList(),

    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedPlatform: MusicPlatform = MusicPlatform.NETEASE,
    val availablePlatforms: List<MusicPlatform> = listOf(MusicPlatform.NETEASE, MusicPlatform.GEQUBAO)
)

class SearchViewModel(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun selectPlatform(platform: MusicPlatform) {
        _uiState.value = _uiState.value.copy(selectedPlatform = platform)
    }

    fun switchTab(tab: SearchTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
        // 有 query 且已搜过 → 切换 tab 时立刻重新搜该维度
        val q = _uiState.value.query.trim()
        if (q.isNotEmpty()) search()
    }

    /** 回车 / 点按钮触发搜索。按当前 tab 分发到不同搜索实现 */
    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        val tab = _uiState.value.tab

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                when (tab) {
                    SearchTab.ALL -> searchAll(query)
                    SearchTab.SONG -> searchSongs(query)
                    SearchTab.LYRIC -> searchLyrics(query)
                    SearchTab.ARTIST -> searchArtists(query)
                    SearchTab.ALBUM -> searchAlbums(query)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "搜索失败"
                )
            }
        }
    }

    /** 综合搜索：歌曲 + 歌手 + 专辑 + 歌词 并行发请求（都走网易云接口，响应快） */
    private suspend fun searchAll(query: String) {
        val songDeferred = viewModelScope.async {
            musicRepository.searchSongs(query, MusicPlatform.NETEASE, 8).getOrNull().orEmpty()
        }
        val lyricDeferred = viewModelScope.async {
            musicRepository.searchByLyric(query, 6).getOrNull().orEmpty()
        }
        val artistDeferred = viewModelScope.async {
            musicRepository.searchArtists(query, 5).getOrNull().orEmpty()
        }
        val albumDeferred = viewModelScope.async {
            musicRepository.searchAlbums(query, 5).getOrNull().orEmpty()
        }

        val songs = songDeferred.await()
        val lyrics = lyricDeferred.await()
        val artists = artistDeferred.await()
        val albums = albumDeferred.await()

        _uiState.value = _uiState.value.copy(
            songResults = songs,
            lyricResults = lyrics,
            artistResults = artists,
            albumResults = albums,
            isLoading = false
        )
    }

    private suspend fun searchSongs(query: String) {
        val songs = musicRepository.searchSongs(query, _uiState.value.selectedPlatform, 30)
            .getOrElse { _uiState.value.copy(isLoading = false, errorMessage = it.message ?: "歌曲搜索失败"); return }
        _uiState.value = _uiState.value.copy(songs = songs, isLoading = false)
    }

    private suspend fun searchLyrics(query: String) {
        val hits = musicRepository.searchByLyric(query, 30)
            .getOrElse { _uiState.value.copy(isLoading = false, errorMessage = it.message ?: "歌词搜索失败"); return }
        _uiState.value = _uiState.value.copy(lyrics = hits, isLoading = false)
    }

    private suspend fun searchArtists(query: String) {
        val artists = musicRepository.searchArtists(query, 20)
            .getOrElse { _uiState.value.copy(isLoading = false, errorMessage = it.message ?: "歌手搜索失败"); return }
        _uiState.value = _uiState.value.copy(artists = artists, isLoading = false)
    }

    private suspend fun searchAlbums(query: String) {
        val albums = musicRepository.searchAlbums(query, 20)
            .getOrElse { _uiState.value.copy(isLoading = false, errorMessage = it.message ?: "专辑搜索失败"); return }
        _uiState.value = _uiState.value.copy(albums = albums, isLoading = false)
    }

    // =================================================================
    //  播放（给歌曲卡片 / 歌词卡片用）
    // =================================================================
    fun playSong(song: Song, onPlay: (List<Song>, Int) -> Unit) {
        viewModelScope.launch {
            musicRepository.getSongPlayUrl(song)
                .onSuccess { url ->
                    val playableSong = song.copy(sourceUrl = url)
                    val results = currentSongList()
                    val index = results.indexOfFirst {
                        it.songId == song.songId && it.platform == song.platform
                    }
                    onPlay(results.map { s ->
                        if (s.songId == song.songId && s.platform == song.platform) playableSong else s
                    }, index.coerceAtLeast(0))
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: "获取播放地址失败"
                    )
                }
        }
    }

    private fun currentSongList(): List<Song> = when (_uiState.value.tab) {
        SearchTab.ALL -> _uiState.value.songResults
        SearchTab.SONG -> _uiState.value.songs
        SearchTab.LYRIC -> _uiState.value.lyrics.map { it.song }
        SearchTab.ARTIST -> emptyList() // 歌手 tab 点播放 → 单独拉热门
        SearchTab.ALBUM -> emptyList()
    }

    /** 点歌手卡 → 取 Top Songs 播放 */
    fun playArtistSongs(artist: ArtistHit, onPlay: (List<Song>, Int) -> Unit) {
        viewModelScope.launch {
            val songs = musicRepository.fetchArtistTopSongs(artist.artistId, 15)
                .getOrElse { _uiState.value.copy(errorMessage = it.message); return@launch }
            if (songs.isEmpty()) {
                _uiState.value = _uiState.value.copy(errorMessage = "该歌手暂无可播放热门歌曲")
                return@launch
            }
            // 把第一首预解析 URL，其余留给 MusicPlayer 懒加载
            val firstUrl = runCatching {
                musicRepository.getSongPlayUrl(songs.first()).getOrNull()
            }.getOrNull()
            val playableList = songs.mapIndexed { i, s ->
                if (i == 0 && firstUrl != null) s.copy(sourceUrl = firstUrl) else s
            }
            onPlay(playableList, 0)
        }
    }

    /** 点专辑卡 → 取全部歌曲播放 */
    fun playAlbumSongs(album: AlbumHit, onPlay: (List<Song>, Int) -> Unit) {
        viewModelScope.launch {
            val songs = musicRepository.fetchAlbumSongs(album.albumId)
                .getOrElse { _uiState.value.copy(errorMessage = it.message); return@launch }
            if (songs.isEmpty()) {
                _uiState.value = _uiState.value.copy(errorMessage = "该专辑暂无歌曲")
                return@launch
            }
            val firstUrl = runCatching {
                musicRepository.getSongPlayUrl(songs.first()).getOrNull()
            }.getOrNull()
            val playableList = songs.mapIndexed { i, s ->
                if (i == 0 && firstUrl != null) s.copy(sourceUrl = firstUrl) else s
            }
            onPlay(playableList, 0)
        }
    }

    class Factory(
        private val musicRepository: MusicRepository,
        private val playlistRepository: PlaylistRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(musicRepository, playlistRepository) as T
        }
    }
}
