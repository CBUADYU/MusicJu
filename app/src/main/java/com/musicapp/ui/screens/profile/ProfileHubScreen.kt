package com.musicapp.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musicapp.domain.model.Song
import com.musicapp.ui.components.SongItem
import com.musicapp.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileHubScreen(
    onLocalClick: () -> Unit,
    onImportClick: () -> Unit,
    onSettingsClick: () -> Unit,
    recentSongs: List<Song> = emptyList(),
    onPlaySong: (List<Song>, Int) -> Unit = { _, _ -> },
    onViewAllHistory: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 头像区（开源版固定为游客）
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Primary.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Primary,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "游客账号",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "本地歌单与播放历史可用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 最近播放 - 内嵌展示播放记录
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "最近播放",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onViewAllHistory) {
                            Text("查看全部")
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (recentSongs.isEmpty()) {
                        Text(
                            "暂无播放记录，去听首歌吧",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    } else {
                        recentSongs.forEachIndexed { idx, song ->
                            SongItem(
                                song = song,
                                index = idx,
                                onClick = { onPlaySong(recentSongs, idx) }
                            )
                        }
                    }
                }
            }

            EntryCard(
                icon = Icons.Default.LibraryMusic,
                title = "本地音乐",
                desc = "扫描手机本地音乐 & 解密 NCM 等格式",
                onClick = onLocalClick
            )
            EntryCard(
                icon = Icons.Default.ImportExport,
                title = "导入歌单",
                desc = "从网易云 / QQ / 酷狗 / 汽水等平台一键导入",
                onClick = onImportClick
            )
            EntryCard(
                icon = Icons.Default.Settings,
                title = "设置",
                desc = "账号管理、下载目录、搜索偏好…",
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun EntryCard(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        tonalElevation = 0.5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = Primary.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
