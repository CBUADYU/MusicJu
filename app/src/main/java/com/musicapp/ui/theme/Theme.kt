package com.musicapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Shapes
import androidx.core.view.WindowCompat

enum class ThemeMode { SYSTEM, LIGHT, DARK }

// ======================================================================
// MagicOS 规范色值 — 来自 aitool-plus MagicOS 参考文档
// ======================================================================
private object MagicColors {
    // 主色
    val primaryLight = Color(0xFF0066FF)
    val primaryDark = Color(0xFF4D94FF)
    val onPrimary = Color.White

    // 次级/辅助
    val secondaryLight = Color(0xFF00C853)
    val secondaryDark = Color(0xFF4D94FF)

    // 浅模式背景
    val surfaceLight = Color(0xFFFFFFFF)
    val surfaceVariantLight = Color(0xFFF7F8FA)    // 面板/搜索框填充
    val backgroundLight = Color(0xFFF7F8FA)

    // 深模式背景
    val surfaceDark = Color(0xFF1F2937)             // 卡片深背景
    val surfaceVariantDark = Color(0xFF374151)     // 面板深填充
    val backgroundDark = Color(0xFF111827)

    // 语义文字色
    val onSurfaceLight = Color(0xFF1F2937)
    val onSurfaceDark = Color(0xFFF3F4F6)
    val onSurfaceVariantLight = Color(0xFF6B7280)
    val onSurfaceVariantDark = Color(0xFFD1D5DB)

    // 分隔线
    val outlineLight = Color(0xFFE1E4E8)
    val outlineDark = Color(0xFF4B5563)
    val outlineVariantLight = Color(0xFFE5E7EB)
    val outlineVariantDark = Color(0xFF374151)

    // 状态色（沿用 Color.kt 已有的荣耀 UIKit 规范）
    val errorLight = Color(0xFFEA3C37)
    val errorDark = Color(0xFFD93A34)
}

/**
 * MagicOS 标准 Shapes — 16dp 卡片 / 20dp 弹窗 / 胶囊按钮
 */
private fun MagicShapes(): Shapes {
    val extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    val small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    val medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)  // 卡片标准圆角
    val large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)   // 弹窗/底部面板
    val extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    return Shapes(
        extraSmall = extraSmall,
        small = small,
        medium = medium,
        large = large,
        extraLarge = extraLarge
    )
}

/**
 * MusicAppTheme — 荣耀 MagicOS 原生风格。
 *
 * 直接映射 MagicOS 官方规范到 Material3 ColorScheme，
 * 使用 HnrTypography（Honor Sans CN 字体）作为唯一 Typography。
 */
@Composable
fun MusicAppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemIsDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            runCatching {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) magicDarkColorScheme() else magicLightColorScheme(),
        typography = HnrTypography,
        shapes = MagicShapes()
    ) {
        content()
    }
}

private fun magicLightColorScheme() = lightColorScheme(
    primary = MagicColors.primaryLight,
    onPrimary = MagicColors.onPrimary,
    primaryContainer = Color(0xFFD1E0FF),
    onPrimaryContainer = Color(0xFF001A33),
    secondary = MagicColors.secondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8FFE0),
    onSecondaryContainer = Color(0xFF00210B),
    tertiary = Color(0xFFAF52DE),
    background = MagicColors.backgroundLight,
    onBackground = MagicColors.onSurfaceLight,
    surface = MagicColors.surfaceLight,
    onSurface = MagicColors.onSurfaceLight,
    surfaceVariant = MagicColors.surfaceVariantLight,
    onSurfaceVariant = MagicColors.onSurfaceVariantLight,
    error = MagicColors.errorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = MagicColors.outlineLight,
    outlineVariant = MagicColors.outlineVariantLight
)

private fun magicDarkColorScheme() = darkColorScheme(
    primary = MagicColors.primaryDark,
    onPrimary = Color(0xFF001A33),
    primaryContainer = Color(0xFF0047AB),
    onPrimaryContainer = Color(0xFFD1E0FF),
    secondary = MagicColors.secondaryDark,
    onSecondary = Color(0xFF001A33),
    secondaryContainer = Color(0xFF0047AB),
    onSecondaryContainer = Color(0xFFD1E0FF),
    tertiary = Color(0xFFD8B4FE),
    background = MagicColors.backgroundDark,
    onBackground = MagicColors.onSurfaceDark,
    surface = MagicColors.surfaceDark,
    onSurface = MagicColors.onSurfaceDark,
    surfaceVariant = MagicColors.surfaceVariantDark,
    onSurfaceVariant = MagicColors.onSurfaceVariantDark,
    error = MagicColors.errorDark,
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = MagicColors.outlineDark,
    outlineVariant = MagicColors.outlineVariantDark
)