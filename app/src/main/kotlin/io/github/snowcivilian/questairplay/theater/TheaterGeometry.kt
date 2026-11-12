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

/** S1: where the two DS screens go. Centers in meters (LOCAL_FLOOR, +Z forward), tilt in degrees. */
data class DsLayout(
    val topCenterY: Float,
    val bottom: ScreenSpec,
    val bottomCenterY: Float,
    val bottomZ: Float,
    /** Pitch of the bottom screen; positive faces up toward the viewer, like a handheld's lower screen. */
    val bottomTiltDeg: Float,
)

object TheaterGeometry {
    const val DS_GAP_M = 0.08f
    const val DS_BOTTOM_TILT_DEG = 25f
    /** Bottom screen sits this fraction of the distance closer than the top screen. */
    const val DS_BOTTOM_CLOSER = 0.15f
    private const val MIN_FLOOR_CLEARANCE_M = 0.1f

    /**
     * Stack the two DS screens around the configured elevation: [top] is the main screen spec (4:3),
     * the bottom screen is [bottomScalePct] of its height. If the pair would reach the floor it is
     * raised so the bottom screen clears it.
     */
    fun dsLayout(top: ScreenSpec, bottomScalePct: Int): DsLayout {
        val scale = bottomScalePct.coerceIn(10, 100) / 100f
        val bh = top.heightM * scale
        val bottom = ScreenSpec(top.widthM * scale, bh, top.distanceM * (1f - DS_BOTTOM_CLOSER), top.elevationM, null, top.pixelW, top.pixelH)
        val total = top.heightM + DS_GAP_M + bh
        var topY = top.elevationM + total / 2f - top.heightM / 2f
        var bottomY = topY - top.heightM / 2f - DS_GAP_M - bh / 2f
        val lowEdge = bottomY - bh / 2f
        if (lowEdge < MIN_FLOOR_CLEARANCE_M) {
            val lift = MIN_FLOOR_CLEARANCE_M - lowEdge
            topY += lift; bottomY += lift
        }
        return DsLayout(topY, bottom, bottomY, bottom.distanceM, DS_BOTTOM_TILT_DEG)
    }

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
