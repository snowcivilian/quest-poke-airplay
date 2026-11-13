package io.github.snowcivilian.questairplay.pokemon

import kotlin.math.abs
import kotlin.math.hypot

/** A battle name box read off the screen (S4): the name as shown, its species if known, where, and its badge. */
data class BoxRead(val name: String, val species: Species?, val cx: Float, val cy: Float, val status: Status? = null)

/** What a whole-screen read of the battle scene found. */
data class BattleScene(val opp: List<BoxRead>, val mine: List<BoxRead>, val message: String)

/** One of my party on the switch screen. */
data class PartyRead(val name: String, val species: Species?)

/**
 * Reading a whole screen's text lines by where they are (S4), so it works without a tuned spot for
 * every box: in every supported game the opponent's name boxes are on the left and mine on the
 * right, each name box has a level, a status badge sits in its box, and the message box is at the
 * bottom of the top screen. Pure.
 */
object SceneReading {
    /** "Lv32" as OCR reads Platinum's level: Lv as Lu, digits as look-alike letters ("FLuB2" = ♀Lv32). */
    private val LEVEL_TOKEN = Regex("(^|\\s|\\S{0,2})L[VYU]\\.?:?\\s?[0-9ZOSIlB]{1,3}(\\s|$)", RegexOption.IGNORE_CASE)

    fun hasLevel(text: String) = LEVEL_TOKEN.containsMatchIn(text)

    /** The "HP" label by each HP bar, often read with a stray letter ("HPD", "HPI", "MPI"); never a name. */
    private val HP_LABEL = Regex("^[HM][PA][A-Z0-9]?$", RegexOption.IGNORE_CASE)

    fun isHpLabel(text: String) = HP_LABEL.matches(text.trim())

    /** Worth asking "which Pokémon is X?" about: a real-looking name, not a misread label. */
    fun plausibleName(text: String) = text.count { it.isLetter() } >= 3 && !isHpLabel(text)

    /** "What will X do?", as OCR reads it ("hat uill Sceptile do?"). */
    private val CHOOSER = Regex("[wu]?ill\\s+(.+?)\\s+do\\b", RegexOption.IGNORE_CASE)

    /**
     * Which of my Pokémon the game is asking about ("What will X do?"): the box whose name or
     * species is closest to X. Null when the message isn't that question (attacks, effects).
     */
    fun chooser(message: String, mine: List<BoxRead>): BoxRead? {
        val asked = CHOOSER.find(message)?.groupValues?.get(1)?.let(NameMatcher::normalize)?.takeIf { it.length >= 2 } ?: return null
        fun dist(b: BoxRead) = listOfNotNull(b.name, b.species?.name).minOf { NameMatcher.distance(NameMatcher.normalize(it), asked) }
        return mine.minByOrNull(::dist)?.takeIf { dist(it) <= NameMatcher.maxDistance(asked.length) }
    }

    /** Two boxes on either side: a double battle, even when the other side is down to one. */
    fun isDouble(s: BattleScene) = s.opp.size >= 2 || s.mine.size >= 2

    /**
     * Name boxes: lines with a level; the name is on the same line or the nearest line just left
     * of or above it. [messageTop] (0..1 of the same image) is where the message box starts.
     */
    fun battle(lines: List<TextLine>, data: PokeData, nickname: (String) -> String?, messageTop: Float): BattleScene {
        val upper = lines.filter { it.cy < messageTop }
        val boxes = mutableListOf<BoxRead>()
        for (lv in upper.filter { hasLevel(it.text) }) {
            val own = NameReading.candidates(lv.text).firstOrNull()?.takeIf { !isHpLabel(it) }
            // the name is on the level's row, to its left (the HP label just below is closer, but never the name)
            val nameLine = if (own != null) lv else upper
                .filter { it !== lv && !hasLevel(it.text) && !isHpLabel(it.text) && StatusConditions.parse(it.text) == null &&
                    NameReading.candidates(it.text).isNotEmpty() }
                .filter { abs(it.cy - lv.cy) < maxOf(lv.height, it.height) * 0.7f && it.cx < lv.cx }
                .minByOrNull { lv.cx - it.cx } ?: continue
            val name = NameReading.candidates(nameLine.text).firstOrNull() ?: continue
            val species = NameReading.match(data.speciesMatcher, nameLine.text)?.let { data.speciesNamed(it.name) }
                ?: nickname(name)?.let(data::speciesNamed)
            val cx = (lv.cx + nameLine.cx) / 2; val cy = (lv.cy + nameLine.cy) / 2
            if (boxes.any { abs(it.cx - cx) < 0.1f && abs(it.cy - cy) < 0.05f }) continue
            boxes += BoxRead(name, species, cx, cy, StatusConditions.parse(lv.text) ?: StatusConditions.parse(nameLine.text))
        }
        // badges read as their own line go to the nearest box
        val withStatus = boxes.map { b ->
            if (b.status != null) return@map b
            val badge = upper.filter { StatusConditions.parse(it.text) != null }
                .minByOrNull { hypot(it.cx - b.cx, it.cy - b.cy) }
                ?.takeIf { hypot(it.cx - b.cx, it.cy - b.cy) < 0.25f && boxes.minBy { o -> hypot(it.cx - o.cx, it.cy - o.cy) } === b }
            badge?.let { b.copy(status = StatusConditions.parse(it.text)) } ?: b
        }
        val message = lines.filter { it.cy >= messageTop }.sortedWith(compareBy({ it.top }, { it.left }))
            .joinToString(" ") { it.text.trim() }
        return BattleScene(
            opp = withStatus.filter { it.cx < 0.5f }.sortedBy { it.cy },
            mine = withStatus.filter { it.cx >= 0.5f }.sortedBy { it.cy },
            message = message,
        )
    }

