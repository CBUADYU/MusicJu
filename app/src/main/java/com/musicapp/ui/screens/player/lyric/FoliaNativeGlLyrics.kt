package com.musicapp.ui.screens.player.lyric

import com.musicapp.util.LrcParser
import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * Folia 原生 OpenGL ES 渲染 — A 方案
 *
 * GLSurfaceView + GLES20 + Folia 原版 spring 物理 + sweep shader + 三层 glow
 * 比 WebView 方案性能更好、APK 更小（不依赖 Pixi.js），但需要设备支持 GLES 2.0
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun FoliaNativeGlLyrics(
    lines: List<LrcParser.LrcLine>,
    positionMsFlow: StateFlow<Long>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setZOrderOnTop(false)
        }
    }

    val renderer = remember { FoliaNativeGlRenderer() }

    LaunchedEffect(Unit) {
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    LaunchedEffect(lines) {
        val glLines = lines.map { FoliaNativeGlRenderer.GlLine(it.text, it.timeMs, it.timeMs + 3000) }
        renderer.setLines(glLines)
    }

    LaunchedEffect(isPlaying) {
        while (true) {
            renderer.setPosition(positionMsFlow.value)
            delay(if (isPlaying) 16L else 500L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            glView.onPause()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { glView },
        update = { }
    )
}

