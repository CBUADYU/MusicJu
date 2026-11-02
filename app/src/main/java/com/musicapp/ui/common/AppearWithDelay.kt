package com.musicapp.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 包装任意内容，让其在 [delayMs] 后从下方淡入 + 缩放 0.96 → 1.0 进入。
 * 用于 LazyColumn 里给每行加一个轻微的错峰出现动画。
 *
 * 用法：
 *   items(playlists, key = { it.id }) { playlist ->
 *       AppearWithDelay(delayMs = index * 40) {
 *           PlaylistCard(...)
 *       }
 *   }
 */
@Composable
fun AppearWithDelay(
    delayMs: Int = 0,
    initialYOffset: Float = 16f,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs.toLong())
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 380),
        label = "appearAlpha"
    )
    val ty by animateFloatAsState(
        targetValue = if (visible) 0f else initialYOffset,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
        label = "appearTy"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.96f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
        label = "appearScale"
    )
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = ty
            this.scaleX = scale
            this.scaleY = scale
        }
    ) {
        content()
    }
}
