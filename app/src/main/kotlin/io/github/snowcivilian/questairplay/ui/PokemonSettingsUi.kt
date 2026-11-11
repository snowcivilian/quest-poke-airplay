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
import io.github.snowcivilian.questairplay.pokemon.PokemonSettings
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
    Text("Battle recognition is still being built: game data and type charts are in; on-screen detection and the card come next.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp))
}
