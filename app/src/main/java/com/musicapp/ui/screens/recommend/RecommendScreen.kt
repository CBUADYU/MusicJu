package com.musicapp.ui.screens.recommend

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musicapp.domain.model.MusicPlatform
import com.musicapp.domain.model.Song

/**
 * 推荐首屏 —— 纯大封面瀑布流（2 列，无圆角，无歌曲信息）。
 * 网易云 music.163.com 直爬，无限滚动。
 * 长按封面弹出歌曲信息。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecommendScreen(
    viewModel: RecommendViewModel,
    onPlaylistClick: (Long) -> Unit,
    onSearchClick: () -> Unit,
    onImportClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onPlaySong: (List<Song>, Int) -> Unit = { _, _ -> }
) {
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val gridState = rememberLazyGridState()

    // 长按弹出的歌曲信息
    var infoSong by remember { mutableStateOf<Song?>(null) }

    // 底部缓冲：2 列下每屏约 10 个，预取 2 屏（≈20）保证滑动不卡顿
    val preloadBuffer = 20
    // 无限滚动：剩余不足 2 屏（上下延伸预取）时触发 loadMore
    LaunchedEffect(gridState, isLoadingMore) {
        snapshotFlow {
            val layout = gridState.layoutInfo
            val total = layout.totalItemsCount
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 1 || (total - 1 - lastVisible) <= preloadBuffer
        }.collect { shouldLoad ->
            if (shouldLoad) viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {},
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "换一批")
                    }
                    IconButton(onClick = onImportClick) {
                        Icon(Icons.Default.UploadFile, contentDescription = "导入")
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (songs.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("没拿到推荐，点🔄换一批", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(songs, key = { _, song -> "${song.platform.name}-${song.songId}" }) { index, song ->
                    PureCoverItem(
                        song = song,
                        index = index,
                        onClick = {
                            val idx = songs.indexOf(song)
                            if (idx >= 0) onPlaySong(songs, idx)
                        },
                        onLongClick = { infoSong = song }
                    )
                }

                // 底部加载指示器
                if (isLoadingMore) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier)
                        }
                    }
                }
            }
        }
    }

    // ===== 长按歌曲信息弹窗 =====
    infoSong?.let { song ->
        LongPressSongDialog(
            song = song,
            onDismiss = { infoSong = null },
            onPlay = {
                val idx = songs.indexOf(song)
                if (idx >= 0) onPlaySong(songs, idx)
                infoSong = null
            }
        )
    }
}

/** 纯封面 —— 无圆角、无文字、无背景占位；单击播放、长按弹窗。首次出现带「弹出」动画。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PureCoverItem(
    song: Song,
    index: Int = 0,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.97f else 1f, label = "pureCoverPressScale"
    )

    // 首次进入视口时的「弹出」动画：alpha 渐显 + 缩放 0.85→1，带 40ms 交错
    var entered by remember(song) { mutableStateOf(false) }
    val popScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (entered) 1f else 0.85f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "pureCoverPopScale"
    )
    val popAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(260),
        label = "pureCoverPopAlpha"
    )
    LaunchedEffect(song) {
        kotlinx.coroutines.delay((index % 8) * 40L)
        entered = true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale(pressScale * popScale)
            .alpha(popAlpha)
            .background(Color(0xFF1F2937))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        if (song.coverUrl.isNotBlank()) {
            AsyncImage(
                model = song.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** 长按弹窗 —— 歌曲大封面 + 名称 + 歌手 + 专辑 + 时长 + 平台 + 立即播放 */
@Composable
private fun LongPressSongDialog(
    song: Song,
    onDismiss: () -> Unit,
    onPlay: () -> Unit
) {
    val platformLabel = song.platform.displayName
    val durationLabel = song.duration.let { ms ->
        if (ms > 0) {
            val m = ms / 60000
            val s = (ms % 60000) / 1000
            "%d:%02d".format(m, s)
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            TextButton(onClick = onPlay) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("立即播放")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 大封面
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1F2937),
                    modifier = Modifier.size(width = 220.dp, height = 220.dp)
                ) {
                    if (song.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = song.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.MusicNote, contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 歌名
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))

                // 歌手
                Text(
                    text = song.artist.ifBlank { "未知艺术家" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                // 专辑 / 时长 / 平台
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    song.album.ifBlank { null }?.let { alb ->
                        Text(
                            text = alb,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (durationLabel != null || platformLabel.isNotBlank()) {
                        val parts = buildString {
                            if (durationLabel != null) append(durationLabel)
                            if (isNotEmpty() && platformLabel.isNotBlank()) append(" · ")
                            if (platformLabel.isNotBlank()) append(platformLabel)
                        }
                        if (parts.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = parts,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    )
}
