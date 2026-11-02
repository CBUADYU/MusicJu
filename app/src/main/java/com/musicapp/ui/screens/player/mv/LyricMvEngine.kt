package com.musicapp.ui.screens.player.mv

import android.graphics.BlurMaskFilter
import android.graphics.Color as AndroidColor
import android.graphics.Path
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.random.Random
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.musicapp.util.LrcParser.LrcLine
import kotlinx.coroutines.delay

/**
 * 歌词 MV 实时渲染引擎 v2 —— 三层动画结构：
 *
 *   ┌─────────────────────────────────┐
 *   │  LAYER 1 : 静态底 (drawBg)       │
 *   │  LAYER 2 : 动画主景 (drawScene)  │ ← 波形 / 粒子 / 光带 / 爆闪
 *   │  LAYER 3 : 歌词文字 (drawLyric)  │
 *   └─────────────────────────────────┘
 */
@Composable
fun LyricMvRenderer(
    style: LyricMvStyle,
    lines: List<LrcLine>,
    currentPositionMs: Long,
    songDurationMs: Long,
    modifier: Modifier = Modifier
) {
    var tickMs by remember { mutableLongStateOf(0L) }
    var lastIdx by remember { mutableLongStateOf(-999L) }
    // 检测歌词切行 → 触发 burst
    var burstTrigger by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            tickMs = System.currentTimeMillis()
            delay(24L) // ~42fps，更流畅
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(style.background),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { composeCanvas ->
                val native = composeCanvas.nativeCanvas
                val w = size.width
                val h = size.height

                val (idx, progress) = findIdx(lines, currentPositionMs, songDurationMs)
                // burst 检测
                if (idx.toLong() != lastIdx && idx >= 0) {
                    lastIdx = idx.toLong()
                    burstTrigger = tickMs
                }

                drawBg(style, native, w, h)
                drawScene(style, native, w, h, tickMs, burstTrigger, idx)
                val line = lines.getOrNull(idx)
                drawLyric(style, native, line, lines.getOrNull(idx - 1), lines.getOrNull(idx + 1), w, h, progress, tickMs)
            }
        }
    }
}

// =====================================================================
//  辅助
// =====================================================================
private fun findIdx(lines: List<LrcLine>, posMs: Long, durationMs: Long): Pair<Int, Float> {
    if (lines.isEmpty()) return -1 to 0f
    for (i in lines.indices) {
        val start = lines[i].timeMs
        val end = lines.getOrNull(i + 1)?.timeMs
            ?: durationMs.takeIf { it > start }
            ?: (start + 4000L)
        if (posMs >= start && posMs < end) {
            val p = if (end > start) (posMs - start).toFloat() / (end - start) else 0f
            return i to p.coerceIn(0f, 1f)
        }
    }
    return 0 to 0f
}

private fun toAC(c: androidx.compose.ui.graphics.Color): Int =
    AndroidColor.argb(
        (c.alpha * 255).toInt(),
        (c.red * 255).toInt(),
        (c.green * 255).toInt(),
        (c.blue * 255).toInt()
    )

private fun withAlpha(alpha: Int, colorInt: Int): Int =
    AndroidColor.argb(alpha, AndroidColor.red(colorInt), AndroidColor.green(colorInt), AndroidColor.blue(colorInt))

private fun basePaint(size: Float, center: Boolean = true): Paint = Paint().apply {
    isAntiAlias = true; textSize = size; isFakeBoldText = true
    textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
}

private fun formatTs(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return "%02d:%02d:%02d".format(h, m, sec)
}

// =====================================================================
//  LAYER 1 —— 静态底（纯色 + 极少量装饰，性能开销要低）
// =====================================================================
private fun drawBg(style: LyricMvStyle, c: android.graphics.Canvas, w: Float, h: Float) {
    c.drawColor(toAC(style.background))
}

