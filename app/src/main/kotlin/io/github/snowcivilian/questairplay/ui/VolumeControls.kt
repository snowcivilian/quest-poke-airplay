package io.github.snowcivilian.questairplay.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlin.math.roundToInt

/** Touch targets are at least 48dp (Meta's Look and Pinch guidance). */
internal val TARGET = 48.dp

/** Mute button, 0-100% slider and readout. Changes apply live and persist (R4). */
@Composable
fun VolumeRow(modifier: Modifier = Modifier, contentColor: Color = Color.Unspecified) {
    val volume by rememberPref(Prefs.APP_VOLUME)
    val muted by rememberPref(Prefs.APP_MUTE)
    val setVolume = prefSetter(Prefs.APP_VOLUME)
    val setMute = prefSetter(Prefs.APP_MUTE)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { setMute(!muted) }, modifier = Modifier.size(TARGET)) {
            Icon(
                if (muted || volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (muted) "Unmute" else "Mute",
                tint = contentColor,
            )
        }
        Slider(
            value = volume / 100f,
            onValueChange = { setVolume((it * 100).roundToInt()); if (muted) setMute(false) },
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

/** Floating bar over the video: preset menu and volume. */
@Composable
fun VolumeOverlay(@Suppress("UNUSED_PARAMETER") viewModel: MainViewModel, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(max = 480.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PresetMenuButton(tint = Color.White)
            VolumeRow(Modifier.weight(1f), contentColor = Color.White)
        }
    }
}

/** Quest sections at the top of the settings screen. */
@Composable
fun QuestSettings(@Suppress("UNUSED_PARAMETER") viewModel: MainViewModel) {
    VolumeSettings()
    DisplaySettings()
    TheaterSettings()
}

@Composable
fun VolumeSettings() {
    val ignore by rememberPref(Prefs.IGNORE_SENDER_VOLUME)
    val setIgnore = prefSetter(Prefs.IGNORE_SENDER_VOLUME)
    SectionTitle("Volume")
    VolumeRow(Modifier.padding(horizontal = 8.dp))
    SettingToggle("Ignore iPhone volume", "The iPhone's volume buttons don't change the sound here. Use the slider above.",
        ignore, setIgnore)
}
