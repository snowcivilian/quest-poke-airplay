package io.github.snowcivilian.questairplay.pokemon

/** Type effectiveness for one data generation (S4). Pure. */
class TypeChart(val types: List<String>, private val chart: Array<FloatArray>) {
    private val index = types.withIndex().associate { (i, t) -> t.lowercase() to i }

    fun has(type: String) = type.lowercase() in index

    /** Single attacking type against one defending type; unknown types count as neutral. */
    fun factor(attack: String, defend: String): Float {
        val a = index[attack.lowercase()] ?: return 1f
        val d = index[defend.lowercase()] ?: return 1f
        return chart[a][d]
    }

    /** Attacking type against a (one- or two-typed) defender. */
    fun multiplier(attack: String, defenders: List<String>): Float =
        defenders.fold(1f) { m, d -> m * factor(attack, d) }

    /** How every attacking type fares against [defenders], grouped 4x, 2x, 1/2x, 1/4x, 0x (neutral left out). */
    fun defensiveProfile(defenders: List<String>): Map<Float, List<String>> =
        types.map { it to multiplier(it, defenders) }
            .filter { it.second != 1f }
            .groupBy({ it.second }, { it.first })
            .toSortedMap(compareByDescending { it })
}

/** What the battle card shows about types (S4). Types only: the opponent's moves aren't visible. */
object Matchups {
    data class Summary(
        /** My types that hit the opponent for 2x or more, best first. */
        val myStrongTypes: List<Pair<String, Float>>,
        /** Opponent types that hit me for 2x or more, worst first. */
        val theirThreats: List<Pair<String, Float>>,
    )

    fun summary(chart: TypeChart, mine: List<String>, theirs: List<String>) = Summary(
        mine.map { it to chart.multiplier(it, theirs) }.filter { it.second >= 2f }.sortedByDescending { it.second },
        theirs.map { it to chart.multiplier(it, mine) }.filter { it.second >= 2f }.sortedByDescending { it.second },
    )

    data class MoveEffect(val move: Move, val multiplier: Float)

    /** Each of my moves against the opponent; status moves get 1x. */
    fun moves(chart: TypeChart, moves: List<Move>, theirs: List<String>) =
        moves.map { MoveEffect(it, if (it.category == "status") 1f else chart.multiplier(it.type, theirs)) }
}
