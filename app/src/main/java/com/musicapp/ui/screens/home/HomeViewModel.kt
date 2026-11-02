package com.musicapp.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicapp.data.repository.PlaylistRepository
import com.musicapp.domain.model.Playlist
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: PlaylistRepository
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            try {
                repository.getAllPlaylists().collect { list ->
                    _playlists.value = list
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _playlists.value = emptyList()
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist.id)
        }
    }

    fun togglePin(playlist: Playlist) {
        viewModelScope.launch {
            repository.setPinned(playlist.id, !playlist.isPinned)
        }
    }

    fun createCustomPlaylist(name: String) {
        viewModelScope.launch {
            repository.createCustomPlaylist(name)
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    class Factory(private val repository: PlaylistRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}