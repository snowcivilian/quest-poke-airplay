package io.github.snowcivilian.questairplay.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import io.github.snowcivilian.questairplay.theater.TheaterLayoutStore
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.Prefs
import io.github.snowcivilian.questairplay.theater.TheaterLauncher

/** Theater mode section (R10). The same screen controls appear inside the theater. */
@Composable
fun TheaterSettings() {
    val ctx = LocalContext.current
    if (!TheaterLauncher.available(ctx)) return
    SectionTitle("Theater mode")
    Button(
        onClick = { (ctx as? Activity)?.let(TheaterLauncher::enter) },
        modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = TARGET),
    ) { Text("Enter theater") }
    TheaterScreenSettings()
}

/** Passthrough and screen geometry; shared by Settings and the in-theater controls. */
@Composable
fun TheaterScreenSettings() {
    val passthrough by rememberPref(Prefs.THEATER_PASSTHROUGH)
    SettingToggle("Passthrough", "See your room around the screen instead of darkness", passthrough,
        prefSetter(Prefs.THEATER_PASSTHROUGH))
    IntSlider("Screen height", Prefs.THEATER_HEIGHT, 50, 600, " cm")
    IntSlider("Distance", Prefs.THEATER_DISTANCE, 100, 1500, " cm")
    IntSlider("Elevation", Prefs.THEATER_ELEVATION, 30, 250, " cm")
    IntSlider("Curve", Prefs.THEATER_CURVE, 0, 100, "%")
    val split by rememberPref(Prefs.THEATER_DS_SPLIT)
    SettingToggle("Split DS screens", "With the DS both-screens preset: top screen in front, bottom screen below like a handheld",
        split, prefSetter(Prefs.THEATER_DS_SPLIT))
    if (split) DsBottomScreenControls()
}

/** S1: preset sizes instead of a fiddly slider, and a reset for a moved bottom screen. */
@Composable
private fun DsBottomScreenControls() {
    val ctx = LocalContext.current
    val size by rememberPref(Prefs.THEATER_DS_BOTTOM_SCALE)
    val setSize = prefSetter(Prefs.THEATER_DS_BOTTOM_SCALE)
    Row(Modifier.padding(horizontal = 16.dp).heightIn(min = TARGET), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("DS bottom screen", Modifier.weight(1f))
        listOf("Small" to 30, "Medium" to 40, "Large" to 55).forEach { (label, pct) ->
            FilterChip(selected = size == pct, onClick = { setSize(pct) }, label = { Text(label) },
                modifier = Modifier.heightIn(min = TARGET))
        }
    }
    TextButton(onClick = { TheaterLayoutStore.resetBottomPose(ctx.questPrefs()) },
        modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = TARGET)) {
        Text("Reset bottom screen position")
    }
    Text("Move the bottom screen by grabbing it with the grip button or a pinch.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp))
}