// =====================================================================
//  LAYER 2 —— 动画主景 ⭐ 所有视觉爆点在这里
// =====================================================================
private fun drawScene(
    style: LyricMvStyle,
    c: android.graphics.Canvas,
    w: Float, h: Float,
    t: Long,
    burstAt: Long,
    currentIdx: Int
) {
    when (style.technique) {
        LyricMvStyle.Technique.NEON_GLOW -> sceneNeon(c, w, h, t, burstAt, style)
        LyricMvStyle.Technique.CYBER_GLITCH -> sceneGlitch(c, w, h, t, burstAt, style)
        LyricMvStyle.Technique.SUMI_ZHU -> sceneSumi(c, w, h, t, burstAt, style)
        LyricMvStyle.Technique.PARTICLE_DREAM -> sceneParticle(c, w, h, t, burstAt, style)
        LyricMvStyle.Technique.MINIMAL_LINE -> sceneMinimal(c, w, h, t, style)
        LyricMvStyle.Technique.KINETYPO -> sceneKinetypo(c, w, h, t, style)
        LyricMvStyle.Technique.HORROR_MONITOR -> sceneHorror(c, w, h, t, burstAt, style)
        LyricMvStyle.Technique.COLOR_FILM -> sceneFilm(c, w, h, t, burstAt, style, currentIdx)
    }
}

