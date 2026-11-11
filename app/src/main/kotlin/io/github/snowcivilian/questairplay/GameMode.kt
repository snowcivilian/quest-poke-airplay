package io.github.snowcivilian.questairplay

import android.content.SharedPreferences
import android.util.Log
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.get
import io.github.jqssun.airplay.observe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Low-latency game mode (R7) as one switch over upstream's existing settings, applied only when the
 * switch changes so manual tweaks to those settings survive restarts.
 *
 * On: low-latency audio output, frames shown as soon as they're decoded (no PTS pacing), the
 * tightest adaptive audio buffer. Off (video watching): paced frames and upstream's default buffer.
 * The decoder is always asked for low-latency mode by upstream (KEY_LOW_LATENCY when supported).
 */
object GameMode {
    private const val TAG = "GameMode"
    private const val KEY_APPLIED = "quest_game_mode_applied"

    fun init(prefs: SharedPreferences, scope: CoroutineScope) {
        scope.launch {
            prefs.observe { this[Prefs.GAME_MODE] }.collect { on ->
                if (prefs.contains(KEY_APPLIED) && prefs.getBoolean(KEY_APPLIED, on) == on) return@collect
                apply(prefs, on)
            }
        }
    }

    private fun apply(prefs: SharedPreferences, on: Boolean) {
        Log.i(TAG, "game mode ${if (on) "on" else "off"}")
        Prefs.LOW_LATENCY.write(prefs, on)
        Prefs.SCHEDULED_OUTPUT_BUFFER_RELEASE.write(prefs, !on)
        Prefs.AUDIO_AUTO_BUFFER.write(prefs, true)
        Prefs.AUDIO_ADAPTIVE_STEP.write(prefs, if (on) 0 else Prefs.AUDIO_ADAPTIVE_STEP.def)
        prefs.edit().putBoolean(KEY_APPLIED, on).apply()
    }
}
