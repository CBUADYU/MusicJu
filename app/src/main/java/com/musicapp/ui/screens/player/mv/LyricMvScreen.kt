package com.musicapp.ui.screens.player.mv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 字幕 MV 效果选择页。
 * 选一种主题 → 进入全屏播放页。
 */
@Composable
fun LyricMvScreen(
    currentSongTitle: String,
    currentSongArtist: String,
    selectedStyle: LyricMvStyle,
    onSelectStyle: (LyricMvStyle) -> Unit,
    onConfirm: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val styles = LyricMvStyle.values().toList()
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFF1a1a2e), Color(0xFF0a0a12))
            )
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 100.dp)
        ) {
            // Top bar
            Box(Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.ArrowBack, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.CenterStart).size(22.dp)
                        .clickable { onNavigateBack() }
                )
                Text(
                    "字幕 MV", color = Color.White, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "当前歌曲", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp
            )
            Text(
                "$currentSongTitle · $currentSongArtist",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(18.dp))
            Text("选择效果", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(styles) { style ->
                    LyricMvStyleCard(
                        style = style,
                        selected = style == selectedStyle,
                        onClick = { onSelectStyle(style) }
                    )
                }
            }
        }

        // 底部「开始 MV」按钮
        Box(
            Modifier.fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(16.dp))
                .background(selectedStyle.background)
                .clickable { onConfirm() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "▶ 开始 MV · ${selectedStyle.displayName}",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LyricMvStyleCard(
    style: LyricMvStyle,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF22222e))
            .clickable { onClick() }
            .then(
                if (selected) Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF333342))
                else Modifier
            )
            .padding(12.dp)
    ) {
        // 颜色条（预览）
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(listOf(style.background, style.accent, style.ghostA))
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                style.displayName.take(2),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle, null,
                    tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            style.displayName, color = Color.White,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold
        )
        Text(
            style.description, color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp, lineHeight = 14.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}
