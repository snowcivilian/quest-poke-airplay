package io.github.snowcivilian.questairplay.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.get
import io.github.jqssun.airplay.observe
import io.github.jqssun.airplay.ui.theme.AirPlayTheme
import io.github.snowcivilian.questairplay.pokemon.PokemonEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The battle card in its own Horizon OS panel (S4). Horizon OS opens an activity started with
 * FLAG_ACTIVITY_LAUNCH_ADJACENT | FLAG_ACTIVITY_NEW_TASK as a separate panel; the activity is
 * singleInstance with its own task affinity, so starting it again brings back the same window
 * instead of opening a second one. Declared only in the quest manifest.
 */
object CardWindow {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()
    @Volatile private var launchedAt = 0L

    /** Whether the card should be in its own window right now. */
    fun wanted(ctx: Context): Boolean {
        val p = ctx.questPrefs()
        return ctx.resources.getBoolean(R.bool.card_window_supported) &&
            p[Prefs.POKEMON_HELPER] && p[Prefs.POKEMON_CARD_WINDOW] && p[Prefs.POKEMON_CARD_SHOWN]
    }

    /** Opens the window (or brings it back). [force] = the user asked; otherwise skip if it's already up. */
    fun show(ctx: Context, force: Boolean = false) {
        if (!force && (_open.value || SystemClock.elapsedRealtime() - launchedAt < 3_000)) return
        launchedAt = SystemClock.elapsedRealtime()
        ctx.startActivity(Intent(ctx, CardWindowActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    internal fun setOpen(v: Boolean) { _open.value = v }
}

class CardWindowActivity : ComponentActivity() {
    private var closingOurselves = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CardWindow.setOpen(true)
        val prefs = questPrefs()
        // hidden from the toolbar, moved back onto the video, or helper turned off: close the window
        lifecycleScope.launch {
            prefs.observe { Triple(this[Prefs.POKEMON_HELPER], this[Prefs.POKEMON_CARD_WINDOW], this[Prefs.POKEMON_CARD_SHOWN]) }
                .collect { (on, window, shown) -> if (!(on && window && shown)) { closingOurselves = true; finish() } }
        }
        setContent { CardWindowContent() }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isChangingConfigurations) return
        CardWindow.setOpen(false)
        // closed with the panel's own close button: same as hiding the card, so the toolbar icon agrees
        if (!closingOurselves) Prefs.POKEMON_CARD_SHOWN.write(questPrefs(), false)
    }
}

/** The card window's content: the battle card, or what the helper is doing between battles. */
@Composable
fun CardWindowContent() = AirPlayTheme(darkTheme = true) {
    val card by PokemonEngine.card.collectAsState()
    val sprite by PokemonEngine.sprite.collectAsState()
    val status by PokemonEngine.status.collectAsState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        val c = card
        if (c != null) BattleCardContent(c, sprite, Modifier.padding(16.dp))
        else Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Battle helper", style = MaterialTheme.typography.titleLarge)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("The card shows up here during a battle.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Toolbar toggle beside theater and fullscreen: shows or hides the battle card, on the video or in
 * its own window. Only there while the helper is on. Also reopens the window when it should be up.
 */
@Composable
fun PokemonCardButton(tint: Color) {
    val ctx = LocalContext.current
    val on by rememberPref(Prefs.POKEMON_HELPER)
    val window by rememberPref(Prefs.POKEMON_CARD_WINDOW)
    val shown by rememberPref(Prefs.POKEMON_CARD_SHOWN)
    val supported = booleanResource(R.bool.card_window_supported)
    LaunchedEffect(on, window, shown) { if (on && window && shown && supported) CardWindow.show(ctx) }
    if (!on) return
    IconButton(onClick = {
        val next = !shown
        Prefs.POKEMON_CARD_SHOWN.write(ctx.questPrefs(), next)
        if (next && window && supported) CardWindow.show(ctx, force = true)
    }) {
        Icon(Icons.Default.CatchingPokemon, contentDescription = if (shown) "Hide battle card" else "Show battle card",
            tint = if (shown) Color(0xFFFF5252) else tint)
    }
}
