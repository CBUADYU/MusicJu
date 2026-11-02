package com.musicapp.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color

/**
 * 荣耀 MagicOS 按压反馈
 *
 * 荣耀系统的点击反馈特点：
 *  - 颜色：accent 蓝 (#256FFF)，透明度 12%
 *  - 半径：无边界扩散，限定在组件内
 *  - 无过度弹跳，稳定的 ripple 扩散
 */
@Composable
fun hnrRipple(color: Color = HnrAccentLight) = rememberRipple(
    color = color.copy(alpha = 0.12f),
    bounded = true
)

/**
 * 荣耀风格可点击 Modifier — 统一按压反馈
 */
fun Modifier.hnrClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    this.clickable(
        enabled = enabled,
        interactionSource = interaction,
        indication = hnrRipple(),
        onClick = onClick
    )
}
