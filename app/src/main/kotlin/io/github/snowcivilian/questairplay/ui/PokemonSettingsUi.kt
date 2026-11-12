package io.github.snowcivilian.questairplay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.Prefs
import io.github.snowcivilian.questairplay.pokemon.DataGen
import io.github.snowcivilian.questairplay.pokemon.GameProfile
import io.github.snowcivilian.questairplay.pokemon.Nicknames
import io.github.snowcivilian.questairplay.pokemon.PokemonEngine
import io.github.snowcivilian.questairplay.pokemon.PokemonSettings
import androidx.compose.material3.TextButton
import io.github.snowcivilian.questairplay.video.VideoController

/** S4 settings: off by default; game and data generation only shown when on. */
@Composable
fun PokemonSettingsSection() {
    val ctx = LocalContext.current
    val prefs = ctx.questPrefs()
    val on by rememberPref(Prefs.POKEMON_HELPER)
    val profileId by rememberPref(Prefs.POKEMON_PROFILE)
    val active by VideoController.active.collectAsState()
    SectionTitle("Pokémon battle helper")
    SettingToggle("Battle helper", "Type matchups for Pokémon battles. Off: no recognition runs at all.",
        on, prefSetter(Prefs.POKEMON_HELPER))
    if (!on) return
    val profiles = GameProfile.forSystem(PokemonSettings.profiles(ctx), GameProfile.systemOfPreset(active.id))
    val selected = PokemonSettings.profile(ctx, prefs)
    Text("Game", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    profiles.forEach { p ->
        ListItem(
            modifier = Modifier.heightIn(min = TARGET)
                .selectable(selected = p.id == profileId, role = Role.RadioButton) { Prefs.POKEMON_PROFILE.write(prefs, p.id) },
            headlineContent = { Text(p.label) },
            leadingContent = { RadioButton(selected = p.id == profileId, onClick = null) },
        )
    }
    // per-profile choice; a counter forces recomposition since it isn't a registered pref
    var bump by remember { mutableIntStateOf(0) }
    val gen = remember(selected.id, bump) { PokemonSettings.generation(prefs, selected) }
    Text("Data generation for ${selected.label}", style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    Text("Type chart, Pokémon and move data. Many ROM hacks use later-generation mechanics.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp))
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DataGen.entries.forEach { g ->
            FilterChip(selected = g == gen, onClick = { PokemonSettings.setGeneration(prefs, selected, g); bump++ },
                label = { Text(g.label) }, modifier = Modifier.heightIn(min = TARGET))
        }
    }
    val status by PokemonEngine.status.collectAsState()
    val ocr by PokemonEngine.ocrTest.collectAsState()
    val left by rememberPref(Prefs.POKEMON_CARD_LEFT)
    val window by rememberPref(Prefs.POKEMON_CARD_WINDOW)
    val shown by rememberPref(Prefs.POKEMON_CARD_SHOWN)
    val windowSupported = androidx.compose.ui.res.booleanResource(io.github.jqssun.airplay.R.bool.card_window_supported)
    val calibration by rememberPref(Prefs.POKEMON_CALIBRATION)
    val record by rememberPref(Prefs.POKEMON_RECORD)
    Text("Status: $status" + (selected.layout?.let { "" } ?: " (no screen layout for this game yet)"),
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    SettingToggle("Show battle card", "Also on the toolbar (the Poké Ball beside theater and fullscreen).", shown,
        prefSetter(Prefs.POKEMON_CARD_SHOWN))
    Text("Card position", Modifier.padding(start = 16.dp, top = 4.dp))
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val onVideo = !window || !windowSupported
        if (windowSupported) FilterChip(selected = !onVideo, onClick = {
            Prefs.POKEMON_CARD_WINDOW.write(prefs, true); Prefs.POKEMON_CARD_SHOWN.write(prefs, true)
            CardWindow.show(ctx, force = true)
        }, label = { Text("Own window") }, modifier = Modifier.heightIn(min = TARGET))
        FilterChip(selected = onVideo && left, onClick = { Prefs.POKEMON_CARD_WINDOW.write(prefs, false); Prefs.POKEMON_CARD_LEFT.write(prefs, true) },
            label = { Text("Top left") }, modifier = Modifier.heightIn(min = TARGET))
        FilterChip(selected = onVideo && !left, onClick = { Prefs.POKEMON_CARD_WINDOW.write(prefs, false); Prefs.POKEMON_CARD_LEFT.write(prefs, false) },
            label = { Text("Top right") }, modifier = Modifier.heightIn(min = TARGET))
    }
    Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { PokemonEngine.showDemo(ctx) }, modifier = Modifier.heightIn(min = TARGET)) { Text("Show sample card") }
        TextButton(onClick = { PokemonEngine.runOcrSelfTest() }, modifier = Modifier.heightIn(min = TARGET)) { Text("Test text reading") }
    }
    ocr?.let { Text("Text reading: $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
    SettingToggle("Calibration mode", "Draws this game's screen regions over the video and saves a frame every 2 s to " +
        PokemonEngine.capturesDir(ctx, selected.id).absolutePath, calibration, prefSetter(Prefs.POKEMON_CALIBRATION))
    SettingToggle("Save battle frames", "For tuning: saves a frame whenever the picture changes during and just after battles " +
        "(newest 600 kept), with no boxes drawn. Turn on when something isn't read right.", record, prefSetter(Prefs.POKEMON_RECORD))
    NicknameList(selected)
}

@Composable
private fun NicknameList(profile: GameProfile) {
    val ctx = LocalContext.current
    val prefs = ctx.questPrefs()
    var bump by remember { mutableIntStateOf(0) }
    val nicks = remember(profile.id, bump) { Nicknames.all(prefs, profile.id) }
    if (nicks.isEmpty()) return
    Text("Saved nicknames", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    nicks.forEach { (nick, species) ->
        ListItem(
            headlineContent = { Text("$nick → $species") },
            trailingContent = {
                TextButton(onClick = { Nicknames.remove(prefs, profile.id, nick); bump++ }) { Text("Delete") }
            },
        )
    }
}
