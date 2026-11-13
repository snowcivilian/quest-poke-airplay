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
        assertEquals("Sceptile", NameReading.candidates("Sceptile Lu36").first()) // Platinum, read on the headset
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
        assertEquals(listOf("Keen Eye"), c.oppAbilities.map { it.name })
        assertEquals("Prevents accuracy from being lowered.", c.oppAbilities.single().text)
    }

    @Test fun abilityTextIsTheGenerationsOwn() {
        // Sturdy only blocked one-hit KO moves before Gen 5
        val gen4 = PokeData.parse(DataGen.GEN4, java.io.File("src/main/assets/pokemon/gen4.json").readText())
        val gen5 = PokeData.parse(DataGen.GEN5, java.io.File("src/main/assets/pokemon/gen5.json").readText())
        assertTrue(gen4.abilityText["Sturdy"]!!.startsWith("One Hit KO moves have no effect"))
        assertTrue(gen5.abilityText["Sturdy"]!!.startsWith("Prevents being KOed from full HP"))
    }

    private val gen4 = PokeData.parse(DataGen.GEN4, java.io.File("src/main/assets/pokemon/gen4.json").readText())
    private fun side(n: String) = Side(n, gen4.speciesNamed(n))

    @Test fun doubleBattleRatesMovesAgainstBothOpponents() {
        val moves = listOf("Leaf Blade", "X-Scissor").map { gen4.moveNamed(it)!! }
        val c = BattleCards.build(gen4, BattlePhase.DOUBLE, side("Staravia"), side("Sceptile"), false, moves, null,
            opponent2 = side("Bidoof"), partner = side("Luxio"))
        assertEquals("Bidoof", c.opponent2!!.side.species!!.name)
        assertEquals(listOf("Fighting"), c.opponent2!!.profile[2f])
        val leaf = c.moves.first { it.move.name == "Leaf Blade" }
        assertEquals(0.5f, leaf.multiplier, 0f)        // Grass on Staravia (Normal/Flying)
        assertEquals(1f, leaf.vsSecond!!, 0f)          // Grass on Bidoof (Normal)
        assertEquals("Luxio", c.partner!!.species!!.name)
    }

    @Test fun partyRowsShowEachMatchup() {
        val c = BattleCards.build(gen4, BattlePhase.IN_BATTLE, side("Staravia"), side("Sceptile"), false, emptyList(), null,
            party = listOf(PartyRead("LEAFY", gen4.speciesNamed("Sceptile")), PartyRead("Luxio", gen4.speciesNamed("Luxio")), PartyRead("???", null)))
        val luxio = c.party.first { it.name == "Luxio" }.matches.single()
        assertEquals("Electric" to 2f, luxio.bestHit)
        assertEquals("Normal" to 1f, luxio.worstTaken)  // its Flying only does ½× to Electric
        assertEquals(2f, c.party.first { it.name == "LEAFY" }.matches.single().worstTaken.second, 0f) // Flying on Grass
        assertTrue(c.party.first { it.name == "???" }.matches.isEmpty())
    }

    @Test fun learningAMoveComparesWithKnownMoves() {
        val known = listOf("Leaf Blade", "Dragon Pulse", "Giga Drain", "X-Scissor").map { gen4.moveNamed(it)!! }
        val c = BattleCards.build(gen4, BattlePhase.IN_BATTLE, null, side("Sceptile"), null, emptyList(), null,
            learning = gen4.moveNamed("Leaf Storm"), knownMoves = known, afterBattle = true)
        val l = c.learning!!
        assertEquals("Leaf Storm", l.move.name); assertTrue(l.stab)
        assertEquals(listOf(true, false, true, false), l.currentStab)
        assertTrue(c.afterBattle)
        assertEquals(listOf("Overgrow"), c.myAbilities.map { it.name })
    }

    @Test fun unknownOpponentHasNoProfileYet() {
        val c = BattleCards.build(gen3, BattlePhase.IN_BATTLE, Side("???", null), null, null, emptyList(), null)
        assertTrue(c.opponentProfile.isEmpty()); assertNull(c.summary)
    }
}
