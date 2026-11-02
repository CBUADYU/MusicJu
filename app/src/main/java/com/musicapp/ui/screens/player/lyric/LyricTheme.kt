package com.musicapp.ui.screens.player.lyric

/**
 * 歌词动画主题 — 定义渲染风格 + 驱动参数。
 *
 * 每个主题包含：
 *  - id / displayName / description：UI 展示
 *  - useSpring：是否用 Folia spring 物理（false = 直接插值到目标）
 *  - perWordEffect：是否启用逐字特效（sweep 填充 / pop 弹出 / glow 脉冲）
 *  - visibleBefore / visibleAfter：可见的前后行数
 *  - anchorRatio：活动行在容器中的垂直锚点位置（0.5 = 居中）
 *  - blurPassed：经过的行是否模糊（仅当 !useSpring 时也生效）
 *
 * 主题只定义"想让 track 变成什么样"。spring 推进、帧循环由 Folia draw() 统一驱动。
 */
enum class LyricTheme(
    val id: String,
    val displayName: String,
    val description: String,
    val useSpring: Boolean = true,
    val perWordEffect: Boolean = false,
    val visibleBefore: Int = 1,
    val visibleAfter: Int = 1,
    val anchorRatio: Float = 0.46f,
    val blurPassed: Boolean = true
) {
    /** Folia 原版：spring 弹性滚动 + 前后行缩放淡化 */
    FOLIA_SCROLL(
        id = "folia_scroll",
        displayName = "Folia 滚动",
        description = "弹性弹簧滚动，前后行缩放淡化"
    ),

    /** 逐字点亮：活动行的每个字随进度从左到右填充颜色 */
    WORD_SWEEP(
        id = "word_sweep",
        displayName = "逐字点亮",
        description = "活动行逐字点亮填充颜色，如 K 歌歌词",
        perWordEffect = true,
        visibleBefore = 2, visibleAfter = 2
    ),

    /** 逐字弹出：每个字依次弹入，上一个字放大淡出成为背景 */
    WORD_POP(
        id = "word_pop",
        displayName = "逐字弹出",
        description = "每个字依次弹跳出场，前一字放大淡出成背景",
        perWordEffect = true,
        visibleBefore = 2, visibleAfter = 2
    ),

    /** 发光脉冲：活动行每个字随节拍发出发光脉冲 */
    GLOW_PULSE(
        id = "glow_pulse",
        displayName = "发光脉冲",
        description = "活动行持续发光脉冲，音乐感强",
        perWordEffect = true,
        visibleBefore = 1, visibleAfter = 1
    ),

    /** 淡入滑动：上一行向上滑出淡出，下一行淡入滑入（不用 spring） */
    FADE_SLIDE(
        id = "fade_slide",
        displayName = "淡入滑动",
        description = "上下行滑动淡入淡出，温柔无弹性",
        useSpring = false,
        visibleBefore = 1, visibleAfter = 1
    ),

    /** 普通列表：无动画，只高亮活动行，性能最好 */
    PLAIN(
        id = "plain",
        displayName = "普通列表",
        description = "无动画，只高亮活动行，性能最佳",
        useSpring = false,
        perWordEffect = false,
        visibleBefore = 3, visibleAfter = 3,
        blurPassed = false
    );

    companion object {
        fun fromId(id: String): LyricTheme = entries.firstOrNull { it.id == id } ?: FOLIA_SCROLL
    }
}

/**
 * 歌词渲染引擎 — 决定底层实现
 * - FOLIA_WEB: WebView + Pixi.js + Folia 原版 WebGL shader（原汁原味 Folia）
 * - FOLIA_NATIVE_GL: GLSurfaceView + 原生 OpenGL ES（A 方案，性能最好）
 * - COMPOSE_THEMED: Compose Canvas CPU 软绘制（兼容性最好）
 */
enum class LyricRenderEngine(
    val id: String,
    val displayName: String,
    val description: String
) {
    FOLIA_WEB("folia_web", "Folia WebGL (推荐)", "原版 Folia Pixi.js + GLSL shader 渲染"),
    FOLIA_NATIVE_GL("folia_native_gl", "原生 OpenGL ES", "GLES 2.0 原生渲染，性能最好"),
    COMPOSE_THEMED("compose", "Compose Canvas", "CPU 软绘制，兼容性好");

    companion object {
        fun fromId(id: String): LyricRenderEngine = entries.firstOrNull { it.id == id } ?: FOLIA_WEB
    }
}
