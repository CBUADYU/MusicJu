package com.musicapp.ui.screens.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musicapp.domain.model.AlbumHit
import com.musicapp.domain.model.ArtistHit
import com.musicapp.domain.model.LyricHit
import com.musicapp.domain.model.MusicPlatform
import com.musicapp.domain.model.Song
import com.musicapp.util.rememberSongCover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateBack: (() -> Unit)?,
    onPlaySong: (List<Song>, Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    // 首次进入聚焦输入框
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("搜索音乐", fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)
        ) {
            // ========= 搜索输入框 + 回车触发 =========
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.onQueryChange(it) },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "清除")
                            }
                        }
                        IconButton(onClick = { viewModel.search() }) {
                            Icon(Icons.Default.Send, contentDescription = "搜索")
                        }
                    }
                },
                placeholder = { Text("回车搜索歌曲/歌手/专辑/歌词") },
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { viewModel.search() }
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search
                )
            )

            Spacer(Modifier.height(8.dp))

            // ========= 综合搜索 Tab（ALL / 歌曲 / 歌手 / 专辑 / 歌词） =========
            TabRow(
                selectedTabIndex = uiState.tab.ordinal,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                indicator = { tabPositions ->
                    val offset by animateDpAsState(
                        targetValue = tabPositions[uiState.tab.ordinal].left,
                        label = "tabOffset"
                    )
                    Box(
                        Modifier.offset(x = offset)
                            .width(tabPositions[uiState.tab.ordinal].width)
                            .height(2.dp)
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                },
                divider = { Spacer(Modifier.height(0.dp)) }
            ) {
                SearchTab.values().forEach { tab ->
                    Tab(
                        selected = uiState.tab == tab,
                        onClick = { viewModel.switchTab(tab) },
                        text = { Text(tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (uiState.tab == tab) FontWeight.SemiBold else FontWeight.Normal) }
                    )
                }
            }

            // ========= 加载中 =========
            AnimatedVisibility(visible = uiState.isLoading, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // ========= 错误 =========
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut()
            ) {
                uiState.errorMessage?.let { msg ->
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(msg, color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.search() }) { Text("重试",
                                color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ========= 结果区 =========
            val hasResults = when (uiState.tab) {
                SearchTab.ALL -> uiState.songResults.isNotEmpty() || uiState.artistResults.isNotEmpty() ||
                                uiState.albumResults.isNotEmpty() || uiState.lyricResults.isNotEmpty()
                SearchTab.SONG -> uiState.songs.isNotEmpty()
                SearchTab.LYRIC -> uiState.lyrics.isNotEmpty()
                SearchTab.ARTIST -> uiState.artists.isNotEmpty()
                SearchTab.ALBUM -> uiState.albums.isNotEmpty()
            }
            val hasQuery = uiState.query.isNotEmpty()
            val isError = uiState.errorMessage != null
            val isIdle = !hasQuery && !uiState.isLoading

            AnimatedContent(targetState = uiState.tab, transitionSpec = {
                fadeIn(tween(250)) togetherWith fadeOut(tween(150))
            }, label = "resultTab") { tab ->
                when {
                    uiState.isLoading -> Box(Modifier.fillMaxSize()) // 显示在上面的 CircularProgressIndicator

                    isIdle -> EmptyHint("输入歌名 / 歌手 / 专辑 / 任意一句歌词")
                    hasResults -> ResultsArea(tab = tab, uiState = uiState,
                        onSongClick = { s -> viewModel.playSong(s, onPlaySong) },
                        onArtistClick = { a -> viewModel.playArtistSongs(a, onPlaySong) },
                        onAlbumClick = { al -> viewModel.playAlbumSongs(al, onPlaySong) })
                    !isError -> EmptyResultHint(uiState.query)
                    else -> Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ===================== 空态提示 =====================

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyResultHint(query: String) {
    Box(Modifier.fillMaxSize().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            Text("未找到「$query」相关结果", color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("试试其他关键词，或切换到不同 Tab 搜索", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
        }
    }
}

// ===================== 结果区 =====================

@Composable
private fun ResultsArea(
    tab: SearchTab,
    uiState: SearchUiState,
    onSongClick: (Song) -> Unit,
    onArtistClick: (ArtistHit) -> Unit,
    onAlbumClick: (AlbumHit) -> Unit
) {
    when (tab) {
        SearchTab.ALL -> AllSections(uiState, onSongClick, onArtistClick, onAlbumClick)
        SearchTab.SONG -> SongList(uiState.songs, onSongClick)
        SearchTab.LYRIC -> LyricList(uiState.lyrics, onSongClick)
        SearchTab.ARTIST -> ArtistList(uiState.artists, onArtistClick)
        SearchTab.ALBUM -> AlbumList(uiState.albums, onAlbumClick)
    }
}

@Composable
private fun AllSections(
    uiState: SearchUiState,
    onSongClick: (Song) -> Unit,
    onArtistClick: (ArtistHit) -> Unit,
    onAlbumClick: (AlbumHit) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // 歌曲
        if (uiState.songResults.isNotEmpty()) {
            item { SectionHeader("歌曲", count = uiState.songResults.size) }
            itemsIndexed(uiState.songResults.take(6), key = { _, s -> "s_${s.songId}" }) { _, s ->
                SearchSongCard(s) { onSongClick(s) }
            }
        }
        // 歌手
        if (uiState.artistResults.isNotEmpty()) {
            item { SectionHeader("歌手", count = uiState.artistResults.size) }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    itemsIndexed(uiState.artistResults.take(5), key = { _, a -> "a_${a.artistId}" }) { _, a ->
                        ArtistChip(a) { onArtistClick(a) }
                    }
                }
            }
        }
        // 专辑
        if (uiState.albumResults.isNotEmpty()) {
            item { SectionHeader("专辑", count = uiState.albumResults.size) }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    itemsIndexed(uiState.albumResults.take(5), key = { _, al -> "al_${al.albumId}" }) { _, al ->
                        AlbumChip(al) { onAlbumClick(al) }
                    }
                }
            }
        }
        // 歌词
        if (uiState.lyricResults.isNotEmpty()) {
            item { SectionHeader("歌词匹配", count = uiState.lyricResults.size) }
            itemsIndexed(uiState.lyricResults.take(4), key = { _, lh -> "l_${lh.song.songId}" }) { _, lh ->
                SearchLyricCard(lh) { onSongClick(lh.song) }
            }
        }
    }
}

@Composable
private fun SongList(songs: List<Song>, onSongClick: (Song) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        itemsIndexed(songs, key = { _, s -> "s_${s.songId}" }) { _, s -> SearchSongCard(s) { onSongClick(s) } }
    }
}

@Composable
private fun LyricList(hits: List<LyricHit>, onSongClick: (Song) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        itemsIndexed(hits, key = { _, lh -> "l_${lh.song.songId}" }) { _, lh -> SearchLyricCard(lh) { onSongClick(lh.song) } }
    }
}

