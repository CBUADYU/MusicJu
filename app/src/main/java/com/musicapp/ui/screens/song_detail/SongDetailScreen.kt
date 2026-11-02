package com.musicapp.ui.screens.song_detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.musicapp.domain.model.MusicPlatform
import com.musicapp.domain.model.Playlist
import com.musicapp.domain.model.Song
import com.musicapp.ui.theme.Primary
import com.musicapp.util.formatDuration

/**
 * 单曲详情页
 *
 * 包含：
 *  - 顶部返回
 *  - 专辑封面（点击→歌词页回调）
 *  - 歌名 / 歌手 / 专辑 / 时长 / 平台标签
 *  - 操作行：▶ 立即播放 / ➕ 加入歌单 / ⬇️ 下载
 *  - 「加入自定义歌单」弹窗
 *  - 长按歌曲标题区域 = 加入歌单
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SongDetailScreen(
    viewModel: SongDetailViewModel,
    onNavigateBack: () -> Unit,
    onOpenLyrics: () -> Unit,
    onPlay: (Song) -> Unit,
) {
    val song by viewModel.song.collectAsStateWithLifecycle()
    val customPlaylists by viewModel.customPlaylists.collectAsStateWithLifecycle()
    val pickerVisible by viewModel.pickPlaylistVisible.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isLiked by viewModel.isLiked.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    // 长按操作菜单（封面/标题区域长按触发）
    var longPressMenu by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歌曲详情", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val s = song ?: return@IconButton
                        shareSong(ctx, s)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        song?.let { s ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background,
                                Primary.copy(alpha = 0.06f)
                            )
                        )
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(20.dp))

                // —— 专辑封面（点击→歌词页），长按→加入歌单
                Box(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = onOpenLyrics,
                            onLongClick = { longPressMenu = true }
                        )
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = s.coverUrl.ifBlank { null },
                        contentDescription = "专辑封面 · 点击打开歌词",
                        modifier = Modifier.size(260.dp)
                    )
                    // 点击提示
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lyrics,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "点击封面查看歌词",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // —— 标题（长按 = 加入歌单）
                Text(
                    text = s.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .clickable { longPressMenu = true }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    s.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (s.album.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "专辑：${s.album}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlatformChip(s.platform)
                    if (s.duration > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text(formatDuration(s.duration)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                // —— 操作按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionButton(
                        icon = Icons.Default.PlayArrow,
                        label = "播放",
                        bgColor = Primary
                    ) { onPlay(s) }

                    ActionButton(
                        icon = Icons.Default.PlaylistAdd,
                        label = "加入歌单"
                    ) { viewModel.showPlaylistPicker() }

                    ActionButton(
                        icon = Icons.Default.Download,
                        label = "下载"
                    ) {
                        Toast.makeText(ctx, "下载功能已在播放器页支持", Toast.LENGTH_SHORT).show()
                    }

                    ActionButton(
                        icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        label = if (isLiked) "已喜欢" else "喜欢",
                        bgColor = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
                    ) { viewModel.toggleLike() }
                }

                Spacer(Modifier.height(32.dp))

                // 底部小提示
                Text(
                    "提示：长按封面 / 标题可快速「加入自定义歌单」",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // 长按封面/标题 → 加入歌单
            AnimatedVisibility(
                visible = longPressMenu,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                AlertDialog(
                    onDismissRequest = { longPressMenu = false },
                    confirmButton = {
                        TextButton(onClick = {
                            longPressMenu = false
                            viewModel.showPlaylistPicker()
                        }) { Text("加入自定义歌单") }
                    },
                    dismissButton = {
                        TextButton(onClick = { longPressMenu = false }) { Text("取消") }
                    },
                    title = { Text("操作") },
                    text = { Text("对《${s.title}》执行什么操作？") }
                )
            }
        }
    }

    // 选择自定义歌单 BottomSheet / Dialog
    AnimatedVisibility(
        visible = pickerVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        PlaylistPickerDialog(
            playlists = customPlaylists,
            onPick = { pl ->
                viewModel.addToPlaylist(pl.id)
                viewModel.hidePlaylistPicker()
            },
            onCreateNew = { name ->
                viewModel.createCustomPlaylist(name)
            },
            onDismiss = viewModel::hidePlaylistPicker
        )
    }
}

@Composable
private fun PlatformChip(platform: MusicPlatform) {
    AssistChip(
        onClick = {},
        label = { Text(platform.displayName, fontSize = 12.sp) },
        leadingIcon = {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        }
    )
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bgColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    onClick: () -> Unit
) {
    val fgColor = if (bgColor == Primary) Color.White else MaterialTheme.colorScheme.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = fgColor, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 分享当前歌曲：构造平台分享链接（若可识别）→ 复制到剪贴板并弹出系统分享面板
 */
private fun shareSong(ctx: Context, song: Song) {
    val text = buildString {
        append(song.title)
        append(" - ")
        append(song.artist)
        if (song.album.isNotBlank()) {
            append("（")
            append(song.album)
            append("）")
        }
        append("\n")
        val link = shareLinkFor(song)
        if (link != null) {
            append(link)
        }
    }
    // 1) 复制到剪贴板，方便粘贴到任何地方
    runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("song", text))
    }
    // 2) 弹系统分享面板
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(intent, "分享歌曲"))
    }.onFailure {
        Toast.makeText(ctx, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
}

/** 已知平台的单曲分享链接规则（移动端友好链接）；LOCAL/UNKNOWN 返回 null */
private fun shareLinkFor(song: Song): String? {
    val id = song.songId.takeIf { it.isNotBlank() } ?: return null
    return when (song.platform) {
        MusicPlatform.NETEASE -> "https://y.music.163.com/m/song?id=$id"
        MusicPlatform.QQ -> "https://y.qq.com/n/ryqq/songDetail/$id"
        MusicPlatform.KUGOU -> "https://www.kugou.com/song/#hash=$id"
        MusicPlatform.KUWO -> "https://www.kuwo.cn/play_detail/$id"
        MusicPlatform.MIGU -> "https://music.migu.cn/v3/music/song/$id"
        MusicPlatform.QISHUI -> "https://qishui.douyin.com/s/$id"
        MusicPlatform.FIVE_SING -> "https://5sing.kugou.com/yc/$id"
        MusicPlatform.KKBOX -> "https://www.kkbox.com/tw/tc/song/$id"
        MusicPlatform.GEQUBAO -> "https://www.gequbao.net/music/$id"
        MusicPlatform.APPLE_MUSIC -> id
        else -> null
    }
}

/**
 * 加入自定义歌单选择对话框：包含已有自定义歌单列表 + 「+ 新建歌单」输入框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
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
                // 新建歌单输入
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
                            if (newName.isNotBlank()) {
                                onCreateNew(newName.trim())
                                newName = ""
                            }
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Divider()
                Spacer(Modifier.height(10.dp))

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "还没有自定义歌单\n上面输入名称并点击 + 即可创建",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            ListItem(
                                leadingContent = {
                                    AsyncImage(
                                        model = pl.coverUrl.ifBlank { null },
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                },
                                headlineContent = {
                                    Text(pl.name, fontWeight = FontWeight.SemiBold)
                                },
                                supportingContent = {
                                    Text("${pl.songCount} 首歌曲")
                                },
                                trailingContent = {
                                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = Primary)
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPick(pl) }
                            )
                        }
                    }
                }
            }
        }
    )
}
