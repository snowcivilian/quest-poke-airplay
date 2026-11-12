package io.github.snowcivilian.questairplay.pokemon

import io.github.snowcivilian.questairplay.video.NormRect
import org.json.JSONArray
import org.json.JSONObject

/** Screen regions of a game's battle UI, as fractions of the cropped game frame (S4). */
data class BattleLayout(
    val oppHpBar: NormRect,
    val playerHpBar: NormRect,
    val oppName: NormRect,
    val playerName: NormRect,
    val textBox: NormRect,
    val fightInfo: NormRect,
    val moveSlots: List<NormRect>,
    val oppSprite: NormRect,
    /** HP box background as RGB when it isn't light (DPPt boxes are dark grey); null = light. */
    val boxColor: IntArray? = null,
    /** Type label of each move slot, read when the move name won't read (DS games show one). */
    val moveTypes: List<NormRect> = emptyList(),
    /** Status badge spots; null = the badge shows inside the name area (Gen 3 replaces the level with it). */
    val oppStatus: NormRect? = null,
    val playerStatus: NormRect? = null,
    /** The battle screen, read whole for names, badges and messages; the whole frame unless given (DS: the top screen). */
    val topScreen: NormRect = NormRect(0f, 0f, 1f, 1f),
    /** DS: the touch screen, where the switch and learn-move screens show. */
    val bottomScreen: NormRect? = null,
) {
    /** For the calibration overlay. */
    fun named(): List<Pair<String, NormRect>> = listOf(
        "opp HP" to oppHpBar, "player HP" to playerHpBar, "opp name" to oppName, "player name" to playerName,
        "text" to textBox, "fight info" to fightInfo, "sprite" to oppSprite,
    ) + moveSlots.mapIndexed { i, r -> "move ${i + 1}" to r } + moveTypes.mapIndexed { i, r -> "type ${i + 1}" to r } +
        listOfNotNull(oppStatus?.let { "opp status" to it }, playerStatus?.let { "player status" to it })

    companion object {
        private fun JSONArray.rect() = NormRect(getDouble(0).toFloat(), getDouble(1).toFloat(), getDouble(2).toFloat(), getDouble(3).toFloat())

        fun parse(o: JSONObject) = BattleLayout(
            o.getJSONArray("oppHpBar").rect(), o.getJSONArray("playerHpBar").rect(),
            o.getJSONArray("oppName").rect(), o.getJSONArray("playerName").rect(),
            o.getJSONArray("textBox").rect(), o.getJSONArray("fightInfo").rect(),
            o.getJSONArray("moveSlots").let { a -> (0 until a.length()).map { a.getJSONArray(it).rect() } },
            o.getJSONArray("oppSprite").rect(),
            o.optJSONArray("boxColor")?.let { a -> IntArray(3) { a.getInt(it) } },
            o.optJSONArray("moveTypes")?.let { a -> (0 until a.length()).map { a.getJSONArray(it).rect() } } ?: emptyList(),
            o.optJSONArray("oppStatus")?.rect(), o.optJSONArray("playerStatus")?.rect(),
            o.optJSONArray("topScreen")?.rect() ?: NormRect(0f, 0f, 1f, 1f), o.optJSONArray("bottomScreen")?.rect(),
        )
    }
}

/** An RGBA frame of the cropped game, top row first. */
class GameFrame(val rgba: ByteArray, val width: Int, val height: Int) {
    fun r(x: Int, y: Int) = rgba[(y * width + x) * 4].toInt() and 0xFF
    fun g(x: Int, y: Int) = rgba[(y * width + x) * 4 + 1].toInt() and 0xFF
    fun b(x: Int, y: Int) = rgba[(y * width + x) * 4 + 2].toInt() and 0xFF

    /** Pixel bounds of a fractional region, clamped to the frame. */
    fun bounds(r: NormRect): IntArray = intArrayOf(
        (r.left * width).toInt().coerceIn(0, width - 1), (r.top * height).toInt().coerceIn(0, height - 1),
        (r.right * width).toInt().coerceIn(1, width), (r.bottom * height).toInt().coerceIn(1, height),
    )
}

/** What one frame shows (S4). */
data class Observation(
    val oppBox: Boolean,
    val playerBox: Boolean,
    /** Number of separate HP bars in the opponent area; 2 means a double battle. */
    val oppBars: Int,
)

/**
 * Battle detection from colour alone, cheap enough for 3 frames a second: both HP areas must show
 * an HP bar (green, yellow or red fill) next to the HP box's background colour. Pure.
 */
