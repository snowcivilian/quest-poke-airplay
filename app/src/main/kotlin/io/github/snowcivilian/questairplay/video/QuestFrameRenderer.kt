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

    fun init() {
        program = _program(VERT, FRAG)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aTex = GLES20.glGetAttribLocation(program, "aTex")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uCrop = GLES20.glGetUniformLocation(program, "uCrop")
        uScan = GLES20.glGetUniformLocation(program, "uScan")
        uLines = GLES20.glGetUniformLocation(program, "uLines")
    }

    /** Draw one frame into the bound window surface of size winW x winH. Caller swaps. */
    fun draw(oesTex: Int, texMatrix: FloatArray, winW: Int, winH: Int, frameW: Int, frameH: Int) {
        val cfg = VideoController.renderConfig(frameW, frameH)
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
