package com.musicapp.domain.model

/**
 * 音乐平台枚举
 */
enum class MusicPlatform(val displayName: String, val hostKeywords: List<String>) {
    NETEASE("网易云音乐", listOf("music.163.com", "163cn.tv")),
    QQ("QQ音乐", listOf("y.qq.com", "i.y.qq.com")),
    KUGOU("酷狗音乐", listOf("kugou.com", "t.kugou.com")),
    KUWO("酷我音乐", listOf("kuwo.cn")),
    MIGU("咪咕音乐", listOf("migu.cn")),
    QISHUI("汽水音乐", listOf("qishui.com", "qishui.douyin.com", "douyin.com")),
    FIVE_SING("5sing", listOf("5sing.kugou.com", "5sing.com")),
    APPLE_MUSIC("Apple Music", listOf("music.apple.com")),
    KKBOX("KKBOX", listOf("kkbox.com")),
    GEQUBAO("歌曲宝", listOf("gequbao.net")),
    CUSTOM("自定义", emptyList()),
    LOCAL("本地音乐", emptyList()),
    UNKNOWN("未知平台", emptyList());

    companion object {
        fun detect(url: String): MusicPlatform {
            return entries.firstOrNull { platform ->
                platform.hostKeywords.any { url.contains(it, ignoreCase = true) }
            } ?: UNKNOWN
        }
    }
}