@Composable
private fun ArtistList(artists: List<ArtistHit>, onArtistClick: (ArtistHit) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        gridItemsIndexed(artists, key = { _, a -> "a_${a.artistId}" }) { _, a -> ArtistChip(a) { onArtistClick(a) } }
    }
}

@Composable
private fun AlbumList(albums: List<AlbumHit>, onAlbumClick: (AlbumHit) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        gridItemsIndexed(albums, key = { _, al -> "al_${al.albumId}" }) { _, al -> AlbumChip(al) { onAlbumClick(al) } }
    }
}

// ===================== Section Header =====================

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("$count", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ===================== Cards =====================

@Composable
private fun SearchSongCard(song: Song, onClick: () -> Unit) {
    val cover = rememberSongCover(song)
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 400f), label = "songCardScale")

    Box(Modifier.fillMaxWidth().scale(scale).clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick).padding(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center) {
                if (cover?.isNotBlank() == true) {
                    AsyncImage(model = cover, contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${song.artist} · ${song.album}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "播放", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 歌词命中卡 —— 副标题显示"歌词中包含的关键词" */
@Composable
private fun SearchLyricCard(hit: LyricHit, onClick: () -> Unit) {
    val cover = rememberSongCover(hit.song)
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 400f), label = "lyricCardScale")

    Box(Modifier.fillMaxWidth().scale(scale).clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick).padding(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center) {
                if (cover?.isNotBlank() == true) {
                    AsyncImage(model = cover, contentDescription = hit.song.title,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(hit.song.title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(hit.song.artist, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                // 歌词关键词标签
                Row(Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Default.Subtitles, contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                    Spacer(Modifier.width(3.dp))
                    Text("歌词匹配", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "播放", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ArtistChip(artist: ArtistHit, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "artistChipScale")

    Column(
        modifier = Modifier.scale(scale).width(110.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(44.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center) {
            if (artist.coverUrl.isNotBlank()) {
                AsyncImage(model = artist.coverUrl, contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.Person, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(artist.name, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (artist.songCount > 0) {
            Text("${artist.songCount} 首", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlbumChip(album: AlbumHit, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "albumChipScale")

    Column(
        modifier = Modifier.scale(scale).width(110.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(110.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center) {
            if (album.coverUrl.isNotBlank()) {
                AsyncImage(model = album.coverUrl, contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.Album, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(album.title, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(album.artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
