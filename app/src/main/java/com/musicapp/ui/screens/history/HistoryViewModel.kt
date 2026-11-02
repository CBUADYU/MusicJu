package com.musicapp.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicapp.data.local.entity.PlayHistoryEntity
import com.musicapp.data.repository.PlayHistoryRepository
import com.musicapp.domain.model.MusicPlatform
import com.musicapp.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val history: List<PlayHistoryEntity> = emptyList(),
    val isLoading: Boolean = true
)

class HistoryViewModel(
    private val repository: PlayHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllHistory().collect { list ->
                _uiState.value = _uiState.value.copy(
                    history = list,
                    isLoading = false
                )
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun toSong(entity: PlayHistoryEntity): Song = Song(
        songId = entity.songId,
        title = entity.title,
        artist = entity.artist,
        album = entity.album,
        coverUrl = entity.coverUrl,
        duration = entity.duration,
        platform = try { MusicPlatform.valueOf(entity.platform) } catch (_: Exception) { MusicPlatform.UNKNOWN }
    )

    class Factory(private val repository: PlayHistoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(repository) as T
        }
    }
}