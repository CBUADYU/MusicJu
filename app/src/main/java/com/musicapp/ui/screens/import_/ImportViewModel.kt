package com.musicapp.ui.screens.import_

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicapp.data.repository.MusicRepository
import com.musicapp.data.repository.PlaylistRepository
import com.musicapp.domain.model.Playlist
import com.musicapp.domain.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 导入模式 */
enum class ImportMode { PLAYLIST, SONG }

data class ImportUiState(
    val mode: ImportMode = ImportMode.PLAYLIST,
    val playlistUrl: String = "",
    val songUrl: String = "",
    val isLoading: Boolean = false,
    val result: ImportResult? = null,
    /** 解析后的单曲（仅 SONG 模式） */
    val parsedSong: Song? = null,
    /** 单曲要加入的目标歌单 id（null 表示需要让用户选） */
    val pendingSongTargetPlaylistId: Long? = null
) {
    /** 当前输入框展示用的 url（按 mode 路由到 playlistUrl/songUrl） */
    val currentUrl: String
        get() = when (mode) {
            ImportMode.PLAYLIST -> playlistUrl
            ImportMode.SONG -> songUrl
        }
}

sealed class ImportResult {
    data class Success(val playlist: Playlist) : ImportResult()
    data class SongParsed(val song: Song) : ImportResult()
    data class SongAdded(val playlistName: String, val song: Song) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

class ImportViewModel(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun onUrlChange(url: String) {
        _uiState.value = when (_uiState.value.mode) {
            ImportMode.PLAYLIST -> _uiState.value.copy(
                playlistUrl = url,
                result = null,
                parsedSong = null
            )
            ImportMode.SONG -> _uiState.value.copy(
                songUrl = url,
                result = null,
                parsedSong = null
            )
        }
    }

    fun switchMode(mode: ImportMode) {
        // 切换模式时保留另一模式的 url 输入，避免用户辛苦粘贴的链接被清空
        _uiState.value = _uiState.value.copy(
            mode = mode,
            result = null,
            parsedSong = null
        )
    }

    fun importPlaylist() {
        val url = _uiState.value.playlistUrl.trim()
        if (url.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                result = ImportResult.Error("请输入歌单分享链接")
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, result = null)

            musicRepository.importPlaylist(url)
                .onSuccess { playlist ->
                    if (playlistRepository.isPlaylistExists(playlist.playlistId, playlist.platform)) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            result = ImportResult.Error("该歌单已存在")
                        )
                    } else {
                        playlistRepository.savePlaylist(playlist)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            result = ImportResult.Success(playlist),
                            playlistUrl = ""
                        )
                        autoClearSuccessAfter()
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        result = ImportResult.Error(error.message ?: "导入失败")
                    )
                }
        }
    }

    fun parseSong() {
        val url = _uiState.value.songUrl.trim()
        if (url.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                result = ImportResult.Error("请输入单曲分享链接")
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, result = null, parsedSong = null)

            musicRepository.parseSingleSong(url)
                .onSuccess { song ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        parsedSong = song,
                        result = ImportResult.SongParsed(song)
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        result = ImportResult.Error(error.message ?: "解析失败")
                    )
                }
        }
    }

    /**
     * 把已解析的单曲加入到指定歌单
     */
    fun addParsedSongToPlaylist(playlistId: Long) {
        val song = _uiState.value.parsedSong ?: run {
            _uiState.value = _uiState.value.copy(
                result = ImportResult.Error("请先解析单曲")
            )
            return
        }
        if (playlistId <= 0L) {
            _uiState.value = _uiState.value.copy(
                result = ImportResult.Error("无效的歌单")
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, result = null)
            val playlist = playlistRepository.getPlaylistById(playlistId)
            if (playlist == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    result = ImportResult.Error("歌单不存在")
                )
                return@launch
            }
            // 转换：Song 是 domain model，需要写库（domain 已有 id 字段是 0 时是新建）
            // 这里我们走 PlaylistViewModel 的 addSongToOtherPlaylist 路径最干净。
            // 简化处理：直接 savePlaylist（全量替换 songs）。先读现有 songs。
            val existing = playlistRepository.getSongsByPlaylistId(playlistId)
            val newSong = song.copy(id = 0L) // 让 DB 重新分配
            val updated = playlist.copy(songs = existing + newSong)
            playlistRepository.savePlaylist(updated)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                result = ImportResult.SongAdded(playlist.name, song),
                parsedSong = null,
                songUrl = ""
            )
            autoClearSuccessAfter()
        }
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(result = null)
    }

    /**
     * 3 秒后自动清除成功反馈；不打断用户进一步操作（用户继续操作会被 copy 覆盖而自然取消）
     */
    private fun autoClearSuccessAfter(delayMs: Long = 3000L) {
        viewModelScope.launch {
            delay(delayMs)
            val cur = _uiState.value.result
            if (cur is ImportResult.Success || cur is ImportResult.SongAdded) {
                _uiState.value = _uiState.value.copy(result = null)
            }
        }
    }

    class Factory(
        private val musicRepository: MusicRepository,
        private val playlistRepository: PlaylistRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ImportViewModel(musicRepository, playlistRepository) as T
        }
    }
}
