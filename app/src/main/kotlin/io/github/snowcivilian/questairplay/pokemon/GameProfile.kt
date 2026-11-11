package io.github.snowcivilian.questairplay.pokemon

import android.content.Context
import android.content.SharedPreferences
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.get
import org.json.JSONObject

/** A supported game (S4), from assets/pokemon/profiles.json. */
data class GameProfile(val id: String, val label: String, val system: String, val defaultGen: DataGen) {
    companion object {
        fun parseAll(json: String): List<GameProfile> {
            val a = JSONObject(json).getJSONArray("profiles")
            return (0 until a.length()).map {
                val o = a.getJSONObject(it)
                GameProfile(o.getString("id"), o.getString("label"), o.getString("system"),
                    DataGen.of(o.getString("defaultGen")) ?: error("bad defaultGen in ${o.getString("id")}"))
            }
        }

        /** System of a preset id, for narrowing the profile list: GBA presets show GBA games, DS show DS. */
        fun systemOfPreset(presetId: String): String? = when {
            presetId == "gba" -> "gba"
            presetId.startsWith("ds") -> "ds"
            else -> null
        }

        fun forSystem(all: List<GameProfile>, system: String?) =
            if (system == null) all else all.filter { it.system == system }
    }
}

/** Settings for the battle helper; the per-profile data generation is stored under its own key. */
object PokemonSettings {
    private const val GEN_KEY_PREFIX = "pokemon_gen_"

    @Volatile private var cached: List<GameProfile>? = null

    fun profiles(ctx: Context): List<GameProfile> = cached ?: GameProfile.parseAll(
        ctx.assets.open("pokemon/profiles.json").bufferedReader().use { it.readText() }
    ).also { cached = it }

    fun profile(ctx: Context, prefs: SharedPreferences): GameProfile =
        profiles(ctx).let { all -> all.firstOrNull { it.id == prefs[Prefs.POKEMON_PROFILE] } ?: all.first() }

    fun generation(prefs: SharedPreferences, profile: GameProfile): DataGen =
        DataGen.of(prefs.getString(GEN_KEY_PREFIX + profile.id, null)) ?: profile.defaultGen

    fun setGeneration(prefs: SharedPreferences, profile: GameProfile, gen: DataGen) =
        prefs.edit().putString(GEN_KEY_PREFIX + profile.id, gen.key).apply()
}

/** Loads and caches one generation's data from assets (~100-250 KB of JSON each). */
object PokeDataRepository {
    private val cache = mutableMapOf<DataGen, PokeData>()

    @Synchronized
    fun get(ctx: Context, gen: DataGen): PokeData = cache.getOrPut(gen) {
        PokeData.parse(gen, ctx.assets.open("pokemon/${gen.key}.json").bufferedReader().use { it.readText() })
    }
}
