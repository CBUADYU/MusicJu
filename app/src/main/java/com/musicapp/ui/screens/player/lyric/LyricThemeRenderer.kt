package com.musicapp.ui.screens.player.lyric

import com.musicapp.ui.screens.player.lyric.LyricTimeline.LineStatus
import com.musicapp.ui.screens.player.lyric.LyricTimeline.VisibleLineEntry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 主题 Tone 计算 — 根据 LyricTheme 决定每个 track 的目标 alpha/scale/blur。
 *
 * 每种主题返回不同的目标 Tone：
 *  - FOLIA_SCROLL / WORD_SWEEP / WORD_POP / GLOW_PULSE：沿用 Folia Monet 公式
 *  - FADE_SLIDE：简单线性 fade，不缩放
 *  - PLAIN：只给活动行 alpha=1，其它 alpha=0.5
 */
internal object ThemeToneResolver {

    fun resolve(
        theme: LyricTheme,
        status: LineStatus,
        offset: Int
    ): MonetTone {
        return when (theme) {
            LyricTheme.FOLIA_SCROLL -> MonetToneResolver.resolve(status, offset)
            LyricTheme.WORD_SWEEP -> MonetToneResolver.resolve(status, offset)
            LyricTheme.WORD_POP -> MonetToneResolver.resolve(status, offset)
            LyricTheme.GLOW_PULSE -> MonetToneResolver.resolve(status, offset)
            LyricTheme.FADE_SLIDE -> resolveFadeSlide(status, offset)
            LyricTheme.PLAIN -> resolvePlain(status, offset)
        }
    }

    /** FADE_SLIDE：活动行满 alpha 不缩放，邻近行淡出，远的直接消失 */
    private fun resolveFadeSlide(status: LineStatus, offset: Int): MonetTone {
        if (status == LineStatus.ACTIVE) {
            return MonetTone(alpha = 1f, scale = 1f, blur = 0f, baseAlpha = 1f)
        }
        val d = max(abs(offset), 1)
        val alpha = when {
            d == 1 -> 0.42f
            d == 2 -> 0.18f
            else -> 0f
        }
        return MonetTone(alpha = alpha, scale = 1f, blur = 0f, baseAlpha = alpha)
    }

    /** PLAIN：只给活动行满 alpha，前后 3 行半透明，其余不渲染 */
    private fun resolvePlain(status: LineStatus, offset: Int): MonetTone {
        if (status == LineStatus.ACTIVE) {
            return MonetTone(alpha = 1f, scale = 1f, blur = 0f, baseAlpha = 1f)
        }
        val d = max(abs(offset), 1)
        val alpha = when {
            d <= 2 -> 0.55f
            d <= 3 -> 0.35f
            else -> 0f
        }
        return MonetTone(alpha = alpha, scale = 1f, blur = 0f, baseAlpha = alpha)
    }
}

/**
 * 逐字进度估算 — 因为 LRC 没有逐字时间戳，用行间时间差线性插值。
 *
 * @param currentLineStartTimeMs 当前行开始时间
 * @param nextLineStartTimeMs 下一行开始时间（-1 表示没有下一行）
 * @param positionMs 当前播放位置
 * @return 0f ~ 1f 的进度
 */
internal fun estimateLineProgress(
    currentLineStartTimeMs: Long,
    nextLineStartTimeMs: Long,
    positionMs: Long
): Float {
    val lineDuration = when {
        nextLineStartTimeMs > currentLineStartTimeMs ->
            (nextLineStartTimeMs - currentLineStartTimeMs).coerceAtLeast(100L)
        else -> 3000L // 末行或异常时给个默认值
    }
    val elapsed = (positionMs - currentLineStartTimeMs).coerceIn(0L, lineDuration)
    return elapsed.toFloat() / lineDuration.toFloat()
}

/**
 * 将字符串拆分为 grapheme 字素列表（兼容中英文）。
 * 英文按空格分词，中文按单字分。
 */
internal fun splitGraphemes(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val result = mutableListOf<String>()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        // 中文字符（CJK Unified Ideographs + 扩展）
        val isCjk = c.code in 0x4E00..0x9FFF ||
                c.code in 0x3400..0x4DBF ||
                c.code in 0x20000..0x2A6DF ||
                c.code == 0x3000 // CJK 空格
        if (isCjk) {
            result.add(c.toString())
            i++
        } else if (c.isWhitespace()) {
            // 连续 whitespace 合并为一个空格字素
            val sb = StringBuilder()
            while (i < text.length && text[i].isWhitespace()) {
                sb.append(text[i]); i++
            }
            result.add(sb.toString())
        } else {
            // 英文/数字/符号按连续非空白分组
            val sb = StringBuilder()
            while (i < text.length && !text[i].isWhitespace() && text[i].code !in 0x4E00..0x9FFF) {
                sb.append(text[i]); i++
            }
            result.add(sb.toString())
        }
    }
    return result
}

/**
 * 简单正弦脉冲 — 用于 GLOW_PULSE / WORD_POP 的节拍感动画。
 */
internal fun beatPulse(timeSec: Float, bpm: Float = 120f): Float {
    val beatInterval = 60f / bpm
    val phase = (timeSec % beatInterval) / beatInterval
    // 每个 beat 一个从 0→1→0 的 pulse，用 sin² 平滑
    return kotlin.math.sin(phase * kotlin.math.PI.toFloat()).coerceIn(0f, 1f)
}
