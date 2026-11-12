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

    /** Synthetic 720x480 GBA battle frame: dark scene, light HP boxes, coloured HP bars. */
    private fun frame(opp: Int = 1, player: Boolean = true, color: IntArray = intArrayOf(112, 248, 168), barFrac: Float = 1f): GameFrame {
        val w = 720; val h = 480
        val px = ByteArray(w * h * 4)
        fun fill(r: NormRect, c: IntArray) {
            for (y in (r.top * h).toInt() until (r.bottom * h).toInt()) for (x in (r.left * w).toInt() until (r.right * w).toInt()) {
                val i = (y * w + x) * 4; px[i] = c[0].toByte(); px[i + 1] = c[1].toByte(); px[i + 2] = c[2].toByte(); px[i + 3] = -1
            }
        }
        fill(NormRect(0f, 0f, 1f, 1f), intArrayOf(40, 60, 40))
        fun box(name: NormRect, bar: NormRect, bars: Int) {
            fill(name, intArrayOf(248, 248, 216))
            val rowH = bar.height / 3
            repeat(bars) { i ->
                val top = bar.top + i * rowH * 1.5f
                fill(NormRect(bar.left + bar.width * 0.1f, top, bar.left + bar.width * (0.1f + 0.8f * barFrac), top + rowH * 0.6f), color)
            }
        }
        if (opp > 0) box(gba.oppName, gba.oppHpBar, opp)
        if (player) box(gba.playerName, gba.playerHpBar, 1)
        return GameFrame(px, w, h)
    }

    @Test fun gen4HasALayoutGen5NotYet() {
        assertNotNull(profiles.first { it.id == "rse" }.layout)
        assertNotNull(profiles.first { it.id == "dppt" }.layout)
        assertNotNull(profiles.first { it.id == "hgss" }.layout)
        assertNull(profiles.first { it.id == "bw" }.layout)
    }

    /** A real Platinum capture from the headset (FIGHT menu open, Sceptile vs Staravia). */
    private fun platinum(): GameFrame {
        val img = javax.imageio.ImageIO.read(javaClass.getResourceAsStream("/pokemon/platinum_fight.png"))
        val px = ByteArray(img.width * img.height * 4)
        for (y in 0 until img.height) for (x in 0 until img.width) {
            val c = img.getRGB(x, y); val i = (y * img.width + x) * 4
            px[i] = (c shr 16).toByte(); px[i + 1] = (c shr 8).toByte(); px[i + 2] = c.toByte(); px[i + 3] = -1
        }
        return GameFrame(px, img.width, img.height)
    }

    @Test fun realPlatinumBattleIsDetected() {
        val l = profiles.first { it.id == "dppt" }.layout!!
        val o = BattleDetector.observe(platinum(), l)
        assertTrue(o.oppBox && o.playerBox); assertEquals(1, o.oppBars)
    }

    @Test fun platinumBoxesNeedTheirColourNotJustHpBars() {
        // the same frame with the GBA rule (light boxes only) is not a battle: the box colour matters
        val l = profiles.first { it.id == "dppt" }.layout!!.copy(boxColor = null)
        val o = BattleDetector.observe(platinum(), l)
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
