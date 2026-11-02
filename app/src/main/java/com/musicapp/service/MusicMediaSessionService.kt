package com.musicapp.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.musicapp.MainActivity
import com.musicapp.MusicApp
import com.musicapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 音乐播放前台服务 — 荣耀 MagicOS 标准媒体控制适配版。
 *
 * 依据 Android 官方规范：继承 [MediaSessionService]，把 ExoPlayer 交给 [MediaSession]，
 * 系统（通知栏 / 控制中心 / 灵动胶囊）会自动创建标准 MediaStyle 媒体通知，无需手动 startForeground
 * 或手动拼通知。只要发出的是标准媒体通知，MagicOS 即能在通知栏、控制中心、灵动胶囊自动展示与控制。
 *
 * 关键点：
 *  1. MediaSession.Builder(player) 绑定播放器，.build() 后默认 isActive=true
 *  2. 用 [androidx.media3.exoplayer.ExoPlayer] 的真实包装（wrappedPlayer）确保暴露 next/prev 命令，
 *     MagicOS 控制中心 / 灵动胶囊读 PlaybackState.Action 才能看到上/下一曲
 *  3. 通知渠道 IMPORTANCE_LOW — 播放时不响不震，符合系统媒体渠道规范
 *  4. 交由 Media3 的 DefaultMediaNotificationProvider 自动发布/替换媒体通知，不使用手动占位通知
 */
