package io.github.snowcivilian.questairplay.video

import org.json.JSONArray
import org.json.JSONObject

/**
 * One system preset (R5), loaded from assets/presets.json.
 * A preset with no [sourceAspect] is "full frame": no crop, panel follows the stream.
 */
data class Preset(
    val id: String,
    val label: String,
    /** Aspect of the game box inside the incoming frame; null = whole frame. */
    val sourceAspect: Float?,
    /** Aspect of the output panel; null = same as what's shown. */
    val displayAspect: Float?,
    /** Explicit crop override. */
    val crop: NormRect?,
    val nativeW: Int,
    val nativeH: Int,
    val detect: Boolean,
) {
    val isFull get() = sourceAspect == null && crop == null

    /** Crop for a frame of the given size when nothing better (a detection) is known. */
    fun cropFor(frameW: Int, frameH: Int): NormRect =
        crop ?: sourceAspect?.let { Geometry.fit(frameW, frameH, it) } ?: NormRect.FULL

    /** Output aspect given the crop actually used. */
    fun outputAspect(crop: NormRect, frameW: Int, frameH: Int): Float =
        displayAspect ?: crop.pixelAspect(frameW, frameH).takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)

    companion object {
        const val FULL_ID = "full"

        fun parseAll(json: String): List<Preset> {
            val arr = JSONObject(json).getJSONArray("presets")
            return (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
        }

        private fun JSONObject.ratio(key: String): Float? =
            optJSONArray(key)?.let { it.getDouble(0).toFloat() / it.getDouble(1).toFloat() }

        private fun parse(o: JSONObject): Preset {
            val native = o.optJSONArray("native")
            val nw = native?.getInt(0) ?: 0
            val nh = native?.getInt(1) ?: 0
            val source = o.ratio("sourceAspect") ?: if (nw > 0 && nh > 0) nw.toFloat() / nh else null
            val crop = o.optJSONArray("crop")?.let { c: JSONArray ->
                NormRect(c.getDouble(0).toFloat(), c.getDouble(1).toFloat(), c.getDouble(2).toFloat(), c.getDouble(3).toFloat())
            }
            return Preset(
                id = o.getString("id"),
                label = o.getString("label"),
                sourceAspect = source,
                displayAspect = o.ratio("displayAspect"),
                crop = crop,
                nativeW = nw,
                nativeH = nh,
                detect = o.optBoolean("detect", true),
            )
        }
    }
}
