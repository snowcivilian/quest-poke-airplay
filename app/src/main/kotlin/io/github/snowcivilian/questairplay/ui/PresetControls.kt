package io.github.snowcivilian.questairplay.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.viewmodel.MainViewModel
import io.github.snowcivilian.questairplay.video.VideoController

private fun Context.prefs() = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

/** Quick menu (R5): pick a system preset from the video overlay. */
@Composable
fun PresetMenuButton(viewModel: MainViewModel, tint: Color = Color.Unspecified) {
    val ctx = LocalContext.current
    val active by VideoController.active.collectAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.AspectRatio, contentDescription = "System preset", tint = tint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            QuickMenuExtras(viewModel) { open = false }
            VideoController.presets.forEach { p ->
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = 48.dp),
                    text = { Text(p.label) },
                    leadingIcon = { if (p.id == active.id) Icon(Icons.Default.Check, null) },
                    onClick = { VideoController.select(ctx.prefs(), p.id); open = false },
                )
            }
        }
    }
}

/** Hook for items added above the preset list in later milestones. */
@Composable
internal fun QuickMenuExtras(viewModel: MainViewModel, close: () -> Unit) {}

/** Display section of the settings screen. */
@Composable
fun DisplaySettings(viewModel: MainViewModel) {
    val ctx = LocalContext.current
    val active by VideoController.active.collectAsState()
    val panelResize by viewModel.pref(Prefs.PANEL_RESIZE).collectAsState()
    SectionTitle("Display")
    SettingToggle("Resize panel to fit the game", "Reopens the panel at the system's shape when the preset changes",
        panelResize) { viewModel.set(Prefs.PANEL_RESIZE, it) }
    SectionTitle("System preset")
    VideoController.presets.forEach { p ->
        ListItem(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .selectable(selected = p.id == active.id, role = Role.RadioButton) {
                    VideoController.select(ctx.prefs(), p.id)
                },
            headlineContent = { Text(p.label) },
            leadingContent = { RadioButton(selected = p.id == active.id, onClick = null) },
        )
    }
}

@Composable
internal fun SectionTitle(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
)

@Composable
internal fun SettingToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) = ListItem(
    modifier = Modifier.heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    headlineContent = { Text(title) },
    supportingContent = { Text(description) },
    trailingContent = { Switch(checked = checked, onCheckedChange = null) },
)
