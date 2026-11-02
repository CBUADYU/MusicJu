package com.musicapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.musicapp.domain.model.MusicPlatform
import com.musicapp.domain.model.Playlist
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MusicNote
import com.musicapp.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "cardScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = hnrRipple(),
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = hnrRipple(),
                        onClick = onClick
                    )
                }
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = playlist.coverUrl,
                        contentDescription = playlist.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                PlatformChip(platform = playlist.platform)

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${playlist.songCount}首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun PlatformChip(
    platform: MusicPlatform,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (platform) {
        MusicPlatform.NETEASE -> NeteaseRed to Color.White
        MusicPlatform.QQ -> QQBlue to Color.White
        MusicPlatform.KUGOU -> KugouBlue to Color.White
        MusicPlatform.KUWO -> KuwoOrange to Color.White
        MusicPlatform.MIGU -> MiguPurple to Color.White
        MusicPlatform.QISHUI -> QishuiPink to Color.White
        MusicPlatform.FIVE_SING -> KugouBlue to Color.White
        MusicPlatform.APPLE_MUSIC -> MaterialTheme.colorScheme.primary to Color.White
        MusicPlatform.KKBOX -> MiguPurple to Color.White
        MusicPlatform.GEQUBAO -> MaterialTheme.colorScheme.secondary to Color.White
        MusicPlatform.CUSTOM -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        MusicPlatform.LOCAL -> MaterialTheme.colorScheme.primary to Color.White
        MusicPlatform.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bgColor.copy(alpha = 0.85f)
    ) {
        Text(
            text = platform.displayName,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}