object BattleDetector {
    /** HP fill colours (green, yellow, red), tolerant of video compression. */
    fun isHpFill(r: Int, g: Int, b: Int): Boolean {
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        if (max < 140 || max - min < 70) return false
        val hue = hue(r, g, b, max, min)
        return hue in 85f..175f || hue in 38f..70f || hue < 18f || hue > 345f
    }

    /** Text ink for OCR: dark-to-mid grey, not a coloured border or background. */
    fun isInk(r: Int, g: Int, b: Int) =
        (299 * r + 587 * g + 114 * b) / 1000 < 170 && maxOf(r, g, b) - minOf(r, g, b) < 60

    fun isLight(r: Int, g: Int, b: Int) = minOf(r, g, b) > 190 && maxOf(r, g, b) - minOf(r, g, b) < 60

    private fun hue(r: Int, g: Int, b: Int, max: Int, min: Int): Float {
        val d = (max - min).toFloat()
        val h = when (max) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return if (h < 0) h + 360f else h
    }

    /**
     * Horizontal HP-fill runs in a region, merged into separate bars by row. A bar needs a run of
     * at least [minRunFrac] of the region width (a nearly empty bar is still a few pixels long).
     */
    fun hpBars(f: GameFrame, region: NormRect, minRunFrac: Float = 0.03f): Int {
        val (x0, y0, x1, y1) = f.bounds(region).let { listOf(it[0], it[1], it[2], it[3]) }
        val minRun = maxOf(2, ((x1 - x0) * minRunFrac).toInt())
        var bars = 0
        var inBar = false
        var gap = 0
        for (y in y0 until y1) {
            var run = 0; var best = 0
            for (x in x0 until x1) {
                if (isHpFill(f.r(x, y), f.g(x, y), f.b(x, y))) { run++; if (run > best) best = run } else run = 0
            }
            if (best >= minRun) {
                if (!inBar) { bars++; inBar = true }
                gap = 0
            } else if (inBar && ++gap > 2) inBar = false
        }
        return bars
    }

    /** Near a box colour, with room for video compression (sum of channel differences). */
    fun isNear(r: Int, g: Int, b: Int, c: IntArray) =
        kotlin.math.abs(r - c[0]) + kotlin.math.abs(g - c[1]) + kotlin.math.abs(b - c[2]) <= 60

    /**
     * Share of HP box background pixels in a region: light (GBA), or near [boxColor] when the
     * layout gives one (Platinum's dark grey boxes).
     */
    fun boxFraction(f: GameFrame, region: NormRect, boxColor: IntArray? = null): Float {
        val b = f.bounds(region)
        var n = 0; var hit = 0
        for (y in b[1] until b[3]) for (x in b[0] until b[2]) {
            n++
            val r = f.r(x, y); val g = f.g(x, y); val bl = f.b(x, y)
            if (isLight(r, g, bl) || (boxColor != null && isNear(r, g, bl, boxColor))) hit++
        }
        return if (n == 0) 0f else hit.toFloat() / n
    }

    fun observe(f: GameFrame, l: BattleLayout): Observation {
        val oppBars = hpBars(f, l.oppHpBar)
        val playerBars = hpBars(f, l.playerHpBar)
        return Observation(
            oppBox = oppBars > 0 && boxFraction(f, l.oppName, l.boxColor) > 0.3f,
            playerBox = playerBars > 0 && boxFraction(f, l.playerName, l.boxColor) > 0.3f,
            oppBars = oppBars,
        )
    }
}

enum class BattlePhase { IDLE, IN_BATTLE, DOUBLE, OVER }

/**
 * Battle state with debouncing (S4): two battle frames in a row to enter, four non-battle frames
 * (~1.3 s at 3 fps) to leave, so text boxes and attack animations covering an HP box don't end it.
 * Over lasts one sample, then Idle. Pure.
 */
class BattleTracker(private val enterAfter: Int = 2, private val leaveAfter: Int = 4) {
    var phase = BattlePhase.IDLE; private set
    private var hits = 0
    private var misses = 0

    fun update(o: Observation): BattlePhase {
        val battle = o.oppBox || o.playerBox
        val both = o.oppBox && o.playerBox
        when (phase) {
            BattlePhase.IDLE, BattlePhase.OVER -> {
                phase = BattlePhase.IDLE
                hits = if (both) hits + 1 else 0
                if (hits >= enterAfter) { phase = if (o.oppBars >= 2) BattlePhase.DOUBLE else BattlePhase.IN_BATTLE; misses = 0 }
            }
            BattlePhase.IN_BATTLE, BattlePhase.DOUBLE -> {
                if (battle) {
                    misses = 0
                    if (o.oppBars >= 2) phase = BattlePhase.DOUBLE
                } else if (++misses >= leaveAfter) { phase = BattlePhase.OVER; hits = 0 }
            }
        }
        return phase
    }
}
