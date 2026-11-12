package io.github.snowcivilian.questairplay.video

import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryTest {
    private fun assertRect(e: NormRect, a: NormRect) {
        assertEquals(e.left, a.left, 1e-4f); assertEquals(e.top, a.top, 1e-4f)
        assertEquals(e.right, a.right, 1e-4f); assertEquals(e.bottom, a.bottom, 1e-4f)
    }

    @Test fun gbaInSixteenByNineIsPillarboxed() =
        assertRect(NormRect(0.078125f, 0f, 0.921875f, 1f), Geometry.fit(1920, 1080, 1.5f))

    @Test fun wideContentIsLetterboxed() =
        assertRect(NormRect(0f, 0.125f, 1f, 0.875f), Geometry.fit(1000, 1000, 4f / 3f))

    @Test fun sameAspectIsFull() = assertRect(NormRect.FULL, Geometry.fit(1920, 1080, 16f / 9f))

    @Test fun tallDsFitsByHeight() {
        val r = Geometry.fit(1920, 1080, 2f / 3f)
        assertEquals(2f / 3f, r.pixelAspect(1920, 1080), 1e-4f)
        assertEquals(1f, r.height, 1e-6f)
    }

    @Test fun viewportIsCenteredAndBottomLeft() {
        val v = Geometry.viewport(1280, 720, 4f / 3f)
        assertEquals(Viewport(160, 0, 960, 720), v)
        val l = Geometry.viewport(800, 800, 2f)
        assertEquals(Viewport(0, 200, 800, 400), l)
    }

    @Test fun subRectMapsIntoOuter() = assertRect(
        NormRect(0.25f, 0.5f, 0.75f, 1f),
        Geometry.sub(NormRect(0.25f, 0f, 0.75f, 1f), NormRect(0f, 0.5f, 1f, 1f)))

    @Test fun badInputFallsBackToFull() = assertRect(NormRect.FULL, Geometry.fit(0, 1080, 1.5f))
}
