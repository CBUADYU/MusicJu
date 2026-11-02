package com.musicapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.musicapp.R

/**
 * 荣耀 MagicOS 文本规范 — Honor Sans CN
 *
 * 已打包 Honor Sans CN 全字重字体（res/font/），
 * 覆盖中文/英文/数字，确保在所有设备上渲染一致。
 *
 * 参考: https://hnr.yun.hihonor.com/uikit/#/zh-CN/component/introduction
 */
object HnrFontWeight {
    val Thin = FontWeight.Thin       // 100
    val Light = FontWeight.Light     // 300
    val Regular = FontWeight.Normal  // 400
    val Medium = FontWeight.Medium   // 500
    val Demibold = FontWeight.SemiBold // 600
    val Bold = FontWeight.Bold       // 700
}

/**
 * 荣耀字体族 — 打包的 Honor Sans CN，按字重映射。
 */
val HonorSansFontFamily: FontFamily = FontFamily(
    Font(R.font.honor_sans_thin, FontWeight.Thin),
    Font(R.font.honor_sans_light, FontWeight.Light),
    Font(R.font.honor_sans_regular, FontWeight.Normal),
    Font(R.font.honor_sans_medium, FontWeight.Medium),
    Font(R.font.honor_sans_demibold, FontWeight.SemiBold),
    Font(R.font.honor_sans_bold, FontWeight.Bold),
)

// 荣耀规范文本尺寸（dp → sp，手机端已适配下调）
val HnrTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = HonorSansFontFamily,
        fontWeight = HnrFontWeight.Demibold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = HonorSansFontFamily,
        fontWeight = HnrFontWeight.Demibold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = HonorSansFontFamily,
        fontWeight = HnrFontWeight.Demibold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = HonorSansFontFamily,
        fontWeight = HnrFontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = HonorSansFontFamily,
        fontWeight = HnrFontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = HonorSansFontFamily,
        fontWeight = HnrFontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = HonorSansFontFamily,
        fontWeight = HnrFontWeight.Regular,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = HonorSansFontFamily,
        fontWeight = HnrFontWeight.Regular,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = HonorSansFontFamily,
        fontWeight = HnrFontWeight.Regular,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = HonorSansFontFamily,
        fontWeight = HnrFontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    labelMedium = TextStyle(
        fontFamily = HonorSansFontFamily,
        fontWeight = HnrFontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontFamily = HonorSansFontFamily,
        fontWeight = HnrFontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
)

val Typography = HnrTypography
