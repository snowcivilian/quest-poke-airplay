package io.github.snowcivilian.questairplay.video

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Who receives game frames (S4). Implemented by PokemonEngine; checked on every drawn frame. */
interface GameFrameSink {
    /** Cheap: whether a frame is wanted now. */
    fun wantsFrame(nowNs: Long): Boolean
    /** RGBA, top row first; ownership passes to the sink. Called on the GL thread; must return fast. */
    fun onFrame(rgba: ByteArray, width: Int, height: Int)
}

/**
 * Reads back the cropped game at about 3x its native resolution (720x480 for GBA) for the battle
 * helper and calibration captures. Runs on the GL thread only when a sink asks for a frame.
 */
class GameFrameTap {
    @Volatile var sink: GameFrameSink? = null
    private var fbo = 0
    private var tex = 0
    private var w = 0
    private var h = 0
    private var buf: ByteBuffer? = null

    fun maybeCapture(renderer: QuestFrameRenderer, oesTex: Int, texMatrix: FloatArray) {
        val s = sink ?: return
        if (!s.wantsFrame(System.nanoTime())) return
        val (cw, ch) = captureSize()
        _ensure(cw, ch)
        if (fbo == 0) return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, cw, ch)
        renderer._quad(oesTex, texMatrix, VideoController.gameCrop, 0f, 0)
        val b = buf!!
        b.position(0)
        GLES20.glReadPixels(0, 0, cw, ch, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, b)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        // GL rows are bottom-up; hand over top-down
        val out = ByteArray(cw * ch * 4)
        val row = cw * 4
        for (y in 0 until ch) { b.position((ch - 1 - y) * row); b.get(out, y * row, row) }
        s.onFrame(out, cw, ch)
    }

    /** About 3x native (GBA 720x480, DS stacked 512x768), long side at most 1024; full frame 640x360. */
    private fun captureSize(): Pair<Int, Int> {
        val p = VideoController.active.value
        if (p.nativeW > 0 && p.nativeH > 0) {
            val scale = (720 / p.nativeW).coerceIn(1, 4)
            var cw = p.nativeW * scale; var ch = p.nativeH * scale
            val long = maxOf(cw, ch)
            if (long > 1024) { cw = cw * 1024 / long; ch = ch * 1024 / long }
            return cw to ch
        }
        return 640 to 360
    }

    private fun _ensure(cw: Int, ch: Int) {
        if (cw == w && ch == h && fbo != 0) return
        release()
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); tex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, cw, ch, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glGenFramebuffers(1, ids, 0); fbo = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex, 0)
        val ok = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (!ok) { release(); return }
        w = cw; h = ch
        buf = ByteBuffer.allocateDirect(cw * ch * 4).order(ByteOrder.nativeOrder())
    }

    fun release() {
        if (fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
        fbo = 0; tex = 0; w = 0; h = 0
    }

    companion object {
        /** Process-wide tap; the engine sets [sink]. */
        val shared = GameFrameTap()
    }
}
