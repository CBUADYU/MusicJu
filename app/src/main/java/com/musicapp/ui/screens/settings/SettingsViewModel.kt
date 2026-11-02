package com.musicapp.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicapp.data.local.LoginDataStore
import com.musicapp.data.local.dao.UserPlatformDao
import com.musicapp.data.local.entity.UserPlatformEntity
import com.musicapp.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val platforms: List<UserPlatformEntity> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val newPlatformName: String = "",
    val newPlatformApi: String = "",
    val newPlatformSearchPath: String = "/search",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class SettingsViewModel(
    private val userPlatformDao: UserPlatformDao,
    private val loginDataStore: LoginDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = loginDataStore.themeMode
        .map {
            runCatching { ThemeMode.valueOf(it ?: "") }.getOrDefault(ThemeMode.SYSTEM)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    init {
        viewModelScope.launch {
            userPlatformDao.getAllPlatforms().collect { list ->
                _uiState.value = _uiState.value.copy(
                    platforms = list,
                    isLoading = false
                )
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            loginDataStore.setThemeMode(mode.name)
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            newPlatformName = "",
            newPlatformApi = "",
            newPlatformSearchPath = "/search",
            errorMessage = null
        )
    }

    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun onNewPlatformNameChange(name: String) {
        _uiState.value = _uiState.value.copy(newPlatformName = name)
    }

    fun onNewPlatformApiChange(api: String) {
        _uiState.value = _uiState.value.copy(newPlatformApi = api)
    }

    fun onNewPlatformSearchPathChange(path: String) {
        _uiState.value = _uiState.value.copy(newPlatformSearchPath = path)
    }

    fun addPlatform() {
        val state = _uiState.value
        if (state.newPlatformName.isBlank() || state.newPlatformApi.isBlank()) {
            _uiState.value = state.copy(errorMessage = "名称和API地址不能为空")
            return
        }

        viewModelScope.launch {
            userPlatformDao.insert(
                UserPlatformEntity(
                    name = state.newPlatformName.trim(),
                    apiBaseUrl = state.newPlatformApi.trim(),
                    searchApiPath = state.newPlatformSearchPath.trim()
                )
            )
            _uiState.value = _uiState.value.copy(
                showAddDialog = false,
                successMessage = "已添加 ${state.newPlatformName}"
            )
        }
    }

    fun togglePlatform(platform: UserPlatformEntity) {
        viewModelScope.launch {
            userPlatformDao.update(platform.copy(isEnabled = !platform.isEnabled))
        }
    }

    fun deletePlatform(id: Long) {
        viewModelScope.launch {
            userPlatformDao.deleteById(id)
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    class Factory(
        private val dao: UserPlatformDao,
        private val loginDataStore: LoginDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(dao, loginDataStore) as T
        }
    }
}