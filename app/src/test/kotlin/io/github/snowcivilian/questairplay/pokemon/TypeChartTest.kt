package io.github.snowcivilian.questairplay.pokemon

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks each generation's bundled chart (built from PokeAPI) against an independent reference
 * written out by hand from the games' published charts.
 */
class TypeChartTest {
    private fun data(gen: DataGen) =
        PokeData.parse(gen, java.io.File("src/main/assets/pokemon/${gen.key}.json").readText())

    /** attacker -> (super effective, not very effective, no effect), Gen 6+ chart. */
    private val latest = mapOf(
        "Normal" to Triple(listOf(), listOf("Rock", "Steel"), listOf("Ghost")),
        "Fire" to Triple(listOf("Grass", "Ice", "Bug", "Steel"), listOf("Fire", "Water", "Rock", "Dragon"), listOf()),
        "Water" to Triple(listOf("Fire", "Ground", "Rock"), listOf("Water", "Grass", "Dragon"), listOf()),
        "Electric" to Triple(listOf("Water", "Flying"), listOf("Electric", "Grass", "Dragon"), listOf("Ground")),
        "Grass" to Triple(listOf("Water", "Ground", "Rock"), listOf("Fire", "Grass", "Poison", "Flying", "Bug", "Dragon", "Steel"), listOf()),
        "Ice" to Triple(listOf("Grass", "Ground", "Flying", "Dragon"), listOf("Fire", "Water", "Ice", "Steel"), listOf()),
        "Fighting" to Triple(listOf("Normal", "Ice", "Rock", "Dark", "Steel"), listOf("Poison", "Flying", "Psychic", "Bug", "Fairy"), listOf("Ghost")),
        "Poison" to Triple(listOf("Grass", "Fairy"), listOf("Poison", "Ground", "Rock", "Ghost"), listOf("Steel")),
        "Ground" to Triple(listOf("Fire", "Electric", "Poison", "Rock", "Steel"), listOf("Grass", "Bug"), listOf("Flying")),
        "Flying" to Triple(listOf("Grass", "Fighting", "Bug"), listOf("Electric", "Rock", "Steel"), listOf()),
        "Psychic" to Triple(listOf("Fighting", "Poison"), listOf("Psychic", "Steel"), listOf("Dark")),
        "Bug" to Triple(listOf("Grass", "Psychic", "Dark"), listOf("Fire", "Fighting", "Poison", "Flying", "Ghost", "Steel", "Fairy"), listOf()),
        "Rock" to Triple(listOf("Fire", "Ice", "Flying", "Bug"), listOf("Fighting", "Ground", "Steel"), listOf()),
        "Ghost" to Triple(listOf("Psychic", "Ghost"), listOf("Dark"), listOf("Normal")),
        "Dragon" to Triple(listOf("Dragon"), listOf("Steel"), listOf("Fairy")),
        "Dark" to Triple(listOf("Psychic", "Ghost"), listOf("Fighting", "Dark", "Fairy"), listOf()),
        "Steel" to Triple(listOf("Ice", "Rock", "Fairy"), listOf("Fire", "Water", "Electric", "Steel"), listOf()),
        "Fairy" to Triple(listOf("Fighting", "Dragon", "Dark"), listOf("Fire", "Poison", "Steel"), listOf()),
    )

    /** Gen 2-5 chart: no Fairy, and Steel also resists Ghost and Dark. */
    private val gen2to5 = latest.filterKeys { it != "Fairy" }.mapValues { (atk, t) ->
        val nve = t.second.filter { it != "Fairy" } + if (atk == "Ghost" || atk == "Dark") listOf("Steel") else listOf()
        Triple(t.first.filter { it != "Fairy" }, nve, t.third.filter { it != "Fairy" })
    }

    private fun check(gen: DataGen, ref: Map<String, Triple<List<String>, List<String>, List<String>>>) {
        val chart = data(gen).chart
        assertEquals(ref.keys.sorted(), chart.types.sorted())
        for (atk in chart.types) for (def in chart.types) {
            val (se, nve, none) = ref.getValue(atk)
            val expected = when (def) { in se -> 2f; in nve -> 0.5f; in none -> 0f; else -> 1f }
            assertEquals("${gen.label}: $atk -> $def", expected, chart.factor(atk, def), 0f)
        }
    }

    @Test fun gen3Chart() = check(DataGen.GEN3, gen2to5)
    @Test fun gen4Chart() = check(DataGen.GEN4, gen2to5)
    @Test fun gen5Chart() = check(DataGen.GEN5, gen2to5)
    @Test fun latestChart() = check(DataGen.LATEST, latest)

    @Test fun dualTypesMultiply() {
        val c = data(DataGen.LATEST).chart
        assertEquals(4f, c.multiplier("Rock", listOf("Fire", "Flying")), 0f)
        assertEquals(0f, c.multiplier("Ground", listOf("Fire", "Flying")), 0f)
        assertEquals(0.25f, c.multiplier("Grass", listOf("Fire", "Flying")), 0f)
    }

    @Test fun charizardDefensiveProfile() {
        val p = data(DataGen.GEN3).chart.defensiveProfile(listOf("Fire", "Flying"))
        assertEquals(listOf("Rock"), p[4f])
        assertEquals(setOf("Water", "Electric"), p[2f]!!.toSet())
        assertEquals(setOf("Fire", "Fighting", "Steel"), p[0.5f]!!.toSet())
        assertEquals(setOf("Grass", "Bug"), p[0.25f]!!.toSet())
        assertEquals(listOf("Ground"), p[0f])
        assertEquals(listOf(4f, 2f, 0.5f, 0.25f, 0f), p.keys.toList())
    }

    @Test fun matchupSummaryUsesTypesOnly() {
        val c = data(DataGen.GEN3).chart
        val s = Matchups.summary(c, mine = listOf("Water"), theirs = listOf("Fire", "Flying"))
        assertEquals(listOf("Water" to 2f), s.myStrongTypes)
        assertEquals(emptyList<Pair<String, Float>>(), s.theirThreats)
        val s2 = Matchups.summary(c, mine = listOf("Grass", "Poison"), theirs = listOf("Fire", "Flying"))
        assertEquals(setOf("Fire", "Flying"), s2.theirThreats.map { it.first }.toSet())
    }

    @Test fun moveEffectivenessFollowsGeneration() {
        val g3 = data(DataGen.GEN3)
        val bite = g3.moveNamed("Bite")!!
        assertEquals("special", bite.category) // Gen 3: category by type
        assertEquals("physical", data(DataGen.GEN4).moveNamed("Bite")!!.category)
        val eff = Matchups.moves(g3.chart, listOf(bite, g3.moveNamed("Growl")!!), listOf("Psychic"))
        assertEquals(2f, eff[0].multiplier, 0f)
        assertEquals(1f, eff[1].multiplier, 0f)
    }

    @Test fun speciesDataIsPerGeneration() {
        assertEquals(listOf("Normal"), data(DataGen.GEN5).speciesNamed("Clefairy")!!.types)
        assertEquals(listOf("Fairy"), data(DataGen.LATEST).speciesNamed("Clefairy")!!.types)
        assertEquals(null, data(DataGen.GEN4).speciesNamed("Butterfree")!!.hidden)
        assertEquals("Tinted Lens", data(DataGen.GEN5).speciesNamed("Butterfree")!!.hidden)
        assertEquals(386, data(DataGen.GEN3).species.size)
        assertEquals(null, data(DataGen.GEN3).speciesNamed("Turtwig"))
    }
}
