package io.github.snowcivilian.questairplay.pokemon

/** One side of the battle: what was read off the screen and the species it matched. */
data class Side(val readAs: String, val species: Species?)

/** An ability the opponent may have, with what it does in the chosen generation. */
data class AbilityInfo(val name: String, val text: String?, val hidden: Boolean)

/** Everything the battle card shows (S4). Built by [BattleCards.build]; pure data. */
data class BattleCard(
    val gen: DataGen,
    val phase: BattlePhase,
    val opponent: Side?,
    val player: Side?,
    /** true = wild (catch rate shown), false = trainer, null = not known yet. */
    val wild: Boolean?,
    /** Opponent's weaknesses and resistances: 4x, 2x, 1/2x, 1/4x, 0x. */
    val opponentProfile: Map<Float, List<String>>,
    val summary: Matchups.Summary?,
    /** My moves against the opponent; only while the FIGHT menu is open. */
    val moves: List<Matchups.MoveEffect>,
    /** My Pokémon's on-screen name when it isn't a species and has no saved nickname yet. */
    val nicknamePrompt: String?,
    val note: String? = null,
    val demo: Boolean = false,
    /** Abilities the opponent may have. */
    val oppAbilities: List<AbilityInfo> = emptyList(),
)

object BattleCards {
    fun build(
        data: PokeData, phase: BattlePhase, opponent: Side?, player: Side?, wild: Boolean?,
        moves: List<Move>, nicknamePrompt: String?, demo: Boolean = false,
    ): BattleCard {
        if (phase == BattlePhase.DOUBLE) return BattleCard(data.gen, phase, null, null, null, emptyMap(), null,
            emptyList(), null, note = "Double battles not supported")
        val opp = opponent?.species
        val mine = player?.species
        return BattleCard(
            gen = data.gen, phase = phase, opponent = opponent, player = player, wild = wild,
            opponentProfile = opp?.let { data.chart.defensiveProfile(it.types) } ?: emptyMap(),
            summary = if (opp != null && mine != null) Matchups.summary(data.chart, mine.types, opp.types) else null,
            moves = if (opp != null) Matchups.moves(data.chart, moves, opp.types) else emptyList(),
            nicknamePrompt = nicknamePrompt, demo = demo,
            oppAbilities = opp?.let { s ->
                s.abilities.map { AbilityInfo(it, data.abilityText[it], false) } +
                    listOfNotNull(s.hidden?.let { AbilityInfo(it, data.abilityText[it], true) })
            } ?: emptyList(),
        )
    }
}

/** Turning OCR text from a name box into lookup candidates (S4). Pure. */
object NameReading {
    /** OCR often runs the level into the name ("NIDORAN♂Lv8"): split before "Lv" + digit. Platinum's Lv glyph often reads "Lu". */
    private val LEVEL_GLUED = Regex("(?<=\\S)(?=L[VYU]\\.?\\d)", RegexOption.IGNORE_CASE)
    private val GENDER_BEFORE_LEVEL = Regex("[♂♀](?=L[VYU]|\\d)", RegexOption.IGNORE_CASE)
    private val LEVEL = Regex("^((L[VYU]|LEVEL)\\.?:?\\d{0,3}|\\d{1,3})$", RegexOption.IGNORE_CASE)

    /**
     * Whole line without level and gender marks first, then single words longest first, then
     * neighbouring pairs ("MR. MIME"). "PIDGEY ♂Lv5" -> ["PIDGEY", ...].
     */
    fun candidates(text: String): List<String> {
        val words = text.replace('\n', ' ').replace(LEVEL_GLUED, " ").split(' ', '\t')
            // a gender mark before the level belongs to the level ("♂Lv5"); after a name it may be part of it (Nidoran♀)
            .map { it.trim().replace(GENDER_BEFORE_LEVEL, "") }
            .filter { it.isNotEmpty() && !LEVEL.matches(it) && it != "♂" && it != "♀" }
        if (words.isEmpty()) return emptyList()
        val pairs = words.zipWithNext { a, b -> "$a $b" }
        return (listOf(words.joinToString(" ")) + words.sortedByDescending { it.length } + pairs).distinct()
    }

    /** First candidate that matches. */
    fun match(matcher: NameMatcher, text: String): NameMatcher.Match? =
        candidates(text).firstNotNullOfOrNull { matcher.match(it) }
}
