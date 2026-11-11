package io.github.snowcivilian.questairplay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.ui.theme.AirPlayTheme

/** In-theater control panel (R10), same components and theme as the settings screen. */
@Composable
fun TheaterControlsPanel(onBack: () -> Unit, onMinimize: () -> Unit) = AirPlayTheme(darkTheme = true) {
    val crt by rememberPref(Prefs.CRT_ENABLED)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Theater", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onMinimize) { Icon(Icons.Default.CloseFullscreen, contentDescription = "Minimize") }
            }
            FilledTonalButton(onClick = onBack, modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = TARGET)) {
                Icon(Icons.Default.Tv, null); Spacer(Modifier.width(8.dp)); Text("Back to panel")
            }
            SectionTitle("Volume")
            VolumeRow(Modifier.padding(horizontal = 8.dp))
            SectionTitle("Picture")
            SettingToggle("CRT scanlines", null, crt, prefSetter(Prefs.CRT_ENABLED))
            if (crt) IntSlider("Intensity", Prefs.CRT_INTENSITY, 0, 100, "%", live = true)
            SectionTitle("Screen")
            TheaterScreenSettings()
            Text(
                "Move this panel: point at it and hold the grip button. Recenter: hold the Meta button.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** Minimized controls: a small pill that brings the panel back. */
@Composable
fun TheaterControlsPill(onExpand: () -> Unit, onBack: () -> Unit) = AirPlayTheme(darkTheme = true) {
    Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onExpand, modifier = Modifier.heightIn(min = TARGET)) {
                Icon(Icons.Default.Tune, null); Spacer(Modifier.width(6.dp)); Text("Controls")
            }
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = TARGET)) {
                Icon(Icons.Default.Tv, null); Spacer(Modifier.width(6.dp)); Text("Panel")
            }
        }
    }
}
