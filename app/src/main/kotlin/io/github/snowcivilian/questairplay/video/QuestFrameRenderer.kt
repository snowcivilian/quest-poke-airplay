package io.github.snowcivilian.questairplay.video

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Draws the decoder's external texture onto the current EGL surface: crops to the active preset,
 * keeps its aspect ratio (black around it if the surface differs), optional scanlines.
 * Runs on upstream's VideoPipeline GL thread, never on the decoder thread.
 */
class QuestFrameRenderer {
    private var program = 0
    private var aPos = 0
    private var aTex = 0
    private var uTexMatrix = 0
    private var uCrop = 0
    private var uScan = 0
    private var uLines = 0
    private var fbo = 0
    private var fboTex = 0
    private val readBuf = ByteBuffer.allocateDirect(DW * DH * 4).order(ByteOrder.nativeOrder())
    private val readBytes = ByteArray(DW * DH * 4)
    private var lastDetectNs = 0L
    private var lastFrameW = 0
    private var lastFrameH = 0

    fun init() {
        program = _program(VERT, FRAG)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aTex = GLES20.glGetAttribLocation(program, "aTex")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uCrop = GLES20.glGetUniformLocation(program, "uCrop")
        uScan = GLES20.glGetUniformLocation(program, "uScan")
        uLines = GLES20.glGetUniformLocation(program, "uLines")
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); fboTex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, DW, DH, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glGenFramebuffers(1, ids, 0); fbo = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex, 0)
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.w(TAG, "detection framebuffer incomplete; auto-detect disabled")
            GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0); fbo = 0
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    /**
     * Auto-detection sample (R6): draw the whole uncropped frame into a small offscreen buffer and
     * read it back. ~[DW]x[DH] RGBA every [DETECT_INTERVAL_NS], on the GL thread.
     */
    private fun _detect(oesTex: Int, texMatrix: FloatArray, frameW: Int, frameH: Int) {
        if (fbo == 0 || frameW <= 0 || frameH <= 0) return
        val now = System.nanoTime()
        val resized = frameW != lastFrameW || frameH != lastFrameH
        if (!resized && now - lastDetectNs < DETECT_INTERVAL_NS) return
        lastDetectNs = now; lastFrameW = frameW; lastFrameH = frameH
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, DW, DH)
        _quad(oesTex, texMatrix, NormRect.FULL, 0f, 0)
        readBuf.position(0)
        GLES20.glReadPixels(0, 0, DW, DH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, readBuf)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        readBuf.position(0)
        readBuf.get(readBytes)
        VideoController.onDetection(BarDetector.detect(readBytes, DW, DH, bottomUp = true), frameW, frameH, resized)
    }

    /** Draw one frame into the bound window surface of size winW x winH. Caller swaps. */
    fun draw(oesTex: Int, texMatrix: FloatArray, winW: Int, winH: Int, frameW: Int, frameH: Int) {
        val cfg = VideoController.renderConfig(frameW, frameH)
        if (cfg.detect) _detect(oesTex, texMatrix, frameW, frameH)
        GameFrameTap.shared.maybeCapture(this, oesTex, texMatrix) // S4
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, winW, winH)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val vp = Geometry.viewport(winW, winH, cfg.outputAspect)
        GLES20.glViewport(vp.x, vp.y, vp.w, vp.h)
        _quad(oesTex, texMatrix, cfg.crop, cfg.scanlineIntensity, cfg.scanlineCount)
    }

    /** Draws the cropped texture over the current viewport. */
    internal fun _quad(oesTex: Int, texMatrix: FloatArray, crop: NormRect, scan: Float, lines: Int) {
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        // texture space before the SurfaceTexture matrix has its origin at the image's bottom-left
        GLES20.glUniform4f(uCrop, crop.left, 1f - crop.bottom, crop.width, crop.height)
        GLES20.glUniform1f(uScan, if (lines > 0) scan else 0f)
        GLES20.glUniform1f(uLines, lines.toFloat())
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, POS)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, TEX)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
    }

    fun release() {
        if (program != 0) GLES20.glDeleteProgram(program)
        GameFrameTap.shared.release()
        if (fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (fboTex != 0) GLES20.glDeleteTextures(1, intArrayOf(fboTex), 0)
        fbo = 0; fboTex = 0
        program = 0
    }

    private fun _program(vs: String, fs: String): Int {
        fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src)
            GLES20.glCompileShader(it)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(it, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) Log.e(TAG, "shader compile failed: ${GLES20.glGetShaderInfoLog(it)}")
        }
        val v = shader(GLES20.GL_VERTEX_SHADER, vs)
        val f = shader(GLES20.GL_FRAGMENT_SHADER, fs)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, v)
            GLES20.glAttachShader(it, f)
            GLES20.glLinkProgram(it)
            GLES20.glDeleteShader(v)
            GLES20.glDeleteShader(f)
        }
    }

    companion object {
        private const val TAG = "QuestFrameRenderer"
        /** Detection buffer: 16:9 and fine enough to tell GB/GBC (10:9) from SNES (8:7). */
        const val DW = 320
        const val DH = 180
        private const val DETECT_INTERVAL_NS = 400_000_000L

        private val POS = _fb(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        private val TEX = _fb(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))

        private fun _fb(a: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply { put(a); position(0) }

        private const val VERT =
            "attribute vec2 aPos;\n" +
            "attribute vec2 aTex;\n" +
            "uniform mat4 uTexMatrix;\n" +
            "uniform vec4 uCrop;\n" +         // x0, y0, w, h in pre-matrix texture space
            "varying vec2 vTex;\n" +
            "varying vec2 vSrc;\n" +          // 0..1 across the cropped game, y up
            "void main() {\n" +
            "  gl_Position = vec4(aPos, 0.0, 1.0);\n" +
            "  vSrc = aTex;\n" +
            "  vTex = (uTexMatrix * vec4(uCrop.xy + aTex * uCrop.zw, 0.0, 1.0)).xy;\n" +
            "}\n"

        // scanlines: darken toward the boundary between source lines; uScan = 0 disables
        private const val FRAG =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTex;\n" +
            "varying vec2 vSrc;\n" +
            "uniform samplerExternalOES sTex;\n" +
            "uniform float uScan;\n" +
            "uniform float uLines;\n" +
            "void main() {\n" +
            "  vec4 c = texture2D(sTex, vTex);\n" +
            "  if (uScan > 0.0) {\n" +
            "    float y = (1.0 - vSrc.y) * uLines;\n" +
            "    float s = 0.5 - 0.5 * cos(6.2831853 * y);\n" +
            "    c.rgb *= mix(1.0, s, uScan) * (1.0 + 0.35 * uScan);\n" +
            "  }\n" +
            "  gl_FragColor = c;\n" +
            "}\n"
    }
}
