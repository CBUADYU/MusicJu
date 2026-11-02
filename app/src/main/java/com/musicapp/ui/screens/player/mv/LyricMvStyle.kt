package com.musicapp.ui.screens.player.mv

import androidx.compose.ui.graphics.Color

/**
 * 字幕 MV 主题。
 *
 * 每个主题包含：配色 + 动画技法 (Technique)。
 * Technique 是渲染引擎的能力（8 种），主题是对它们的具体穿搭。
 */
enum class LyricMvStyle(
    val displayName: String,
    val description: String,
    val background: Color,
    val mainText: Color,
    val subText: Color,
    val accent: Color,
    val ghostA: Color,
    val ghostB: Color,
    val technique: Technique,
    val particleShape: ParticleShape = ParticleShape.DOT
) {
    // ========== 演唱会 & 舞台 ==========
    NEON_STAGE(
        displayName = "霓虹舞台",
        description = "深紫夜空 + 霓虹光晕 + 环形频谱 · 演唱会感",
        background = Color(0xFF0B0A1E),
        mainText = Color(0xFFFFFFFF),
        subText = Color(0xFFB8B5D0),
        accent = Color(0xFFFF3FA3),
        ghostA = Color(0xFFFF3FA3),
        ghostB = Color(0xFF00E5FF),
        technique = Technique.NEON_GLOW
    ),
    CYBER_GLITCH(
        displayName = "赛博故障",
        description = "暗青底 + 红青错位 + 数据破损 · 电子感",
        background = Color(0xFF071A1F),
        mainText = Color(0xFFFFFFFF),
        subText = Color(0xFF5A7A80),
        accent = Color(0xFFFF1744),
        ghostA = Color(0xFFFF1744),
        ghostB = Color(0xFF00E5FF),
        technique = Technique.CYBER_GLITCH
    ),
    DARK_AURORA(
        displayName = "暗黑极光",
        description = "黑底 + 极光渐变 + 呼吸光带 · 神秘",
        background = Color(0xFF050510),
        mainText = Color(0xFFF0ECFF),
        subText = Color(0xFF8886A0),
        accent = Color(0xFF7C4DFF),
        ghostA = Color(0xFF7C4DFF),
        ghostB = Color(0xFF00E676),
        technique = Technique.NEON_GLOW
    ),

    // ========== 国风 & 传统 ==========
    INK_AND_SEAL(
        displayName = "水墨朱砂",
        description = "深墨 + 朱红印章 + 漂浮墨点 · 古风",
        background = Color(0xFF0E0E10),
        mainText = Color(0xFFE8E4D8),
        subText = Color(0xFF9A9790),
        accent = Color(0xFFC1272D),
        ghostA = Color(0xFFC1272D),
        ghostB = Color(0xFFE8E4D8),
        technique = Technique.SUMI_ZHU
    ),
    SAKURA_DREAM(
        displayName = "樱花纷飞",
        description = "淡粉底 + 深紫 + 花瓣飘零 · 浪漫",
        background = Color(0xFFFBE4EA),
        mainText = Color(0xFF4A1434),
        subText = Color(0xFF8A4A69),
        accent = Color(0xFFD93A74),
        ghostA = Color(0xFFEC6A9A),
        ghostB = Color(0xFF9A8AE6),
        technique = Technique.SUMI_ZHU,
        particleShape = ParticleShape.PETAL
    ),
    GOLDEN_NIGHT(
        displayName = "金箔夜空",
        description = "深蓝夜空 + 金色主字 + 星光粒子 · 华丽",
        background = Color(0xFF0B1530),
        mainText = Color(0xFFFFD700),
        subText = Color(0xFFFFF1A8),
        accent = Color(0xFF00BFFF),
        ghostA = Color(0xFFFFD700),
        ghostB = Color(0xFF00BFFF),
        technique = Technique.PARTICLE_DREAM
    ),

    // ========== 梦幻 & 浪漫 ==========
    DREAM_PARTICLE(
        displayName = "梦幻粒子",
        description = "柔光斑 + 上升光点 + 爆闪粒子 · 梦幻",
        background = Color(0xFF141030),
        mainText = Color(0xFFFFF0FF),
        subText = Color(0xFFB099D8),
        accent = Color(0xFFFF6EC7),
        ghostA = Color(0xFFFF6EC7),
        ghostB = Color(0xFF00FFD5),
        technique = Technique.PARTICLE_DREAM
    ),
    OCEAN_BLUE(
        displayName = "深海蓝调",
        description = "浓蓝深海 + 青色发光 + 泡泡粒子 · 安静",
        background = Color(0xFF031A2E),
        mainText = Color(0xFFE4FAFF),
        subText = Color(0xFF7FB2C8),
        accent = Color(0xFF1FD2E6),
        ghostA = Color(0xFF1FD2E6),
        ghostB = Color(0xFF3B5BFF),
        technique = Technique.PARTICLE_DREAM
    ),

    // ========== 极简 & 现代 ==========
    MINIMAL_LIGHT(
        displayName = "极简光线",
        description = "深灰 + 细横线 + 节拍点 · 简约现代",
        background = Color(0xFF121214),
        mainText = Color(0xFFF0F0F0),
        subText = Color(0xFF888888),
        accent = Color(0xFF64B5F6),
        ghostA = Color(0xFF64B5F6),
        ghostB = Color(0xFFF0F0F0),
        technique = Technique.MINIMAL_LINE
    ),
    KINETIC_BOLD(
        displayName = "动感粗体",
        description = "粗大字 + 旋转方块 + 滚动背景字 · 活力",
        background = Color(0xFFFF6B35),
        mainText = Color(0xFFFFFFFF),
        subText = Color(0xFFFFD2B0),
        accent = Color(0xFF2B2BD9),
        ghostA = Color(0xFFFFFFFF),
        ghostB = Color(0xFF2B2BD9),
        technique = Technique.KINETYPO
    ),

    // ========== 复古 & 怀旧 ==========
    FILM_REtro(
        displayName = "胶片复古",
        description = "胶片边框 + 颗粒噪点 + 帧切换闪白 · 老电影",
        background = Color(0xFF1A1410),
        mainText = Color(0xFFF5E6D3),
        subText = Color(0xFFA89880),
        accent = Color(0xFFE8A24B),
        ghostA = Color(0xFFE8A24B),
        ghostB = Color(0xFFF5E6D3),
        technique = Technique.COLOR_FILM
    ),
    VAPOR_80S(
        displayName = "蒸汽波",
        description = "粉紫渐变 + 80s 霓虹 + 网格地面 · 复古未来",
        background = Color(0xFF1D0B3B),
        mainText = Color(0xFFFFD1F2),
        subText = Color(0xFFB89BFF),
        accent = Color(0xFFFF6EC7),
        ghostA = Color(0xFF00FFD5),
        ghostB = Color(0xFFFF6EC7),
        technique = Technique.NEON_GLOW
    ),

    // ========== 悬疑 & 暗黑 ==========
    MONITOR_HORROR(
        displayName = "监控惊魂",
        description = "CRT 扫描线 + 雪花 + HUD 录像 · 悬疑感",
        background = Color(0xFF0A0A0A),
        mainText = Color(0xFFDCDCDC),
        subText = Color(0xFF666666),
        accent = Color(0xFFFF2D2D),
        ghostA = Color(0xFFFF2D2D),
        ghostB = Color(0xFF00FF41),
        technique = Technique.HORROR_MONITOR
    ),
    ;

    /** 底层渲染能力 —— 引擎里实现共享的 Canvas 画法 */
    enum class Technique {
        NEON_GLOW,      // 霓虹光晕（呼吸 + 环形频谱 + 辐射光带）
        CYBER_GLITCH,   // RGB 错位 + 抖动 + 扫光 + 频谱条
        SUMI_ZHU,       // 毛笔墨点 + 朱印 + 古风边框
        PARTICLE_DREAM,  // 发光渐变 + 上升光点 + 爆发粒子
        MINIMAL_LINE,   // 逐字滑入 + 节拍点 + 横线
        KINETYPO,       // 粗体大字 + 旋转方块 + 滚动背景字
        HORROR_MONITOR, // CRT 扫描线 + 雪花 + 视频噪声
        COLOR_FILM,     // 胶片边框 + 颗粒 + 帧切换闪白
    }

    /** SUMI_ZHU 技法下飘浮粒子的形状 */
    enum class ParticleShape { DOT, PETAL }
}
