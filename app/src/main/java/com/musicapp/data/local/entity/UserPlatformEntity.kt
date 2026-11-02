package com.musicapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_platforms")
data class UserPlatformEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val apiBaseUrl: String,
    val searchApiPath: String = "/search",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)