package com.musicapp.ui.screens.player.mv

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicapp.ui.screens.player.PlayerViewModel
import kotlinx.coroutines.delay

/**
 * 横屏全屏 MV 播放页（原生 Canvas 引擎 + 自动横屏）。
 */
@Composable
fun LyricMvFullscreen(
    style: LyricMvStyle,
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    // ---- 强制横屏（退出时恢复） ----
    DisposableEffect(Unit) {
        val act = context as Activity
        val prev = act.requestedOrientation
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { act.requestedOrientation = prev }
    }

    val lines by viewModel.lrcLines.collectAsState()
    val currentPos by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(3500)
            controlsVisible = false
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .clickable { controlsVisible = !controlsVisible }
    ) {
        // 原生 Canvas 引擎 —— 27 种风格 × 8 种动画技法
        LyricMvRenderer(
            style = style,
            lines = lines,
            currentPositionMs = currentPos,
            songDurationMs = duration,
            modifier = Modifier.fillMaxSize()
        )

        // 顶栏返回（3.5s 自动隐藏，点击屏幕切换）
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(top = 48.dp, start = 16.dp)
        ) {
            Icon(
                Icons.Default.ArrowBack, null, tint = Color.White,
                modifier = Modifier.size(24.dp)
                    .clickable { onNavigateBack() }
            )
        }

        // 右上角显示当前风格名
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 20.dp)
        ) {
            Text(
                "MV · ${style.displayName}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    BackHandler { onNavigateBack() }
}
