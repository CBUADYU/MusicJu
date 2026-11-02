package com.musicapp.ui.screens.player.lyric

/**
 * Folia latticeLyricTimeline.stepLatticeSpring — 精确移植。
 *
 * 固定 120Hz 子步进，掉帧时 spring 不会振荡飞出去。
 * 返回 { value, velocity, settled }。
 */
internal data class SpringResult(
    val value: Float,
    val velocity: Float,
    val settled: Boolean
)

internal data class SpringConfig(
    val stiffness: Float,
    val damping: Float,
    val mass: Float
)

/** Folia MONET_SCROLL_SPRING — 歌词纵向位置弹簧 */
internal val MONET_SCROLL_SPRING = SpringConfig(stiffness = 142f, damping = 28f, mass = 0.82f)

/** Folia MONET_SCALE_SPRING — 歌词缩放弹簧 */
internal val MONET_SCALE_SPRING = SpringConfig(stiffness = 150f, damping = 30f, mass = 0.78f)

internal object LyricSpring {
    private const val SUBSTEP_HZ = 120

    fun step(
        value: Float,
        velocity: Float,
        target: Float,
        deltaSec: Float,
        spring: SpringConfig
    ): SpringResult {
        val steps = maxOf(1, kotlin.math.ceil(deltaSec * SUBSTEP_HZ).toInt())
        val dt = deltaSec / steps
        var v = velocity
        var x = value
        repeat(steps) {
            v += ((target - x) * spring.stiffness - v * spring.damping) / spring.mass * dt
            x += v * dt
        }
        val settled = kotlin.math.abs(x - target) < 0.01f && kotlin.math.abs(v) < 0.01f
        return SpringResult(
            value = if (settled) target else x,
            velocity = if (settled) 0f else v,
            settled = settled
        )
    }
}
