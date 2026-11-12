package io.github.snowcivilian.questairplay.pokemon

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.get
import io.github.jqssun.airplay.observe
import io.github.snowcivilian.questairplay.video.GameFrameSink
import io.github.snowcivilian.questairplay.video.GameFrameTap
import io.github.snowcivilian.questairplay.video.NormRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Pokémon battle helper (S4). Takes ~3 frames a second from the renderer while the helper or
 * calibration mode is on, and does all detection and text reading on its own worker thread.
 * With both off it unregisters from the renderer, so nothing runs.
 */
object PokemonEngine : GameFrameSink {
    private const val TAG = "PokemonHelper"
    private const val FRAME_INTERVAL_NS = 330_000_000L
    private const val CAPTURE_INTERVAL_MS = 2_000L
    private const val MAX_CAPTURES = 400
    private const val NAMES_EVERY_MS = 2_500L
    private const val MOVES_EVERY_MS = 3_000L

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "PokemonHelper").apply { priority = Thread.MIN_PRIORITY } }
    private val busy = AtomicBoolean(false)
    @Volatile private var enabled = false
    @Volatile private var calibrating = false
    @Volatile private var lastFrameNs = 0L
    private var lastCaptureMs = 0L
    private var reader: TextReader? = null
    private val tracker = BattleTracker()
    private var lastObs: Observation? = null
    private var session: Session? = null

    private val _card = MutableStateFlow<BattleCard?>(null)
    val card: StateFlow<BattleCard?> = _card.asStateFlow()
    private val _sprite = MutableStateFlow<Bitmap?>(null)
    val sprite: StateFlow<Bitmap?> = _sprite.asStateFlow()
    private val _status = MutableStateFlow("Off")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _ocrTest = MutableStateFlow<String?>(null)
    val ocrTest: StateFlow<String?> = _ocrTest.asStateFlow()

    private class Session {
        var opp: Side? = null
        var player: Side? = null
        var wild: Boolean? = null
        var textChecks = 0
        var namesAt = 0L
        var movesAt = 0L
        var fight = false
        var fightChecks = 0
        /** Last good read per move slot; one bad OCR read doesn't wipe it. */
        val slots = arrayOfNulls<Move>(4)
        val moves get() = slots.filterNotNull()
        var slotsFor: String? = null
        var prompt: String? = null
        var spriteFor: String? = null
    }

    fun init(ctx: Context, prefs: SharedPreferences, scope: CoroutineScope) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        this.prefs = prefs
        scope.launch {
            prefs.observe { this[Prefs.POKEMON_HELPER] to this[Prefs.POKEMON_CALIBRATION] }.collect { (on, cal) ->
                enabled = on; calibrating = cal
                GameFrameTap.shared.sink = if (on || cal) this@PokemonEngine else null
                if (!on) worker.execute { session = null; if (_card.value?.demo != true) _card.value = null; _sprite.value = null }
                _status.value = when { on -> "Waiting for a battle"; cal -> "Calibration only"; else -> "Off" }
                Log.i(TAG, "helper=$on calibration=$cal")
                if (on && _ocrTest.value == null) runOcrSelfTest()
            }
        }
    }

    override fun wantsFrame(nowNs: Long) =
        (enabled || calibrating) && !busy.get() && nowNs - lastFrameNs >= FRAME_INTERVAL_NS

    override fun onFrame(rgba: ByteArray, width: Int, height: Int) {
        lastFrameNs = System.nanoTime()
        busy.set(true)
        worker.execute {
            try { process(GameFrame(rgba, width, height)) }
            catch (t: Throwable) { Log.w(TAG, "frame failed", t) }
            finally { busy.set(false) }
        }
    }

    /** Checks on-device OCR with rendered game-like text; result shown in Settings and the log. */
    fun runOcrSelfTest() {
        _ocrTest.value = "Testing…"
        worker.execute {
            val r = runCatching { OcrSelfTest.run(reader()) }.getOrElse { "failed: ${it.javaClass.simpleName}: ${it.message}" }
            Log.i(TAG, "OCR self-test: $r")
            _ocrTest.value = r
        }
    }

    /** A sample card so the layout can be seen without a battle; clears after 30 s. */
    fun showDemo(ctx: Context) {
        val p = ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        worker.execute {
            val profile = PokemonSettings.profile(ctx, p)
            val data = PokeDataRepository.get(ctx, PokemonSettings.generation(p, profile))
            val moves = listOf("Scratch", "Growl", "Ember", "Smokescreen").mapNotNull(data::moveNamed)
            _sprite.value = null
            _card.value = BattleCards.build(data, BattlePhase.IN_BATTLE,
                Side("PIDGEY", data.speciesNamed("Pidgey")), Side("CHARMANDER", data.speciesNamed("Charmander")),
                wild = true, moves = moves, nicknamePrompt = null, demo = true)
            worker.schedule({ if (_card.value?.demo == true) _card.value = null }, 30, TimeUnit.SECONDS)
        }
    }

    fun dismissDemo() { if (_card.value?.demo == true) _card.value = null }

    /** Answer to "which Pokémon is NICK?": saved per game, applied right away. */
    fun saveNickname(onScreen: String, species: String) {
        worker.execute {
            val profile = PokemonSettings.profile(app, prefs)
            Nicknames.save(prefs, profile.id, onScreen, species)
            session?.let { s ->
                val data = PokeDataRepository.get(app, PokemonSettings.generation(prefs, profile))
                s.player = Side(onScreen, data.speciesNamed(species)); s.prompt = null
                publish(data, s, BattlePhase.IN_BATTLE)
            }
        }
    }

    private fun reader(): TextReader = reader ?: MlKitTextReader().also { reader = it }

    private fun process(f: GameFrame) {
        if (calibrating) saveCapture(f)
        if (!enabled) return
        val profile = PokemonSettings.profile(app, prefs)
        val layout = profile.layout ?: run { _status.value = "No screen layout for ${profile.label} yet"; return }
        val data = PokeDataRepository.get(app, PokemonSettings.generation(prefs, profile))
        val obs = BattleDetector.observe(f, layout)
        if (obs != lastObs) { lastObs = obs; Log.d(TAG, "seen: $obs (${profile.id})") }
        val phase = tracker.update(obs)
        when (phase) {
            BattlePhase.IDLE -> _status.value = "Waiting for a battle"
            BattlePhase.OVER -> {
                Log.i(TAG, "battle over")
                session = null; _sprite.value = null
                if (_card.value?.demo != true) _card.value = null
            }
            BattlePhase.DOUBLE -> {
                _status.value = "Double battle"
                _card.value = BattleCards.build(data, phase, null, null, null, emptyList(), null)
            }
            BattlePhase.IN_BATTLE -> {
                val s = session ?: Session().also { session = it; Log.i(TAG, "battle started") }
                read(f, layout, data, profile, s)
                publish(data, s, phase)
            }
        }
    }

    private fun read(f: GameFrame, l: BattleLayout, data: PokeData, profile: GameProfile, s: Session) {
        val now = System.currentTimeMillis()
        if (s.opp?.species == null || s.player == null || now - s.namesAt > NAMES_EVERY_MS) {
            s.namesAt = now
            val oppText = ocr(f, l.oppName)
            NameReading.match(data.speciesMatcher, oppText)?.let { m ->
                if (s.opp?.species?.name != m.name) s.opp = Side(oppText.lineSequence().first(), data.speciesNamed(m.name))
            } ?: run { if (s.opp == null && oppText.isNotBlank()) s.opp = Side(oppText.lineSequence().first(), null) }
            val playerText = ocr(f, l.playerName)
            val playerName = NameReading.candidates(playerText).firstOrNull()
            if (playerName != null) {
                val match = NameReading.match(data.speciesMatcher, playerText)
                val nick = Nicknames.lookup(prefs, profile.id, playerName)
                s.player = when {
                    match != null -> Side(playerName, data.speciesNamed(match.name))
                    nick != null -> Side(playerName, data.speciesNamed(nick))
                    else -> Side(playerName, null)
                }
                s.prompt = if (s.player?.species == null) playerName else null
            }
            Log.d(TAG, "names: opp=\"$oppText\"->${s.opp?.species?.name} player=\"$playerText\"->${s.player?.species?.name}")
        }
        if (s.wild == null && s.textChecks < 8) {
            s.textChecks++
            val t = ocr(f, l.textBox).uppercase()
            if ("WILD" in t) s.wild = true
            else if ("WOULD LIKE TO BATTLE" in t || "WANTS TO BATTLE" in t || "SENT OUT" in t || "CHALLENGED" in t) s.wild = false
        }
        // FIGHT menu: the move info box shows "PP" and "TYPE"; checked every other frame
        if (++s.fightChecks % 2 == 0) {
            val info = ocr(f, l.fightInfo).uppercase()
            s.fight = "PP" in info || "TYPE" in info
            // a different Pokémon of mine has different moves
            val mine = s.player?.let { it.species?.name ?: it.readAs }
            if (mine != s.slotsFor) { s.slotsFor = mine; s.slots.fill(null) }
            if (s.fight && (s.slots.any { it == null } || now - s.movesAt > MOVES_EVERY_MS)) {
                s.movesAt = now
                val raw = l.moveSlots.take(4).mapIndexed { i, r ->
                    val text = ocr(f, r, ink = true)
                    NameReading.match(data.moveMatcher, text)?.let { data.moveNamed(it.name) }?.let { s.slots[i] = it }
                    text.replace('\n', ' ')
                }
                Log.d(TAG, "moves: read=$raw -> ${s.slots.map { it?.name }}")
            }
        }
        val oppName = s.opp?.species?.name
        if (oppName != null && s.spriteFor != oppName) {
            s.spriteFor = oppName
            _sprite.value = crop(f, l.oppSprite, 1)
        }
    }

    private fun publish(data: PokeData, s: Session, phase: BattlePhase) {
        _status.value = "In battle" + (s.opp?.species?.let { ": ${it.name}" } ?: "")
        _card.value = BattleCards.build(data, phase, s.opp, s.player, s.wild,
            if (s.fight) s.moves else emptyList(), s.prompt)
    }

    /**
     * OCR a region: crop, scale 2x without smoothing, read. [ink] turns it into black text on white
     * first (Platinum's outlined move font reads far better that way).
     */
    private fun ocr(f: GameFrame, r: NormRect, ink: Boolean = false): String =
        runCatching { reader().read(crop(f, r, 2, ink)) }.onFailure { Log.w(TAG, "OCR failed", it) }.getOrDefault("")

    private fun crop(f: GameFrame, r: NormRect, scale: Int, ink: Boolean = false): Bitmap {
        val b = f.bounds(r)
        val w = (b[2] - b[0]).coerceAtLeast(1); val h = (b[3] - b[1]).coerceAtLeast(1)
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val sx = b[0] + x; val sy = b[1] + y
            val red = f.r(sx, sy); val g = f.g(sx, sy); val bl = f.b(sx, sy)
            px[y * w + x] = if (ink) (if (BattleDetector.isInk(red, g, bl)) 0xFF000000.toInt() else -1)
                else (0xFF shl 24) or (red shl 16) or (g shl 8) or bl
        }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        return if (scale == 1) bmp else Bitmap.createScaledBitmap(bmp, w * scale, h * scale, false)
    }

    /** Calibration: a PNG of the cropped game every 2 s, newest 400 kept. Pull with adb. */
    private fun saveCapture(f: GameFrame) {
        val now = System.currentTimeMillis()
        if (now - lastCaptureMs < CAPTURE_INTERVAL_MS) return
        lastCaptureMs = now
        val dir = capturesDir(app, PokemonSettings.profile(app, prefs).id)
        val bmp = Bitmap.createBitmap(f.width, f.height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(f.rgba))
        val name = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date(now)) + ".png"
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        dir.listFiles()?.sortedBy { it.name }?.dropLast(MAX_CAPTURES)?.forEach { it.delete() }
    }

    fun capturesDir(ctx: Context, profile: String): File =
        File(ctx.getExternalFilesDir("captures"), profile).apply { mkdirs() }
}
