package io.github.snowcivilian.questairplay.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneReadingTest {
    private val gen4 = PokeData.parse(DataGen.GEN4, java.io.File("src/main/assets/pokemon/gen4.json").readText())
    private val nicks = mapOf("LEAFY" to "Sceptile")
    private fun nick(n: String) = nicks[n.uppercase()]
    private fun line(t: String, l: Float, top: Float, r: Float, b: Float) = TextLine(t, l, top, r, b)

    /** Lines as ML Kit read the Platinum top screen on the headset (fractions of the top screen). */
    private val single = listOf(
        line("Staravia", 0.0f, 0.13f, 0.20f, 0.19f), line("BLu26", 0.26f, 0.13f, 0.38f, 0.19f),
        line("Sceptile BLu36", 0.59f, 0.53f, 0.95f, 0.59f), line("106/106", 0.75f, 0.63f, 0.97f, 0.68f),
        line("What will Sceptile do?", 0.04f, 0.80f, 0.6f, 0.87f),
    )

    @Test fun singleBattleBoxesAndMessage() {
        val s = SceneReading.battle(single, gen4, ::nick, messageTop = 0.76f)
        assertEquals(listOf("Staravia"), s.opp.map { it.species?.name })
        assertEquals(listOf("Sceptile"), s.mine.map { it.species?.name })
        assertEquals("What will Sceptile do?", s.message)
        assertNull(s.opp.single().status)
    }

    @Test fun badgeOnItsOwnLineGoesToTheNearestBox() {
        val s = SceneReading.battle(single + line("PAR", 0.02f, 0.22f, 0.10f, 0.26f), gen4, ::nick, 0.76f)
        assertEquals(Status.PAR, s.opp.single().status)
        assertNull(s.mine.single().status)
    }

    @Test fun badgeOnTheNameLine() =
        assertEquals(Status.BRN, SceneReading.battle(single.map { if (it.text.startsWith("Sceptile")) it.copy(text = "Sceptile BRN Lu36") else it },
            gen4, ::nick, 0.76f).mine.single().status)

    @Test fun doubleBattleHasTwoOnEachSide() {
        val lines = listOf(
            line("Staravia", 0.0f, 0.05f, 0.2f, 0.10f), line("Lu26", 0.27f, 0.05f, 0.38f, 0.10f),
            line("Bidoof", 0.0f, 0.22f, 0.2f, 0.27f), line("Lu24", 0.27f, 0.22f, 0.38f, 0.27f),
            line("LEAFY", 0.6f, 0.45f, 0.8f, 0.50f), line("Lu36", 0.85f, 0.45f, 0.97f, 0.50f),
            line("Luxio Lv30", 0.6f, 0.60f, 0.97f, 0.65f),
        )
        val s = SceneReading.battle(lines, gen4, ::nick, 0.76f)
        assertEquals(listOf("Staravia", "Bidoof"), s.opp.map { it.species?.name })
        assertEquals(listOf("Sceptile", "Luxio"), s.mine.map { it.species?.name })   // LEAFY is a saved nickname
    }

    @Test fun battleShapeOpponentsAboveMine() {
        assertTrue(SceneReading.looksLikeBattle(SceneReading.battle(single, gen4, ::nick, 0.76f)))
        // party-style screen: names with levels in two columns at the same heights
        val columns = listOf(line("Sceptile", 0.05f, 0.2f, 0.3f, 0.25f), line("Lv36", 0.32f, 0.2f, 0.42f, 0.25f),
            line("Luxio", 0.55f, 0.2f, 0.75f, 0.25f), line("Lv30", 0.8f, 0.2f, 0.9f, 0.25f))
        assertTrue(!SceneReading.looksLikeBattle(SceneReading.battle(columns, gen4, ::nick, 0.76f)))
    }

    /** The Platinum double battle from the headset (Furret, Linoone vs Sceptile, Vibrava), HP labels misread. */
    @Test fun realDoubleBattleIgnoresHpLabels() {
        val lines = listOf(
            line("Furret", 0.01f, 0.03f, 0.17f, 0.09f), line("FLuB2", 0.29f, 0.03f, 0.41f, 0.09f), line("HPD", 0.17f, 0.11f, 0.21f, 0.135f),
            line("Linoone", 0.01f, 0.18f, 0.18f, 0.24f), line("Lu32", 0.29f, 0.18f, 0.41f, 0.24f), line("HPI", 0.17f, 0.26f, 0.21f, 0.29f),
            line("Sceptile", 0.56f, 0.48f, 0.73f, 0.55f), line("ŠLu37", 0.78f, 0.48f, 0.95f, 0.55f), line("HPD", 0.70f, 0.57f, 0.74f, 0.60f),
            line("ibrava", 0.59f, 0.64f, 0.74f, 0.70f), line("Lu34", 0.82f, 0.64f, 0.96f, 0.70f), line("MPI", 0.72f, 0.72f, 0.76f, 0.75f),
            line("What will Sceptile do?", 0.04f, 0.80f, 0.6f, 0.87f),
        )
        val s = SceneReading.battle(lines, gen4, ::nick, 0.76f)
        assertEquals(listOf("Furret", "Linoone"), s.opp.map { it.species?.name })
        assertEquals(listOf("Sceptile", "Vibrava"), s.mine.map { it.species?.name })
        assertTrue(SceneReading.looksLikeBattle(s))
    }

    @Test fun overworldTextIsNoBattle() {
        val s = SceneReading.battle(listOf(line("Professor Rowan's lab", 0.1f, 0.3f, 0.9f, 0.36f)), gen4, ::nick, 0.76f)
        assertTrue(s.opp.isEmpty() && s.mine.isEmpty())
    }

    @Test fun partyScreen() {
        val lines = listOf(
            line("LEAFY", 0.10f, 0.05f, 0.35f, 0.10f), line("Lv36", 0.36f, 0.12f, 0.48f, 0.17f),
            line("Luxio", 0.60f, 0.08f, 0.80f, 0.13f), line("Lv30", 0.86f, 0.15f, 0.98f, 0.20f),
            line("Ponyta", 0.10f, 0.38f, 0.35f, 0.43f), line("Lv28", 0.36f, 0.45f, 0.48f, 0.50f),
            line("Choose a POKéMON.", 0.05f, 0.9f, 0.6f, 0.95f), line("CANCEL", 0.75f, 0.9f, 0.95f, 0.96f),
        )
        assertEquals(listOf("Sceptile", "Luxio", "Ponyta"), SceneReading.party(lines, gen4, ::nick).map { it.species?.name })
    }

    @Test fun fightMenuIsNotAParty() {
        val lines = listOf(line("Leaf Blade", 0.1f, 0.2f, 0.4f, 0.25f), line("GRASS", 0.1f, 0.3f, 0.2f, 0.34f),
            line("PP 15/15", 0.25f, 0.3f, 0.45f, 0.34f), line("Dragon Pulse", 0.6f, 0.2f, 0.9f, 0.25f), line("CANCEL", 0.4f, 0.9f, 0.6f, 0.95f))
        assertTrue(SceneReading.party(lines, gen4, ::nick).isEmpty())
    }

    @Test fun learnMoveMessages() {
        assertEquals("Leaf Storm", SceneReading.learning("Sceptile wants to learn the move Leaf Storm.", gen4)?.name)
        assertEquals("Leaf Storm", SceneReading.learning("Delete a move to make room for Leaf Storm?", gen4)?.name)
        assertEquals("Leaf Storm", SceneReading.learning("Sceptile is trying to learn Leaf Storm.", gen4)?.name)
        assertNull(SceneReading.learning("But Sceptile can't learn more than four moves.", gen4))
        assertNull(SceneReading.learning("What will Sceptile do?", gen4))
    }
}
