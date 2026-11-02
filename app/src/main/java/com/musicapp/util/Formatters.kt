package com.musicapp.util

/** Duration (ms or seconds) → "mm:ss" string. If value > 43200s assume ms. */
fun formatDuration(valueMs: Long): String {
    if (valueMs <= 0) return ""
    // Rough heuristic: if value seems seconds (less than 43200s=12h *1000) treat as secs, else ms
    val totalSeconds = if (valueMs < 43_200_000L && valueMs > 43_200L) valueMs / 1000
    else if (valueMs < 43_200L) valueMs
    else valueMs / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d", m, s)
}
