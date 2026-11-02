package com.musicapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.musicapp.data.repository.LocalMusicRepository
import com.musicapp.data.repository.PlaylistRepository
import com.musicapp.ui.common.GlobalMessageBus
import com.musicapp.ui.navigation.AppNavGraph
import com.musicapp.ui.screens.player.PlayerViewModel
import com.musicapp.ui.theme.MusicAppTheme
import com.musicapp.ui.theme.ThemeMode
import com.musicapp.util.MusicPlayer

/**
 * MainActivity — 荣耀/HyperOS 适配要点：
 *  - Manifest 中已声明 configChanges，旋转/窗口切换不会重建 Activity。
 *  - enableEdgeToEdge() 处理打孔屏/全屏屏的安全区。
 *  - 依赖从 MusicApp 单例 lazy 获取，所有调用都 runCatching 保护。
 *  - 播放服务 Intent 延迟到首次播放时才发，避免启动即触发 Android 12+ 的后台启动限制。
 */
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 权限结果由各 Screen 重新触发扫描处理 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 请求媒体权限
        requestMediaPermissions()

        val app = application as MusicApp

        // 所有依赖从 Application lazy 实例获取 — 不会 crash
        val playlistRepository = runCatching {
            PlaylistRepository(app.database.playlistDao(), app.database.songDao())
        }.getOrNull()
        val musicRepository = runCatching { app.musicRepository }.getOrNull()
        val authRepository = runCatching { app.authRepository }.getOrNull()
        val musicPlayer = runCatching { app.musicPlayer }.getOrNull()
        val playHistoryRepository = runCatching { app.playHistoryRepository }.getOrNull()
        val downloadRepository = runCatching { app.downloadRepository }.getOrNull()
        val localMusicRepository = runCatching {
            LocalMusicRepository(app.database.localSongDao(), this)
        }.getOrNull()
        val loginDataStore = runCatching { app.loginDataStore }.getOrNull()
        val userPlatformDao = runCatching { app.database.userPlatformDao() }.getOrNull()

        // 关键依赖缺失时安全退出，同时打印日志便于排查
        if (playlistRepository == null || musicPlayer == null || localMusicRepository == null) {
            Log.e("MainActivity", "Critical dependency initialization failed: " +
                    "playlistRepo=${playlistRepository != null}, " +
                    "musicPlayer=${musicPlayer != null}, " +
                    "localMusicRepo=${localMusicRepository != null}")
            finish(); return
        }

        setContent {
            val themeMode by loginDataStore?.themeMode?.collectAsState(initial = null)
                ?: remember { mutableStateOf(null) }
            val mode = ThemeMode.valueOf(themeMode ?: ThemeMode.SYSTEM.name)

            val navController = rememberNavController()
            val playerViewModel: PlayerViewModel = viewModel(
                factory = PlayerViewModel.Factory(
                    playlistRepository,
                    musicRepository ?: app.musicRepository,
                    musicPlayer,
                    playHistoryRepository,
                    downloadRepository,
                    loginDataStore,
                    app.honorAudioKitManager
                )
            )

            Crossfade(
                targetState = mode,
                animationSpec = tween(220),
                label = "theme_transition"
            ) { m ->
                MusicAppTheme(themeMode = m) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val snack = remember { SnackbarHostState() }
                        LaunchedEffect(snack) {
                            GlobalMessageBus.messages.collect { msg ->
                                snack.showSnackbar(msg)
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            // 全部依赖已 runCatching 获取，安全传入
                            AppNavGraph(
                                navController = navController,
                                playlistRepository = playlistRepository,
                                musicRepository = musicRepository ?: app.musicRepository,
                                authRepository = authRepository ?: app.authRepository,
                                playHistoryRepository = playHistoryRepository
                                    ?: app.playHistoryRepository,
                                downloadRepository = downloadRepository
                                    ?: app.downloadRepository,
                                localMusicRepository = localMusicRepository,
                                playerViewModel = playerViewModel,
                                loginDataStore = loginDataStore,
                                userPlatformDao = userPlatformDao,
                                app = app
                            )
                            SnackbarHost(
                                hostState = snack,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            ) { data -> Snackbar(snackbarData = data) }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Activity 销毁时不 release MusicPlayer — 它由 Application 生命周期管理
        // 但确保 UI 回调解绑
    }

    private fun requestMediaPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needsRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsRequest) {
            runCatching { requestPermissionLauncher.launch(permissions) }
        }
    }
}
