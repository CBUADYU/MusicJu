package com.musicapp.util

/**
 * LRC 歌词解析器
 */
object LrcParser {

    data class LrcLine(
        val timeMs: Long,      // 时间戳（毫秒）
        val text: String,      // 歌词文本
        val rawTime: String    // 原始时间标签 [mm:ss.xx]
    )

    data class LrcResult(
        val lines: List<LrcLine>,
        val title: String = "",
        val artist: String = "",
        val album: String = ""
    )

    /**
     * 解析 LRC 歌词文本
     * 格式: [mm:ss.xx]歌词文本
     */
    fun parse(lrcText: String?): LrcResult {
        if (lrcText.isNullOrBlank()) {
            return LrcResult(emptyList())
        }

        val lines = mutableListOf<LrcLine>()
        var title = ""
        var artist = ""
        var album = ""

        val timePattern = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
        val metaPattern = Regex("""\[ti:(.*?)\]|\[ar:(.*?)\]|\[al:(.*?)\]""")

        for (rawLine in lrcText.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            // 解析元数据
            val metaMatch = metaPattern.find(trimmed)
            if (metaMatch != null) {
                when {
                    metaMatch.groupValues[1].isNotEmpty() -> title = metaMatch.groupValues[1].trim()
                    metaMatch.groupValues[2].isNotEmpty() -> artist = metaMatch.groupValues[2].trim()
                    metaMatch.groupValues[3].isNotEmpty() -> album = metaMatch.groupValues[3].trim()
                }
                continue
            }

            // 解析时间标签
            val timeMatches = timePattern.findAll(trimmed).toList()
            if (timeMatches.isEmpty()) continue

            // 提取歌词文本（去掉所有时间标签）
            val text = timePattern.replace(trimmed, "").trim()

            for (match in timeMatches) {
                val minutes = match.groupValues[1].toInt()
                val seconds = match.groupValues[2].toInt()
                val millis = match.groupValues[3].takeIf { it.isNotEmpty() }?.let {
                    // 两位小数 = 10ms, 三位 = 1ms
                    val ms = it.toInt()
                    if (it.length == 2) ms * 10 else ms
                } ?: 0

                val timeMs = minutes * 60_000L + seconds * 1000L + millis
                lines.add(LrcLine(timeMs, text, match.value))
            }
        }

        // 按时间排序
        lines.sortBy { it.timeMs }

        return LrcResult(lines, title, artist, album)
    }

    /**
     * 根据当前播放时间（毫秒）找到对应的歌词行索引
     */
    fun findCurrentLineIndex(lines: List<LrcLine>, currentTimeMs: Long): Int {
        if (lines.isEmpty()) return -1

        var index = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= currentTimeMs) {
                index = i
            } else {
                break
            }
        }
        return index
    }
}