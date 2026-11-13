package io.github.snowcivilian.questairplay.pokemon

import io.github.snowcivilian.questairplay.video.NormRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleDetectionTest {
    private val profiles = GameProfile.parseAll(java.io.File("src/main/assets/pokemon/profiles.json").readText())
    private val gba = profiles.first { it.id == "frlg" }.layout!!

    /**
     * Synthetic battle frame: dark scene, HP boxes in [boxColor] (light for GBA), coloured HP bars.
     * Default is a 720x480 GBA frame; [ds] gives a 512x768 stacked DS frame.
     */
    private fun frame(
        opp: Int = 1, player: Boolean = true, color: IntArray = intArrayOf(112, 248, 168), barFrac: Float = 1f,
        layout: BattleLayout = gba, boxColor: IntArray = intArrayOf(248, 248, 216), ds: Boolean = false,
    ): GameFrame {
        val w = if (ds) 512 else 720; val h = if (ds) 768 else 480
        val px = ByteArray(w * h * 4)
        fun fill(r: NormRect, c: IntArray) {
            for (y in (r.top * h).toInt() until (r.bottom * h).toInt()) for (x in (r.left * w).toInt() until (r.right * w).toInt()) {
                val i = (y * w + x) * 4; px[i] = c[0].toByte(); px[i + 1] = c[1].toByte(); px[i + 2] = c[2].toByte(); px[i + 3] = -1
            }
        }
        fill(NormRect(0f, 0f, 1f, 1f), intArrayOf(40, 60, 40))
        fun box(name: NormRect, bar: NormRect, bars: Int) {
            fill(name, boxColor)
            val rowH = bar.height / 3
            repeat(bars) { i ->
                val top = bar.top + i * rowH * 1.5f
                fill(NormRect(bar.left + bar.width * 0.1f, top, bar.left + bar.width * (0.1f + 0.8f * barFrac), top + rowH * 0.6f), color)
            }
        }
        if (opp > 0) box(layout.oppName, layout.oppHpBar, opp)
        if (player) box(layout.playerName, layout.playerHpBar, 1)
        return GameFrame(px, w, h)
    }

    private val dppt = profiles.first { it.id == "dppt" }.layout!!

    /** Platinum-style frame: stacked DS screens with the layout's dark grey HP boxes. */
    private fun platinum() = frame(layout = dppt, boxColor = dppt.boxColor!!, ds = true)

    @Test fun gen4HasALayoutGen5NotYet() {
        assertNotNull(profiles.first { it.id == "rse" }.layout)
        assertNotNull(profiles.first { it.id == "dppt" }.layout)
        assertNotNull(profiles.first { it.id == "hgss" }.layout)
        assertNull(profiles.first { it.id == "bw" }.layout)
    }

    @Test fun platinumBattleIsDetected() {
        val o = BattleDetector.observe(platinum(), dppt)
        assertTrue(o.oppBox && o.playerBox); assertEquals(1, o.oppBars)
    }

    @Test fun moveTextInkIgnoresColouredBorders() {
        assertTrue(BattleDetector.isInk(17, 18, 9))      // glyph outline
        assertTrue(BattleDetector.isInk(152, 151, 133))  // glyph fill
        assertTrue(!BattleDetector.isInk(255, 255, 182)) // slot background
        assertTrue(!BattleDetector.isInk(39, 187, 0))    // green slot border
    }

    @Test fun platinumBoxesNeedTheirColourNotJustHpBars() {
        // the same frame with the GBA rule (light boxes only) is not a battle: the box colour matters
        val o = BattleDetector.observe(platinum(), dppt.copy(boxColor = null))
        assertTrue(!o.oppBox && !o.playerBox)
    }

    @Test fun hpColoursAreRecognised() {
        assertTrue(BattleDetector.isHpFill(112, 248, 168)) // green
        assertTrue(BattleDetector.isHpFill(248, 224, 56))  // yellow
        assertTrue(BattleDetector.isHpFill(248, 88, 56))   // red
        assertTrue(!BattleDetector.isHpFill(248, 248, 216)) // HP box background
        assertTrue(!BattleDetector.isHpFill(40, 60, 40))   // dark scenery
    }

    @Test fun singleBattleIsDetected() {
        val o = BattleDetector.observe(frame(), gba)
        assertTrue(o.oppBox && o.playerBox); assertEquals(1, o.oppBars)
    }

    @Test fun lowHpRedBarStillCounts() =
        assertTrue(BattleDetector.observe(frame(color = intArrayOf(248, 88, 56), barFrac = 0.06f), gba).oppBox)

    @Test fun doubleBattleHasTwoOpponentBars() = assertEquals(2, BattleDetector.observe(frame(opp = 2), gba).oppBars)

    @Test fun overworldIsNotABattle() {
        val o = BattleDetector.observe(frame(opp = 0, player = false), gba)
        assertTrue(!o.oppBox && !o.playerBox)
    }

    @Test fun trackerDebouncesEntryAndExit() {
        val t = BattleTracker()
        val battle = Observation(true, true, 1); val none = Observation(false, false, 0); val half = Observation(false, true, 0)
        assertEquals(BattlePhase.IDLE, t.update(battle))
        assertEquals(BattlePhase.IN_BATTLE, t.update(battle))
        repeat(3) { assertEquals(BattlePhase.IN_BATTLE, t.update(none)) } // text box / animation
        assertEquals(BattlePhase.IN_BATTLE, t.update(half)) // one box visible keeps it alive
        repeat(3) { t.update(none) }
        assertEquals(BattlePhase.OVER, t.update(none))
        assertEquals(BattlePhase.IDLE, t.update(none))
    }

    @Test fun trackerFlagsDoubleBattles() {
        val t = BattleTracker()
        t.update(Observation(true, true, 2))
        assertEquals(BattlePhase.DOUBLE, t.update(Observation(true, true, 2)))
    }
}
