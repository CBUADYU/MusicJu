package com.musicapp.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musicapp.domain.model.Playlist
import com.musicapp.ui.common.AppearWithDelay
import com.musicapp.ui.components.PlaylistCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPlaylistClick: (Long) -> Unit,
    onImportClick: () -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit = {}
) {
    val playlists by viewModel.playlists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var longPressed by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    var showNewDialog by remember { mutableStateOf(false) }
    var gridMode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {},
                actions = {
                    // 视图切换按钮
                    IconButton(onClick = { gridMode = !gridMode }) {
                        Icon(
                            imageVector = if (gridMode) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = if (gridMode) "列表视图" else "网格视图"
                        )
                    }
                    IconButton(onClick = { showNewDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新建歌单")
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = onImportClick) {
                        Icon(Icons.Default.UploadFile, contentDescription = "导入歌单")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            playlists.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "还没有歌单",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点右上角 ＋ 新建歌单创建属于自己的歌单\n或从网易云 / QQ / 酷狗 / 汽水等平台一键导入",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassPillButton(
                                    text = "导入歌单",
                                    icon = Icons.Default.UploadFile,
                                    onClick = onImportClick
                                )
                                GlassPillButton(
                                    text = "新建",
                                    icon = Icons.Default.Add,
                                    onClick = { showNewDialog = true }
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                if (gridMode) {
                    // 4 列网格模式
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            GridPlaylistItem(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist.id) },
                                onLongClick = { longPressed = playlist }
                            )
                        }
                    }
                } else {
                    // 横条列表模式
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(playlists, key = { _, it -> it.id }) { index, playlist ->
                            AppearWithDelay(delayMs = index * 40) {
                                PlaylistCard(
                                    playlist = playlist,
                                    onClick = { onPlaylistClick(playlist.id) },
                                    onLongClick = { longPressed = playlist }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ======= 歌单操作菜单（长按弹 Material3 AlertDialog） =======
    val lp = longPressed
    if (lp != null) {
        AlertDialog(
            onDismissRequest = { longPressed = null },
            title = { Text(lp.name, fontWeight = FontWeight.SemiBold) },
            text = {
                val plat = lp.platform
                Text(
                    if (lp.isCustom) "自定义"
                    else plat.displayName
                )
            },
            confirmButton = {},
            dismissButton = {},
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ======= 删除确认 =======
    val del = deleting
    if (del != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除歌单") },
            text = { Text("确认删除「${del.name}」？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
            dismissButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(del)
                        deleting = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ======= 新建自定义歌单 =======
    if (showNewDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("新建自定义歌单") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("歌单名称") },
                    placeholder = { Text("例如：喜欢的音乐") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showNewDialog = false }) { Text("取消") }
                    TextButton(
                        onClick = {
                            viewModel.createCustomPlaylist(name.trim())
                            showNewDialog = false
                        },
                        enabled = name.isNotBlank()
                    ) { Text("创建") }
                }
            },
            dismissButton = {},
            shape = RoundedCornerShape(20.dp)
        )
    }
}

/** HomeScreen 网格模式：封面 + 标题 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridPlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "homeGridScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE5E7EB)),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.1
        )
    }
}

@Composable
private fun GlassPillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
