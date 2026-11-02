package com.musicapp.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * 按下缩放 + 点击合体的便捷修饰符
 * 用法：Modifier.pressScaleClickable(pressedScale = 0.94f) { onClick() }
 */
@Composable
fun Modifier.pressScaleClickable(
    pressedScale: Float = 0.94f,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scaleValue by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "pressScale"
    )
    return this
        .scale(scaleValue)
        .clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
}
