package io.github.snowcivilian.questairplay.video

/** One black-bar measurement of a downscaled frame. */
data class Detection(
    /** Bounding box of non-black content, or null if the whole frame is black. */
    val content: NormRect?,
    /** Mean luma of the whole frame, 0..1. */
    val meanLuma: Float,
)

/** Black-bar detection (R6). Pure; runs on small RGBA readbacks, never on the decoder thread. */
object BarDetector {
    /** Luma (0..255) above which a pixel counts as picture rather than bar. */
    const val THRESHOLD = 28

    /**
     * @param rgba w*h*4 bytes. @param bottomUp true for glReadPixels order (first row = bottom).
     */
    fun detect(rgba: ByteArray, w: Int, h: Int, bottomUp: Boolean, threshold: Int = THRESHOLD): Detection {
        require(rgba.size >= w * h * 4)
        val rowHits = IntArray(h)
        val colHits = IntArray(w)
        var lumaSum = 0L
        for (y in 0 until h) {
            val row = if (bottomUp) h - 1 - y else y
            var i = row * w * 4
            for (x in 0 until w) {
                val r = rgba[i].toInt() and 0xFF
                val g = rgba[i + 1].toInt() and 0xFF
                val b = rgba[i + 2].toInt() and 0xFF
                val l = (r * 77 + g * 150 + b * 29) shr 8
                lumaSum += l
                if (l > threshold) { rowHits[y]++; colHits[x]++ }
                i += 4
            }
        }
        val mean = lumaSum.toFloat() / (w * h) / 255f
        // a line is content if a few pixels on it are lit; ignores single-pixel compression noise
        val minRow = maxOf(2, w / 100)
        val minCol = maxOf(2, h / 100)
        val top = rowHits.indexOfFirst { it >= minRow }
        if (top < 0) return Detection(null, mean)
        val bottom = rowHits.indexOfLast { it >= minRow }
        val left = colHits.indexOfFirst { it >= minCol }
        val right = colHits.indexOfLast { it >= minCol }
        if (left < 0) return Detection(null, mean)
        return Detection(
            NormRect(left.toFloat() / w, top.toFloat() / h, (right + 1).toFloat() / w, (bottom + 1).toFloat() / h),
            mean,
        )
    }
}

/** Picks the preset whose game box matches a detection (R6). Pure. */
object PresetMatcher {
    /** Max log-aspect error to accept a match. Half the gap between GB/GBC and SNES (ln 1.0286). */
    const val TOLERANCE = 0.013f
    /** Below this mean luma the frame is treated as a fade or black screen and ignored. */
    const val DARK_LUMA = 0.03f
    private const val EDGE = 0.02f

    fun match(d: Detection, frameW: Int, frameH: Int, presets: List<Preset>): String? {
        val r = d.content ?: return null
        if (d.meanLuma < DARK_LUMA || frameW <= 0 || frameH <= 0) return null
        val fullW = r.left <= EDGE && r.right >= 1f - EDGE
        val fullH = r.top <= EDGE && r.bottom >= 1f - EDGE
        // Delta fits the game to the frame, so a real game box spans the full width or height.
        // Anything else (dark scene, menu, fade) is not trusted.
        if (fullW && fullH) return presets.firstOrNull { it.isFull && it.detect }?.id
        if (!fullW && !fullH) return null
        val aspect = r.pixelAspect(frameW, frameH)
        val best = presets.filter { it.detect && it.sourceAspect != null }
            .minByOrNull { Geometry.aspectDistance(it.sourceAspect!!, aspect) } ?: return null
        return best.id.takeIf { Geometry.aspectDistance(best.sourceAspect!!, aspect) <= TOLERANCE }
    }
}

/**
 * Hysteresis for auto-detection (R6): a new preset must be seen [required] times in a row before it
 * becomes current. Unknown samples (null) neither confirm nor reset, so fades don't cause switches.
 */
class AutoDetector(private val required: Int = 3) {
    var current: String? = null; private set
    private var candidate: String? = null
    private var streak = 0

    /** Returns the new preset id when the current one changes, else null. */
    fun feed(id: String?): String? {
        if (id == null) return null
        if (id == current) { candidate = null; streak = 0; return null }
        if (id == candidate) streak++ else { candidate = id; streak = 1 }
        if (streak < required) return null
        current = id; candidate = null; streak = 0
        return id
    }

    fun reset(current: String?) {
        this.current = current; candidate = null; streak = 0
    }
}