// ---- Neon 场景：中心脉冲波 + 环形频谱 + 辐射光带 ----
private fun sceneNeon(c: android.graphics.Canvas, w: Float, h: Float, t: Long, burstAt: Long, s: LyricMvStyle) {
    val cx = w / 2f; val cy = h / 2f
    // 中心径向渐变（呼吸）
    val breath = 0.8f + 0.2f * kotlin.math.sin(t / 500f).toFloat()
    val grad = RadialGradient(
        cx, cy, maxOf(w, h) / 2.2f * breath,
        AndroidColor.TRANSPARENT,
        withAlpha(80, toAC(s.ghostA)),
        Shader.TileMode.CLAMP
    )
    c.drawPaint(Paint().apply { shader = grad })

    // 环形频谱（64 根柱子围成圆环，像演唱会舞台灯光）
    val bars = 64; val baseR = minOf(w, h) * 0.28f
    for (i in 0 until bars) {
        val angle = (i.toFloat() / bars) * kotlin.math.PI.toFloat() * 2f
        // 模拟实时频谱：多频率叠加
        val freq = kotlin.math.sin(t / 80f + i * 0.4).toFloat() * 0.5f +
                kotlin.math.sin(t / 200f + i * 0.15).toFloat() * 0.3f +
                Random.nextFloat() * 0.2f
        val barLen = (0.35f + 0.65f * abs(freq)) * minOf(w, h) * 0.18f
        val x1 = cx + kotlin.math.cos(angle).toFloat() * baseR
        val y1 = cy + kotlin.math.sin(angle).toFloat() * baseR
        val x2 = cx + kotlin.math.cos(angle).toFloat() * (baseR + barLen)
        val y2 = cy + kotlin.math.sin(angle).toFloat() * (baseR + barLen)
        val p = Paint().apply {
            color = withAlpha(180, toAC(s.ghostA))
            strokeWidth = 2.5f; isAntiAlias = true
        }
        c.drawLine(x1, y1, x2, y2, p)
    }

    // 爆发脉冲圆（切行时触发）
    val dt = (t - burstAt).coerceAtLeast(0L)
    if (dt < 1200L) {
        val pulseR = minOf(w, h) * 0.1f + (dt / 1200f) * minOf(w, h) * 0.6f
        val pulseAlpha = (255 * (1f - dt / 1200f)).toInt()
        val ringP = Paint().apply {
            color = withAlpha(pulseAlpha, toAC(s.accent))
            style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true
        }
        c.drawCircle(cx, cy, pulseR, ringP)
    }

    // 辐射光带（12 道从中心向外发散的光）
    for (i in 0 until 12) {
        val angle = (i.toFloat() / 12f) * kotlin.math.PI.toFloat() * 2f + t / 3000f
        val beamLen = minOf(w, h) * 0.4f * (0.5f + 0.5f * kotlin.math.sin(t / 400f + i).toFloat())
        val x1 = cx + kotlin.math.cos(angle).toFloat() * 50f
        val y1 = cy + kotlin.math.sin(angle).toFloat() * 50f
        val x2 = cx + kotlin.math.cos(angle).toFloat() * beamLen
        val y2 = cy + kotlin.math.sin(angle).toFloat() * beamLen
        val p = Paint().apply {
            color = withAlpha(25, toAC(s.accent))
            strokeWidth = 28f; isAntiAlias = true
            maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawLine(x1, y1, x2, y2, p)
    }
}

// ---- Glitch 场景：动态扫光 + 故障块 + 竖条频谱 ----
private fun sceneGlitch(c: android.graphics.Canvas, w: Float, h: Float, t: Long, burstAt: Long, s: LyricMvStyle) {
    // 全幅扫光（从左到右飞速扫）
    val sweepX = ((t % 1500) / 1500f) * w
    val sweepP = Paint().apply {
        color = withAlpha(22, toAC(s.accent))
        strokeWidth = 80f; isAntiAlias = true
        maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
    }
    c.drawLine(sweepX, 0f, sweepX, h, sweepP)

    // 横向扫描线（快速滚动）
    val scanY = ((t % 2000) / 2000f) * h
    val scanLine = Paint().apply { color = withAlpha(40, toAC(s.ghostA)); strokeWidth = 3f }
    c.drawLine(0f, scanY, w, scanY, scanLine)

    // 故障块（随机出现的横条色块）
    repeat(3) {
        if (Random.nextFloat() < 0.15f) {
            val fy = Random.nextFloat() * h
            val fh = 4f + Random.nextFloat() * 20f
            val fp = Paint().apply { color = withAlpha(100, toAC(if (Random.nextBoolean()) s.ghostA else s.ghostB)) }
            c.drawRect(0f, fy, w, fy + fh, fp)
        }
    }

    // 竖条频谱（底部）
    val bars = 48; val barW = w / bars
    for (i in 0 until bars) {
        val v = kotlin.math.sin(t / 60f + i * 0.3).toFloat() * 0.4f +
                kotlin.math.sin(t / 180f + i * 0.1).toFloat() * 0.3f + Random.nextFloat() * 0.3f
        val bh = (0.1f + abs(v) * 0.6f) * h * 0.25f
        val p = Paint().apply {
            color = withAlpha(160, toAC(if (i % 2 == 0) s.ghostA else s.ghostB))
            isAntiAlias = true
        }
        c.drawRect(i * barW + 1f, h - bh, (i + 1) * barW - 1f, h, p)
    }

    // 爆发：切行时的全屏闪 + 色块
    val dt = (t - burstAt).coerceAtLeast(0L)
    if (dt < 300L) {
        val flashAlpha = (120 * (1f - dt / 300f)).toInt()
        c.drawColor(withAlpha(flashAlpha, toAC(s.accent)))
        repeat(5) {
            val fy = Random.nextFloat() * h
            val fh = 3f + Random.nextFloat() * 15f
            val p = Paint().apply { color = withAlpha(200, toAC(if (Random.nextBoolean()) s.ghostA else s.ghostB)) }
            c.drawRect(0f, fy, w, fy + fh, p)
        }
    }
}

// ---- Sumi 场景：飘浮樱花/墨点 + 墨迹笔触 + 古风边框 ----
private fun sceneSumi(c: android.graphics.Canvas, w: Float, h: Float, t: Long, burstAt: Long, s: LyricMvStyle) {
    // 大量飘浮粒子（樱花 / 墨点）
    val particleCount = 80
    val accentI = toAC(s.accent)
    val mainI = toAC(s.mainText)
    for (i in 0 until particleCount) {
        val speed = 0.3f + (i % 5) * 0.15f
        val px = ((i * 97f + t / 50f * speed) % w)
        val py = ((i * 53f + t / 30f * speed) % h)
        val sway = kotlin.math.sin(t / 600f + i * 0.3).toFloat() * 15f
        val r = 2f + (i % 3) * 1.5f
        val colorI = if (i % 3 == 0) accentI else mainI
        val p = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.argb(140, AndroidColor.red(colorI), AndroidColor.green(colorI), AndroidColor.blue(colorI))
        }
        if (s.particleShape == LyricMvStyle.ParticleShape.PETAL && i % 5 == 0) {
            // 樱花形状：画 5 个小圆组成花瓣
            for (k in 0 until 5) {
                val a = (k / 5f) * kotlin.math.PI.toFloat() * 2f
                c.drawCircle(px + sway + kotlin.math.cos(a).toFloat() * 3f,
                    py + kotlin.math.sin(a).toFloat() * 3f, 2.5f, p)
            }
        } else {
            c.drawCircle(px + sway, py, r, p)
        }
    }

    // 墨迹笔触（左上角大片模糊黑墨）
    val inkP = Paint().apply {
        color = withAlpha(30, mainI)
        maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
        isAntiAlias = true
    }
    c.drawCircle(w * 0.15f, h * 0.2f, minOf(w, h) * 0.3f, inkP)
    c.drawCircle(w * 0.85f, h * 0.8f, minOf(w, h) * 0.25f, inkP)

    // 爆发：切行时撒墨点
    val dt = (t - burstAt).coerceAtLeast(0L)
    if (dt < 800L) {
        val alpha = (200 * (1f - dt / 800f)).toInt()
        for (k in 0..12) {
            val cx2 = w / 2f + Random.nextFloat() * 120f - 60f
            val cy2 = h / 2f + Random.nextFloat() * 120f - 60f
            val r2 = 4f + Random.nextFloat() * 12f * (dt / 800f)
            c.drawCircle(cx2, cy2, r2, Paint().apply {
                isAntiAlias = true; color = withAlpha(alpha, accentI)
            })
        }
    }
}

