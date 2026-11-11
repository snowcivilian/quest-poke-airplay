package io.github.snowcivilian.questairplay.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class DetectionTest {
    private val presets = Preset.parseAll(
        java.io.File("src/main/assets/presets.json").readText()
    )

    /** Synthetic frame: [box] of mid-grey picture (with some dark pixels inside) on black, plus noise. */
    private fun frame(w: Int, h: Int, box: NormRect?, luma: Int = 120, noise: Int = 10, bottomUp: Boolean = false): ByteArray {
        val rnd = Random(42)
        val out = ByteArray(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val fy = (y + 0.5f) / h; val fx = (x + 0.5f) / w
            val inBox = box != null && fx >= box.left && fx < box.right && fy >= box.top && fy < box.bottom
            var l = if (inBox) (if ((x + y) % 7 == 0) 5 else luma) else rnd.nextInt(noise + 1)
            l = l.coerceIn(0, 255)
            val row = if (bottomUp) h - 1 - y else y
            val i = (row * w + x) * 4
            out[i] = l.toByte(); out[i + 1] = l.toByte(); out[i + 2] = l.toByte(); out[i + 3] = -1
        }
        return out
    }

    private fun detectBox(aspect: Float, bottomUp: Boolean = false): Detection {
        val box = Geometry.fit(1920, 1080, aspect)
        return BarDetector.detect(frame(320, 180, box, bottomUp = bottomUp), 320, 180, bottomUp)
    }

    private fun matchAspect(aspect: Float) = PresetMatcher.match(detectBox(aspect), 1920, 1080, presets)

    @Test fun findsPillarboxedGba() {
        val r = detectBox(1.5f).content!!
        assertEquals(0.078f, r.left, 0.01f); assertEquals(0.922f, r.right, 0.01f)
        assertEquals(0f, r.top, 0f); assertEquals(1f, r.bottom, 0f)
    }

    @Test fun bottomUpReadbackGivesSameBox() {
        val box = NormRect(0.25f, 0.1f, 0.75f, 0.6f)
        val d = BarDetector.detect(frame(320, 180, box, bottomUp = true), 320, 180, bottomUp = true).content!!
        assertEquals(0.1f, d.top, 0.01f); assertEquals(0.6f, d.bottom, 0.01f)
    }

    @Test fun blackFrameHasNoContent() =
        assertNull(BarDetector.detect(frame(320, 180, null), 320, 180, false).content)

    @Test fun matchesEverySystemByItsSourceAspect() {
        assertEquals("gba", matchAspect(240f / 160))
        assertEquals("gb", matchAspect(160f / 144))
        assertEquals("snes", matchAspect(256f / 224))
        assertEquals("nes", matchAspect(256f / 240))
        assertEquals("n64", matchAspect(320f / 240))
        assertEquals("genesis", matchAspect(320f / 224))
        assertEquals("ds_stacked", matchAspect(256f / 384))
        assertEquals("full", matchAspect(16f / 9))
    }

    @Test fun unknownAspectDoesNotMatch() = assertNull(matchAspect(1.25f))

    @Test fun nearBlackFrameIsIgnored() {
        // end of a fade: the box is still measurable but the frame is almost black
        val d = Detection(Geometry.fit(1920, 1080, 1.5f), meanLuma = 0.01f)
        assertNull(PresetMatcher.match(d, 1920, 1080, presets))
    }

    @Test fun dimButVisibleGameStillMatches() {
        val box = Geometry.fit(1920, 1080, 1.5f)
        val d = BarDetector.detect(frame(320, 180, box, luma = 40, noise = 0), 320, 180, false)
        assertEquals("gba", PresetMatcher.match(d, 1920, 1080, presets))
    }

    @Test fun boxNotTouchingEdgesIsIgnored() {
        // a dark scene inside a GBA game leaves a small lit area in the middle
        val d = BarDetector.detect(frame(320, 180, NormRect(0.3f, 0.3f, 0.7f, 0.7f)), 320, 180, false)
        assertNull(PresetMatcher.match(d, 1920, 1080, presets))
    }

    @Test fun hysteresisNeedsThreeInARow() {
        val a = AutoDetector(3)
        assertNull(a.feed("gba")); assertNull(a.feed("gba")); assertEquals("gba", a.feed("gba"))
        assertNull(a.feed("snes")); assertNull(a.feed("gba")) // flicker resets the candidate
        assertNull(a.feed("snes")); assertNull(a.feed("snes")); assertEquals("snes", a.feed("snes"))
    }

    @Test fun fadesNeitherConfirmNorReset() {
        val a = AutoDetector(3)
        a.reset("gba")
        assertNull(a.feed("snes")); assertNull(a.feed(null)); assertNull(a.feed(null))
        assertNull(a.feed("snes")); assertEquals("snes", a.feed("snes"))
        repeat(10) { assertNull(a.feed(null)) }
        assertEquals("snes", a.current)
    }
}
