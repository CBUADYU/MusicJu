package com.musicapp

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.app.ActivityManager
import com.musicapp.data.local.AppDatabase
import com.musicapp.data.local.LoginDataStore
import com.musicapp.data.remote.api.NeteaseApi
import com.musicapp.data.repository.AuthRepository
import com.musicapp.data.repository.DownloadRepository
import com.musicapp.data.repository.LocalMusicRepository
import com.musicapp.data.repository.MusicRepository
import com.musicapp.data.repository.PlayHistoryRepository
import com.musicapp.data.repository.PlaylistRepository
import com.musicapp.util.HonorAudioKitManager
import com.musicapp.util.MusicPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * MusicApp — Application 入口。
 *
 * 荣耀/HyperOS 适配要点：
 *  - musicPlayer 使用 lazy(SYNCHRONIZED) 初始化，避免多进程或提前访问导致 UninitializedPropertyAccessException。
 *  - 持有 appScope(SupervisorJob)，所有后台协程在此 scope 运行。
 *  - createNotificationChannels 兼容 HyperOS 严格的通知渠道策略。
 */
class MusicApp : Application() {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val loginDataStore: LoginDataStore by lazy { LoginDataStore(this) }

    val authRepository: AuthRepository by lazy {
        AuthRepository(loginDataStore)
    }
    val playHistoryRepository: PlayHistoryRepository by lazy {
        PlayHistoryRepository(database.playHistoryDao())
    }
    val downloadRepository: DownloadRepository by lazy { DownloadRepository(this) }
    val musicRepository: MusicRepository by lazy { MusicRepository() }
    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(database.playlistDao(), database.songDao())
    }
    val localMusicRepository: LocalMusicRepository by lazy {
        LocalMusicRepository(database.localSongDao(), this@MusicApp)
    }

    // 关键修复：从 lateinit 改为 lazy(SYNCHRONIZED)，线程安全，不会在任何时序下 crash
    val musicPlayer: MusicPlayer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MusicPlayer(this)
    }

    // 荣耀 Audio Kit：三维声内容接入 / 高清播放 / 空间音频
    val honorAudioKitManager: HonorAudioKitManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HonorAudioKitManager(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannels()

        if (isMainProcess()) {
            runCatching { musicPlayer }
            runCatching { honorAudioKitManager.initialize() }
            authRepository.setNeteaseApiBaseUrl(NeteaseApi.DEFAULT_BASE_URL)
            musicRepository.setNeteaseApiBaseUrl(NeteaseApi.DEFAULT_BASE_URL)
            com.musicapp.util.SongCoverCache.init(musicRepository)
        }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        val mediaChannel = NotificationChannel(
            CHANNEL_MEDIA,
            "音乐播放",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "显示当前正在播放的音乐和控制按钮"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableLights(false)
            enableVibration(false)
        }
        nm.createNotificationChannel(mediaChannel)

        val infoChannel = NotificationChannel(
            CHANNEL_INFO,
            "应用通知",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "应用内消息、下载进度等通知"
            setShowBadge(true)
        }
        nm.createNotificationChannel(infoChannel)
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel("Application terminating")
        runCatching { musicPlayer.release() }
        runCatching { honorAudioKitManager.destroy() }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        runCatching { database.close() }
    }

    private fun isMainProcess(): Boolean {
        val pid = android.os.Process.myPid()
        val manager = getSystemService(ActivityManager::class.java)
        @Suppress("DEPRECATION")
        return manager.runningAppProcesses?.any {
            it.pid == pid && it.processName == packageName
        } ?: true
    }

    companion object {
        @Volatile
        lateinit var instance: MusicApp
            private set

        // 注意：v2 渠道 ID 是刻意的——因为 Android 渠道重要性创建后即被锁定，
        // 老用户设备上旧 "music_playback"(LOW) 已锁死无法调高，而 Honor 会隐藏 LOW 媒体通知。
        // 换新 ID 强制创建一条 DEFAULT 渠道，保证控制中心/灵动胶囊可见。
        const val CHANNEL_MEDIA = "music_playback_v2"
        const val CHANNEL_INFO = "music_app_info"
    }
}
