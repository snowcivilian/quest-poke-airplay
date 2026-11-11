package io.github.snowcivilian.questairplay.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetTest {
    private val presets = Preset.parseAll(java.io.File("src/main/assets/presets.json").readText())
    private fun p(id: String) = presets.first { it.id == id }

    @Test fun allSystemsPresent() = assertEquals(
        listOf("full", "nes", "nes_8x7", "snes", "n64", "gb", "gba", "ds_top", "ds_stacked", "genesis"),
        presets.map { it.id })

    @Test fun idsUnique() = assertEquals(presets.size, presets.map { it.id }.toSet().size)

    @Test fun fullFrameHasNoCrop() {
        assertTrue(p("full").isFull)
        assertEquals(NormRect.FULL, p("full").cropFor(1920, 1080))
        assertEquals(16f / 9, p("full").outputAspect(NormRect.FULL, 1920, 1080), 1e-4f)
    }

    @Test fun snesIsCroppedAtNativeButShownAtFourThree() {
        val crop = p("snes").cropFor(1920, 1080)
        assertEquals(256f / 224, crop.pixelAspect(1920, 1080), 1e-3f)
        assertEquals(4f / 3, p("snes").outputAspect(crop, 1920, 1080), 1e-4f)
    }

    @Test fun dsStackedIsTall() = assertEquals(2f / 3, p("ds_stacked").displayAspect!!, 1e-4f)

    @Test fun nonDetectablePresetsAreManualOnly() {
        assertTrue(!p("nes_8x7").detect && !p("ds_top").detect)
        assertNull(p("full").sourceAspect)
    }
}
