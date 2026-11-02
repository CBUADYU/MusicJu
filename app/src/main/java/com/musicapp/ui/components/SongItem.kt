package com.musicapp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musicapp.domain.model.Song
import com.musicapp.ui.theme.hnrRipple
import com.musicapp.util.formatDuration
import com.musicapp.util.rememberSongCover

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: Song,
    index: Int,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onCoverClick: (() -> Unit)? = null,
    glassStyle: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "songItemScale_${song.id}"
    )
    // 播放中：封面做一个轻微的 "呼吸" 缩放
    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "songCoverScale_${song.id}"
    )

    // 封面（自动补抓）
    val cover = rememberSongCover(song)

    val baseModifier = modifier
        .fillMaxWidth()
        .scale(scale)
        .clip(RoundedCornerShape(if (glassStyle) 14.dp else 10.dp))
        .then(
            if (glassStyle) Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            else Modifier
        )
        .combinedClickable(
            interactionSource = interactionSource,
            indication = hnrRipple(),
            onClick = onClick,
            onLongClick = onLongClick,
        )

    Row(
        modifier = baseModifier
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 序号 / 播放状态
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.4f, stiffness = 500f)) + fadeIn(tween(200))) togetherWith
                        (scaleOut(spring(dampingRatio = 0.4f, stiffness = 500f)) + fadeOut(tween(200)))
                },
                label = "songIndexIcon_${song.id}"
            ) { playing ->
                if (playing) {
                    EqualizerIndicator()
                } else {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 封面
        Box(
            modifier = Modifier
                .size(48.dp)
                .scale(coverScale)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (glassStyle) 0.5f else 1f))
                .then(
                    if (onCoverClick != null) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = hnrRipple(),
                        onClick = onCoverClick
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (cover?.isNotBlank() == true) {
                AsyncImage(
                    model = cover,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 歌曲信息
        AnimatedContent(
            targetState = song.title to song.artist,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "songInfo_${song.id}",
            modifier = Modifier.weight(1f)
        ) { (title, artist) ->
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(artist)
                        if (song.album.isNotEmpty()) append(" · ").append(song.album)
                        if (song.duration > 0) append("  ·  ").append(formatDuration(song.duration))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EqualizerIndicator() {
    val infinite = rememberInfiniteTransition(label = "eq")
    Row(
        Modifier
            .width(26.dp)
            .height(20.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(4) { i ->
            val h by infinite.animateFloat(
                initialValue = 0.22f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 380 + i * 140),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "eq_b_$i"
            )
            Box(
                Modifier
                    .padding(horizontal = 1.2.dp)
                    .width(3.dp)
                    .fillMaxHeight(h)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