// ---- Particle Dream 场景：爆发粒子 + 上升光点 + 背景光斑 ----
private fun sceneParticle(c: android.graphics.Canvas, w: Float, h: Float, t: Long, burstAt: Long, s: LyricMvStyle) {
    // 背景柔光斑（缓慢漂移的大光斑）
    val ghostAI = toAC(s.ghostA)
    val ghostBI = toAC(s.ghostB)
    for (i in 0..4) {
        val cx2 = w * (0.15f + (i * 0.2f) + kotlin.math.sin(t / 2500f + i).toFloat() * 0.08f)
        val cy2 = h * (0.3f + kotlin.math.cos(t / 3000f + i).toFloat() * 0.12f)
        val r = minOf(w, h) * 0.25f
        val grad = RadialGradient(cx2, cy2, r,
            withAlpha(60, if (i % 2 == 0) ghostAI else ghostBI),
            AndroidColor.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawPaint(Paint().apply { shader = grad })
    }

    // 上升光点（密集）
    val accentI = toAC(s.accent)
    val dots = 120
    for (i in 0 until dots) {
        val speed = 0.6f + (i % 7) * 0.12f
        val px = ((i * 173f + kotlin.math.sin(t / 300f + i).toFloat() * 30f) % w)
        val py = h - ((t / 25f * speed + i * 19f) % h)
        val r = 1.2f + (i % 4) * 0.8f
        val colorI = if (i % 2 == 0) accentI else ghostAI
        val p = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.argb(180, AndroidColor.red(colorI), AndroidColor.green(colorI), AndroidColor.blue(colorI))
        }
        c.drawCircle(px, py, r, p)
    }

    // 爆发：歌词切行时从中心向四周发射大量粒子
    val dt = (t - burstAt).coerceAtLeast(0L)
    if (dt < 1500L) {
        val cx = w / 2f; val cy = h / 2f
        val burstCount = 80
        for (k in 0 until burstCount) {
            val angle = (k.toFloat() / burstCount) * kotlin.math.PI.toFloat() * 2f + k * 0.1f
            val speed = minOf(w, h) / 25f // px per ms
            val dist = (dt / 1500f) * minOf(w, h) * 0.5f + dt * speed / 1000f * 100f
            val bx = cx + kotlin.math.cos(angle).toFloat() * dist
            val by = cy + kotlin.math.sin(angle).toFloat() * dist
            val r = 2f + (1f - dt / 1500f) * 4f
            val a = (255 * (1f - dt / 1500f)).toInt()
            val p = Paint().apply {
                isAntiAlias = true
                color = AndroidColor.argb(a, AndroidColor.red(accentI), AndroidColor.green(accentI), AndroidColor.blue(accentI))
            }
            c.drawCircle(bx, by, r, p)
        }
    }
}

