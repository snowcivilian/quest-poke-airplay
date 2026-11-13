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
    private const val MAX_CAPTURES = 600
    private const val NAMES_EVERY_MS = 2_500L
    private const val MOVES_EVERY_MS = 3_000L
    private const val STATUS_EVERY_MS = 1_000L
    /** Whole-screen reads: every second in a battle; between battles, often enough to catch ones the colour check misses. */
    private const val SCENE_EVERY_MS = 1_000L
    private const val IDLE_SCENE_EVERY_MS = 1_500L
    /** After a battle my side stays up this long, longer while a move is being learned. */
    private const val AFTER_BATTLE_MS = 20_000L
    private const val LEARN_SHOW_MS = 15_000L
    private const val RECORD_INTERVAL_MS = 1_000L

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "PokemonHelper").apply { priority = Thread.MIN_PRIORITY } }
    private val busy = AtomicBoolean(false)
    @Volatile private var enabled = false
    @Volatile private var calibrating = false
    @Volatile private var recording = false
    private var lastScene: BattleScene? = null
    private var lastSceneAt = 0L
    private var lastSceneLog = ""
    private var lastRecordMs = 0L
    private var lastRecordThumb: IntArray? = null
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
        /** Move names read per slot, counted; the most-read one is shown, so one bad read can't win. */
        val votes = Array(4) { mutableMapOf<Move, Int>() }
        /** Type-label fallback per slot, for names that don't read at all. */
        val typeOnly = arrayOfNulls<Move>(4)
        val slots get() = votes.mapIndexed { i, v -> v.maxByOrNull { it.value }?.key ?: typeOnly[i] }
        val moves get() = slots.filterNotNull()
        var slotsFor: String? = null
        var prompt: String? = null
        var spriteFor: String? = null
        val oppStatus = StatusDebounce()
        val myStatus = StatusDebounce()
        var statusAt = 0L
        // double battles: the second opponent and my second Pokémon
        var double = false
        var opp2: Side? = null
        var partner: Side? = null
        /** Doubles: each side's boxes by slot, so one fainting on either side leaves the other in place. */
        val oppSlots = SideSlots()
        val mySlots = SideSlots()
        var oppSlotStatus: List<Status?> = emptyList()
        var mySlotStatus: Status? = null
        // switch screen
        var bottomAt = 0L
        var party: List<PartyRead> = emptyList()
        var partyMisses = 0
        // a move being learned, and the moves it would replace (read off the learn screen)
        var learning: Move? = null
        var learningAt = 0L
        var learnKnown: List<Move> = emptyList()
        /** Set when the battle ends: my side stays on the card until then. */
        var afterUntil = 0L
    }

    fun init(ctx: Context, prefs: SharedPreferences, scope: CoroutineScope) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        this.prefs = prefs
        scope.launch {
            prefs.observe { this[Prefs.POKEMON_RECORD] }.collect { recording = it }
        }
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
            runCatching { sceneSelfTest() }.onFailure { Log.w(TAG, "scene self-test failed", it) }
        }
    }

    /** Reads the newest saved frame for this game the way a battle is read, and logs what it found. */
    private fun sceneSelfTest() {
        val profile = PokemonSettings.profile(app, prefs)
        val layout = profile.layout ?: return
        val file = capturesDir(app, profile.id).listFiles { f -> f.name.endsWith(".png") }?.maxByOrNull { it.name.removePrefix("battle-") } ?: return
        val bmp = android.graphics.BitmapFactory.decodeFile(file.path) ?: return
        val buf = ByteBuffer.allocate(bmp.width * bmp.height * 4)
        bmp.copy(Bitmap.Config.ARGB_8888, false).copyPixelsToBuffer(buf)
        val f = GameFrame(buf.array(), bmp.width, bmp.height)
        val data = PokeDataRepository.get(app, PokemonSettings.generation(prefs, profile))
        val t0 = System.nanoTime()
        val scene = readScene(f, layout, data, profile)
        val ms = (System.nanoTime() - t0) / 1_000_000
        val party = layout.bottomScreen?.let { SceneReading.party(readLines(f, it), data) { n -> Nicknames.lookup(prefs, profile.id, n) } }
        readLines(f, layout.topScreen).forEach { Log.d(TAG, "  line \"${it.text}\" x=%.2f-%.2f y=%.2f-%.2f".format(it.left, it.right, it.top, it.bottom)) }
        Log.i(TAG, "scene self-test on ${file.name} (${ms} ms): battle=${SceneReading.looksLikeBattle(scene)} " +
            "opp=${scene.opp.map { "${it.name}/${it.species?.name}/${it.status}" }} mine=${scene.mine.map { "${it.name}/${it.species?.name}/${it.status}" }} " +
            "msg=\"${scene.message}\" party=${party?.map { it.name }}")
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
                wild = true, moves = moves, nicknamePrompt = null, demo = true,
                oppStatus = Status.PAR, myStatus = Status.BRN,
                party = listOf("Squirtle", "Pikachu", "Geodude").map { PartyRead(it, data.speciesNamed(it)) },
                learning = data.moveNamed("Metal Claw"), knownMoves = moves)
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
        val now = System.currentTimeMillis()
        if (recording && session != null) recordIfChanged(f, now)
        // whole-screen read: names by position, badges, the message box (and doubles the colour check can miss)
        if (now - lastSceneAt > if (session != null) SCENE_EVERY_MS else IDLE_SCENE_EVERY_MS) {
            lastSceneAt = now
            lastScene = readScene(f, layout, data, profile)
        }
        val scene = lastScene?.takeIf { now - lastSceneAt < 2_000 }
        val colour = BattleDetector.observe(f, layout)
        val obs = if (scene != null && SceneReading.looksLikeBattle(scene))
            colour.copy(oppBox = true, playerBox = true, oppBars = if (SceneReading.isDouble(scene)) maxOf(colour.oppBars, 2) else colour.oppBars) else colour
        if (obs != lastObs) { lastObs = obs; Log.d(TAG, "seen: $obs colour=$colour (${profile.id})") }
        val phase = tracker.update(obs)
        val s0 = session
        when (phase) {
            BattlePhase.IDLE -> if (s0 != null && s0.afterUntil > now) {
                afterBattle(f, layout, data, s0, scene, now)
            } else {
                if (s0 != null) { Log.i(TAG, "card cleared"); endSession() }
                _status.value = "Waiting for a battle"
            }
            BattlePhase.OVER -> s0?.let {
                Log.i(TAG, "battle over; keeping my side")
                it.afterUntil = now + AFTER_BATTLE_MS
                afterBattle(f, layout, data, it, scene, now)
            }
            BattlePhase.IN_BATTLE, BattlePhase.DOUBLE -> {
                val s = s0?.takeIf { it.afterUntil == 0L } ?: Session().also { n ->
                    // a new battle: my Pokémon and its moves carry over, nothing about the opponent does
                    s0?.let { old -> n.player = old.player; n.slotsFor = old.slotsFor; old.votes.forEachIndexed { i, v -> n.votes[i].putAll(v) } }
                    session = n; Log.i(TAG, "battle started")
                }
                if (phase == BattlePhase.DOUBLE && !s.double) { s.double = true; Log.i(TAG, "double battle") }
                read(f, layout, data, profile, s, scene, now)
                publish(data, s, phase, now)
            }
        }
    }

    private fun endSession() {
        session = null; _sprite.value = null
        if (_card.value?.demo != true) _card.value = null
    }

    /** Battle over: keep my Pokémon on the card, and watch for "wants to learn X". */
    private fun afterBattle(f: GameFrame, l: BattleLayout, data: PokeData, s: Session, scene: BattleScene?, now: Long) {
        scene?.let { learnFrom(it, data, s, now) }
        if (s.learning != null && now - s.learningAt < LEARN_SHOW_MS) {
            s.afterUntil = maxOf(s.afterUntil, s.learningAt + LEARN_SHOW_MS)
            readLearnScreen(f, l, data, s, now)
        }
        _status.value = "Battle over"
        publish(data, s, BattlePhase.IN_BATTLE, now, after = true)
    }

    private fun learnFrom(scene: BattleScene, data: PokeData, s: Session, now: Long) {
        SceneReading.learning(scene.message, data)?.let {
            if (s.learning != it) Log.i(TAG, "learning ${it.name}")
            s.learning = it; s.learningAt = now
        }
    }

    /** The learn screen lists the moves it knows: read them off the bottom screen (or the whole screen). */
    private fun readLearnScreen(f: GameFrame, l: BattleLayout, data: PokeData, s: Session, now: Long) {
        if (now - s.bottomAt < SCENE_EVERY_MS) return
        s.bottomAt = now
        val lines = readLines(f, l.bottomScreen ?: l.topScreen)
        val known = lines.mapNotNull { NameReading.match(data.moveMatcher, it.text)?.let { m -> data.moveNamed(m.name) } }
            .distinct().filter { it != s.learning }
        if (known.size >= 2) s.learnKnown = known.take(4)
    }

    private fun read(f: GameFrame, l: BattleLayout, data: PokeData, profile: GameProfile, s: Session, scene: BattleScene?, now: Long) {
        scene?.let { sc ->
            learnFrom(sc, data, s, now)
            if (s.wild == null) wildFrom(sc.message)?.let { s.wild = it }
            if (s.double) applyDouble(sc, data, profile, s)
        }
        if (!s.double && (s.opp?.species == null || s.player == null || now - s.namesAt > NAMES_EVERY_MS)) {
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
                s.prompt = if (s.player?.species == null && SceneReading.plausibleName(playerName)) playerName else null
            }
            // the whole-screen read covers a fixed spot that read junk (or a layout that isn't quite singles)
            scene?.opp?.singleOrNull()?.takeIf { it.species != null && s.opp?.species == null }?.let { s.opp = Side(it.name, it.species) }
            scene?.mine?.singleOrNull()?.takeIf { it.species != null && s.player?.species == null }?.let { s.player = Side(it.name, it.species); s.prompt = null }
            Log.d(TAG, "names: opp=\"$oppText\"->${s.opp?.species?.name} player=\"$playerText\"->${s.player?.species?.name}")
        }
        if (s.wild == null && s.textChecks < 8) {
            s.textChecks++
            wildFrom(ocr(f, l.textBox))?.let { s.wild = it }
        }
        // FIGHT menu: the move info box shows "PP" and "TYPE"; checked every other frame
        if (++s.fightChecks % 2 == 0) {
            val info = ocr(f, l.fightInfo).uppercase()
            s.fight = "PP" in info || "TYPE" in info
            // a different Pokémon of mine has different moves
            val mine = s.player?.let { it.species?.name ?: it.readAs }
            if (mine != s.slotsFor) { s.slotsFor = mine; s.votes.forEach { it.clear() }; s.typeOnly.fill(null) }
            if (s.fight && (s.votes.any { it.isEmpty() } || now - s.movesAt > MOVES_EVERY_MS)) {
                s.movesAt = now
                val raw = l.moveSlots.take(4).mapIndexed { i, r ->
                    // pixel fonts read differently at different sizes; first version that names a real move wins
                    var tried = ""
                    for ((label, scale, ink) in MOVE_READS) {
                        val text = ocr(f, r, scale, ink).replace('\n', ' ')
                        tried += "$label:$text "
                        val m = NameReading.match(data.moveMatcher, text)?.let { data.moveNamed(it.name) } ?: continue
                        s.votes[i].merge(m, 1, Int::plus); tried = "$label:$text"; break
                    }
                    if (s.votes[i].isEmpty() && s.typeOnly[i] == null) l.moveTypes.getOrNull(i)?.let { r ->
                        val t = ocr(f, r, 3).trim()
                        tried += " type:$t"
                        data.chart.types.firstOrNull { NameMatcher.normalize(it) == NameMatcher.normalize(t) }
                            ?.let { s.typeOnly[i] = Move(UNREAD_MOVE, it, "unknown", null) }
                    }
                    tried.trim()
                }
                Log.d(TAG, "moves: $raw -> ${s.slots.map { it?.name }}")
            }
        }
        if (now - s.statusAt > STATUS_EVERY_MS) {
            s.statusAt = now
            // badge found anywhere in the name box by the whole-screen read; else the box's badge spot
            val sceneOpp = scene?.opp?.firstOrNull()?.status
            val sceneMine = scene?.mine?.firstOrNull { it.name == s.player?.readAs }?.status ?: scene?.mine?.firstOrNull()?.status
            val before = Triple(statusOf(s, 0), statusOf(s, -1), statusOf(s, 1))
            if (!s.double) {   // doubles: the slots debounce their own badges
                val opp = sceneOpp ?: StatusConditions.parse(ocr(f, l.oppStatus ?: l.oppName, 3))
                val mine = sceneMine ?: StatusConditions.parse(ocr(f, l.playerStatus ?: l.playerName, 3))
                s.oppStatus.update(opp); s.myStatus.update(mine)
            }
            if (before != Triple(statusOf(s, 0), statusOf(s, -1), statusOf(s, 1)))
                Log.d(TAG, "status: opp=${statusOf(s, 0)} mine=${statusOf(s, -1)} opp2=${statusOf(s, 1)}")
        }
        // switch screen: my party's names on the bottom screen (outside the FIGHT menu)
        if (!s.fight && now - s.bottomAt > SCENE_EVERY_MS) {
            s.bottomAt = now
            val lines = l.bottomScreen?.let { readLines(f, it) } ?: emptyList()
            val party = SceneReading.party(lines, data) { Nicknames.lookup(prefs, profile.id, it) }
            if (party.isNotEmpty()) {
                if (party.map { it.name } != s.party.map { it.name }) Log.d(TAG, "party: ${party.map { "${it.name}->${it.species?.name}" }}")
                s.party = party; s.partyMisses = 0
            } else if (s.party.isNotEmpty() && ++s.partyMisses >= 2) s.party = emptyList()
        }
        if (s.learning != null && now - s.learningAt < LEARN_SHOW_MS) readLearnScreen(f, l, data, s, now)
        val oppName = s.opp?.species?.name
        if (s.double) _sprite.value = null
        else if (oppName != null && s.spriteFor != oppName) {
            s.spriteFor = oppName
            _sprite.value = crop(f, l.oppSprite, 1)
        }
    }

    private fun publish(data: PokeData, s: Session, phase: BattlePhase, now: Long = System.currentTimeMillis(), after: Boolean = false) {
        if (!after) _status.value = (if (s.double) "Double battle" else "In battle") +
            (listOfNotNull(s.opp?.species?.name, s.opp2?.species?.name).takeIf { it.isNotEmpty() }?.joinToString(" & ", ": ") ?: "")
        val learning = s.learning?.takeIf { now - s.learningAt < LEARN_SHOW_MS }
        _card.value = BattleCards.build(data, if (s.double) BattlePhase.DOUBLE else phase,
            if (after) null else s.opp, s.player, s.wild,
            if (s.fight && !after) s.moves else emptyList(), s.prompt,
            oppStatus = if (after) null else statusOf(s, 0), myStatus = statusOf(s, -1),
            opponent2 = if (after || !s.double) null else s.opp2, opp2Status = statusOf(s, 1),
            partner = if (s.double) s.partner else null,
            party = if (after) emptyList() else s.party,
            learning = learning, knownMoves = s.learnKnown.ifEmpty { s.moves.filter { it.name != UNREAD_MOVE } },
            afterBattle = after)
    }

    /** Badge of opponent slot [i] (0, 1), or mine (-1); singles use the debouncers, doubles the slots. */
    private fun statusOf(s: Session, i: Int): Status? = when {
        !s.double -> when (i) { 0 -> s.oppStatus.current; -1 -> s.myStatus.current; else -> null }
        i < 0 -> s.mySlotStatus
        else -> s.oppSlotStatus.getOrNull(i)
    }

    private fun wildFrom(text: String): Boolean? {
        val t = text.uppercase()
        return when {
            "WILD" in t -> true
            "WOULD LIKE TO BATTLE" in t || "WANTS TO BATTLE" in t || "SENT OUT" in t || "CHALLENGED" in t -> false
            else -> null
        }
    }

    /**
     * Doubles: both sides from the whole-screen read, tracked by slot so either side can be down to
     * one (in either slot). Mine in the FIGHT menu is the one asked "What will X do?".
     */
    private fun applyDouble(sc: BattleScene, data: PokeData, profile: GameProfile, s: Session) {
        val opps = s.oppSlots.update(sc.opp)
        s.opp = opps.getOrNull(0)?.let { Side(it.name, it.species) }
        s.opp2 = opps.getOrNull(1)?.let { Side(it.name, it.species) }
        s.oppSlotStatus = opps.map { it.status }
        val mine = s.mySlots.update(sc.mine)
        if (mine.isEmpty()) return
        // the one asked "What will X do?"; between those questions, whoever was asked last
        fun same(a: BoxRead, p: Side?) = p != null && (a.name == p.readAs || (a.species != null && a.species == p.species))
        val active = SceneReading.chooser(sc.message, mine) ?: mine.firstOrNull { same(it, s.player) } ?: mine.first()
        if (active.name != s.player?.readAs) Log.d(TAG, "active: ${active.name} (${active.species?.name})")
        s.player = Side(active.name, active.species)
        s.mySlotStatus = active.status
        s.prompt = if (active.species == null && SceneReading.plausibleName(active.name)) active.name else null
        s.partner = mine.firstOrNull { it !== active }?.let { Side(it.name, it.species) }
    }

    /** OCR a whole screen into positioned lines, scaled so text is big enough to read. */
    private fun readLines(f: GameFrame, r: NormRect): List<TextLine> {
        val h = (r.height * f.height).toInt().coerceAtLeast(1)
        return runCatching { reader().lines(crop(f, r, (768 / h).coerceIn(1, 3))) }
            .onFailure { Log.w(TAG, "OCR failed", it) }.getOrDefault(emptyList())
    }

    private fun readScene(f: GameFrame, l: BattleLayout, data: PokeData, profile: GameProfile): BattleScene {
        val top = l.topScreen
        val messageTop = ((l.textBox.top - top.top) / top.height).coerceIn(0f, 1f)
        val scene = SceneReading.battle(readLines(f, top), data, { Nicknames.lookup(prefs, profile.id, it) }, messageTop)
        val log = "opp=${scene.opp.map { "${it.name}/${it.species?.name}/${it.status}" }} mine=${scene.mine.map { "${it.name}/${it.species?.name}/${it.status}" }} msg=\"${scene.message}\""
        if (log != lastSceneLog) { lastSceneLog = log; Log.d(TAG, "scene: $log") }
        return scene
    }

    /** Tuning recorder: a frame whenever the picture changes (at most 1 a second) during and just after battles. */
    private fun recordIfChanged(f: GameFrame, now: Long) {
        if (now - lastRecordMs < RECORD_INTERVAL_MS) return
        val thumb = IntArray(16 * 24) { i ->
            val x = (i % 16) * f.width / 16; val y = (i / 16) * f.height / 24
            (f.r(x, y) + f.g(x, y) + f.b(x, y)) / 3
        }
        val prev = lastRecordThumb
        if (prev != null && thumb.indices.sumOf { kotlin.math.abs(thumb[it] - prev[it]) } / thumb.size < 6) return
        lastRecordThumb = thumb; lastRecordMs = now
        writeFrame(f, now, "battle-")
    }

    /** Name of a move known only by its type label. */
    const val UNREAD_MOVE = "Unreadable move"

    /** Move-name OCR attempts: label, scale, black-on-white. */
    private val MOVE_READS = listOf(Triple("x2", 2, false), Triple("x3", 3, false), Triple("ink", 3, true))

    /**
     * OCR a region: crop, scale without smoothing, read. [ink] turns it into black text on white
     * with a margin first.
     */
    private fun ocr(f: GameFrame, r: NormRect, scale: Int = 2, ink: Boolean = false): String =
        runCatching { reader().read(crop(f, r, scale, ink)) }.onFailure { Log.w(TAG, "OCR failed", it) }.getOrDefault("")

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
        val scaled = if (scale == 1) bmp else Bitmap.createScaledBitmap(bmp, w * scale, h * scale, false)
        if (!ink) return scaled
        val pad = 12 * scale
        return Bitmap.createBitmap(scaled.width + 2 * pad, scaled.height + 2 * pad, Bitmap.Config.ARGB_8888).also {
            android.graphics.Canvas(it).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(scaled, pad.toFloat(), pad.toFloat(), null) }
        }
    }

    /** Calibration: a PNG of the cropped game every 2 s, newest 400 kept. Pull with adb. */
    private fun saveCapture(f: GameFrame) {
        val now = System.currentTimeMillis()
        if (now - lastCaptureMs < CAPTURE_INTERVAL_MS) return
        lastCaptureMs = now
        writeFrame(f, now, "")
    }

    private fun writeFrame(f: GameFrame, now: Long, prefix: String) {
        val dir = capturesDir(app, PokemonSettings.profile(app, prefs).id)
        val bmp = Bitmap.createBitmap(f.width, f.height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(f.rgba))
        val name = prefix + SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date(now)) + ".png"
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        dir.listFiles()?.sortedBy { it.name.removePrefix("battle-") }?.dropLast(MAX_CAPTURES)?.forEach { it.delete() }
    }

    fun capturesDir(ctx: Context, profile: String): File =
        File(ctx.getExternalFilesDir("captures"), profile).apply { mkdirs() }
}
