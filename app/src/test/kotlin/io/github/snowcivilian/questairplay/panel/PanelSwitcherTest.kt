package io.github.snowcivilian.questairplay.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanelSwitcherTest {
    private val aspects = listOf(16f / 9, 4f / 3, 3f / 2, 10f / 9, 8f / 7, 2f / 3)

    @Test fun exactMatches() {
        assertEquals(2, PanelSwitcher.nearest(aspects, 1.5f))
        assertEquals(5, PanelSwitcher.nearest(aspects, 256f / 384))
        assertEquals(0, PanelSwitcher.nearest(aspects, 1920f / 1080))
    }

    @Test fun phonePortraitPicksTallPanel() = assertEquals(5, PanelSwitcher.nearest(aspects, 0.46f))

    @Test fun ultrawidePicksWidest() = assertEquals(0, PanelSwitcher.nearest(aspects, 2.4f))

    @Test fun emptyListIsMinusOne() = assertEquals(-1, PanelSwitcher.nearest(emptyList(), 1f))

    @Test fun parsesRatios() {
        assertEquals(4f / 3, PanelSwitcher.parseRatio("4:3")!!, 1e-6f)
        assertNull(PanelSwitcher.parseRatio("4x3"))
        assertNull(PanelSwitcher.parseRatio("0:3"))
    }
}
