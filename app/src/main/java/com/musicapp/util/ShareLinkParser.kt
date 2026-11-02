package com.musicapp.util

import com.musicapp.domain.model.MusicPlatform

/**
 * 音乐链接解析器 - 从各音乐平台分享链接中提取信息
 *  支持：
 *   - 歌单链接 (Playlist)
 *   - 单曲链接  (Song)
 */
object ShareLinkParser {

    data class ParsedLink(
        val platform: MusicPlatform,
        val playlistId: String,
        val originalUrl: String,
        /** 是否单曲链接 */
        val isSong: Boolean = false,
        /** 单曲的资源 ID（仅 isSong=true 时有效） */
        val songId: String? = null
    )

    /**
     * 解析分享链接，提取平台和歌单ID / 单曲ID
     */
    fun parse(url: String): ParsedLink? {
        val trimmedUrl = url.trim()
        // 先尝试识别是否是单曲链接
        parseSong(trimmedUrl)?.let { return it }
        // 否则按歌单解析
        return parsePlaylist(trimmedUrl)
    }

    // -----------------------------------------------------------------
    //  单曲解析（含 PC / 移动端 H5 / 短链）
    // -----------------------------------------------------------------
    private fun parseSong(url: String): ParsedLink? {
        // 网易云: https://music.163.com/song?id=123456
        val neteaseSong = Regex("""music\.163\.com.*?/song\?id=(\d+)""")
        neteaseSong.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.NETEASE,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }

