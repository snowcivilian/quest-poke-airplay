package io.github.snowcivilian.questairplay.video

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.get
import io.github.jqssun.airplay.observe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the GL renderer needs for one frame. Immutable; swapped atomically. */
data class RenderConfig(
    val crop: NormRect,
    val outputAspect: Float,
    val scanlineIntensity: Float = 0f,
    val scanlineCount: Int = 0,
    /** Whether the renderer should sample frames for auto-detection. */
    val detect: Boolean = false,
)

/**
 * Process-wide video state for the quest fork: which preset is active, and the crop/aspect the
 * renderer should use. The service initialises it; the GL thread and the UI read it.
 */
object VideoController {
    private const val TAG = "QuestVideo"
    private const val KEY_LAST_DETECTED = "quest_last_detected_preset"
    private val FALLBACK = Preset(Preset.FULL_ID, "Full frame", null, null, null, 0, 0, true)

    @Volatile var presets: List<Preset> = listOf(FALLBACK); private set
    private var initialised = false

    private val _active = MutableStateFlow(FALLBACK)
    val active: StateFlow<Preset> = _active.asStateFlow()

    /** Aspect of what is shown (after crop and pixel-aspect correction). Drives panel size. */
    private val _displayAspect = MutableStateFlow(16f / 9f)
    val displayAspect: StateFlow<Float> = _displayAspect.asStateFlow()

    /** True while auto-detection picks the preset (R6); a manual pick turns it off. */
    private val _auto = MutableStateFlow(true)
    val auto: StateFlow<Boolean> = _auto.asStateFlow()
    private val detector = AutoDetector()
    private var manualId = Preset.FULL_ID
    private var prefs: SharedPreferences? = null

    /** Last detection, for the debug overlay: measured game-box aspect and the preset it matched. */
    @Volatile var lastDetectedAspect = 0f; private set
    @Volatile var lastMatch: String? = null; private set

    @Volatile private var frameW = 0
    @Volatile private var frameH = 0
    @Volatile private var config = RenderConfig(NormRect.FULL, 16f / 9f)

    @Synchronized
    fun init(ctx: Context, prefs: SharedPreferences, scope: CoroutineScope) {
        if (initialised) return
        initialised = true
        presets = runCatching {
            Preset.parseAll(ctx.assets.open("presets.json").bufferedReader().use { it.readText() })
        }.onFailure { Log.e(TAG, "presets.json failed to load", it) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(FALLBACK)
        Log.i(TAG, "loaded ${presets.size} presets")
        this.prefs = prefs
        scope.launch {
            prefs.observe { this[Prefs.PRESET] to this[Prefs.AUTO_DETECT] }.collect { (id, auto) ->
                manualId = id
                _auto.value = auto
                if (auto) {
                    // resume from the last detected preset rather than flashing back to full frame
                    val last = prefs.getString(KEY_LAST_DETECTED, null)
                    if (detector.current == null) detector.reset(last)
                    setActive(byId(detector.current ?: Preset.FULL_ID))
                } else setActive(byId(id))
            }
        }
    }

    fun byId(id: String): Preset = presets.firstOrNull { it.id == id } ?: presets.first()

    /** Manual pick from the UI; overrides auto-detection until it is turned back on (R6). */
    fun select(prefs: SharedPreferences, id: String) {
        Prefs.PRESET.write(prefs, id)
        Prefs.AUTO_DETECT.write(prefs, false)
    }

    fun setAuto(prefs: SharedPreferences, on: Boolean) {
        Prefs.AUTO_DETECT.write(prefs, on)
    }

    /** From the GL thread, every ~400 ms and on stream resolution changes. */
    fun onDetection(d: Detection, fw: Int, fh: Int, resized: Boolean) {
        val id = PresetMatcher.match(d, fw, fh, presets)
        lastDetectedAspect = d.content?.pixelAspect(fw, fh) ?: 0f
        lastMatch = id
        if (!_auto.value) return
        // a new resolution is a new layout: confirm afresh instead of trusting old samples
        if (resized) detector.reset(detector.current)
        val switched = detector.feed(id) ?: return
        Log.i(TAG, "auto-detect: aspect %.3f -> %s".format(lastDetectedAspect, switched))
        prefs?.edit()?.putString(KEY_LAST_DETECTED, switched)?.apply()
        if (_auto.value) setActive(byId(switched))
    }

    @Synchronized
    private fun setActive(p: Preset) {
        Log.i(TAG, "preset -> ${p.id}")
        _active.value = p
        _recompute()
    }

    /** Called by the GL thread for every frame drawn. Cheap unless the frame size changed. */
    fun renderConfig(fw: Int, fh: Int): RenderConfig {
        if (fw != frameW || fh != frameH) synchronized(this) {
            frameW = fw; frameH = fh
            _recompute()
        }
        return config
    }

    private fun _recompute() {
        val p = _active.value
        val fw = frameW; val fh = frameH
        val crop = if (fw > 0 && fh > 0) p.cropFor(fw, fh) else NormRect.FULL
        val aspect = if (fw > 0 && fh > 0) p.outputAspect(crop, fw, fh) else (p.displayAspect ?: 16f / 9f)
        config = RenderConfig(crop, aspect, detect = _auto.value)
        _displayAspect.value = aspect
    }
}
