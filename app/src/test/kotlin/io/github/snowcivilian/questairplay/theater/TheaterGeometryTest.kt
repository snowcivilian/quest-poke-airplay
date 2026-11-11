package io.github.snowcivilian.questairplay.theater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TheaterGeometryTest {
    @Test fun wideScreenSizes() {
        val s = TheaterGeometry.screen(16f / 9, 200, 350, 130, 0)
        assertEquals(3.5556f, s.widthM, 1e-3f); assertEquals(2f, s.heightM, 0f)
        assertEquals(3.5f, s.distanceM, 0f); assertEquals(1.3f, s.elevationM, 1e-6f)
        assertNull(s.radiusM)
        assertEquals(1920, s.pixelW); assertEquals(1080, s.pixelH)
    }

    @Test fun tallDsScreenKeepsLongSideVertical() {
        val s = TheaterGeometry.screen(2f / 3, 200, 350, 130, 0)
        assertEquals(1280, s.pixelW); assertEquals(1920, s.pixelH)
        assertEquals(1.3333f, s.widthM, 1e-3f)
    }

    @Test fun fullCurveWrapsAroundViewer() =
        assertEquals(3.5f, TheaterGeometry.screen(1.5f, 200, 350, 130, 100).radiusM!!, 1e-4f)

    @Test fun halfCurveIsGentler() =
        assertEquals(7f, TheaterGeometry.screen(1.5f, 200, 350, 130, 50).radiusM!!, 1e-4f)

    @Test fun tinyCurveIsCapped() =
        assertEquals(60f, TheaterGeometry.screen(1.5f, 200, 350, 130, 1).radiusM!!, 1e-4f)

    @Test fun pixelSizesAreEven() {
        val s = TheaterGeometry.screen(10f / 9, 200, 350, 130, 0)
        assertEquals(0, s.pixelH % 2); assertEquals(1728, s.pixelH)
    }

    @Test fun badAspectFallsBackToSixteenNine() =
        assertEquals(1080, TheaterGeometry.screen(Float.NaN, 200, 350, 130, 0).pixelH)
}