// ---- Minimal 场景：跳动横线 + 节拍标记 ----
private fun sceneMinimal(c: android.graphics.Canvas, w: Float, h: Float, t: Long, s: LyricMvStyle) {
    // 底部横线
    val p = Paint().apply { color = withAlpha(80, toAC(s.subText)); strokeWidth = 1.5f; isAntiAlias = true }
    c.drawLine(0f, h * 0.72f, w, h * 0.72f, p)
    // 顶部更细的装饰线
    val p2 = Paint().apply { color = withAlpha(40, toAC(s.subText)); strokeWidth = 1f; isAntiAlias = true }
    c.drawLine(0f, h * 0.12f, w, h * 0.12f, p2)
    // 节拍点（跟着节拍跳动的小圆点沿顶部线）
    for (i in 0..6) {
        val x = w * (0.08f + i * 0.14f)
        val beat = kotlin.math.sin(t / 200f + i).toFloat()
        val r = 2f + abs(beat) * 4f
        val alpha = (80 + 120 * abs(beat)).toInt()
        c.drawCircle(x, h * 0.12f, r, Paint().apply {
            isAntiAlias = true; color = withAlpha(alpha, toAC(s.accent))
        })
    }
}

// ---- Kinetypo 场景：巨大背景字 + 旋转方块 + 网格 ----
private fun sceneKinetypo(c: android.graphics.Canvas, w: Float, h: Float, t: Long, s: LyricMvStyle) {
    // 巨大背景字（滚动的 PV/POP/LOVE 等字）
    val hugeWords = listOf("POP", "LOVE", "NOW", "PLAY", "GO", "NEW")
    val word = hugeWords[((t / 1500) % hugeWords.size).toInt()]
    val wordP = Paint().apply {
        isAntiAlias = true; isFakeBoldText = true; textAlign = Paint.Align.CENTER
        textSize = w * 0.35f; color = withAlpha(28, toAC(s.mainText))
    }
    val offset = ((t / 30) % (w * 2f)) - w
    c.drawText(word, w / 2f + offset, h * 0.82f, wordP)
    c.drawText(word, w / 2f + offset + w, h * 0.82f, wordP)

    // 旋转方块（四角各一个小装饰）
    val rotate = t / 1500f
    val corners = listOf(w * 0.12f to h * 0.12f, w * 0.88f to h * 0.12f,
        w * 0.12f to h * 0.88f, w * 0.88f to h * 0.88f)
    for ((cx, cy) in corners) {
        val save = c.saveLayer(cx - 20f, cy - 20f, cx + 20f, cy + 20f, null)
        c.save()
        c.translate(cx, cy)
        c.rotate(rotate * 180f / Math.PI.toFloat())
        val rp = Paint().apply {
            color = toAC(s.ghostA); strokeWidth = 2.5f; isAntiAlias = true; style = Paint.Style.STROKE
        }
        c.drawRect(RectF(-12f, -12f, 12f, 12f), rp)
        c.drawCircle(0f, 0f, 5f, Paint().apply { color = toAC(s.accent) })
        c.restore()
        c.restoreToCount(save)
    }
}

