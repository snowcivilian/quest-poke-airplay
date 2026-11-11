package io.github.snowcivilian.questairplay.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlin.math.roundToInt

/** Touch targets are at least 48dp (Meta's Look and Pinch guidance). */
private val TARGET = 48.dp

/** Mute button, 0-100% slider and readout. Changes apply live and persist (R4). */
@Composable
fun VolumeRow(viewModel: MainViewModel, modifier: Modifier = Modifier, contentColor: Color = Color.Unspecified) {
    val volume by viewModel.pref(Prefs.APP_VOLUME).collectAsState()
    val muted by viewModel.pref(Prefs.APP_MUTE).collectAsState()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.set(Prefs.APP_MUTE, !muted) }, modifier = Modifier.size(TARGET)) {
            Icon(
                if (muted || volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (muted) "Unmute" else "Mute",
                tint = contentColor,
            )
        }
        Slider(
            value = volume / 100f,
            onValueChange = {
                viewModel.set(Prefs.APP_VOLUME, (it * 100).roundToInt())
                if (muted) viewModel.set(Prefs.APP_MUTE, false)
            },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (muted) "Muted" else "$volume%",
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 52.dp),
        )
    }
}

/** Floating volume bar for the video view. */
@Composable
fun VolumeOverlay(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(max = 480.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PresetMenuButton(viewModel, tint = Color.White)
            VolumeRow(viewModel, Modifier.weight(1f), contentColor = Color.White)
        }
    }
}

/** Quest sections at the top of the settings screen. */
@Composable
fun QuestSettings(viewModel: MainViewModel) {
    VolumeSettings(viewModel)
    DisplaySettings(viewModel)
    TheaterSettings(viewModel)
}

/** Volume section for the settings screen. */
@Composable
fun VolumeSettings(viewModel: MainViewModel) {
    val ignore by viewModel.pref(Prefs.IGNORE_SENDER_VOLUME).collectAsState()
    Text(
        text = "Volume",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    VolumeRow(viewModel, Modifier.padding(horizontal = 8.dp))
    ListItem(
        modifier = Modifier.toggleable(
            value = ignore, role = Role.Switch,
            onValueChange = { viewModel.set(Prefs.IGNORE_SENDER_VOLUME, it) },
        ),
        headlineContent = { Text("Ignore iPhone volume") },
        supportingContent = { Text("The iPhone's volume buttons don't change the sound here. Use the slider above.") },
        trailingContent = { Switch(checked = ignore, onCheckedChange = null) },
    )
}
