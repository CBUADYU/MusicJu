package com.musicapp.ui.screens.import_

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.musicapp.domain.model.MusicPlatform
import com.musicapp.domain.model.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "导入",
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 模式切换 pill
            ModeSwitcher(
                mode = uiState.mode,
                onModeChange = { viewModel.switchMode(it) }
            )

            // 主体
            when (uiState.mode) {
                ImportMode.PLAYLIST -> PlaylistImportPanel(
                    viewModel = viewModel,
                    state = uiState
                )
                ImportMode.SONG -> SingleSongImportPanel(
                    viewModel = viewModel,
                    state = uiState
                )
            }
        }
    }
}

// ============ 模式切换 ============
@Composable
private fun ModeSwitcher(
    mode: ImportMode,
    onModeChange: (ImportMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .clip(RoundedCornerShape(percent = 50)).background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModePill(
            text = "歌单",
            selected = mode == ImportMode.PLAYLIST,
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(ImportMode.PLAYLIST) }
        )
        ModePill(
            text = "单曲",
            selected = mode == ImportMode.SONG,
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(ImportMode.SONG) }
        )
    }
}

@Composable
private fun ModePill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor = if (selected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    else
        Color.Transparent
    val textColor = if (selected)
        MaterialTheme.colorScheme.onSurface
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(percent = 50))
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// ============ 歌单导入面板 ============
@Composable
private fun PlaylistImportPanel(
    viewModel: ImportViewModel,
    state: ImportUiState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 输入卡片
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "粘贴歌单分享链接",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.playlistUrl,
                    onValueChange = { viewModel.onUrlChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "例如：https://music.163.com/playlist?id=123456",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                )
                Spacer(Modifier.height(12.dp))
                GlassActionButton(
                    text = if (state.isLoading) "解析中..." else "解析歌单",
                    icon = if (state.isLoading) null else Icons.Default.Download,
                    loading = state.isLoading,
                    enabled = !state.isLoading && state.playlistUrl.isNotBlank(),
                    onClick = { viewModel.importPlaylist() }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 结果展示
        when (val result = state.result) {
            is ImportResult.Success -> ResultCard(
                isSuccess = true,
                title = "导入成功！",
                message = "${result.playlist.name}\n${result.playlist.songCount}首歌曲 · ${result.playlist.platform.displayName}"
            )

            is ImportResult.Error -> ResultCard(
                isSuccess = false,
                title = if (state.mode == ImportMode.PLAYLIST) "导入失败" else "解析失败",
                message = result.message
            )

            else -> {}
        }

        Spacer(Modifier.height(24.dp))

        // 支持平台
        Text(
            text = "支持的平台",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))

        val platforms = MusicPlatform.entries.filter { it != MusicPlatform.UNKNOWN }
        platforms.forEach { platform ->
            PlatformInfoRow(platform)
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))

        // 使用说明
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    "使用说明",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "1. 在音乐App中打开歌单\n" +
                            "2. 点击「分享」按钮\n" +
                            "3. 选择「复制链接」\n" +
                            "4. 回到本App粘贴链接\n" +
                            "5. 点击「解析歌单」导入",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ============ 单曲导入面板 ============