        // 网易云移动端 H5: https://y.music.163.com/m/song?id=123456
        val neteaseSongM = Regex("""y\.music\.163\.com/m/song\?id=(\d+)""")
        neteaseSongM.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.NETEASE,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }

        // 网易云短链: https://163cn.tv/xxxxx （短链跳转，songId 需重定向解析，此处仅识别平台）
        if (url.contains("163cn.tv", ignoreCase = true)) {
            return ParsedLink(
                platform = MusicPlatform.NETEASE,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = ""
            )
        }

        // QQ: https://y.qq.com/n/ryqq/songDetail/003aQYLo2x8izL
        val qqSong1 = Regex("""y\.qq\.com.*?/songDetail/([a-zA-Z0-9]+)""")
        qqSong1.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.QQ,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }
        // QQ 移动端 H5: https://i.y.qq.com/v8/playsong.html?songmid=...
        val qqSong2 = Regex("""y\.qq\.com.*?[?&]songmid=([a-zA-Z0-9]+)""")
        qqSong2.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.QQ,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }
        // QQ: https://y.qq.com/n/ryqq/song?id=123
        val qqSong3 = Regex("""y\.qq\.com.*?/song\?id=([a-zA-Z0-9]+)""")
        qqSong3.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.QQ,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }
        // QQ 短链: https://c6.y.qq.com/base/fcgi-bin/u?__=xxxxx
        val qqShort = Regex("""c\d*\.y\.qq\.com.*?[?&]__=([a-zA-Z0-9]+)""")
        qqShort.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.QQ,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }

        // 酷狗: https://www.kugou.com/song/#hash=xxx
        val kugouSong = Regex("""kugou\.com/song.*?hash=([a-zA-Z0-9]+)""")
        kugouSong.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.KUGOU,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }
        // 酷狗移动端短链: https://t.kugou.com/song.html?id=xxx / https://t1.kugou.com/song.html?id=xxx
        val kugouSongT = Regex("""t\d*\.kugou\.com/song.*?id=([a-zA-Z0-9]+)""")
        kugouSongT.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.KUGOU,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }

        // 酷我: http://www.kuwo.cn/play_detail/xxx  或  /song/xxx
        val kuwoSong1 = Regex("""kuwo\.cn/play_detail/(\d+)""")
        kuwoSong1.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.KUWO,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }
        val kuwoSong2 = Regex("""kuwo\.cn/song/(\d+)""")
        kuwoSong2.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.KUWO,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }
        // 酷我移动端 H5: https://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=xxx
        val kuwoSongM = Regex("""kuwo\.cn.*?[?&]musicId=(\d+)""")
        kuwoSongM.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.KUWO,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }

        // 咪咕: https://music.migu.cn/v3/music/song/xxx
        val miguSong = Regex("""migu\.cn.*?/song/([a-zA-Z0-9]+)""")
        miguSong.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.MIGU,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }
        // 咪咕移动端 H5: https://music.migu.cn/v3/music/player/audio?songId=xxx
        val miguSongM = Regex("""migu\.cn.*?[?&]songId=([a-zA-Z0-9]+)""")
        miguSongM.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.MIGU,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }
        // 咪咕短链: https://c.migu.cn/xxxxx （短链跳转，songId 需重定向解析）
        if (Regex("""c\.migu\.cn/([a-zA-Z0-9]+)""").containsMatchIn(url)) {
            val code = Regex("""c\.migu\.cn/([a-zA-Z0-9]+)""").find(url)?.groupValues?.get(1) ?: ""
            return ParsedLink(
                platform = MusicPlatform.MIGU,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = code
            )
        }

        // 汽水音乐短链: https://qishui.douyin.com/s/XXX（单曲与歌单共用，按单曲处理）
        val qishuiSong = Regex("""qishui\.douyin\.com/s/([a-zA-Z0-9]+)""")
        qishuiSong.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.QISHUI,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }
        // 抖音: https://www.douyin.com/music/xxx
        val douyinSong = Regex("""douyin\.com.*?/music/(\d+)""")
        douyinSong.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.QISHUI,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }

        // 5sing: https://5sing.kugou.com/yc/xxx 或 fc/xxx
        val fivesingSong = Regex("""5sing\.kugou\.com/(?:yc|fc)/([a-zA-Z0-9]+)""")
        fivesingSong.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.FIVE_SING,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }

        // Apple Music: https://music.apple.com/.../song/123
        val appleSong = Regex("""music\.apple\.com.*?/song/(\d+)""")
        appleSong.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.APPLE_MUSIC,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }

        // KKBOX: https://www.kkbox.com/.../song/xxx
        val kkboxSong = Regex("""kkbox\.com.*?/song/([a-zA-Z0-9]+)""")
        kkboxSong.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.KKBOX,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }

        // 歌曲宝: https://www.gequbao.net/music/xxx
        val gequbaoSong = Regex("""gequbao\.net/music/(\d+)""")
        gequbaoSong.find(url)?.let {
            return ParsedLink(
                platform = MusicPlatform.GEQUBAO,
                playlistId = "",
                originalUrl = url,
                isSong = true,
                songId = it.groupValues[1]
            )
        }

        return null
    }

    // -----------------------------------------------------------------
    //  歌单解析
    // -----------------------------------------------------------------
    private fun parsePlaylist(url: String): ParsedLink? {
        val trimmedUrl = url

        // 网易云短链: https://163cn.tv/xxxxx （歌单或单曲短链）
        if (trimmedUrl.contains("163cn.tv", ignoreCase = true)) {
            return ParsedLink(MusicPlatform.NETEASE, "", trimmedUrl)
        }

        // 网易云: https://music.163.com/playlist?id=123456
        // 或: https://y.music.163.com/m/playlist?id=123456
        val neteasePattern = Regex("""music\.163\.com.*[?&/]id=(\d+)""")
        neteasePattern.find(trimmedUrl)?.let {
            return ParsedLink(MusicPlatform.NETEASE, it.groupValues[1], trimmedUrl)
        }

        // QQ短链: https://c6.y.qq.com/base/fcgi-bin/u?__=xxxxx
        val qqShort = Regex("""c\d*\.y\.qq\.com.*?[?&]__=([a-zA-Z0-9]+)""")
        qqShort.find(trimmedUrl)?.let {
            return ParsedLink(MusicPlatform.QQ, it.groupValues[1], trimmedUrl)
        }

        // QQ音乐: https://y.qq.com/n/ryqq/playlist/123456
        // 或: https://i.y.qq.com/n2/m/share/details/taoge.html?id=123456
        val qqPattern1 = Regex("""y\.qq\.com.*playlist/(\d+)""")
        val qqPattern2 = Regex("""y\.qq\.com.*[?&]id=(\d+)""")
        qqPattern1.find(trimmedUrl)?.let {
            return ParsedLink(MusicPlatform.QQ, it.groupValues[1], trimmedUrl)
        }
        qqPattern2.find(trimmedUrl)?.let {
            return ParsedLink(MusicPlatform.QQ, it.groupValues[1], trimmedUrl)
        }

        // 酷狗: https://www.kugou.com/songlist/xxx
        // 或: https://t.kugou.com/song.html?id=xxx
        val kugouPattern1 = Regex("""kugou\.com/songlist/([a-zA-Z0-9]+)""")
        val kugouPattern2 = Regex("""kugou\.com.*[?&]id=([a-zA-Z0-9]+)""")
        kugouPattern1.find(trimmedUrl)?.let {
            return ParsedLink(MusicPlatform.KUGOU, it.groupValues[1], trimmedUrl)
        }
        kugouPattern2.find(trimmedUrl)?.let {
            return ParsedLink(MusicPlatform.KUGOU, it.groupValues[1], trimmedUrl)
        }

        // 酷我移动端 H5: https://m.kuwo.cn/newh5/...
        // 酷我: http://www.kuwo.cn/playlist_detail/xxx
        val kuwoPattern = Regex("""kuwo\.cn/playlist_detail/(\d+)""")
        kuwoPattern.find(trimmedUrl)?.let {
            return ParsedLink(MusicPlatform.KUWO, it.groupValues[1], trimmedUrl)
        }

        // 咪咕短链: https://c.migu.cn/xxxxx
        val miguShort = Regex("""c\.migu\.cn/([a-zA-Z0-9]+)""")
        miguShort.find(trimmedUrl)?.let {
            return ParsedLink(MusicPlatform.MIGU, it.groupValues[1], trimmedUrl)
        }

        // 咪咕: https://music.migu.cn/v3/music/playlist/xxx
        val miguPattern = Regex("""migu\.cn.*playlist/(\d+)""")
        miguPattern.find(trimmedUrl)?.let {
            return ParsedLink(MusicPlatform.MIGU, it.groupValues[1], trimmedUrl)
        }

        // 汽水音乐: https://qishui.douyin.com/s/XXXXXX
        // 或: https://www.douyin.com/music/xxx
        val qishuiPattern1 = Regex("""qishui\.douyin\.com/s/([a-zA-Z0-9]+)""")
        val qishuiPattern2 = Regex("""douyin\.com.*music/(\d+)""")
        qishuiPattern1.find(trimmedUrl)?.let {
            return ParsedLink(MusicPlatform.QISHUI, it.groupValues[1], trimmedUrl)
        }
        qishuiPattern2.find(trimmedUrl)?.let {
            return ParsedLink(MusicPlatform.QISHUI, it.groupValues[1], trimmedUrl)
        }

        // 5sing: https://5sing.kugou.com/...
        val fivesingPattern = Regex("""5sing\.kugou\.com.*/([a-zA-Z0-9]+)""")
        fivesingPattern.find(trimmedUrl)?.let {
            return ParsedLink(MusicPlatform.FIVE_SING, it.groupValues[1], trimmedUrl)
        }

        // Apple Music: https://music.apple.com/.../playlist/...
        val applePattern = Regex("""music\.apple\.com.*playlist[/.]([a-zA-Z0-9.-]+)""")
        applePattern.find(trimmedUrl)?.let {
            return ParsedLink(MusicPlatform.APPLE_MUSIC, it.groupValues[1], trimmedUrl)
        }

        // KKBOX: https://www.kkbox.com/.../playlist/...
        val kkboxPattern = Regex("""kkbox\.com.*/playlist/([a-zA-Z0-9]+)""")
        kkboxPattern.find(trimmedUrl)?.let {
            return ParsedLink(MusicPlatform.KKBOX, it.groupValues[1], trimmedUrl)
        }

        // 兜底：通过 host 关键词检测平台
        val platform = MusicPlatform.detect(trimmedUrl)
        if (platform != MusicPlatform.UNKNOWN) {
            // 尝试提取任何数字ID
            val fallbackId = Regex("""(\d{5,})""").find(trimmedUrl)?.groupValues?.get(1)
            if (fallbackId != null) {
                return ParsedLink(platform, fallbackId, trimmedUrl)
            }
        }

        return null
    }
}
