package com.musicapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS 风格底部 TabBar —— 磨砂半透明 + 50dp + 蓝色选中态 + 无 indicator。
 * 三个 Tab：推荐 / 歌单 / 我的。
 */
@Composable
fun MainBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f

    val containerColor = if (isDark) Color(0xD9111827) else Color(0xD9FFFFFF)
    val topBorderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    val selectedColor = scheme.primary
    val unselectedColor = Color(0xFF8E8E93)

    val tabs = listOf(
        BottomTab("recommend", "推荐", Icons.Filled.Radio, Icons.Outlined.Radio),
        BottomTab("library", "歌单", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
        BottomTab("profile", "我的", Icons.Filled.Person, Icons.Outlined.Person),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .navigationBarsPadding()
            .height(50.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.3.dp)
                .align(Alignment.TopCenter)
                .background(topBorderColor)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                IosTabItem(
                    selected = selected,
                    onClick = { onNavigate(tab.route) },
                    selectedIcon = tab.selectedIcon,
                    unselectedIcon = tab.unselectedIcon,
                    label = tab.label,
                    selectedColor = selectedColor,
                    unselectedColor = unselectedColor
                )
            }
        }
    }
}

@Composable
private fun IosTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selectedColor: Color,
    unselectedColor: Color
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "tab-scale")

    Box(
        modifier = Modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .height(50.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = null,
                tint = if (selected) selectedColor else unselectedColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) selectedColor else unselectedColor,
                maxLines = 1
            )
        }
    }
}

private data class BottomTab(
    val route: String,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)
