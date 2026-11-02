package com.musicapp.ui.screens.player.lyric

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 歌词动画主题选择 BottomSheet。
 *
 * 展示所有 6 种主题，当前选中的高亮边框 + 勾选图标。
 * 用户点击后立即切换并持久化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricThemeSelectorSheet(
    currentTheme: LyricTheme,
    currentEngine: LyricRenderEngine = LyricRenderEngine.FOLIA_WEB,
    onSelectEngine: (LyricRenderEngine) -> Unit = {},
    onSelectTheme: (LyricTheme) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp, bottom = 6.dp)
                    .width(36.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                "歌词动画",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "选择渲染引擎和动画主题",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))

            // ---- 渲染引擎选择 ----
            Text(
                "渲染引擎",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            LyricRenderEngine.entries.forEachIndexed { idx, engine ->
                ThemeItemRow(
                    displayName = engine.displayName,
                    description = engine.description,
                    selected = engine == currentEngine,
                    onClick = {
                        onSelectEngine(engine)
                        scope.launch { sheetState.hide().also { onDismiss() } }
                    }
                )
                if (idx < LyricRenderEngine.entries.size - 1) {
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- 动画主题选择 ----
            Text(
                "动画主题 (仅 Compose 引擎)",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            LyricTheme.entries.forEachIndexed { idx, theme ->
                ThemeItemRow(
                    displayName = theme.displayName,
                    description = theme.description,
                    selected = theme == currentTheme,
                    onClick = {
                        onSelectTheme(theme)
                        scope.launch { sheetState.hide().also { onDismiss() } }
                    }
                )
                if (idx < LyricTheme.entries.size - 1) {
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}


@Composable
private fun ThemeItemRow(
    displayName: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.12f),
        label = "theme_border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f),
        label = "theme_bg"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ThemeIcon(theme = LyricTheme.FOLIA_SCROLL, isEngine = false) // placeholder, icon不重要
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    displayName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = 2
                )
            }
            Box(
                Modifier.size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Text("✓", color = Color(0xFF1C1C1E), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ThemeItemRow(
    theme: LyricTheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    ThemeItemRow(theme.displayName, theme.description, selected, onClick)
}

@Composable
private fun ThemeIcon(theme: LyricTheme, isEngine: Boolean) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val colors = if (isEngine) listOf(1f, 0.7f, 0.4f) else when (theme) {
                LyricTheme.FOLIA_SCROLL -> listOf(0.4f, 0.7f, 1f)
                LyricTheme.WORD_SWEEP -> listOf(1f, 0.5f, 0.2f)
                LyricTheme.WORD_POP -> listOf(0.3f, 0.6f, 1f)
                LyricTheme.GLOW_PULSE -> listOf(1f, 1f, 1f)
                LyricTheme.FADE_SLIDE -> listOf(0.3f, 0.6f, 0.9f)
                LyricTheme.PLAIN -> listOf(0.5f, 0.5f, 0.5f)
            }
            colors.forEach { alpha ->
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = alpha)))
            }
        }
    }
}
