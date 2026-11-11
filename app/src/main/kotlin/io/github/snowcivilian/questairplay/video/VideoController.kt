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
)

/**
 * Process-wide video state for the quest fork: which preset is active, and the crop/aspect the
 * renderer should use. The service initialises it; the GL thread and the UI read it.
 */
object VideoController {
    private const val TAG = "QuestVideo"
    private val FALLBACK = Preset(Preset.FULL_ID, "Full frame", null, null, null, 0, 0, true)

    @Volatile var presets: List<Preset> = listOf(FALLBACK); private set
    private var initialised = false

    private val _active = MutableStateFlow(FALLBACK)
    val active: StateFlow<Preset> = _active.asStateFlow()

    /** Aspect of what is shown (after crop and pixel-aspect correction). Drives panel size. */
    private val _displayAspect = MutableStateFlow(16f / 9f)
    val displayAspect: StateFlow<Float> = _displayAspect.asStateFlow()

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
        scope.launch {
            prefs.observe { this[Prefs.PRESET] }.collect { setActive(byId(it)) }
        }
    }

    fun byId(id: String): Preset = presets.firstOrNull { it.id == id } ?: presets.first()

    /** Manual pick from the UI. */
    fun select(prefs: SharedPreferences, id: String) {
        Prefs.PRESET.write(prefs, id)
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
        config = RenderConfig(crop, aspect)
        _displayAspect.value = aspect
    }
}
