package io.github.snowcivilian.questairplay.pokemon

import android.content.SharedPreferences
import org.json.JSONObject

/** Saved nicknames per game profile: on-screen name -> species (S4). Editable in Settings. */
object Nicknames {
    private fun key(profile: String) = "pokemon_nicks_$profile"

    fun all(prefs: SharedPreferences, profile: String): Map<String, String> {
        val o = JSONObject(prefs.getString(key(profile), "{}") ?: "{}")
        return o.keys().asSequence().associateWith { o.getString(it) }
    }

    fun lookup(prefs: SharedPreferences, profile: String, onScreen: String): String? =
        all(prefs, profile)[NameMatcher.normalize(onScreen)]

    fun save(prefs: SharedPreferences, profile: String, onScreen: String, species: String) {
        val m = all(prefs, profile).toMutableMap()
        m[NameMatcher.normalize(onScreen)] = species
        prefs.edit().putString(key(profile), JSONObject(m as Map<*, *>).toString()).apply()
    }

    fun remove(prefs: SharedPreferences, profile: String, normalizedName: String) {
        val m = all(prefs, profile).toMutableMap()
        m.remove(normalizedName)
        prefs.edit().putString(key(profile), JSONObject(m as Map<*, *>).toString()).apply()
    }
}
