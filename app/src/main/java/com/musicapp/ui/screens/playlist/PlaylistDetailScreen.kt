package com.musicapp.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.musicapp.data.repository.PlaylistRepository
import com.musicapp.domain.model.Playlist
import com.musicapp.domain.model.Song
import com.musicapp.ui.components.PlatformChip
import com.musicapp.ui.components.SongItem
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Divider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistViewModel,
    onNavigateBack: () -> Unit,
    onSongClick: (Int) -> Unit,
    onOpenSongDetail: (Song, Long) -> Unit = { _, _ -> },
    playlistRepository: PlaylistRepository? = null
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val currentSongIndex by viewModel.currentSongIndex.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val playlistId = playlist?.id ?: 0L

    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(toast) {
        toast?.let {
            snack.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.consumeToast()
        }
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var longPressSong by remember { mutableStateOf<Song?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // "我喜欢"换头图相关
    val isLikedPlaylist = playlist?.name == "我喜欢"
    var showCoverDialog by remember { mutableStateOf(false) }

    // 歌单内搜索过滤
    val filteredSongs = remember(searchQuery, songs) {
        val q = searchQuery.trim()
        if (q.isEmpty()) songs
        else songs.filter {
            it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.album.contains(q, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        playlist?.name ?: "歌单详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // "我喜欢"歌单：允许编辑头图
                    if (isLikedPlaylist) {
                        IconButton(onClick = { showCoverDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "更换封面")
                        }
                    }
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) searchQuery = ""
                    }) {
                        Icon(
                            if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (showSearch) "关闭搜索" else "搜索"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item { PlaylistHeader(playlist = playlist) }

                if (showSearch) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 4.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("搜索歌单内歌曲 / 歌手 / 专辑") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "清除")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(percent = 50),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (songs.isNotEmpty() && !showSearch) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GlassActionButton(
                                text = "播放全部",
                                icon = Icons.Default.PlayArrow,
                                modifier = Modifier.weight(1f),
                                onClick = { onSongClick(0) }
                            )
                            GlassActionButton(
                                text = "随机播放",
                                icon = Icons.Default.Shuffle,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (songs.isNotEmpty()) {
                                        val idx = (0 until songs.size).random()
                                        onSongClick(idx)
                                    }
                                }
                            )
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            GlassActionButton(
                                text = "收藏整歌单",
                                icon = Icons.Default.FavoriteBorder,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { viewModel.favoriteWholePlaylist() }
                            )
                        }
                    }
                }

                if (songs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 24.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.MusicOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "该歌单暂无歌曲",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))
                                GlassActionButton(
                                    text = "添加歌曲",
                                    icon = Icons.Default.Add,
                                    onClick = { showAddSheet = true }
                                )
                            }
                        }
                    }
                } else if (showSearch && filteredSongs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "未找到匹配「$searchQuery」的歌曲",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    itemsIndexed(filteredSongs, key = { _, s -> "p${playlistId}_${s.id}" }) { index, song ->
                        val realIndex = songs.indexOf(song)
                        SongItem(
                            song = song,
                            index = index,
                            isPlaying = realIndex == currentSongIndex,
                            onClick = { onSongClick(realIndex) },
                            onLongClick = { longPressSong = song },
                            onCoverClick = { onSongClick(realIndex) },
                            glassStyle = true,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // ======= "我喜欢" 换头图弹窗 =======
    if (showCoverDialog && isLikedPlaylist) {
        var urlInput by remember { mutableStateOf("") }
        val coverCandidates = songs.map { it.coverUrl }.filter { it.isNotBlank() }.distinct().take(9)

        AlertDialog(
            onDismissRequest = { showCoverDialog = false },
            title = { Text("更换歌单封面") },
            text = {
                Column {
                    Text(
                        "输入网络图片 URL：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        placeholder = { Text("https://...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (coverCandidates.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "或从当前歌曲封面选择：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(coverCandidates) { url ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE5E7EB))
                                        .clickable {
                                            viewModel.updateCoverUrl(url)
                                            showCoverDialog = false
                                        }
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showCoverDialog = false }) {
                        Text("取消")
                    }
                    TextButton(
                        onClick = {
                            val newUrl = urlInput.trim()
                            if (newUrl.isNotBlank()) {
                                viewModel.updateCoverUrl(newUrl)
                                showCoverDialog = false
                            }
                        },
                        enabled = urlInput.trim().isNotBlank()
                    ) {
                        Text("使用 URL")
                    }
                }
            },
            dismissButton = {},
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showAddSheet) {
        val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
        AddSongsSheet(
            playlists = allPlaylists.filter { it.id != playlistId },
            onDismiss = { showAddSheet = false },
            onConfirm = { sourceId, songIds ->
                scope.launch { viewModel.addSongsFromAnotherPlaylist(sourceId, songIds) }
                showAddSheet = false
            }
        )
    }

    val lp = longPressSong
    if (lp != null) {
        val all by viewModel.allPlaylists.collectAsStateWithLifecycle()
        SongActionSheet(
            song = lp,
            customPlaylists = all.filter { it.isCustom },
            onDismiss = { longPressSong = null },
            onPlay = {
                val idx = songs.indexOf(lp).coerceAtLeast(0)
                onSongClick(idx)
                longPressSong = null
            },
            onOpenDetail = {
                onOpenSongDetail(lp, playlistId)
                longPressSong = null
            },
            onAddToPlaylist = { targetId ->
                scope.launch { viewModel.addSongToOtherPlaylist(lp, targetId) }
                longPressSong = null
            },
            onCreateNewPlaylist = { name ->
                scope.launch { viewModel.createCustomPlaylistAndAdd(lp, name) }
                longPressSong = null
            },
            onRemove = {
                viewModel.removeSong(lp)
                longPressSong = null
            }
        )
    }
}

@Composable
private fun PlaylistHeader(playlist: Playlist?) {
    if (playlist == null) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE5E7EB)),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = playlist.coverUrl,
                        contentDescription = playlist.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(playlist.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlatformChip(platform = playlist.platform)
                if (playlist.isCustom) {
                    AssistChip(onClick = {}, label = { Text("自定义") })
                }
                if (playlist.isPinned) {
                    AssistChip(
                        onClick = {},
                        leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, Modifier.size(16.dp)) },
                        label = { Text("已置顶") }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${playlist.songCount} 首歌曲",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GlassActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "glassBtnScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ========== AddSongsSheet ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSongsSheet(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onConfirm: (sourceId: Long, songIds: Set<Long>) -> Unit
) {
    val app = com.musicapp.MusicApp.instance
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        var stage by remember { mutableStateOf(PickStage.Playlist) }
        var selectedSource by remember { mutableStateOf<Playlist?>(null) }

        when (stage) {
            PickStage.Playlist -> {
                Text(
                    "从哪个歌单选择歌曲？",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {
                    if (playlists.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("还没有其它歌单，先导入或新建一个吧～", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        itemsIndexed(playlists, key = { _, p -> p.id }) { _, p ->
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable { selectedSource = p; stage = PickStage.Songs }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Box(
                                        Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE5E7EB)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (p.coverUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = p.coverUrl, contentDescription = null,
                                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Default.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(p.name, fontWeight = FontWeight.Medium)
                                        Text("${p.songCount} 首 · ${p.platform}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, null)
                                }
                                Divider()
                            }
                        }
                    }
                }
            }
            PickStage.Songs -> {
                val source = selectedSource ?: return@ModalBottomSheet
                val sourceSongs by produceState(
                    initialValue = emptyList<Song>(), key1 = source.id,
                    producer = { value = app.playlistRepository.getSongsByPlaylistId(source.id) }
                )
                val checked = remember { mutableStateMapOf<Long, Unit>() }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    TextButton(onClick = { stage = PickStage.Playlist }) {
                        Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(4.dp)); Text("返回")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        if (checked.size == sourceSongs.size) checked.clear()
                        else sourceSongs.forEach { checked[it.id] = Unit }
                    }) { Text(if (checked.size == sourceSongs.size) "取消全选" else "全选") }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(sourceSongs, key = { _, s -> s.id }) { idx, s ->
                        val isChecked = checked.containsKey(s.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().toggleable(value = isChecked) { _ ->
                                if (isChecked) checked.remove(s.id) else checked[s.id] = Unit
                            }.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Checkbox(checked = isChecked, onCheckedChange = null)
                            SongItem(song = s, index = idx, onClick = {
                                if (isChecked) checked.remove(s.id) else checked[s.id] = Unit
                            }, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    GlassActionButton(
                        text = "加入当前歌单（${checked.size} 首）", icon = Icons.Default.PlaylistAdd,
                        onClick = { onConfirm(source.id, checked.keys.toSet()) }, modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private enum class PickStage { Playlist, Songs }

// ========== SongActionSheet ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongActionSheet(
    song: Song, customPlaylists: List<Playlist>,
    onDismiss: () -> Unit, onPlay: () -> Unit, onOpenDetail: () -> Unit,
    onAddToPlaylist: (Long) -> Unit, onCreateNewPlaylist: (String) -> Unit, onRemove: () -> Unit
) {
    var step by remember { mutableStateOf(ActionStep.Root) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        when (step) {
            ActionStep.Root -> {
                Column(Modifier.padding(bottom = 32.dp)) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(song.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    ActionRow(icon = Icons.Default.PlayArrow, title = "播放") { onPlay() }
                    ActionRow(icon = Icons.Default.Info, title = "查看单曲详情") { onOpenDetail() }
                    ActionRow(icon = Icons.Default.PlaylistAdd, title = "加入歌单…") { step = ActionStep.PickPlaylist }
                    ActionRow(icon = Icons.Default.Delete, title = "从当前歌单移除", tint = MaterialTheme.colorScheme.error) { onRemove() }
                }
            }
            ActionStep.PickPlaylist -> {
                var showNewDialog by remember { mutableStateOf(false) }
                Column(Modifier.padding(bottom = 24.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { step = ActionStep.Root }) { Icon(Icons.Default.ArrowBack, null); Text("返回") }
                        Text("加入到哪个歌单？", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showNewDialog = true }) { Icon(Icons.Default.Add, null); Text("新建") }
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        if (customPlaylists.isEmpty()) {
                            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("还没有自定义歌单，先新建一个吧～", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } }
                        } else {
                            itemsIndexed(customPlaylists, key = { _, p -> p.id }) { _, p ->
                                ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text("${p.songCount} 首") },
                                    modifier = Modifier.clickable { onAddToPlaylist(p.id) })
                            }
                        }
                    }
                }
                if (showNewDialog) {
                    var name by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showNewDialog = false },
                        title = { Text("新建自定义歌单") },
                        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("歌单名称") }) },
                        confirmButton = { Row(horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showNewDialog = false }) { Text("取消") }
                            TextButton(enabled = name.isNotBlank(), onClick = { showNewDialog = false; onCreateNewPlaylist(name.trim()) }) { Text("创建并加入") }
                        } },
                        dismissButton = {}, shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
    }
}

private enum class ActionStep { Root, PickPlaylist }

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String,
    tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(16.dp))
            Text(title, color = tint, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