    /**
     * A battle has name boxes on both sides with every opponent box above every one of mine (true in
     * every supported game, singles and doubles). A party or summary screen with names in two
     * columns side by side fails this.
     */
    fun looksLikeBattle(s: BattleScene) =
        s.opp.isNotEmpty() && s.mine.isNotEmpty() && s.opp.maxOf { it.cy } < s.mine.minOf { it.cy }

    /**
     * My party on the switch screen: names next to a level, or that are a species or a saved
     * nickname. Two or more means the screen is up; otherwise empty.
     */
    fun party(lines: List<TextLine>, data: PokeData, nickname: (String) -> String?): List<PartyRead> {
        val found = mutableListOf<Pair<TextLine, PartyRead>>()
        for (l in lines) {
            if (StatusConditions.parse(l.text) != null && NameReading.candidates(l.text).isEmpty()) continue
            val name = NameReading.candidates(l.text).firstOrNull() ?: continue
            if (data.moveMatcher.match(name)?.distance == 0) continue
            val species = NameReading.match(data.speciesMatcher, l.text)?.let { data.speciesNamed(it.name) }
                ?: nickname(name)?.let(data::speciesNamed)
            val nearLevel = hasLevel(l.text) || lines.any { o -> o !== l && hasLevel(o.text) &&
                abs(o.cy - l.cy) < l.height * 2.5f && abs(o.cx - l.cx) < 0.35f }
            if (species == null && !nearLevel) continue
            if (found.any { (fl, _) -> abs(fl.cx - l.cx) < 0.05f && abs(fl.cy - l.cy) < 0.03f }) continue
            found += l to PartyRead(name, species)
        }
        return if (found.size >= 2) found.sortedWith(compareBy({ it.first.cy }, { it.first.cx })).map { it.second } else emptyList()
    }

    private val LEARN = listOf(
        Regex("learn(?:ed)?\\s+(?:the\\s+move\\s+)?([A-Za-z][A-Za-z0-9 .'\\-]{1,24}?)\\s*[.!?]", RegexOption.IGNORE_CASE),
        Regex("room\\s+for\\s+([A-Za-z][A-Za-z0-9 .'\\-]{1,24}?)\\s*[.!?]", RegexOption.IGNORE_CASE),
    )

    /** The move in a "wants to learn X" / "make room for X" / "learned X" message, if any. */
    fun learning(message: String, data: PokeData): Move? = LEARN.firstNotNullOfOrNull { r ->
        r.findAll(message).firstNotNullOfOrNull { m -> data.moveMatcher.match(m.groupValues[1].trim())?.let { data.moveNamed(it.name) } }
    }
}

/**
 * One side's name boxes in a double battle, tracked by slot (S4). A box that's gone for
 * [dropAfter] reads in a row has fainted; the other slot keeps its place, so one left on either
 * side works. A new name in a slot is a switch-in. A misread name keeps the slot's last species,
 * and each slot's badge is debounced. Reads where the side shows no boxes at all (animations)
 * don't count as misses. Pure.
 */
class SideSlots(private val dropAfter: Int = 3) {
    private class Slot(var cy: Float, var box: BoxRead) {
        var misses = 0
        val status = StatusDebounce()
    }
    private val slots = mutableListOf<Slot>()

    /** Boxes in this read -> the side's Pokémon now, top slot first, with debounced badges. */
    fun update(boxes: List<BoxRead>): List<BoxRead> {
        if (boxes.isNotEmpty()) {
            val seen = mutableSetOf<Slot>()
            for (b in boxes.sortedBy { it.cy }) {
                val slot = slots.filter { it !in seen && abs(it.cy - b.cy) < 0.08f }.minByOrNull { abs(it.cy - b.cy) }
                if (slot == null) { slots += Slot(b.cy, b).also { it.status.update(b.status); seen += it }; continue }
                val keep = b.species == null && slot.box.species != null
                slot.box = if (keep) b.copy(name = slot.box.name, species = slot.box.species) else b
                slot.cy = b.cy; slot.misses = 0; slot.status.update(b.status); seen += slot
            }
            slots.filter { it !in seen }.forEach { it.misses++ }
            slots.removeAll { it.misses >= dropAfter }
        }
        return current()
    }

    fun current(): List<BoxRead> = slots.sortedBy { it.cy }.map { it.box.copy(status = it.status.current) }

    fun clear() = slots.clear()
}
