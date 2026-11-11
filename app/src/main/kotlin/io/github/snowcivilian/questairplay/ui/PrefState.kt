package io.github.snowcivilian.questairplay.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.jqssun.airplay.Pref
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.get
import io.github.jqssun.airplay.observe

/** Upstream's single settings store, so the same composables work in the panel and the theater. */
fun Context.questPrefs(): SharedPreferences = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

@Composable
fun <T : Any> rememberPref(p: Pref<T>): State<T> {
    val prefs = LocalContext.current.questPrefs()
    return remember(p) { prefs.observe { this[p] } }.collectAsState(prefs[p])
}

@Composable
fun <T : Any> prefSetter(p: Pref<T>): (T) -> Unit {
    val prefs = LocalContext.current.questPrefs()
    return remember(p) { { v: T -> p.write(prefs, v) } }
}
