package io.github.snowcivilian.questairplay.video

import kotlin.math.abs
import kotlin.math.ln

/** Rectangle in fractions of a frame, origin top-left. */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    /** Aspect ratio in pixels, given the frame's pixel size. */
    fun pixelAspect(frameW: Int, frameH: Int): Float = (width * frameW) / (height * frameH)

    companion object {
        val FULL = NormRect(0f, 0f, 1f, 1f)
    }
}

/** Viewport rectangle in pixels, origin bottom-left (GL convention). */
data class Viewport(val x: Int, val y: Int, val w: Int, val h: Int)

object Geometry {
    /** Largest centered rectangle of [aspect] (w/h) inside a frame of frameW x frameH, as fractions. */
    fun fit(frameW: Int, frameH: Int, aspect: Float): NormRect {
        if (frameW <= 0 || frameH <= 0 || aspect <= 0f) return NormRect.FULL
        val frameAspect = frameW.toFloat() / frameH
        return if (aspect < frameAspect) {
            val w = aspect / frameAspect
            NormRect((1f - w) / 2f, 0f, (1f + w) / 2f, 1f)
        } else {
            val h = frameAspect / aspect
            NormRect(0f, (1f - h) / 2f, 1f, (1f + h) / 2f)
        }
    }

    /** Centered viewport of [aspect] inside a window, in whole pixels. */
    fun viewport(winW: Int, winH: Int, aspect: Float): Viewport {
        val r = fit(winW, winH, aspect)
        val x = Math.round(r.left * winW)
        val w = Math.round(r.right * winW) - x
        val yTop = Math.round(r.top * winH)
        val h = Math.round(r.bottom * winH) - yTop
        return Viewport(x, winH - yTop - h, w, h)
    }

    /** Distance between two aspect ratios on a log scale, so 4:3 vs 3:4 errors are symmetric. */
    fun aspectDistance(a: Float, b: Float): Float = abs(ln(a / b))
}
