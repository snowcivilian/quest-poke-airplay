package io.github.snowcivilian.questairplay.pokemon

import kotlin.math.max
import kotlin.math.min

/**
 * Fuzzy matching of text read off the screen (OCR or font matching) against the species or move
 * names of the chosen data generation (S4). Pure.
 */
class NameMatcher(names: Collection<String>) {
    private val entries = names.map { it to normalize(it) }

    data class Match(val name: String, val distance: Int)

    /** Best match, or null if nothing is close enough. Allows about one error per four letters. */
    fun match(read: String): Match? {
        val q = normalize(read)
        if (q.isEmpty()) return null
        var best: Match? = null
        for ((name, n) in entries) {
            if (kotlin.math.abs(n.length - q.length) > maxDistance(max(n.length, q.length))) continue
            val d = distance(q, n)
            if (best == null || d < best.distance) best = Match(name, d)
            if (d == 0) break
        }
        return best?.takeIf { it.distance <= maxDistance(max(q.length, normalize(it.name).length)) }
    }

    companion object {
        fun maxDistance(len: Int) = max(1, len / 4)

        /**
         * Uppercase, gender symbols to letters, common OCR digit confusions to letters, drop
         * everything else. "Nidoran♀" == "NIDORAN F", "MR. MIME" == "Mr. Mime", "FARFETCH'D" == "Farfetch’d".
         */
        fun normalize(s: String): String = buildString {
            for (c in s.uppercase()) when (c) {
                '♀' -> append('F')
                '♂' -> append('M')
                '0' -> append('O')
                '1' -> append('I')
                '5' -> append('S')
                '8' -> append('B')
                in 'A'..'Z' -> append(c)
                'É' -> append('E')
                else -> {}
            }
        }

        /** Levenshtein distance. */
        fun distance(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                }
                val t = prev; prev = cur; cur = t
            }
            return prev[b.length]
        }
    }
}
