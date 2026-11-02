package com.musicapp.ui.screens.local_music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.musicapp.data.local.entity.LocalSongEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicScreen(
    viewModel: LocalMusicViewModel,
    onNavigateBack: () -> Unit,
    onPlaySong: (List<LocalSongEntity>, Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val hasPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun doScan() {
        if (hasPermission) {
            viewModel.scan()
        } else {
            // 权限不足时不扫描，提示用户
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("本地音乐", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFilter() }) {
                        Icon(
                            if (uiState.showOnlyEncrypted) Icons.Default.FilterAlt else Icons.Default.FilterList,
                            contentDescription = "筛选",
                            tint = if (uiState.showOnlyEncrypted)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { viewModel.decryptAllNcm() },
                        enabled = !uiState.isDecrypting
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = "一键解密")
                    }
                    IconButton(
                        onClick = { doScan() },
                        enabled = !uiState.isScanning && hasPermission
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "扫描")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 扫描状态
            AnimatedVisibility(visible = uiState.isScanning || uiState.isDecrypting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 消息提示
            uiState.errorMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, "关闭", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            uiState.successMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearSuccess() }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, "关闭", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // 统计信息
            uiState.scanResult?.let { stats ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("总歌曲", stats.totalSongs.toString())
                        StatItem("可播放", stats.playableSongs.toString())
                        StatItem("加密", stats.encryptedSongs.toString())
                    }
                }
            }

            // 空状态
            if (uiState.songs.isEmpty() && !uiState.isScanning && !uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (hasPermission) Icons.Default.FolderOpen else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (hasPermission) "暂无本地音乐" else "需要存储权限",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (hasPermission) "点击右上角刷新按钮扫描本地音乐"
                                   else "请授予存储权限以扫描本地音乐文件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { doScan() }, enabled = hasPermission) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("扫描本地音乐")
                        }
                    }
                }
            } else {
                // 歌曲列表
                val displaySongs = if (uiState.showOnlyEncrypted) {
                    uiState.songs.filter { it.isEncrypted }
                } else {
                    uiState.songs
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displaySongs, key = { it.songId }) { song ->
                        LocalSongItem(
                            song = song,
                            onPlay = {
                                val playableSongs = uiState.songs.filter { !it.isEncrypted || it.decryptedPath.isNotEmpty() }
                                val index = playableSongs.indexOfFirst { it.songId == song.songId }
                                if (index >= 0) {
                                    onPlaySong(playableSongs, index)
                                }
                            },
                            onDecrypt = { viewModel.decryptNcm(song.songId) },
                            onDelete = { viewModel.deleteSong(song.songId) },
                            isDecrypting = uiState.isDecrypting
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalSongItem(
    song: LocalSongEntity,
    onPlay: () -> Unit,
    onDecrypt: () -> Unit,
    onDelete: () -> Unit,
    isDecrypting: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !song.isEncrypted || song.decryptedPath.isNotEmpty()) { onPlay() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (song.isEncrypted && song.decryptedPath.isEmpty())
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (song.isEncrypted && song.decryptedPath.isEmpty())
                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (song.isEncrypted) Icons.Default.Lock else Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (song.isEncrypted && song.decryptedPath.isEmpty())
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 歌曲信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (song.isEncrypted) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (song.decryptedPath.isNotEmpty())
                                "${song.encryptedFormat.uppercase()} (已解密)"
                            else
                                "${song.encryptedFormat.uppercase()} (加密)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (song.decryptedPath.isNotEmpty())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 操作按钮
            if (song.isEncrypted && song.decryptedPath.isEmpty()) {
                IconButton(
                    onClick = onDecrypt,
                    enabled = !isDecrypting
                ) {
                    Icon(
                        Icons.Default.LockOpen,
                        contentDescription = "解密",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (!song.isEncrypted || song.decryptedPath.isNotEmpty()) {
                IconButton(onClick = onPlay) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 删除按钮
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
