package io.github.snowcivilian.questairplay

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.jqssun.airplay.Prefs

/**
 * Quest-specific defaults (R3), written into upstream's settings store once, without touching
 * upstream's Prefs.kt. A value is only written when the user has never set that key, so in-app
 * and ADB changes always win.
 */
object QuestDefaults {
    const val RECEIVER_NAME = "Quest"

    /** Not 7000: AIpin keeps a background server on 7000, and both receivers should coexist. */
    const val SERVER_PORT = 7010

    /** Bump when adding entries so existing installs pick up the new ones. */
    private const val SEED_VERSION = 2
    private const val SEED_KEY = "quest_defaults_version"

    fun seed(prefs: SharedPreferences) {
        if (prefs.getInt(SEED_KEY, 0) >= SEED_VERSION) return
        prefs.edit {
            fun <T : Any> default(key: String, value: T) {
                if (prefs.contains(key)) return
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is String -> putString(key, value)
                }
            }
            default(Prefs.SERVER_NAME.key, RECEIVER_NAME)  // fixed name so the iPhone remembers it
            default(Prefs.SERVER_PORT.key, SERVER_PORT)        // v2
            default(Prefs.REQUIRE_PIN.key, false)          // PIN off
            default(Prefs.AUTO_START.key, true)            // listen on launch
            default(Prefs.KEEP_SCREEN_ON.key, true)        // stay awake while streaming
            default(Prefs.AUTO_FULLSCREEN.key, true)       // game fills the panel once mirroring starts
            putInt(SEED_KEY, SEED_VERSION)
        }
    }
}
