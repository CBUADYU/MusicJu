package com.musicapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musicapp.domain.model.Song
import com.musicapp.ui.theme.hnrClickable

/**
 * iOS (Apple Music) 风格迷你播放器 — 悬浮圆角卡片。
 *
 * 设计要点（贴近 iOS「正在播放」的迷你条）：
 *  - 白色圆角卡片悬浮在 TabBar 上方，四周留白、细投影
 *  - 顶部一条细播放进度（Apple Music 红）
 *  - 封面 40dp 圆角、标题/歌手、播放控制
 */
@Composable
fun MiniPlayer(
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayerClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = currentSong != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        val song = currentSong ?: return@AnimatedVisibility
        val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
        val scheme = MaterialTheme.colorScheme
        val isDark = scheme.background.luminance() < 0.5f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .shadow(
                    elevation = if (isDark) 0.dp else 6.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.12f)
                )
                .background(scheme.surface)
                .border(
                    width = 0.5.dp,
                    color = if (isDark) scheme.outline.copy(alpha = 0.5f)
                            else scheme.outline.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPlayerClick
                )
        ) {
            Column(Modifier.fillMaxWidth()) {
                // 顶部播放进度条 — Apple Music 红
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .drawBehind {
                            val w = size.width
                            val filled = w * progress.coerceIn(0f, 1f)
                            // 背景轨道
                            drawLine(
                                color = scheme.outline.copy(alpha = if (isDark) 0.25f else 0.12f),
                                start = Offset(0f, size.height / 2),
                                end = Offset(w, size.height / 2),
                                strokeWidth = size.height
                            )
                            // 已播放部分
                            drawLine(
                                color = scheme.primary,
                                start = Offset(0f, size.height / 2),
                                end = Offset(filled, size.height / 2),
                                strokeWidth = size.height
                            )
                        }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 封面
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.coverUrl.isNotEmpty()) {
                            AsyncImage(
                                model = song.coverUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 标题/歌手
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = song.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 控制按钮 — iOS 简约
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier.hnrClickable { onPlayPrevious() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.SkipPrevious,
                                contentDescription = "上一首",
                                tint = scheme.onSurface,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(scheme.primary)
                                .hnrClickable { onTogglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = scheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box(
                            modifier = Modifier.hnrClickable { onPlayNext() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = "下一首",
                                tint = scheme.onSurface,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}