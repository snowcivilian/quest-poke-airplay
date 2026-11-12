package io.github.snowcivilian.questairplay.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusConditionsTest {
    @Test fun badgesAreReadFromHpBoxText() {
        assertEquals(Status.PSN, StatusConditions.parse("PIDGEY ♂ PSN"))   // Gen 3: badge replaces the level
        assertEquals(Status.BRN, StatusConditions.parse("brn"))
        assertEquals(Status.PAR, StatusConditions.parse("Staravia\nPAR"))
        assertEquals(Status.SLP, StatusConditions.parse("5LP"))            // OCR digit confusion
        assertNull(StatusConditions.parse("Sceptile Lu36"))
        assertNull(StatusConditions.parse("SPARK"))                        // whole words only
    }

    @Test fun debounceNeedsTwoMatchingReads() {
        val d = StatusDebounce()
        assertNull(d.update(Status.BRN))
        assertEquals(Status.BRN, d.update(Status.BRN))
        assertEquals(Status.BRN, d.update(null))      // one blank read (animation) keeps it
        assertEquals(Status.BRN, d.update(Status.BRN))
        d.update(null)
        assertNull(d.update(null))                    // cured
    }

    @Test fun badgeWordsArentPartOfTheName() =
        assertEquals("NICK", NameReading.candidates("NICK PSN").first())

    @Test fun burnHalvesOnlyPhysicalDamage() {
        assertEquals(0.5f, StatusConditions.attackFactor(Status.BRN, "physical"), 0f)
        assertEquals(1f, StatusConditions.attackFactor(Status.BRN, "special"), 0f)
        assertEquals(1f, StatusConditions.attackFactor(Status.PAR, "physical"), 0f)
        assertEquals(1f, StatusConditions.attackFactor(null, "physical"), 0f)
    }

    @Test fun catchBonusFollowsGeneration() {
        assertEquals(2f, StatusConditions.catchBonus(Status.SLP, DataGen.GEN4), 0f)
        assertEquals(2.5f, StatusConditions.catchBonus(Status.SLP, DataGen.GEN5), 0f)
        assertEquals(1.5f, StatusConditions.catchBonus(Status.PAR, DataGen.GEN3), 0f)
    }

    @Test fun notesFollowGenerationAndAbilities() {
        val par4 = StatusConditions.notes(Status.PAR, DataGen.GEN4, mine = false, wild = true, abilities = listOf("Run Away"))
        assertTrue(par4.first().startsWith("Speed ×¼"))
        assertTrue(par4.any { it == "Catch rate ×1.5" })
        val parLatest = StatusConditions.notes(Status.PAR, DataGen.LATEST, mine = true, wild = true, abilities = emptyList())
        assertTrue(parLatest.first().startsWith("Speed ×½"))
        assertTrue(parLatest.none { it.startsWith("Catch") })              // my Pokémon isn't being caught
        val brn = StatusConditions.notes(Status.BRN, DataGen.GEN4, mine = false, wild = false, abilities = listOf("Guts", "Swift Swim"))
        assertTrue(brn.any { it.startsWith("If it has Guts") })
    }
}
