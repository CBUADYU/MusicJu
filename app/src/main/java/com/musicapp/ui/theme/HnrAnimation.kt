package com.musicapp.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.dp

/**
 * 荣耀 MagicOS 动画规范
 *
 * 荣耀/MagicOS 动画原则：
 *  - 稳定、克制、不花哨
 *  - 页面切换用水平滑入/滑出（220-280ms）
 *  - 模态层从底部滑入（240ms）
 *  - 按下反馈用轻微缩放（0.96）+ 透明度变化，不用 spring 弹跳
 *  - 缓动曲线：FastOutSlowIn（Material 标准曲线，与 MagicOS 一致）
 */
object HnrAnimation {
    // 动画时长 — 与 MagicOS 系统动画对齐
    const val SHORT = 120     // 小元素状态切换
    const val MEDIUM = 220    // 页面切换
    const val LONG = 320      // 大元素/模态

    // 缓动曲线
    val Easing = FastOutSlowInEasing
    val EaseIn = LinearOutSlowInEasing

    /**
     * 页面水平进入（从右滑入）— 荣耀导航标准
     */
    val slideInRight: EnterTransition = slideInHorizontally(
        animationSpec = tween(durationMillis = MEDIUM, easing = Easing)
    ) { it / 4 } + fadeIn(
        animationSpec = tween(durationMillis = MEDIUM, easing = Easing)
    )

    /**
     * 页面水平退出（向左滑出）
     */
    val slideOutLeft: ExitTransition = slideOutHorizontally(
        animationSpec = tween(durationMillis = MEDIUM, easing = Easing)
    ) { -it / 4 } + fadeOut(
        animationSpec = tween(durationMillis = MEDIUM, easing = Easing)
    )

    /**
     * 反向：返回时从左滑入
     */
    val slideInLeft: EnterTransition = slideInHorizontally(
        animationSpec = tween(durationMillis = MEDIUM, easing = Easing)
    ) { -it / 4 } + fadeIn(
        animationSpec = tween(durationMillis = MEDIUM, easing = Easing)
    )

    /**
     * 反向：返回时向右滑出
     */
    val slideOutRight: ExitTransition = slideOutHorizontally(
        animationSpec = tween(durationMillis = MEDIUM, easing = Easing)
    ) { it / 4 } + fadeOut(
        animationSpec = tween(durationMillis = MEDIUM, easing = Easing)
    )

    /**
     * 模态层从底部滑入（播放页、歌词页等）
     */
    val slideInBottom: EnterTransition = slideInVertically(
        animationSpec = tween(durationMillis = LONG, easing = Easing)
    ) { it } + fadeIn(
        animationSpec = tween(durationMillis = LONG, easing = Easing)
    )

    /**
     * 模态层向下滑出
     */
    val slideOutBottom: ExitTransition = slideOutVertically(
        animationSpec = tween(durationMillis = LONG, easing = Easing)
    ) { it } + fadeOut(
        animationSpec = tween(durationMillis = LONG, easing = Easing)
    )

    /**
     * 淡入淡出 — 用于搜索、设置等不改变层级的页面
     */
    val fadeInPage: EnterTransition = fadeIn(
        animationSpec = tween(durationMillis = SHORT, easing = Easing)
    )
    val fadeOutPage: ExitTransition = fadeOut(
        animationSpec = tween(durationMillis = SHORT, easing = Easing)
    )
}
