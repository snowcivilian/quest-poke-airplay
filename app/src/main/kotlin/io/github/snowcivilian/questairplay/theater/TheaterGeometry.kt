package io.github.snowcivilian.questairplay.theater

import kotlin.math.roundToInt

/** Virtual screen for theater mode (R10). Meters, LOCAL_FLOOR space, +Z forward. */
data class ScreenSpec(
    val widthM: Float,
    val heightM: Float,
    val distanceM: Float,
    val elevationM: Float,
    /** Cylinder radius in meters, or null for a flat screen. */
    val radiusM: Float?,
    val pixelW: Int,
    val pixelH: Int,
)

object TheaterGeometry {
    /** Long side of the screen's pixel buffer. Meta warns panels near 2064x2208 can crop. */
    const val LONG_SIDE_PX = 1920
    private const val MAX_RADIUS_M = 60f

    /**
     * @param aspect shown aspect (w/h). @param heightCm screen height. @param curve 0 = flat,
     * 100 = a cylinder centered on the viewer (radius = distance); in between, gentler curves.
     */
    fun screen(aspect: Float, heightCm: Int, distanceCm: Int, elevationCm: Int, curve: Int): ScreenSpec {
        val a = if (aspect.isFinite() && aspect > 0f) aspect else 16f / 9f
        val h = heightCm / 100f
        val d = distanceCm / 100f
        val radius = if (curve <= 0) null else (d * 100f / curve.coerceAtMost(100)).coerceAtMost(MAX_RADIUS_M)
        val (pw, ph) = if (a >= 1f) LONG_SIDE_PX to _even(LONG_SIDE_PX / a) else _even(LONG_SIDE_PX * a) to LONG_SIDE_PX
        return ScreenSpec(h * a, h, d, elevationCm / 100f, radius, pw, ph)
    }

    private fun _even(v: Float) = (v.roundToInt() / 2 * 2).coerceAtLeast(2)
}
