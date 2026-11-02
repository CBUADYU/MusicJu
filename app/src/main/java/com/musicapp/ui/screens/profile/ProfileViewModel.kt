package com.musicapp.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * 「我的」页 ViewModel。
 * 开源版不含账号体系，固定为游客模式。
 */
class ProfileViewModel : ViewModel() {
    @Suppress("UNCHECKED_CAST")
    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
                return ProfileViewModel() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }
}
