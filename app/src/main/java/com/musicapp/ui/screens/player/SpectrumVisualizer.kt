package com.musicapp.ui.screens.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.musicapp.ui.theme.Primary
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 频谱可视化组件
 * 以均衡器柱状动画呈现音乐频谱：播放时柱条随节拍跳动，暂停/停止时缓缓回落到静音基线。
 * （为规避不同设备/权限下 Visualizer 采集的不确定性，采用与播放状态联动的确定性动画。）
 */
@Composable
fun SpectrumVisualizer(
    isPlaying: Boolean,
    barCount: Int = 28,
    modifier: Modifier = Modifier
) {
    // 每根柱条当前的“能量”值（0f ~ 1f）
    val bars: SnapshotStateList<Float> = remember {
        List(barCount) { 0f }.toMutableStateList()
    }
    // 每根柱条的独立随机相位，用于形成错落有致的跳动
    val phases = remember { FloatArray(barCount) { Random.nextFloat() } }
    val random = remember { Random(System.currentTimeMillis()) }

    LaunchedEffect(isPlaying, barCount) {
        val targets = FloatArray(barCount) { 0f }
        while (true) {
            if (isPlaying) {
                // 依据相位周期性更新目标，制造涌动感
                for (i in bars.indices) {
                    phases[i] = (phases[i] + 0.12f + random.nextFloat() * 0.06f) % 1f
                    targets[i] = 0.25f + 0.6f * phases[i]
                }
                // 每次刷新随机微调若干柱，形成跳动
                repeat(barCount / 3 + 1) {
                    val i = random.nextInt(barCount)
                    targets[i] = (0.2f + random.nextFloat() * 0.75f).toFloat()
                }
                for (i in bars.indices) {
                    // 指数趋近目标，产生平滑起落
                    bars[i] += (targets[i] - bars[i]) * 0.4f
                }
            } else {
                // 静音：平滑回落
                for (i in bars.indices) {
                    bars[i] *= 0.82f
                    if (bars[i] < 0.01f) bars[i] = 0f
                }
            }
            delay(70)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val gap = size.width / (barCount * 2f + 1f)
        val barW = gap
        for (i in bars.indices) {
            val energy = bars[i].coerceIn(0f, 1f)
            if (energy < 0.012f) continue
            val barH = size.height * (0.1f + 0.9f * energy)
            val x = gap + i * (barW + gap)
            val top = size.height - barH
            val brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFB8A7FF),
                    Primary.copy(alpha = 0.85f)
                )
            )
            // 圆头柱条
            drawRoundRect(
                brush = brush,
                topLeft = androidx.compose.ui.geometry.Offset(x, top),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}