package com.musicapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.musicapp.ui.screens.player.PlayerViewModel

/**
 * 底部 Tab 外壳 —— 主内容 + 迷你播放器 + iOS 风格底栏。
 *
 * 布局从上到下：
 *  - 主内容（可滚动）
 *  - MiniPlayer（播放中有歌曲时滑入）
 *  - MainBottomBar（推荐/歌单/我的）
 */
@Composable
fun BottomTabScreen(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    playerViewModel: PlayerViewModel? = null,
    onOpenPlayer: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
            content()
        }

        // 迷你播放器 —— 有歌才显示
        val vm = playerViewModel
        if (vm != null) {
            val currentSong by vm.currentSong.collectAsState()
            val isPlaying by vm.isPlaying.collectAsState()
            val position by vm.currentPosition.collectAsState()
            val duration by vm.duration.collectAsState()

            MiniPlayer(
                currentSong = currentSong,
                isPlaying = isPlaying,
                currentPosition = position,
                duration = duration,
                onPlayerClick = onOpenPlayer,
                onTogglePlayPause = { vm.togglePlayPause() },
                onPlayPrevious = { vm.playPrevious() },
                onPlayNext = { vm.playNext() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        MainBottomBar(
            currentRoute = currentRoute,
            onNavigate = onNavigate,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
