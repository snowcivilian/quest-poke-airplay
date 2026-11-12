package io.github.snowcivilian.questairplay.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleCardTest {
    private val gen3 = PokeData.parse(DataGen.GEN3, java.io.File("src/main/assets/pokemon/gen3.json").readText())

    @Test fun nameCandidatesDropLevelAndGender() {
        assertEquals("PIDGEY", NameReading.candidates("PIDGEY ♂Lv5").first())
        assertEquals("PIDGEY", NameReading.candidates("PIDGEY Lv 5").first())
        assertEquals("Pidgey", NameReading.match(gen3.speciesMatcher, "PIDGEY ♀Lv12")?.name)
        assertEquals("Mr. Mime", NameReading.match(gen3.speciesMatcher, "MR. MIME Lv30")?.name)
        assertTrue(NameReading.candidates("Lv5").isEmpty())
        assertEquals("Nidoran♀", NameReading.match(gen3.speciesMatcher, "NIDORAN♀ Lv8")?.name)
        assertEquals("Nidoran♂", NameReading.match(gen3.speciesMatcher, "NIDORAN♂♂Lv8")?.name)
    }

    @Test fun cardForAWildBattleInTheFightMenu() {
        val opp = Side("PIDGEY", gen3.speciesNamed("Pidgey"))
        val me = Side("CHARMANDER", gen3.speciesNamed("Charmander"))
        val moves = listOf("Scratch", "Growl", "Ember", "Smokescreen").map { gen3.moveNamed(it)!! }
        val c = BattleCards.build(gen3, BattlePhase.IN_BATTLE, opp, me, true, moves, null)
        assertEquals(listOf("Electric", "Ice", "Rock"), c.opponentProfile[2f]!!.sorted())
        assertEquals(setOf("Ground", "Ghost"), c.opponentProfile[0f]!!.toSet())
        assertEquals(4, c.moves.size)
        assertEquals(1f, c.moves.first { it.move.name == "Ember" }.multiplier, 0f)
        assertEquals("special", c.moves.first { it.move.name == "Ember" }.move.category)
        assertTrue(c.summary!!.myStrongTypes.isEmpty())
        assertEquals(45, c.player!!.species!!.catchRate)
    }

    @Test fun doubleBattleIsJustANote() {
        val c = BattleCards.build(gen3, BattlePhase.DOUBLE, null, null, null, emptyList(), null)
        assertEquals("Double battles not supported", c.note)
        assertNull(c.opponent)
    }

    @Test fun unknownOpponentHasNoProfileYet() {
        val c = BattleCards.build(gen3, BattlePhase.IN_BATTLE, Side("???", null), null, null, emptyList(), null)
        assertTrue(c.opponentProfile.isEmpty()); assertNull(c.summary)
    }
}
