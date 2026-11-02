package com.musicapp.ui.screens.local_music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicapp.data.local.entity.LocalSongEntity
import com.musicapp.data.repository.LocalMusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LocalMusicUiState(
    val songs: List<LocalSongEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val isDecrypting: Boolean = false,
    val showOnlyEncrypted: Boolean = false,
    val scanResult: LocalMusicRepository.ScanStats? = null,
    val decryptResult: LocalMusicRepository.DecryptStats? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class LocalMusicViewModel(
    private val repository: LocalMusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalMusicUiState())
    val uiState: StateFlow<LocalMusicUiState> = _uiState.asStateFlow()

    init {
        loadSongs()
    }

    private fun loadSongs() {
        viewModelScope.launch {
            repository.getAllSongs().collect { songs ->
                _uiState.value = _uiState.value.copy(
                    songs = songs.sortedByDescending { it.dateAdded },
                    isLoading = false
                )
            }
        }
    }

    fun scan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                errorMessage = null,
                successMessage = null
            )
            try {
                val stats = repository.scan()
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanResult = stats,
                    successMessage = "扫描完成：${stats.playableSongs}首可播放，${stats.encryptedSongs}首加密文件"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    errorMessage = "扫描失败：${e.message}"
                )
            }
        }
    }

    fun decryptNcm(songId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDecrypting = true,
                errorMessage = null,
                successMessage = null
            )
            try {
                val result = repository.decryptNcm(songId)
                result.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isDecrypting = false,
                            successMessage = "解密成功"
                        )
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(
                            isDecrypting = false,
                            errorMessage = "解密失败：${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDecrypting = false,
                    errorMessage = "解密失败：${e.message}"
                )
            }
        }
    }

    fun decryptAllNcm() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDecrypting = true,
                errorMessage = null,
                successMessage = null
            )
            try {
                val stats = repository.decryptAllNcm()
                _uiState.value = _uiState.value.copy(
                    isDecrypting = false,
                    decryptResult = stats,
                    successMessage = "解密完成：${stats.success}成功，${stats.failed}失败"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDecrypting = false,
                    errorMessage = "批量解密失败：${e.message}"
                )
            }
        }
    }

    fun toggleFilter() {
        _uiState.value = _uiState.value.copy(
            showOnlyEncrypted = !_uiState.value.showOnlyEncrypted
        )
    }

    fun deleteSong(songId: String) {
        viewModelScope.launch {
            repository.deleteSong(songId)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    class Factory(
        private val repository: LocalMusicRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LocalMusicViewModel(repository) as T
        }
    }
}