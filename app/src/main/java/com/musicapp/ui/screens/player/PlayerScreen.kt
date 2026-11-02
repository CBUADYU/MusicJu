package com.musicapp.ui.screens.player

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musicapp.data.remote.api.NetworkConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    onOpenLyrics: () -> Unit = {},
    onOpenLyricMv: () -> Unit = {}
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val coverUrl by viewModel.coverUrl.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val playMode by viewModel.playMode.collectAsState()
    val error by viewModel.error.collectAsState()
    val lrcLines by viewModel.lrcLines.collectAsState()
    val currentLrcIndex by viewModel.currentLrcIndex.collectAsState()
    val isLiked by viewModel.isCurrentLiked.collectAsState()
    val context = LocalContext.current
    val playlistPickVisible by viewModel.pickPlaylistVisible.collectAsState()
    val customPlaylists by viewModel.customPlaylists.collectAsState()
    val message by viewModel.message.collectAsState()

    // 加入歌单 / 新建歌单成功的 Toast 提示
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    // 「加入歌单」选择弹窗
    if (playlistPickVisible) {
        PlaylistPickerDialog(
            playlists = customPlaylists,
            onPick = viewModel::addCurrentToPlaylist,
            onCreateNew = viewModel::createCustomPlaylist,
            onDismiss = viewModel::hidePlaylistPicker
        )
    }

    BackHandler(enabled = true) { onNavigateBack() }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFF1a1a2e), Color(0xFF0a0a12), Color(0xFF050508))
            )
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp)
                .padding(top = 48.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopBar(onBack = onNavigateBack, onLyrics = onOpenLyrics)
            Spacer(Modifier.height(16.dp))

            // 封面 —— 圆角方形，长按保存到相册
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = {
                            val url = coverUrl?.takeIf { it.isNotBlank() } ?: return@combinedClickable
                            val title = currentSong?.title ?: "cover"
                            saveCoverToGallery(context, url, title)
                        }
                    )
            ) {
                AsyncImage(
                    model = coverUrl?.takeIf { it.isNotBlank() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF2a2a3a))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                )
            }

            Spacer(Modifier.height(14.dp))

            // 歌名 + 歌手 + 一行歌词
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    currentSong?.title ?: "", color = Color.White,
                    fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    currentSong?.artist ?: "", color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))

                // 只显示一行歌词
                val activeLine = lrcLines.getOrNull(currentLrcIndex)
                Text(
                    activeLine?.text ?: "点击查看歌词",
                    color = Color.White.copy(alpha = if (activeLine != null) 0.45f else 0.25f),
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenLyrics() },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(Modifier.height(8.dp))

            // 喜欢 / MV / 加歌单
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionIconBtn(
                    onClick = { viewModel.toggleLikeCurrent() },
                    active = isLiked
                ) {
                    Icon(
                        if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        null,
                        tint = if (isLiked) Color(0xFFFF5A5F) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                ActionIconBtn(onClick = onOpenLyricMv) {
                    Icon(Icons.Default.Movie, null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(16.dp))
                ActionIconBtn(onClick = viewModel::showPlaylistPicker) {
                    Icon(Icons.Default.AddCircle, null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            Controls(
                isPlaying = isPlaying, playMode = playMode,
                positionMs = currentPosition, durationMs = duration, error = error,
                onPrev = { viewModel.playPrevious() },
                onToggle = { viewModel.togglePlayPause() },
                onNext = { viewModel.playNext() },
                onCycleMode = { viewModel.cyclePlayMode() },
                onSeek = { viewModel.seekTo(it) },
                onDownload = { viewModel.downloadCurrentSong() },
                onClearError = { viewModel.clearError() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 「加入自定义歌单」选择弹窗：已有歌单列表 + 新建歌单输入。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerDialog(
    playlists: List<com.musicapp.domain.model.Playlist>,
    onPick: (Long) -> Unit,
    onCreateNew: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        title = { Text("加入自定义歌单") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("新建歌单名称") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newName.isNotBlank() && newName.trim().isNotEmpty()) {
                                onCreateNew(newName)
                                newName = ""
                            }
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }

                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.Divider()
                Spacer(Modifier.height(10.dp))

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "还没有自定义歌单\n上面输入名称并点击 + 即可创建",
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPick(pl.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = pl.coverUrl.ifBlank { null },
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(pl.name, color = Color.White,
                                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text("${pl.songCount} 首歌曲",
                                        color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                }
                                Icon(Icons.Default.AddCircle, null,
                                    tint = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun TopBar(onBack: () -> Unit, onLyrics: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CircularIconBtn(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text("播放中", color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp, fontWeight = FontWeight.Medium)
        CircularIconBtn(onClick = onLyrics) {
            Icon(Icons.Default.QueueMusic, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CircularIconBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "btn")
    Box(
        Modifier.scale(scale).size(40.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun ActionIconBtn(
    onClick: () -> Unit,
    active: Boolean = false,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, label = "act")
    Box(
        Modifier.scale(scale).size(42.dp).clip(CircleShape)
            .background(
                if (active) Color(0x33FF5A5F) else Color.White.copy(alpha = 0.06f)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun Controls(
    isPlaying: Boolean,
    playMode: com.musicapp.util.PlayMode,
    positionMs: Long, durationMs: Long,
    error: String?,
    onPrev: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit,
    onCycleMode: () -> Unit, onSeek: (Long) -> Unit,
    onDownload: () -> Unit, onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragValue by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(positionMs, durationMs, isDragging) {
        if (!isDragging) dragValue = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    }
    Column(modifier) {
        AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
            error?.let { msg ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33ef4444)).padding(12.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, null, tint = Color(0xFFFF8A80), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(msg, color = Color.White, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearError) {
                        Text("关闭", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        IosSlider(
            value = dragValue,
            isDragging = isDragging,
            onValueChange = { dragValue = it; isDragging = true },
            onValueChangeFinished = {
                onSeek((dragValue * durationMs.coerceAtLeast(1L)).toLong())
                isDragging = false
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(positionMs), fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
            Text(formatTime(durationMs), fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularIconBtn(onClick = onCycleMode) {
                Icon(
                    when (playMode) {
                        com.musicapp.util.PlayMode.SHUFFLE -> Icons.Default.Shuffle
                        com.musicapp.util.PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    },
                    null, tint = Color.White, modifier = Modifier.size(20.dp)
                )
            }
            CircularIconBtn(onClick = onPrev) {
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            BigPlayButton(isPlaying = isPlaying, onClick = onToggle)
            CircularIconBtn(onClick = onNext) {
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            CircularIconBtn(onClick = onDownload) {
                Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun BigPlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "play")
    Box(
        Modifier.scale(scale).size(72.dp).clip(CircleShape).background(Color.White)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(visible = !isPlaying,
            enter = scaleIn(initialScale = 0.3f) + fadeIn(),
            exit = scaleOut(targetScale = 0.3f) + fadeOut()) {
            Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF1C1C1E), modifier = Modifier.size(32.dp))
        }
        AnimatedVisibility(visible = isPlaying,
            enter = scaleIn(initialScale = 0.3f) + fadeIn(),
            exit = scaleOut(targetScale = 0.3f) + fadeOut()) {
            Icon(Icons.Default.Pause, null, tint = Color(0xFF1C1C1E), modifier = Modifier.size(32.dp))
        }
    }
}

/**
 * iOS 风格的细进度条：白色轨道、白色细灰底、拖动圆点。
 * 同时支持「点击跳转」与「拖动」，圆点按像素对齐到已播放轨道末端。
 * thumb 仅在 [isDragging] 为 true 时显示（按下/拖动时），静止时隐藏。
 */
@Composable
internal fun IosSlider(
    value: Float,
    isDragging: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val v = value.coerceIn(0f, 1f)
    // 记录容器实际像素宽度，用于圆点精确对齐
    var widthPx by remember { mutableFloatStateOf(0f) }
    val thumbX = v * widthPx
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "thumbScale"
    )
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(if (isDragging) 80 else 180),
        label = "thumbAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(widthPx) {
                awaitEachGesture {
                    // 按下即定位（支持点击）
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    if (widthPx > 0f) {
                        onValueChange((down.position.x / widthPx).coerceIn(0f, 1f))
                    }
                    // 按住移动则跟手（支持拖动）
                    do {
                        val ev = awaitPointerEvent(PointerEventPass.Main)
                        val stillPressed = ev.changes.any { it.pressed }
                        if (stillPressed && widthPx > 0f) {
                            val x = ev.changes.first().position.x
                            onValueChange((x / widthPx).coerceIn(0f, 1f))
                            ev.changes.first().consume()
                        } else break
                    } while (stillPressed)
                    // 抬起时提交跳转
                    onValueChangeFinished()
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // 细轨道底
        Box(
            modifier = Modifier.fillMaxWidth().height(2.dp)
                .background(Color.White.copy(alpha = 0.25f))
        )
        // 已播放轨道（按比例填充，末端即圆点位置）
        Box(
            modifier = Modifier.fillMaxWidth(v).height(2.dp).background(Color.White)
        )
        // 拖动圆点（静止时 scale=0 隐藏，按下/拖动时放大显现）
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbX.toInt() - 6, 0) }
                .size(12.dp)
                .scale(thumbScale)
                .alpha(thumbAlpha)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** 下载封面并保存到系统相册 Pictures 目录（Android 10+ 用 MediaStore，旧版用传统路径） */
private fun saveCoverToGallery(context: android.content.Context, url: String, title: String) {
    val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "cover" }
    CoroutineScope(Dispatchers.IO).launch {
        val bytes = runCatching {
            NetworkConfig.neteaseOkHttpClient.newCall(okhttp3.Request.Builder().url(url).build())
                .execute().use { it.body?.bytes() }
        }.getOrNull()
        if (bytes == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "封面下载失败", Toast.LENGTH_SHORT).show()
            }
            return@launch
        }

        val displayName = "MusicCover_${safeTitle.take(40)}.jpg"

        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+：用 MediaStore + RELATIVE_PATH，无需存储权限
            runCatching {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MusicApp")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues)
                    ?: return@runCatching false
                resolver.openOutputStream(uri).use { os ->
                    os?.write(bytes)
                }
                true
            }.getOrDefault(false)
        } else {
            // Android 9 及以下：写 Pictures 公共目录（需要 WRITE_EXTERNAL_STORAGE 权限，targetSdk<33 时已有）
            runCatching {
                val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                    .let { File(it, "MusicApp") }.also { it.mkdirs() }
                val file = File(dir, displayName)
                FileOutputStream(file).use { it.write(bytes) }
                true
            }.getOrDefault(false)
        }

        withContext(Dispatchers.Main) {
            if (ok) Toast.makeText(context, "封面已保存到相册", Toast.LENGTH_SHORT).show()
            else Toast.makeText(context, "保存失败，请检查权限", Toast.LENGTH_SHORT).show()
        }
    }
}
