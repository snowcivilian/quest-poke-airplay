package io.github.snowcivilian.questairplay.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameProfileTest {
    private val all = GameProfile.parseAll(java.io.File("src/main/assets/pokemon/profiles.json").readText())

    @Test fun everySpecProfileIsPresent() = assertEquals(
        listOf("frlg", "rse", "frlg_hack", "emerald_hack", "dppt", "hgss", "bw", "b2w2"), all.map { it.id })

    @Test fun defaultsFollowTheGames() {
        assertEquals(DataGen.GEN3, all.first { it.id == "frlg" }.defaultGen)
        assertEquals(DataGen.GEN4, all.first { it.id == "hgss" }.defaultGen)
        assertEquals(DataGen.GEN5, all.first { it.id == "b2w2" }.defaultGen)
    }

    @Test fun presetNarrowsTheList() {
        assertEquals("gba", GameProfile.systemOfPreset("gba"))
        assertEquals("ds", GameProfile.systemOfPreset("ds_stacked"))
        assertNull(GameProfile.systemOfPreset("snes"))
        assertEquals(4, GameProfile.forSystem(all, "gba").size)
        assertEquals(4, GameProfile.forSystem(all, "ds").size)
        assertEquals(8, GameProfile.forSystem(all, null).size)
    }
}