@Composable
private fun SingleSongImportPanel(
    viewModel: ImportViewModel,
    state: ImportUiState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 输入卡片
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "粘贴单曲分享链接",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "支持网易云 / QQ / 酷狗 / 酷我 / Apple Music / 汽水 等",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.songUrl,
                    onValueChange = { viewModel.onUrlChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "例如：https://music.163.com/song?id=123456",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                )
                Spacer(Modifier.height(12.dp))
                GlassActionButton(
                    text = if (state.isLoading) "解析中..." else "解析单曲",
                    icon = if (state.isLoading) null else Icons.Default.MusicNote,
                    loading = state.isLoading,
                    enabled = !state.isLoading && state.songUrl.isNotBlank(),
                    onClick = { viewModel.parseSong() }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 解析结果
        val parsed = state.parsedSong
        if (parsed != null) {
            ParsedSongCard(
                song = parsed,
                isLoading = state.isLoading,
                onAddToPlaylist = { viewModel.addParsedSongToPlaylist(it) }
            )
        }

        // 错误
        when (val result = state.result) {
            is ImportResult.Error -> ResultCard(
                isSuccess = false,
                title = "解析失败",
                message = result.message
            )
            is ImportResult.SongAdded -> ResultCard(
                isSuccess = true,
                title = "已加入歌单",
                message = "「${result.song.title}」已加入「${result.playlistName}」"
            )
            else -> {}
        }

        Spacer(Modifier.height(24.dp))

        // 单曲链接示例
        Text(
            text = "支持的链接格式",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Column {
                SingleLinkExample("网易云", "music.163.com/song?id=xxxx")
                SingleLinkExample("QQ 音乐", "y.qq.com/n/ryqq/songDetail/xxxx")
                SingleLinkExample("酷狗", "kugou.com/song/#hash=xxxx")
                SingleLinkExample("酷我", "kuwo.cn/play_detail/xxxx")
                SingleLinkExample("Apple Music", "music.apple.com/.../song/xxxx")
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SingleLinkExample(name: String, example: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = example,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParsedSongCard(
    song: com.musicapp.domain.model.Song,
    isLoading: Boolean,
    onAddToPlaylist: (Long) -> Unit
) {
    val app = com.musicapp.MusicApp.instance
    val customPlaylists by app.playlistRepository
        .getCustomPlaylists()
        .collectAsState(initial = emptyList())

    var showPicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    if (song.coverUrl.isNotEmpty() && !song.coverUrl.contains("|preview=")) {
                        AsyncImage(
                            model = song.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (song.coverUrl.contains("|preview=")) {
                        val cover = song.coverUrl.substringBefore("|preview=")
                        AsyncImage(
                            model = cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(32.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${song.album} · ${song.platform.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            GlassActionButton(
                text = "加入歌单",
                icon = Icons.Default.PlaylistAdd,
                enabled = !isLoading && customPlaylists.isNotEmpty(),
                loading = false,
                onClick = { showPicker = true }
            )
            if (customPlaylists.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "暂无自定义歌单，请先到「我的」新建一个",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "选择要加入的歌单",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                if (customPlaylists.isEmpty()) {
                    Text(
                        "暂无自定义歌单",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                } else {
                    customPlaylists.forEach { p ->
                        PlaylistPickRow(p) {
                            onAddToPlaylist(p.id)
                            showPicker = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistPickRow(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Icon(
            Icons.Default.QueueMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${playlist.songCount} 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============ 通用 UI ============
@Composable
private fun ResultCard(
    isSuccess: Boolean,
    title: String,
    message: String
) {
    val tint = if (isSuccess) Color(0xFF34D399) else MaterialTheme.colorScheme.error
    val icon = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun GlassActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onSurface,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(6.dp))
                }
                Text(text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PlatformInfoRow(platform: MusicPlatform) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    platform.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    platform.exampleUrl(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }
    }
}

private fun MusicPlatform.exampleUrl(): String = when (this) {
    MusicPlatform.NETEASE -> "music.163.com/playlist?id=xxxx"
    MusicPlatform.QQ -> "y.qq.com/n/ryqq/playlist/xxxx"
    MusicPlatform.KUGOU -> "kugou.com/songlist/xxxx"
    MusicPlatform.KUWO -> "kuwo.cn/playlist_detail/xxxx"
    MusicPlatform.MIGU -> "migu.cn/v3/music/playlist/xxxx"
    MusicPlatform.QISHUI -> "qishui.douyin.com/s/xxxx"
    MusicPlatform.FIVE_SING -> "5sing.kugou.com/xxxx"
    MusicPlatform.APPLE_MUSIC -> "music.apple.com/playlist/xxxx"
    MusicPlatform.KKBOX -> "kkbox.com/playlist/xxxx"
    MusicPlatform.GEQUBAO -> "gequbao.net/music/xxxx"
    MusicPlatform.CUSTOM -> ""
    MusicPlatform.LOCAL -> ""
    MusicPlatform.UNKNOWN -> ""
}
