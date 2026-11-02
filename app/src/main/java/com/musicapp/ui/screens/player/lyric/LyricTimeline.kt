package com.musicapp.ui.screens.player.lyric

import com.musicapp.util.LrcParser

/**
 * Folia latticeLyricTimeline + buildMonetVisibleLineEntries — 精确移植。
 *
 *  - 预计算所有行的 timeMs 边界，排序
 *  - 二分查找当前 time 落在哪个区间，用 stamp（区间索引*2+是否正好命中）判断是否需要重建 entries
 *  - 只渲染 anchor 前后各 1 行（before:1, after:1）
 *  - status: active / waiting / passed
 */
internal class LyricTimeline(
    private val lines: List<LrcParser.LrcLine>,
    private val before: Int = 1,
    private val after: Int = 1
) {
    /** 边界点 = 所有行的 timeMs，去重排序 */
    private val boundaries: LongArray = lines.map { it.timeMs }.distinct().sorted().toLongArray()

    private var previousStamp: Int = -1
    private var cachedEntries: List<VisibleLineEntry> = emptyList()

    /** 二分查找 + stamp，只在跨边界时重建 entries */
    fun getEntries(timeMs: Long): List<VisibleLineEntry> {
        var low = 0
        var high = boundaries.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (boundaries[mid] <= timeMs) low = mid + 1 else high = mid
        }
        // stamp = 区间索引 * 2 + (是否正好命中边界)
        val hitBoundary = low > 0 && low - 1 < boundaries.size && boundaries[low - 1] == timeMs
        val stamp = low * 2 + if (hitBoundary) 1 else 0

        if (stamp != previousStamp) {
            previousStamp = stamp
            val activeIndex = findLatestActiveLineIndex(timeMs)
            cachedEntries = buildVisibleLines(activeIndex)
        }
        return cachedEntries
    }

    /** 找最后一个 timeMs <= timeMs 的行 */
    private fun findLatestActiveLineIndex(timeMs: Long): Int {
        var idx = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= timeMs) idx = i else break
        }
        return idx
    }

    /** 构建 anchor 前后各 before/after 行的可见列表 */
    private fun buildVisibleLines(activeIndex: Int): List<VisibleLineEntry> {
        if (lines.isEmpty() || activeIndex < 0) return emptyList()
        val start = (activeIndex - before).coerceAtLeast(0)
        val end = (activeIndex + after).coerceAtMost(lines.size - 1)
        val result = ArrayList<VisibleLineEntry>(end - start + 1)
        for (i in start..end) {
            val offset = i - activeIndex
            val status = when {
                i == activeIndex -> LineStatus.ACTIVE
                i < activeIndex -> LineStatus.PASSED
                else -> LineStatus.WAITING
            }
            result.add(
                VisibleLineEntry(
                    key = "$i-${lines[i].timeMs}-${lines[i].text}",
                    line = lines[i],
                    index = i,
                    offset = offset,
                    status = status
                )
            )
        }
        return result
    }

    enum class LineStatus { ACTIVE, WAITING, PASSED }

    data class VisibleLineEntry(
        val key: String,
        val line: LrcParser.LrcLine,
        val index: Int,
        val offset: Int,
        val status: LineStatus
    )
}
