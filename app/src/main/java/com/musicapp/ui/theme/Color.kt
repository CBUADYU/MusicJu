package com.musicapp.ui.theme

import androidx.compose.ui.graphics.Color

// ========================================================================
// 荣耀 MagicOS UIKit 色板 — https://hnr.yun.hihonor.com/uikit/
// ========================================================================

// --- Accent / 主色 ---
val HnrAccentLight = Color(0xFF256FFF)
val HnrAccentDark = Color(0xFF3D7FFF)

// --- OnBackground（荣耀规范用透明黑/白，这里折算为近似实色）---
val HnrOnBackgroundLight = Color(0xFF1A1A1A)  // #000000E5
val HnrOnBackgroundDark  = Color(0xFFE5E5E5)  // #FFFFFFDB
val HnrOnSurfaceLight    = HnrOnBackgroundLight
val HnrOnSurfaceDark     = HnrOnBackgroundDark
val HnrOnSurfaceVariantLight = Color(0xFF555555) // #00000099
val HnrOnSurfaceVariantDark  = Color(0xFFB2B2B2) // #FFFFFF99

// --- Background ---
val HnrBackgroundLight = Color(0xFFFFFFFF)
val HnrBackgroundDark  = Color(0xFF000000)
val HnrSurfaceLight    = Color(0xFFFFFFFF)
val HnrSurfaceDark     = Color(0xFF202022)
val HnrSurfaceVariantLight = Color(0xFFF2F3F6)
val HnrSurfaceVariantDark  = Color(0xFF1A1A1A)

// --- 卡片 ---
val HnrCardBgSimpleLight = Color(0xFFFFFFFF)
val HnrCardBgSimpleDark  = Color(0xFF404040)
val HnrCardBgComplexLight = Color(0xFFFFFFFF)
val HnrCardBgComplexDark  = Color(0xFF262626)
val HnrListCardBgLight = Color(0xFFFFFFFF)
val HnrListCardBgDark  = Color(0xFF202022)

// --- Magic Gray ---
val HnrMagicGray1Light = Color(0xFFF7F7F7)
val HnrMagicGray1Dark  = Color(0xFF1A1A1A)
val HnrMagicGray3Light = Color(0xFFE5E5E5)
val HnrMagicGray3Dark  = Color(0xFF333333)
val HnrMagicGray4Light = Color(0xFFCCCCCC)
val HnrMagicGray4Dark  = Color(0xFF4D4D4D)
val HnrMagicGray5Light = Color(0xFF9E9E9E)
val HnrMagicGray5Dark  = Color(0xFF616161)

// --- 控件背板 ---
val HnrControlNormalLight = Color(0x0C000000)
val HnrControlNormalDark  = Color(0x19FFFFFF)
val HnrDividerLight = Color(0x33000000)
val HnrDividerDark  = Color(0x4DFFFFFF)

// --- 状态色 ---
val HnrErrorLight   = Color(0xFFEA3C37)
val HnrErrorDark    = Color(0xFFD93A34)
val HnrWarningLight = Color(0xFFF46629)
val HnrWarningDark  = Color(0xFFEB642A)
val HnrSuccessLight = Color(0xFF15C252)
val HnrSuccessDark  = Color(0xFF15B34C)

// --- Magic Colors（多彩色）---
val HnrMagicColor1  = Color(0xFF1587FF)  // 蓝
val HnrMagicColor2  = Color(0xFF00AAEE)  // 青蓝
val HnrMagicColor3  = Color(0xFF00BEC8)  // 青绿
val HnrMagicColor4  = Color(0xFF15C252)  // 绿（成功）
val HnrMagicColor5  = Color(0xFF91D418)  // 黄绿
val HnrMagicColor6  = Color(0xFF8455F2)  // 紫
val HnrMagicColor7  = Color(0xFFE33089)  // 粉
val HnrMagicColor8  = Color(0xFFEA3C37)  // 红
val HnrMagicColor9  = Color(0xFFF46629)  // 橙
val HnrMagicColor10 = Color(0xFFFF9700)  // 深橙
val HnrMagicColor11 = Color(0xFFFFC803)  // 黄
val HnrMagicColor12 = Color(0xFF9E9FA2)  // 灰

// ========================================================================
// 兼容旧引用（业务代码已引用的颜色变量）
// ========================================================================

// Spotify 绿 / 原 Primary
val Primary = HnrAccentLight
val PrimaryDark = HnrAccentDark
val PrimaryLight = Color(0xFF4D7FFF)
val Secondary = HnrMagicColor4
val SecondaryDark = HnrMagicColor6

// Purple / Pink 色板（原 Material3 默认色）
val Purple80 = HnrMagicColor6
val Purple40 = HnrMagicColor6
val PurpleGrey80 = HnrMagicGray5Light
val PurpleGrey40 = HnrMagicGray5Dark
val Pink80 = HnrMagicColor7
val Pink40 = HnrMagicColor7

// Dark / Light Background
val DarkBackground = HnrBackgroundDark
val DarkSurface = HnrSurfaceDark
val DarkSurfaceVariant = HnrSurfaceVariantDark
val DarkOnSurface = HnrOnSurfaceDark
val DarkOnSurfaceVariant = HnrOnSurfaceVariantDark

val LightBackground = HnrBackgroundLight
val LightSurface = HnrSurfaceLight
val LightSurfaceVariant = HnrSurfaceVariantLight
val LightOnSurface = HnrOnSurfaceLight
val LightOnSurfaceVariant = HnrOnSurfaceVariantLight

// 平台品牌色
val NeteaseRed = Color(0xFFC62F2F)
val QQBlue = Color(0xFF31C27C)      // 原 QQ 色值保留
val QQGreen = Color(0xFF31C27C)
val KugouBlue = Color(0xFF2CA8E3)
val KuwoOrange = Color(0xFFFFA500)
val MiguPurple = Color(0xFF9B59B6)
val QishuiPink = Color(0xFFFE2C55)
