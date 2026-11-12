package io.github.snowcivilian.questairplay.pokemon

/** A major status condition, as its badge reads in the HP box (S4). */
enum class Status(val badge: String, val label: String) {
    PSN("PSN", "Poisoned"), PAR("PAR", "Paralyzed"), SLP("SLP", "Asleep"), BRN("BRN", "Burned"), FRZ("FRZ", "Frozen");
}

/**
 * Status badges and what they change (S4). Pure. The rules here are the main-series ones for the
 * chosen data generation; abilities that change them (Guts, Quick Feet, Marvel Scale) are noted,
 * not assumed, because the opponent's ability can't be read.
 */
object StatusConditions {
    /** Badge text as OCR tends to misread it. */
    private val READS = mapOf(
        Status.PSN to listOf("PSN", "P5N", "PSM"),
        Status.PAR to listOf("PAR", "PAP"),
        Status.SLP to listOf("SLP", "5LP", "SIP"),
        Status.BRN to listOf("BRN", "BRM", "8RN"),
        Status.FRZ to listOf("FRZ", "FR2", "FRE"),
    )

    /** The status badge in text read off an HP box, or null. Matches whole words only ("SPARK" isn't PAR). */
    fun parse(text: String): Status? {
        val words = text.uppercase().split(Regex("[^A-Z0-9]+")).filter { it.isNotEmpty() }
        return READS.entries.firstOrNull { (_, reads) -> words.any { it in reads } }?.key
    }

    /** Generation number for rules; Latest uses current rules. */
    fun genNumber(gen: DataGen) = when (gen) { DataGen.GEN3 -> 3; DataGen.GEN4 -> 4; DataGen.GEN5 -> 5; DataGen.LATEST -> 9 }

    /** Damage factor on a move used by a Pokémon with [status]: burn halves physical damage. */
    fun attackFactor(status: Status?, category: String): Float =
        if (status == Status.BRN && category == "physical") 0.5f else 1f

    /** Catch-rate bonus for a wild Pokémon with [status]. */
    fun catchBonus(status: Status, gen: DataGen): Float = when (status) {
        Status.SLP, Status.FRZ -> if (genNumber(gen) >= 5) 2.5f else 2f
        else -> 1.5f
    }

    /**
     * One-line notes on what [status] does. [mine] = it's my Pokémon (wording only); [abilities] are
     * the abilities it may have, for the ones that turn a status around.
     */
    fun notes(status: Status, gen: DataGen, mine: Boolean, wild: Boolean?, abilities: List<String>): List<String> {
        val g = genNumber(gen)
        val its = if (mine) "Your" else "Its"
        val out = mutableListOf<String>()
        when (status) {
            Status.BRN -> {
                out += "$its physical moves do ½ damage"
                out += "Loses ${if (g >= 7) "1/16" else "1/8"} of max HP each turn"
                if ("Guts" in abilities) out += if (mine) "Guts: no damage cut, Attack ×1.5 instead" else "If it has Guts: no damage cut, Attack ×1.5"
            }
            Status.PAR -> {
                out += "Speed ${if (g >= 7) "×½" else "×¼"}; 25% chance it can't move each turn"
                if ("Quick Feet" in abilities) out += if (mine) "Quick Feet: Speed ×1.5 instead" else "If it has Quick Feet: Speed ×1.5 instead"
            }
            Status.PSN -> out += "Loses 1/8 of max HP each turn (more each turn if badly poisoned)"
            Status.SLP -> out += "Can't move until it wakes up"
            Status.FRZ -> out += "Can't move; 20% chance to thaw each turn, or when hit by a Fire move"
        }
        if (!mine && wild == true) out += "Catch rate ×${catchBonus(status, gen).let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }}"
        return out
    }
}

/** A status read must repeat [needed] times before it's believed, so one bad read or animation can't flip it. Pure. */
class StatusDebounce(private val needed: Int = 2) {
    var current: Status? = null; private set
    private var pending: Status? = null
    private var count = 0

    fun update(read: Status?): Status? {
        if (read == current) { count = 0; return current }
        if (read == pending) count++ else { pending = read; count = 1 }
        if (count >= needed) { current = read; count = 0 }
        return current
    }
}
