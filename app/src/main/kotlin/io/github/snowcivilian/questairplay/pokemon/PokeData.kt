package io.github.snowcivilian.questairplay.pokemon

import org.json.JSONArray
import org.json.JSONObject

/** Data generation choice (S4): decides type chart, types, move categories and the name lists. */
enum class DataGen(val key: String, val label: String) {
    GEN3("gen3", "Gen 3"), GEN4("gen4", "Gen 4"), GEN5("gen5", "Gen 5"), LATEST("latest", "Latest");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

data class Species(
    val id: Int,
    val name: String,
    val types: List<String>,
    /** HP, Attack, Defense, Sp. Atk, Sp. Def, Speed. */
    val stats: List<Int>,
    val abilities: List<String>,
    val hidden: String?,
    val catchRate: Int,
    val evolutionLine: List<String>,
)

data class Move(val name: String, val type: String, val category: String, val power: Int?)

/** One generation's bundled data, built by tools/pokedata/build_pokedata.py from PokeAPI. */
class PokeData(
    val gen: DataGen,
    val chart: TypeChart,
    val species: List<Species>,
    val moves: List<Move>,
) {
    private val speciesByName = species.associateBy { NameMatcher.normalize(it.name) }
    private val movesByName = moves.associateBy { NameMatcher.normalize(it.name) }

    fun speciesNamed(name: String): Species? = speciesByName[NameMatcher.normalize(name)]
    fun moveNamed(name: String): Move? = movesByName[NameMatcher.normalize(name)]

    val speciesMatcher by lazy { NameMatcher(species.map { it.name }) }
    val moveMatcher by lazy { NameMatcher(moves.map { it.name }) }

    companion object {
        fun parse(gen: DataGen, json: String): PokeData {
            val o = JSONObject(json)
            val types = o.getJSONArray("types").strings()
            val rows = o.getJSONArray("chart")
            val chart = Array(rows.length()) { i ->
                val r = rows.getJSONArray(i); FloatArray(r.length()) { j -> r.getDouble(j).toFloat() }
            }
            val sp = o.getJSONArray("species").let { a ->
                (0 until a.length()).map { i ->
                    val s = a.getJSONObject(i)
                    Species(
                        s.getInt("id"), s.getString("name"), s.getJSONArray("types").strings(),
                        s.getJSONArray("stats").let { st -> (0 until st.length()).map(st::getInt) },
                        s.getJSONArray("abilities").strings(), s.optString("hidden").ifEmpty { null },
                        s.getInt("catchRate"), s.getJSONArray("evo").strings(),
                    )
                }
            }
            val mv = o.getJSONArray("moves").let { a ->
                (0 until a.length()).map { i ->
                    val m = a.getJSONObject(i)
                    Move(m.getString("name"), m.getString("type"), m.getString("category"),
                        if (m.isNull("power")) null else m.getInt("power"))
                }
            }
            return PokeData(gen, TypeChart(types, chart), sp, mv)
        }

        private fun JSONArray.strings() = (0 until length()).map(::getString)
    }
}
