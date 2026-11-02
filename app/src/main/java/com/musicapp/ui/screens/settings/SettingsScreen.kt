package com.musicapp.ui.screens.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musicapp.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showAddDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "添加平台")
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
                .padding(16.dp)
        ) {
            // 成功提示
            uiState.successMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 主题
            Text("外观", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    val options = listOf(
                        ThemeMode.SYSTEM to "跟随系统",
                        ThemeMode.LIGHT to "浅色",
                        ThemeMode.DARK to "深色"
                    )
                    options.forEach { (mode, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setThemeMode(mode) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (mode) {
                                    ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                    ThemeMode.LIGHT -> Icons.Default.Brightness7
                                    ThemeMode.DARK -> Icons.Default.Brightness4
                                },
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                            if (themeMode == mode) {
                                Icon(Icons.Default.CheckCircle, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("内置平台", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            // 内置平台列表
            val builtinPlatforms = listOf(
                "网易云音乐" to "https://ncm-api.prod.gbclstudio.cn",
                "QQ音乐" to "https://api.qqmusic.com",
                "酷狗音乐" to "https://api.kugou.com",
                "酷我音乐" to "https://api.kuwo.cn",
                "咪咕音乐" to "https://api.migu.cn",
                "汽水音乐" to "https://qishui.douyin.com",
                "5sing" to "https://5sing.kugou.com",
                "Apple Music" to "https://api.music.apple.com",
                "KKBOX" to "https://api.kkbox.com"
            )

            builtinPlatforms.forEach { (name, _) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MusicNote, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(name, Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(16.dp))

            // 自定义平台
            Text("自定义平台", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            if (uiState.platforms.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        "暂无自定义平台，点击右上角 + 添加",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn {
                    items(uiState.platforms) { platform ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Extension, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(platform.name, fontWeight = FontWeight.Medium)
                                    Text(platform.apiBaseUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = platform.isEnabled,
                                    onCheckedChange = { viewModel.togglePlatform(platform) }
                                )
                                IconButton(onClick = { viewModel.deletePlatform(platform.id) }) {
                                    Icon(Icons.Default.Delete, "删除",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }

    // 添加平台对话框
    if (uiState.showAddDialog) {
        var name by remember { mutableStateOf("") }
        var api by remember { mutableStateOf("") }
        var searchPath by remember { mutableStateOf("/search") }

        AlertDialog(
            onDismissRequest = { viewModel.hideAddDialog() },
            title = { Text("添加自定义平台") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            viewModel.onNewPlatformNameChange(it)
                        },
                        label = { Text("平台名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = api,
                        onValueChange = {
                            api = it
                            viewModel.onNewPlatformApiChange(it)
                        },
                        label = { Text("API 地址") },
                        placeholder = { Text("https://api.example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchPath,
                        onValueChange = {
                            searchPath = it
                            viewModel.onNewPlatformSearchPathChange(it)
                        },
                        label = { Text("搜索接口路径") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    uiState.errorMessage?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { viewModel.hideAddDialog() }) {
                        Text("取消")
                    }
                    TextButton(onClick = { viewModel.addPlatform() }) {
                        Text("添加")
                    }
                }
            },
            dismissButton = {},
            shape = RoundedCornerShape(20.dp)
        )
    }
}
