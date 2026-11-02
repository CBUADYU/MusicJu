package com.musicapp.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dokar.amlv.FadingEdges
import com.dokar.amlv.LyricsView
import com.dokar.amlv.LyricsViewState
import com.dokar.amlv.parser.LrcLyricsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoliaLyricsScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val coverUrl by viewModel.coverUrl.collectAsState()
    val lrcLines by viewModel.lrcLines.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    BackHandler(enabled = true) { onNavigateBack() }

    // 进度条拖动 / 点击状态
    //   isDragging = true → thumb 显（持续）
    //   isDragging = false → thumb 隐（通过动画 scale→0）
    var dragValue by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(currentPosition, duration, isDragging) {
        if (!isDragging) {
            dragValue = if (duration > 0) currentPosition.toFloat() / duration else 0f
        }
    }

    // 把 LrcLine 列表转回 LRC 字符串喂给 amlv
    val lrcString = remember(lrcLines) {
        val sb = StringBuilder()
        for (line in lrcLines) {
            val totalSec = line.timeMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val ms = (line.timeMs % 1000) / 10
            sb.append("[%02d:%02d.%02d]%s\n".format(min, sec, ms, line.text))
        }
        sb.toString()
    }

    val lyricsState = remember(lrcString) {
        val lyrics = LrcLyricsParser().parse(lrcString)
        LyricsViewState(lyrics, CoroutineScope(Dispatchers.Main))
    }

    // 外部 position → amlv 同步
    LaunchedEffect(currentPosition) {
        lyricsState.seekTo(currentPosition)
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFF1a1a2e), Color(0xFF0f0f1a), Color(0xFF06060d))
            )
        )
    ) {
        val isLandscape = maxWidth > maxHeight
        val screenWp = LocalConfiguration.current.screenWidthDp

        // 顶部光晕
        Box(
            Modifier.fillMaxWidth().height(280.dp).background(
                Brush.radialGradient(
                    colors = listOf(Color(0x556366f1), Color(0x226366f1), Color.Transparent),
                    center = Offset(x = screenWp / 2f, y = 0f),
                    radius = 200f
                )
            )
        )

        if (isLandscape) {
            LandscapedLayout(
                currentSong = currentSong,
                coverUrl = coverUrl,
                lyricsState = lyricsState,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                dragValue = dragValue,
                isDragging = isDragging,
                onBack = onNavigateBack,
                onToggle = { viewModel.togglePlayPause() },
                onNext = { viewModel.playNext() },
                onPrev = { viewModel.playPrevious() },
                onSeekLine = { t -> viewModel.seekTo(t) },
                onSeekProgressCommit = {
                    isDragging = false
                    viewModel.seekTo((dragValue * duration.coerceAtLeast(1L)).toLong())
                },
                onSeekProgressChange = { dragValue = it; isDragging = true }
            )
        } else {
            PortraitedLayout(
                currentSong = currentSong,
                coverUrl = coverUrl,
                lyricsState = lyricsState,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                dragValue = dragValue,
                isDragging = isDragging,
                onBack = onNavigateBack,
                onToggle = { viewModel.togglePlayPause() },
                onNext = { viewModel.playNext() },
                onPrev = { viewModel.playPrevious() },
                onSeekLine = { t -> viewModel.seekTo(t) },
                onSeekProgressCommit = {
                    isDragging = false
                    viewModel.seekTo((dragValue * duration.coerceAtLeast(1L)).toLong())
                },
                onSeekProgressChange = { dragValue = it; isDragging = true }
            )
        }
    }
}