// ---- Horror 场景：CRT 扫描线 + 雪花 + 随机像素块 + 视频噪声 ----
private fun sceneHorror(c: android.graphics.Canvas, w: Float, h: Float, t: Long, burstAt: Long, s: LyricMvStyle) {
    // CRT 扫描线（满屏）
    val scanP = Paint().apply { color = withAlpha(60, toAC(s.mainText)) }
    var y = 0f
    while (y < h) { c.drawLine(0f, y, w, y, scanP); y += 3f }

    // 大面积雪花噪点（每帧重绘 200 个）
    val noiseP = Paint().apply { color = withAlpha(35, toAC(s.mainText)) }
    repeat(220) {
        c.drawPoint(Random.nextFloat() * w, Random.nextFloat() * h, noiseP)
    }

    // 随机大块视频噪声（每帧 5 个）
    val blockP = Paint().apply { color = withAlpha(80, toAC(s.mainText)) }
    repeat(6) {
        if (Random.nextFloat() < 0.4f) {
            val bx = Random.nextFloat() * w
            val by = Random.nextFloat() * h
            val bw = 10f + Random.nextFloat() * 30f
            val bh = 2f + Random.nextFloat() * 6f
            c.drawRect(bx, by, bx + bw, by + bh, blockP)
        }
    }

    // 色偏（画面边缘暗角 + 轻微偏移条纹）
    val vignette = RadialGradient(
        w / 2f, h / 2f, maxOf(w, h) / 1.2f,
        AndroidColor.TRANSPARENT, withAlpha(180, AndroidColor.BLACK),
        Shader.TileMode.CLAMP
    )
    c.drawPaint(Paint().apply { shader = vignette })

    // 横向滚条（一条稍亮的水平条往下滚）
    val rollingY = ((t % 3500) / 3500f) * h
    c.drawRect(0f, rollingY, w, rollingY + 2f, Paint().apply {
        color = withAlpha(100, toAC(s.mainText))
    })

    // HUD
    val hud = Paint().apply { color = toAC(s.accent); textSize = 26f; isFakeBoldText = true; textAlign = Paint.Align.LEFT }
    c.drawText("REC ● ${formatTs(t)}", 40f, 50f, hud)
    c.drawText("AUTO · SIGNAL NOMINAL", 40f, 80f, Paint().apply {
        color = withAlpha(160, toAC(s.mainText)); textSize = 14f
    })

    // 爆发：切行时的全屏爆闪（模拟视频被干扰）
    val dt = (t - burstAt).coerceAtLeast(0L)
    if (dt < 200L && Random.nextFloat() < 0.4f) {
        c.drawColor(withAlpha(180, AndroidColor.WHITE))
        // 随机像素块覆盖
        repeat(15) {
            val bx = Random.nextFloat() * w
            val by = Random.nextFloat() * h
            val bw = 20f + Random.nextFloat() * 60f
            val bh = 2f + Random.nextFloat() * 10f
            c.drawRect(bx, by, bx + bw, by + bh, Paint().apply {
                color = AndroidColor.WHITE
            })
        }
    }
}

// ---- Film 场景：胶片边框 + 滚动颗粒 + 闪白 + 帧切换 ----
private fun sceneFilm(
    c: android.graphics.Canvas, w: Float, h: Float,
    t: Long, burstAt: Long, s: LyricMvStyle, currentIdx: Int
) {
    // 胶片上下黑边 + 小孔
    val bar = Paint().apply { color = AndroidColor.parseColor("#151515") }
    c.drawRect(0f, 0f, w, 70f, bar)
    c.drawRect(0f, h - 70f, w, h, bar)
    val hole = Paint().apply { color = toAC(s.background) }
    // 小孔也跟着滚（模拟胶片在走）
    val holeOffset = ((t / 30) % 38f)
    var x = -38f - holeOffset
    while (x < w) {
        c.drawOval(RectF(x, 12f, x + 16f, 30f), hole)
        c.drawOval(RectF(x, h - 58f, x + 16f, h - 40f), hole)
        x += 38f
    }

    // 颗粒噪点（模拟胶片颗粒感）
    val grainP = Paint().apply { color = withAlpha(35, toAC(if (currentIdx % 2 == 0) s.accent else s.subText)) }
    repeat(180) {
        c.drawPoint(Random.nextFloat() * w, Random.nextFloat() * h, grainP)
    }

    // 暖黄闪光（偶尔出现的胶片过曝感）
    if (Random.nextFloat() < 0.04f) {
        c.drawColor(withAlpha(25, toAC(s.accent)))
    }

    // 爆发：切行时的帧切换感（全屏闪白）
    val dt = (t - burstAt).coerceAtLeast(0L)
    if (dt < 120L) {
        c.drawColor(withAlpha((200 * (1f - dt / 120f)).toInt(), AndroidColor.WHITE))
    }
    // 偶尔 frame jump（模拟老电影帧丢失）
    if (Random.nextFloat() < 0.01f) {
        val jumpY = Random.nextFloat() * 20f - 10f
        c.save()
        c.translate(0f, jumpY)
        // 这里简单做个条纹
        c.drawRect(0f, h * 0.3f + jumpY, w, h * 0.3f + 4f + jumpY, Paint().apply {
            color = AndroidColor.WHITE
        })
        c.restore()
    }
}

