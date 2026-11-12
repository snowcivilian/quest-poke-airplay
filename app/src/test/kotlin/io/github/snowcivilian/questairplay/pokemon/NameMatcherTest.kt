package io.github.snowcivilian.questairplay.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameMatcherTest {
    private val gen3 = PokeData.parse(DataGen.GEN3, java.io.File("src/main/assets/pokemon/gen3.json").readText())
    private fun sp(read: String) = gen3.speciesMatcher.match(read)?.name

    @Test fun exactInGameCaps() {
        assertEquals("Charmander", sp("CHARMANDER"))
        assertEquals("Mr. Mime", sp("MR. MIME"))
        assertEquals("Farfetch’d", sp("FARFETCH'D"))
    }

    @Test fun genderSymbols() {
        assertEquals("Nidoran♀", sp("NIDORAN♀"))
        assertEquals("Nidoran♂", sp("NIDORAN♂"))
    }

    @Test fun ocrMistakesAreTolerated() {
        assertEquals("Pidgey", sp("P1DGEY"))
        assertEquals("Geodude", sp("GE0DUDE"))
        assertEquals("Zigzagoon", sp("ZIGZAG00N"))
        assertEquals("Rattata", sp("RATTATTA"))
    }

    @Test fun nicknamesAndJunkDontMatch() {
        assertNull(sp("SPARKY"))
        assertNull(sp(""))
        assertNull(sp("!!"))
    }

    @Test fun laterGenerationNamesArentInGen3() = assertNull(sp("PIPLUP"))

    @Test fun movesMatchToo() =
        assertEquals("Thunder Shock", gen3.moveMatcher.match("THUNDERSHOCK")?.name)

    @Test fun levenshtein() {
        assertEquals(0, NameMatcher.distance("ABC", "ABC"))
        assertEquals(1, NameMatcher.distance("ABC", "ABD"))
        assertEquals(3, NameMatcher.distance("", "ABC"))
        assertEquals(3, NameMatcher.distance("KITTEN", "SITTING"))
    }
}
