package io.github.snowcivilian.questairplay.ui

import android.app.Activity
import androidx.compose.foundation.layout.heightIn
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
}