// =====================================================================
//  LAYER 3 —— 歌词文字
// =====================================================================
private fun drawLyric(
    style: LyricMvStyle, c: android.graphics.Canvas,
    line: LrcLine?, prevLine: LrcLine?, nextLine: LrcLine?,
    w: Float, h: Float, progress: Float, t: Long
) {
    val text = line?.text?.takeIf { it.isNotBlank() } ?: return
    when (style.technique) {
        LyricMvStyle.Technique.NEON_GLOW -> drawNeonText(c, text, w, h, t, style)
        LyricMvStyle.Technique.CYBER_GLITCH -> drawGlitchText(c, text, w, h, t, style)
        LyricMvStyle.Technique.SUMI_ZHU -> drawSumiText(c, text, w, h, style)
        LyricMvStyle.Technique.PARTICLE_DREAM -> drawParticleText(c, text, w, h, t, style)
        LyricMvStyle.Technique.MINIMAL_LINE -> drawMinimalText(c, text, w, h, progress, style)
        LyricMvStyle.Technique.KINETYPO -> drawKinetypoText(c, text, w, h, style)
        LyricMvStyle.Technique.HORROR_MONITOR -> drawHorrorText(c, text, w, h, t, style)
        LyricMvStyle.Technique.COLOR_FILM -> drawFilmText(c, text, prevLine, nextLine, w, h, style)
    }
}

// ---- Neon Glow 文字 ----
private fun drawNeonText(c: android.graphics.Canvas, text: String, w: Float, h: Float, t: Long, s: LyricMvStyle) {
    val alpha = 0.55f + 0.45f * kotlin.math.sin(t / 400f).toFloat()
    val size = w * 0.085f; val x = w / 2f; val y = h * 0.56f
    listOf(s.ghostA to 60f, s.ghostB to 30f, s.accent to 18f).forEach { (c2, r) ->
        c.drawText(text, x, y, basePaint(size).apply {
            color = withAlpha((180 * alpha).toInt(), toAC(c2))
            maskFilter = BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL)
        })
    }
    c.drawText(text, x, y, basePaint(size).apply { color = AndroidColor.WHITE; maskFilter = null })
}

// ---- Cyber Glitch 文字 ----
private fun drawGlitchText(c: android.graphics.Canvas, text: String, w: Float, h: Float, t: Long, s: LyricMvStyle) {
    val size = w * 0.09f; val x = w / 2f; val y = h * 0.52f
    val glitch = (kotlin.math.sin(t / 120f).toFloat() * 8f).toInt()
    val base = basePaint(size)
    base.color = toAC(s.ghostA); c.drawText(text, x + glitch, y, base)
    base.color = toAC(s.ghostB); c.drawText(text, x - glitch, y, base)
    base.color = toAC(s.mainText); c.drawText(text, x, y, base)
    // 文字内部随机横切
    if (Random.nextFloat() < 0.2f) {
        val sliceY = y - size * 0.3f + Random.nextFloat() * size * 0.6f
        c.drawRect(x - 200f, sliceY, x + 200f, sliceY + 2f, Paint().apply {
            color = toAC(s.accent)
        })
    }
}

// ---- 墨朱文字 + 印章 ----
private fun drawSumiText(c: android.graphics.Canvas, text: String, w: Float, h: Float, s: LyricMvStyle) {
    val p = Paint().apply {
        isAntiAlias = true; color = toAC(s.mainText); textSize = w * 0.10f
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL_AND_STROKE; strokeWidth = 1.5f
    }
    c.drawText(text, w / 2f, h * 0.55f, p)
    val seal = Paint().apply { color = toAC(s.accent) }
    val cx = w / 2f + text.length * w * 0.05f + 20f; val cy = h * 0.55f
    c.drawRect(cx - 28f, cy - 32f, cx + 28f, cy + 20f, seal)
    val sealText = Paint().apply {
        color = toAC(s.background); textSize = 14f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    c.drawText("印", cx, cy - 6f, sealText)
}

// ---- Particle Dream 文字 ----
private fun drawParticleText(c: android.graphics.Canvas, text: String, w: Float, h: Float, t: Long, s: LyricMvStyle) {
    val size = w * 0.08f; val x = w / 2f; val y = h * 0.54f
    val base = basePaint(size)
    base.color = withAlpha(180, toAC(s.ghostB)); base.maskFilter = BlurMaskFilter(50f, BlurMaskFilter.Blur.NORMAL)
    c.drawText(text, x, y, base)
    base.color = withAlpha(200, toAC(s.ghostA)); base.maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.NORMAL)
    c.drawText(text, x, y, base)
    base.color = toAC(s.mainText); base.maskFilter = null
    c.drawText(text, x, y, base)
}