@OptIn(UnstableApi::class)
class MusicMediaSessionService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val MUSIC_CHANNEL_ID = "music_playback_v2"
        private const val TAG = "MusicMediaSession"
        private const val WAKE_LOCK_TAG = "MusicApp:PlaybackWakeLock"

        // 自定义 SessionCommand — 绕过 ExoPlayer 单 item 时 command 不可用的问题
        private const val CUSTOM_CMD_PREVIOUS = "musicapp.action.CMD_PREVIOUS"
        private const val CUSTOM_CMD_NEXT     = "musicapp.action.CMD_NEXT"

        private const val ACTION_START = "com.musicapp.action.START"
        private const val ACTION_PLAYING = "com.musicapp.action.PLAYING"
        private const val ACTION_PAUSED = "com.musicapp.action.PAUSED"

        fun start(context: Context) {
            val intent = Intent(context, MusicMediaSessionService::class.java)
            intent.action = ACTION_START
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun notifyPlaying(context: Context) {
            val intent = Intent(context, MusicMediaSessionService::class.java)
            intent.action = ACTION_PLAYING
            runCatching { context.startService(intent) }
        }

        fun notifyPaused(context: Context) {
            val intent = Intent(context, MusicMediaSessionService::class.java)
            intent.action = ACTION_PAUSED
            runCatching { context.startService(intent) }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val musicPlayer = runCatching { (application as MusicApp).musicPlayer }.getOrNull()
            ?: run { stopSelf(); return }

        // 标准媒体前台服务：
        // 1) 固定 MediaSession 通知渠道（IMPORTANCE_LOW，媒体渠道不响不震）
        ensureNotificationChannel()

        // 2) 用包装播放器构建 MediaSession —— 暴露 next/prev，MagicOS 控制/胶囊才能看到
        val sessionPlayer = musicPlayer.wrappedPlayer

        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(sessionActivity)
            .setId("music_app_session")
            .setCustomLayout(buildCustomLayout())
            .setCallback(MusicSessionCallback(musicPlayer))
            // 灵动胶囊进度条需要实时位置更新
            .setPeriodicPositionUpdateEnabled(true)
            .build()

        // 诊断日志：确认 MediaSession 已创建（MagicOS 识别“正在播放”的关键）
        runCatching { Log.i(TAG, "MediaSession created with player: ${sessionPlayer.javaClass.simpleName}") }

        // 3) 交给 DefaultMediaNotificationProvider —— 自动构建标准 MediaStyle 媒体通知并管理前台
        //    不要再手动 startForeground 占位通知，否则会干扰系统对媒体通知的识别
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(MUSIC_CHANNEL_ID)
                .setChannelName(R.string.music_playback_channel_name)
                .build()
        )
    }

    // -----------------------------------------------------------------
    //  自定义控制按钮布局 —— Health要求灵动胶囊/控制中心要能看到 prev/play/next
    // -----------------------------------------------------------------

    private fun buildCustomLayout(): List<CommandButton> {
        val btnPrev = CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setSessionCommand(SessionCommand(CUSTOM_CMD_PREVIOUS, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_BACK)
            .setDisplayName("上一曲")
            .setEnabled(true)
            .build()

        val btnToggle = CommandButton.Builder(CommandButton.ICON_PAUSE)
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .setSlots(CommandButton.SLOT_CENTRAL)
            .setDisplayName("播放/暂停")
            .setEnabled(true)
            .build()

        val btnNext = CommandButton.Builder(CommandButton.ICON_NEXT)
            .setSessionCommand(SessionCommand(CUSTOM_CMD_NEXT, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_FORWARD)
            .setDisplayName("下一曲")
            .setEnabled(true)
            .build()

        return listOf(btnPrev, btnToggle, btnNext)
    }

    // -----------------------------------------------------------------
    //  MediaSession.Callback
    // -----------------------------------------------------------------

    private inner class MusicSessionCallback(
        private val musicPlayer: com.musicapp.util.MusicPlayer
    ) : MediaSession.Callback {

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): com.google.common.util.concurrent.ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CUSTOM_CMD_PREVIOUS -> runCatching { musicPlayer.playPrevious() }
                CUSTOM_CMD_NEXT     -> runCatching { musicPlayer.playNext() }
            }
            return com.google.common.util.concurrent.Futures.immediateFuture(
                SessionResult(SessionResult.RESULT_SUCCESS)
            )
        }

        /** 耳机线控按钮 */
        override fun onMediaButtonEvent(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            if (intent.action != Intent.ACTION_MEDIA_BUTTON) return false
            val keyEvent = readKeyEvent(intent) ?: return false
            if (keyEvent.action != android.view.KeyEvent.ACTION_UP) return false
            return when (keyEvent.keyCode) {
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    runCatching { musicPlayer.togglePlayPause() }; true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    runCatching { musicPlayer.playNext() }; true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    runCatching { musicPlayer.playPrevious() }; true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                    runCatching { musicPlayer.release() }; true
                }
                else -> false
            }
        }
    }

    /** 兼容读取 MediaButton KeyEvent（API 33+ 用类型化重载，避免弃用警告） */
    @Suppress("DEPRECATION")
    private fun readKeyEvent(intent: Intent): android.view.KeyEvent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }
    }

    // -----------------------------------------------------------------
    //  通知渠道
    // -----------------------------------------------------------------

    private fun ensureNotificationChannel() {
        // DEFAULT（不是 LOW）：Honor/Huawei 会把 LOW 媒体通知折叠进静默桶、状态栏/控制中心不展示。
        // MediaStyle 媒体通知本身不发声，DEFAULT 只提升可见性，不影响静音体验。
        val channel = NotificationChannelCompat.Builder(
            MUSIC_CHANNEL_ID, android.app.NotificationManager.IMPORTANCE_DEFAULT
        )
            .setName("音乐播放")
            .setDescription("显示正在播放的音乐和控制按钮")
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    // -----------------------------------------------------------------
    //  Service 生命周期
    // -----------------------------------------------------------------

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAYING -> acquireWakeLock()
            ACTION_PAUSED -> releaseWakeLock()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val isPlaying = runCatching {
            val p = mediaSession?.player
            p != null && (p.isPlaying || p.playbackState == Player.STATE_BUFFERING)
        }.getOrDefault(false)

        if (isPlaying) return // 继续播放（后台播放）

        serviceScope.launch {
            delay(2000)
            val stillPlaying = runCatching { mediaSession?.player?.isPlaying == true }.getOrDefault(false)
            if (!stillPlaying) {
                releaseWakeLock()
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        mediaSession?.release()
        mediaSession = null
        serviceScope.cancel("Service destroying")
        super.onDestroy()
    }

    // -----------------------------------------------------------------
    //  WakeLock — 后台保活
    // -----------------------------------------------------------------

    @Synchronized
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                .apply {
                    setReferenceCounted(false)
                    acquire(10L * 60 * 1000)
                }
            Handler(Looper.getMainLooper()).postDelayed({ releaseWakeLock() }, 10L * 60 * 1000)
        }
    }

    @Synchronized
    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }
}