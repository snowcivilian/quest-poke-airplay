package io.github.snowcivilian.questairplay.audio

import kotlin.math.pow

/** Pure volume math for R4. Gains are linear amplitude multipliers, 1 = unity. */
object VolumeMath {
    /** Slider span in dB: 1% is -[RANGE_DB] dB, 100% is unity. Perceptually even steps. */
    const val RANGE_DB = 40f

    /** In-app volume (0..100) and mute to a linear gain. */
    fun appGain(percent: Int, muted: Boolean): Float {
        if (muted || percent <= 0) return 0f
        val p = percent.coerceAtMost(100) / 100f
        return 10f.pow(-RANGE_DB * (1f - p) / 20f)
    }

    /**
     * Gain sent to the audio output. [senderGain] is what upstream derives from the iPhone's volume;
     * with [ignoreSender] on it has no effect, so only the in-app volume counts.
     */
    fun outputGain(appGain: Float, senderGain: Float, ignoreSender: Boolean): Float =
        appGain * if (ignoreSender) 1f else senderGain
}
