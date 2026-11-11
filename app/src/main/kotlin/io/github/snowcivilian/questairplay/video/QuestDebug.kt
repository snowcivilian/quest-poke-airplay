package io.github.snowcivilian.questairplay.video

/** Extra lines for upstream's debug overlay (R9). */
object QuestDebug {
    fun lines(): List<String> = buildList {
        if (LatencyStats.avgMs > 0f) add("Decode→display %.1f ms (max %.1f)".format(LatencyStats.avgMs, LatencyStats.maxMs))
        val p = VideoController.active.value
        add("Preset ${p.label} (${if (VideoController.auto.value) "auto" else "manual"})")
        val a = VideoController.lastDetectedAspect
        if (a > 0f) add("Detected %.3f → %s".format(a, VideoController.lastMatch ?: "no match"))
    }
}
