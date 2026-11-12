package io.github.snowcivilian.questairplay.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.Prefs
import io.github.snowcivilian.questairplay.theater.TheaterLauncher
import io.github.snowcivilian.questairplay.video.VideoController
import kotlin.math.roundToInt

/** Quick menu (R5): theater, auto-detect, CRT, then the system presets. */
@Composable
fun PresetMenuButton(tint: Color = Color.Unspecified) {
    val ctx = LocalContext.current
    val active by VideoController.active.collectAsState()
    val auto by VideoController.auto.collectAsState()
    val crt by rememberPref(Prefs.CRT_ENABLED)
    val setCrt = prefSetter(Prefs.CRT_ENABLED)
    val pokemon by rememberPref(Prefs.POKEMON_HELPER)
    val setPokemon = prefSetter(Prefs.POKEMON_HELPER)
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(TARGET)) {
            Icon(Icons.Default.AspectRatio, contentDescription = "System preset", tint = tint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable fun item(label: String, checked: Boolean, onClick: () -> Unit) = DropdownMenuItem(
                modifier = Modifier.heightIn(min = TARGET),
                text = { Text(label) },
                leadingIcon = { if (checked) Icon(Icons.Default.Check, null) },
                onClick = { open = false; onClick() },
            )
            if (TheaterLauncher.available(ctx)) item("Theater mode", false) { (ctx as? Activity)?.let(TheaterLauncher::enter) }
            item("Auto-detect system", auto) { VideoController.setAuto(ctx.questPrefs(), !auto) }
            item("CRT scanlines", crt) { setCrt(!crt) }
            item("Pokémon battle helper", pokemon) { setPokemon(!pokemon) }
            HorizontalDivider()
            VideoController.presets.forEach { p ->
                item(p.label, !auto && p.id == active.id) { VideoController.select(ctx.questPrefs(), p.id) }
            }
        }
    }
}

/** Theater button for the video overlay, next to fullscreen. Hidden when there is no theater. */
@Composable
fun TheaterButton(tint: Color) {
    val ctx = LocalContext.current
    if (!TheaterLauncher.available(ctx)) return
    IconButton(onClick = { (ctx as? Activity)?.let(TheaterLauncher::enter) }) {
        Icon(Icons.Default.ViewInAr, contentDescription = "Theater mode", tint = tint)
    }
}

/** Display section: the settings people actually change. */
@Composable
fun DisplaySettings() {
    val ctx = LocalContext.current
    val active by VideoController.active.collectAsState()
    val auto by VideoController.auto.collectAsState()
    val gameMode by rememberPref(Prefs.GAME_MODE)
    val crt by rememberPref(Prefs.CRT_ENABLED)
    val stats by rememberPref(Prefs.DEBUG_ENABLED)
    val panelResize by rememberPref(Prefs.PANEL_RESIZE)
    SectionTitle("Display")
    SettingToggle("Game mode", "Lowest delay. Turn off for watching video (smoother, more delay). Reconnects the stream.",
        gameMode, prefSetter(Prefs.GAME_MODE))
    SettingToggle("Auto-detect system", "Measure the black bars and pick the matching preset. Picking a preset by hand turns this off.",
        auto) { VideoController.setAuto(ctx.questPrefs(), it) }

    var presetsOpen by rememberSaveable { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.heightIn(min = TARGET).clickable { presetsOpen = !presetsOpen },
        headlineContent = { Text("System preset") },
        supportingContent = { Text(if (auto) "Auto: ${active.label}" else active.label) },
        trailingContent = { Icon(if (presetsOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) },
    )
    AnimatedVisibility(presetsOpen) {
        Column {
            VideoController.presets.forEach { p ->
                ListItem(
                    modifier = Modifier
                        .heightIn(min = TARGET)
                        .padding(start = 16.dp)
                        .selectable(selected = !auto && p.id == active.id, role = Role.RadioButton) {
                            VideoController.select(ctx.questPrefs(), p.id)
                        },
                    headlineContent = { Text(p.label) },
                    leadingContent = { RadioButton(selected = !auto && p.id == active.id, onClick = null) },
                )
            }
        }
    }

    SettingToggle("CRT scanlines", "Dark lines between the game's rows, like an old TV", crt, prefSetter(Prefs.CRT_ENABLED))
    if (crt) IntSlider("Intensity", Prefs.CRT_INTENSITY, 0, 100, "%", live = true)
    SettingToggle("Show stats overlay", "FPS, bitrate, decode-to-display delay, audio buffer and detected system",
        stats, prefSetter(Prefs.DEBUG_ENABLED))
    SettingToggle("Resize panel to fit the game", "Reopens the panel at the system's shape when the preset changes",
        panelResize, prefSetter(Prefs.PANEL_RESIZE))
}

/** Collapsed-by-default header for upstream's receiver settings. Returns whether it is open. */
@Composable
fun AdvancedSection(): Boolean {
    var open by rememberSaveable { mutableStateOf(false) }
    HorizontalDivider(Modifier.padding(top = 8.dp))
    ListItem(
        modifier = Modifier.heightIn(min = TARGET).clickable { open = !open },
        headlineContent = { Text("Advanced", color = MaterialTheme.colorScheme.primary) },
        supportingContent = { Text("Receiver name and port, codecs, resolution, audio buffering, developer options") },
        trailingContent = { Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) },
    )
    return open
}

@Composable
internal fun SectionTitle(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
)

@Composable
internal fun SettingToggle(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) = ListItem(
    modifier = Modifier.heightIn(min = TARGET).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    headlineContent = { Text(title) },
    supportingContent = description?.let { { Text(it) } },
    trailingContent = { Switch(checked = checked, onCheckedChange = null) },
)

/** Slider over an int pref. [live] writes while dragging; otherwise on release (theater rebuilds). */
@Composable
internal fun IntSlider(label: String, pref: io.github.jqssun.airplay.IntPref, min: Int, max: Int, unit: String, live: Boolean = false) {
    val stored by rememberPref(pref)
    val set = prefSetter(pref)
    var dragging by remember(stored) { mutableStateOf(stored.toFloat()) }
    Row(Modifier.padding(horizontal = 16.dp).heightIn(min = TARGET), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(140.dp))
        Slider(
            value = dragging, valueRange = min.toFloat()..max.toFloat(),
            onValueChange = { dragging = it; if (live) set(it.roundToInt()) },
            onValueChangeFinished = { set(dragging.roundToInt()) },
            modifier = Modifier.weight(1f),
        )
        Text("${dragging.roundToInt()}$unit", Modifier.width(72.dp), textAlign = TextAlign.End)
    }
}
