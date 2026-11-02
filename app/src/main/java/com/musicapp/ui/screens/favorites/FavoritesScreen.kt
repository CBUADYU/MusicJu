package com.musicapp.ui.screens.favorites

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.domain.model.Song
import com.musicapp.ui.components.SongItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onPlaySong: (List<Song>, Int) -> Unit,
    onOpenSongDetail: (Song, Long) -> Unit = { _, _ -> },
    onNavigateBack: () -> Unit,
    onSearchClick: (() -> Unit)? = null
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toast by viewModel.toast.collectAsStateWithLifecycle()

    LaunchedEffect(toast) {
        toast?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.consumeToast() }
    }

    // 长按选中的歌曲菜单
    var menuSong by remember { mutableStateOf<Song?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "我喜欢",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${songs.size} 首歌曲",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (songs.isEmpty()) {
            // 空状态：fade + scale 进入
            val alpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(500),
                label = "emptyAlpha"
            )
            val scale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.6f,
                    stiffness = 280f
                ),
                label = "emptyScale"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                        this.scaleX = scale
                        this.scaleY = scale
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "还没有喜欢的歌曲",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "在歌曲详情页点亮 ♡ 即可收藏到这里",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (onSearchClick != null) {
                        Spacer(Modifier.height(20.dp))
                        FilledTonalButton(onClick = onSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("去搜索喜欢的歌")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(songs, key = { _, it -> it.id }) { index, song ->
                    val isThisSongPlaying =
                        isPlaying && currentSong?.songId == song.songId
                    com.musicapp.ui.common.AppearWithDelay(delayMs = index * 30) {
                        SongItem(
                            song = song,
                            index = songs.indexOf(song),
                            isPlaying = isThisSongPlaying,
                            onClick = { onPlaySong(songs, songs.indexOf(song)) },
                            onCoverClick = { onOpenSongDetail(song, 0L) },
                            onLongClick = { menuSong = song }
                        )
                    }
                }
            }
        }
    }

    // 长按菜单：播放 / 取消喜欢
    menuSong?.let { song ->
        AlertDialog(
            onDismissRequest = { menuSong = null },
            title = { Text(song.title) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { menuSong = null }) {
                        Text("取消")
                    }
                    TextButton(onClick = {
                        menuSong = null
                        onPlaySong(listOf(song), 0)
                    }) {
                        Text("播放")
                    }
                    TextButton(onClick = {
                        viewModel.removeFromLiked(song)
                        menuSong = null
                    }) {
                        Text("取消喜欢")
                    }
                }
            },
            dismissButton = {},
            shape = RoundedCornerShape(20.dp)
        )
    }
}
