package io.github.snowcivilian.questairplay.pokemon

import org.junit.Assert.assertEquals
import org.junit.Test

class EvoLinesTest {
    private val gen4 = PokeData.parse(DataGen.GEN4, java.io.File("src/main/assets/pokemon/gen4.json").readText())
    private fun lines(name: String) = gen4.speciesNamed(name)!!.evolutionLines

    @Test fun linearLine() = assertEquals(listOf("Pidgey → Pidgeotto → Pidgeot"), lines("Pidgeotto"))

    @Test fun splitAtTheLastStageIsOneLine() {
        assertEquals(listOf("Ralts → Kirlia → Gardevoir / Gallade"), lines("Kirlia"))
        assertEquals("Eevee → Vaporeon / Jolteon / Flareon / Espeon / Umbreon / Leafeon / Glaceon", lines("Eevee").single())
    }

    @Test fun earlySplitKeepsEachBranch() =
        assertEquals(listOf("Wurmple → Silcoon → Beautifly", "Wurmple → Cascoon → Dustox"), lines("Dustox"))

    @Test fun noEvolutionNoLine() = assertEquals(emptyList<String>(), lines("Tauros"))

    @Test fun laterGenerationsAreLeftOut() =
        assertEquals(listOf("Eevee → Vaporeon / Jolteon / Flareon / Espeon / Umbreon"),
            PokeData.parse(DataGen.GEN3, java.io.File("src/main/assets/pokemon/gen3.json").readText()).speciesNamed("Eevee")!!.evolutionLines)
}