// =================================================================
//  竖屏布局
// =================================================================
@Composable
private fun PortraitedLayout(
    currentSong: com.musicapp.domain.model.Song?,
    coverUrl: String?,
    lyricsState: LyricsViewState,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    dragValue: Float,
    isDragging: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeekLine: (Long) -> Unit,
    onSeekProgressChange: (Float) -> Unit,
    onSeekProgressCommit: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp)
            .padding(top = 56.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 返回
        Box(
            Modifier.align(Alignment.Start).size(40.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.height(8.dp))

        // 歌名/歌手（左） + 小封面+按钮（右）
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    currentSong?.title ?: "", color = Color.White, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    currentSong?.artist ?: "", color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start
                )
            }
            Spacer(Modifier.width(12.dp))
            CoverWithMiniControls(coverUrl, currentSong, isPlaying, onToggle, onNext, onPrev)
        }

        Spacer(Modifier.height(14.dp))

        // 歌词区
        LyricPane(
            state = lyricsState,
            onSeek = onSeekLine,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        Spacer(Modifier.height(6.dp))

        ProgressRow(
            dragValue = dragValue,
            isDragging = isDragging,
            onValueChange = onSeekProgressChange,
            onValueChangeFinished = onSeekProgressCommit,
            currentPosition = currentPosition,
            duration = duration,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// =================================================================
//  横屏布局
// =================================================================
@Composable
private fun LandscapedLayout(
    currentSong: com.musicapp.domain.model.Song?,
    coverUrl: String?,
    lyricsState: LyricsViewState,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    dragValue: Float,
    isDragging: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeekLine: (Long) -> Unit,
    onSeekProgressChange: (Float) -> Unit,
    onSeekProgressCommit: () -> Unit
) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LyricPane(
            state = lyricsState,
            onSeek = onSeekLine,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        Spacer(Modifier.width(24.dp))

        Column(
            Modifier.width(300.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 返回
            Box(
                Modifier.align(Alignment.Start).size(40.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))

            Text(
                currentSong?.title ?: "", color = Color.White, fontSize = 17.sp,
                fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(3.dp))
            Text(
                currentSong?.artist ?: "", color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))

            // 大封面
            AsyncImage(
                model = coverUrl?.takeIf { it.isNotBlank() },
                contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(180.dp).aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2a2a3a))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.height(16.dp))

            // 播放控制：上一曲 · 播放/暂停 · 下一曲
            TriplePlayRow(
                isPlaying = isPlaying,
                onPrev = onPrev, onToggle = onToggle, onNext = onNext,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            ProgressRow(
                dragValue = dragValue,
                isDragging = isDragging,
                onValueChange = onSeekProgressChange,
                onValueChangeFinished = onSeekProgressCommit,
                currentPosition = currentPosition,
                duration = duration,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// =================================================================
//  可复用小组件
// =================================================================

/** 歌词主体（有歌词滚动视图 / 无歌词占位） */
@Composable
private fun LyricPane(
    state: LyricsViewState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.lyrics != null) {
        LyricsView(
            state = state,
            modifier = modifier,
            darkTheme = true,
            fadingEdges = FadingEdges(top = 60.dp, bottom = 60.dp),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 1.3.em,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 40.dp, bottom = 40.dp),
            onLineClick = { _, timeMs -> onSeek(timeMs) }
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("暂无歌词", color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp)
        }
    }
}

/** 竖屏专辑图 + 下方三个小按钮（上一曲 / 播放·暂停 / 下一曲） */
@Composable
private fun CoverWithMiniControls(
    coverUrl: String?,
    song: com.musicapp.domain.model.Song?,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = coverUrl?.takeIf { it.isNotBlank() },
            contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF2a2a3a))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniCircleBtn(onClick = onPrev) {
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            MiniCircleBtn(onClick = onToggle) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null, tint = Color.White, modifier = Modifier.size(14.dp)
                )
            }
            MiniCircleBtn(onClick = onNext) {
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
}

/** 横屏封面下方的播放行：上一曲 / 大号播放·暂停 / 下一曲 */
@Composable
private fun TriplePlayRow(
    isPlaying: Boolean,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        PillBtn(onClick = onPrev) {
            Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(24.dp))
        BigWhiteBtn(isPlaying = isPlaying, onClick = onToggle)
        Spacer(Modifier.width(24.dp))
        PillBtn(onClick = onNext) {
            Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** 进度条 + 时间行（复用 PlayerScreen 同包可见的 IosSlider —— 点击/拖动都显 thumb） */
@Composable
private fun ProgressRow(
    dragValue: Float,
    isDragging: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    currentPosition: Long,
    duration: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        IosSlider(
            value = dragValue,
            isDragging = isDragging,
            onValueChange = { onValueChange(it) },
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(currentPosition), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            Text(formatTime(duration), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        }
    }
}

// =================================================================
//  按钮组件（带按压缩放反馈）
// =================================================================

/** 竖屏专辑图下方的迷你圆按钮（26dp） */
@Composable
private fun MiniCircleBtn(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 500f),
        label = "miniBtn")
    Box(
        Modifier.scale(scale).size(26.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** 横屏药丸按钮（44dp） */
@Composable
private fun PillBtn(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 500f),
        label = "pillBtn")
    Box(
        Modifier.scale(scale).size(44.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** 大号白底播放 / 暂停按钮（56dp） */
@Composable
private fun BigWhiteBtn(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 500f),
        label = "bigWhiteBtn")
    Box(
        Modifier.scale(scale).size(56.dp).clip(CircleShape)
            .background(Color.White)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            null, tint = Color(0xFF1C1C1E), modifier = Modifier.size(26.dp)
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