// ---- Minimal Line 文字 ----
private fun drawMinimalText(c: android.graphics.Canvas, text: String, w: Float, h: Float, progress: Float, s: LyricMvStyle) {
    val total = text.length.coerceAtLeast(1)
    val vis = (progress * total).toInt().coerceAtLeast(1)
    val shown = text.take(vis); val remain = text.drop(vis)
    val size = w * 0.075f
    val shownP = Paint().apply {
        isAntiAlias = true; color = toAC(s.mainText); textSize = size; isFakeBoldText = true; textAlign = Paint.Align.LEFT
    }
    val remP = Paint().apply {
        isAntiAlias = true; color = toAC(s.subText); textSize = size; textAlign = Paint.Align.LEFT
    }
    val sw = shownP.measureText(shown); val rw = remP.measureText(remain)
    val sx = (w - sw - rw) / 2f; val y = h * 0.52f + size
    c.drawText(shown, sx, y, shownP)
    if (remain.isNotEmpty()) c.drawText(remain, sx + sw, y, remP)
}

// ---- Kinetypo 文字 ----
private fun drawKinetypoText(c: android.graphics.Canvas, text: String, w: Float, h: Float, s: LyricMvStyle) {
    val size = w * 0.13f; val x = w / 2f; val y = h * 0.54f
    val base = basePaint(size)
    base.color = toAC(s.ghostA); c.drawText(text, x + 6f, y + 18f, base)
    base.color = toAC(s.mainText); c.drawText(text, x, y, base)
    base.color = toAC(s.subText); base.style = Paint.Style.STROKE; base.strokeWidth = 3f
    c.drawText(text, x, y, base); base.style = Paint.Style.FILL
}

// ---- Horror 文字 ----
private fun drawHorrorText(c: android.graphics.Canvas, text: String, w: Float, h: Float, t: Long, s: LyricMvStyle) {
    val size = w * 0.07f
    val jitterX = kotlin.math.sin(t / 50f).toFloat() * 3f
    val jitterY = kotlin.math.cos(t / 43f).toFloat() * 2f
    val x = w / 2f; val y = h * 0.53f
    c.drawText(text, x + jitterX, y + jitterY,
        basePaint(size).apply { color = toAC(s.mainText) })
    c.drawText(text, x + jitterX, y + jitterY,
        basePaint(size).apply {
            color = withAlpha(180, toAC(s.accent))
            maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
        })
}

// ---- Film 文字（上中下三行） ----
private fun drawFilmText(
    c: android.graphics.Canvas, text: String, prev: LrcLine?, next: LrcLine?, w: Float, h: Float, s: LyricMvStyle
) {
    val mainSize = w * 0.07f; val sideSize = w * 0.04f
    val prevP = Paint().apply {
        isAntiAlias = true; color = withAlpha(136, toAC(s.subText))
        textSize = sideSize; textAlign = Paint.Align.CENTER
    }
    val mainP = Paint().apply {
        isAntiAlias = true; color = toAC(s.mainText)
        textSize = mainSize; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    prev?.text?.takeIf { it.isNotBlank() }?.let { c.drawText(it, w / 2f, h * 0.32f + prevP.textSize, prevP) }
    c.drawText(text, w / 2f, h * 0.52f + mainP.textSize, mainP)
    next?.text?.takeIf { it.isNotBlank() }?.let { c.drawText(it, w / 2f, h * 0.68f + prevP.textSize, prevP) }
}
