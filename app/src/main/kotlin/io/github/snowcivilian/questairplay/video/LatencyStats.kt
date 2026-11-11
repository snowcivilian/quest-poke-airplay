package io.github.snowcivilian.questairplay.video

/**
 * Decode-to-display time (R9): from handing a frame to the decoder to swapping it onto the screen.
 * Hooks: VideoRenderer (queued, released) and VideoPipeline (displayed, after eglSwapBuffers).
 */
object LatencyStats {
    private const val RING = 128
    private val ptsUs = LongArray(RING) { Long.MIN_VALUE }
    private val queuedNs = LongArray(RING)
    private var next = 0
    @Volatile private var releasedQueueNs = 0L

    private var windowStartNs = 0L
    private var sumNs = 0L
    private var count = 0
    private var maxNs = 0L

    /** Average and worst decode-to-display time over the last second, in ms. */
    @Volatile var avgMs = 0f; private set
    @Volatile var maxMs = 0f; private set

    @Synchronized fun onQueued(pts: Long) {
        ptsUs[next] = pts
        queuedNs[next] = System.nanoTime()
        next = (next + 1) % RING
    }

    @Synchronized fun onReleased(pts: Long) {
        for (i in 0 until RING) if (ptsUs[i] == pts) {
            releasedQueueNs = queuedNs[i]
            ptsUs[i] = Long.MIN_VALUE
            return
        }
    }

    fun onDisplayed() {
        val q = releasedQueueNs
        if (q == 0L) return
        releasedQueueNs = 0L
        val now = System.nanoTime()
        _sample(now, now - q)
    }

    @Synchronized private fun _sample(now: Long, ns: Long) {
        if (windowStartNs == 0L) windowStartNs = now
        sumNs += ns; count++; if (ns > maxNs) maxNs = ns
        if (now - windowStartNs >= 1_000_000_000L) {
            avgMs = sumNs / count / 1e6f
            maxMs = maxNs / 1e6f
            windowStartNs = now; sumNs = 0; count = 0; maxNs = 0
        }
    }

    @Synchronized fun reset() {
        ptsUs.fill(Long.MIN_VALUE); releasedQueueNs = 0L
        windowStartNs = 0L; sumNs = 0; count = 0; maxNs = 0; avgMs = 0f; maxMs = 0f
    }
}
