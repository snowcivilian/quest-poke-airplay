package io.github.snowcivilian.questairplay.pokemon

/** One side of the battle: what was read off the screen and the species it matched. */
data class Side(val readAs: String, val species: Species?)

/** An ability a Pokémon may have, with what it does in the chosen generation. */
data class AbilityInfo(val name: String, val text: String?, val hidden: Boolean)

/** A party Pokémon on the switch screen, with its matchups against who's on the field. */
data class PartyRow(val name: String, val species: Species?, val matches: List<Matchups.PartyMatch>)

/** A move my Pokémon is trying to learn, against the moves it knows. */
data class LearnInfo(val move: Move, val stab: Boolean, val current: List<Move>, val currentStab: List<Boolean>)

/** The opponent in the second slot of a double battle. */
data class SecondOpponent(val side: Side, val status: Status?, val profile: Map<Float, List<String>>, val statusNotes: List<String>)

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
    val oppStatus: Status? = null,
    val myStatus: Status? = null,
    /** What the statuses change, one line each. */
    val oppStatusNotes: List<String> = emptyList(),
    val myStatusNotes: List<String> = emptyList(),
    /** Abilities my Pokémon may have. */
    val myAbilities: List<AbilityInfo> = emptyList(),
    /** Double battles: the second opponent and my second Pokémon. */
    val opponent2: SecondOpponent? = null,
    val partner: Side? = null,
    /** While the switch screen is up. */
    val party: List<PartyRow> = emptyList(),
    val learning: LearnInfo? = null,
    /** The battle is over (opponent gone), but my side and a move to learn are still worth showing. */
    val afterBattle: Boolean = false,
)

object BattleCards {
    fun build(
        data: PokeData, phase: BattlePhase, opponent: Side?, player: Side?, wild: Boolean?,
        moves: List<Move>, nicknamePrompt: String?, demo: Boolean = false,
        oppStatus: Status? = null, myStatus: Status? = null,
        opponent2: Side? = null, opp2Status: Status? = null, partner: Side? = null,
        party: List<PartyRead> = emptyList(), learning: Move? = null, knownMoves: List<Move> = emptyList(),
        afterBattle: Boolean = false,
    ): BattleCard {
        val opp = opponent?.species
        val mine = player?.species
        val opp2 = opponent2?.species
        fun abilities(s: Species?) = s?.let { sp ->
            sp.abilities.map { AbilityInfo(it, data.abilityText[it], false) } + listOfNotNull(sp.hidden?.let { AbilityInfo(it, data.abilityText[it], true) })
        } ?: emptyList()
        val onField = listOfNotNull(opp, opp2)
        return BattleCard(
            gen = data.gen, phase = phase, opponent = opponent, player = player, wild = wild,
            opponentProfile = opp?.let { data.chart.defensiveProfile(it.types) } ?: emptyMap(),
            summary = if (opp != null && mine != null) Matchups.summary(data.chart, mine.types, opp.types) else null,
            moves = if (opp != null) Matchups.moves(data.chart, moves, opp.types).map {
                val f = StatusConditions.attackFactor(myStatus, it.move.category)
                it.copy(adjusted = it.multiplier * f, vsSecond = opp2?.let { o ->
                    if (it.move.category == "status") 1f else data.chart.multiplier(it.move.type, o.types) * f })
            } else emptyList(),
            nicknamePrompt = nicknamePrompt, demo = demo,
            oppAbilities = abilities(opp),
            myAbilities = abilities(mine),
            oppStatus = oppStatus, myStatus = myStatus,
            oppStatusNotes = oppStatus?.let { StatusConditions.notes(it, data.gen, false, wild, opp?.abilities.orEmpty() + listOfNotNull(opp?.hidden)) }.orEmpty(),
            myStatusNotes = myStatus?.let { StatusConditions.notes(it, data.gen, true, wild, mine?.abilities.orEmpty() + listOfNotNull(mine?.hidden)) }.orEmpty(),
            opponent2 = opponent2?.let { o -> SecondOpponent(o, opp2Status,
                o.species?.let { data.chart.defensiveProfile(it.types) } ?: emptyMap(),
                opp2Status?.let { StatusConditions.notes(it, data.gen, false, wild, o.species?.abilities.orEmpty() + listOfNotNull(o.species?.hidden)) }.orEmpty()) },
            partner = partner,
            party = party.map { p -> PartyRow(p.name, p.species, p.species?.let { me -> onField.map { Matchups.party(data.chart, me.types, it) } }.orEmpty()) },
            learning = learning?.let { m ->
                val types = mine?.types.orEmpty()
                val current = knownMoves.filter { it != m }
                LearnInfo(m, m.category != "status" && m.type in types, current, current.map { it.category != "status" && it.type in types })
            },
            afterBattle = afterBattle,
        )
    }
}

/** Turning OCR text from a name box into lookup candidates (S4). Pure. */
object NameReading {
    /** OCR often runs the level into the name ("NIDORAN♂Lv8"): split before "Lv" + digit. Platinum's Lv glyph often reads "Lu". */
    private val LEVEL_GLUED = Regex("(?<=\\S)(?=L[VYU]\\.?\\d)", RegexOption.IGNORE_CASE)
    private val GENDER_BEFORE_LEVEL = Regex("[♂♀](?=L[VYU]|\\d)", RegexOption.IGNORE_CASE)
    private val LEVEL = Regex("^((L[VYU]|LEVEL)\\.?:?\\d{0,3}|\\d{1,3})$", RegexOption.IGNORE_CASE)
    /** A level with a misread gender mark stuck on the front ("BLu36", "ŠLu36", "LuZ6"). */
    private val GLUED_LEVEL = Regex("^.{0,2}L[VYU]\\.?:?[0-9ZOSIlB]{1,3}$", RegexOption.IGNORE_CASE)

    /**
     * Whole line without level and gender marks first, then single words longest first, then
     * neighbouring pairs ("MR. MIME"). "PIDGEY ♂Lv5" -> ["PIDGEY", ...].
     */
    fun candidates(text: String): List<String> {
        val words = text.replace('\n', ' ').replace(LEVEL_GLUED, " ").split(' ', '\t')
            // a gender mark before the level belongs to the level ("♂Lv5"); after a name it may be part of it (Nidoran♀).
            // Single characters are dropped: no name is one letter, but a misread gender mark is ("BLu26" -> "B").
            .map { it.trim().replace(GENDER_BEFORE_LEVEL, "") }
            .filter { it.length >= 2 && !LEVEL.matches(it) && !GLUED_LEVEL.matches(it) && it != "♂" && it != "♀" && StatusConditions.parse(it) == null }
        if (words.isEmpty()) return emptyList()
        val pairs = words.zipWithNext { a, b -> "$a $b" }
        return (listOf(words.joinToString(" ")) + words.sortedByDescending { it.length } + pairs).distinct()
    }

    /** First candidate that matches. */
    fun match(matcher: NameMatcher, text: String): NameMatcher.Match? =
        candidates(text).firstNotNullOfOrNull { matcher.match(it) }
}
