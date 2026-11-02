package com.musicapp.ui.screens.player.lyric

import com.musicapp.ui.screens.player.lyric.LyricTimeline.LineStatus
import com.musicapp.ui.screens.player.lyric.LyricTimeline.VisibleLineEntry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * Folia resolveMonetTone — 精确移植。
 *
 * active:  alpha=1, scale=1, blur=0, baseAlpha=0.34
 * waiting: alpha=max(0.36, 0.72-(d-1)*0.18), scale=max(0.68, 0.72*0.9^(d-1)), blur=(d==1?0.7:1.8+(d-2)*0.8), baseAlpha=0.46
 * passed:  alpha=max(0.28, 0.52-(d-1)*0.12), scale=同上, blur=1.1+(d-1)*0.7, baseAlpha=0.36
 */
internal data class MonetTone(
    val alpha: Float,
    val scale: Float,
    val blur: Float,
    val baseAlpha: Float
)

internal object MonetToneResolver {
    fun resolve(status: LineStatus, offset: Int): MonetTone {
        if (status == LineStatus.ACTIVE) {
            return MonetTone(alpha = 1f, scale = 1f, blur = 0f, baseAlpha = 0.34f)
        }
        val distance = max(abs(offset), 1)
        val waiting = status == LineStatus.WAITING
        val alpha = if (waiting) {
            max(0.36f, 0.72f - (distance - 1) * 0.18f)
        } else {
            max(0.28f, 0.52f - (distance - 1) * 0.12f)
        }
        val scale = max(0.68f, 0.72f * 0.9f.pow(distance - 1))
        val blur = if (waiting) {
            if (distance == 1) 0.7f else 1.8f + (distance - 2) * 0.8f
        } else {
            1.1f + (distance - 1) * 0.7f
        }
        val baseAlpha = if (waiting) 0.46f else 0.36f
        return MonetTone(alpha, scale, blur, baseAlpha)
    }
}

/**
 * Folia Track — 单行歌词的实时物理状态。
 * 对应 createLatticeLyricRuntime 里的 Track interface。
 */
internal class LyricTrack(var entry: VisibleLineEntry) {
    var y: Float = 0f          // 绝对 Y 坐标（像素）
    var vy: Float = 0f         // Y 速度
    var scale: Float = 0.7f    // 缩放
    var vs: Float = 0f         // 缩放速度
    var alpha: Float = 0f      // 透明度
    var blur: Float = 5f       // 模糊强度
    var fromAlpha: Float = 0f  // 淡入起始透明度
    var fromBlur: Float = 5f   // 淡入起始模糊
    var elapsed: Float = 0f    // 淡入计时
    var leaving: Boolean = false
}

/**
 * Folia ease = cubicBezier(0.32, 0.72, 0, 1) — 简化为 ease-out 近似。
 */
internal fun latticeEase(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    // cubic-bezier(0.32, 0.72, 0, 1) 的数值近似
    return 1f - (1f - x) * (1f - x) * (1f - 0.32f * x)
}
