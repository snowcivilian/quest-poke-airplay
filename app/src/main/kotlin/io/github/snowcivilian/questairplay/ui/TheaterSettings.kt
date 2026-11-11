package io.github.snowcivilian.questairplay.ui

import android.app.Activity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.IntPref
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.viewmodel.MainViewModel
import io.github.snowcivilian.questairplay.theater.TheaterLauncher
import kotlin.math.roundToInt

/** Theater mode section (R10): enter button and screen settings, also adjustable inside the theater. */
@Composable
fun TheaterSettings(viewModel: MainViewModel) {
    val ctx = LocalContext.current
    if (!TheaterLauncher.available(ctx)) return
    val passthrough by viewModel.pref(Prefs.THEATER_PASSTHROUGH).collectAsState()
    SectionTitle("Theater mode")
    Button(
        onClick = { (ctx as? Activity)?.let(TheaterLauncher::enter) },
        modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp),
    ) { Text("Enter theater") }
    SettingToggle("Passthrough", "See your room around the screen instead of darkness", passthrough) {
        viewModel.set(Prefs.THEATER_PASSTHROUGH, it)
    }
    IntSlider(viewModel, "Screen height", Prefs.THEATER_HEIGHT, 50, 600, " cm")
    IntSlider(viewModel, "Distance", Prefs.THEATER_DISTANCE, 100, 1500, " cm")
    IntSlider(viewModel, "Screen elevation", Prefs.THEATER_ELEVATION, 30, 250, " cm")
    IntSlider(viewModel, "Curve", Prefs.THEATER_CURVE, 0, 100, "%")
}

/** Slider over an int pref; writes on release since theater screen changes rebuild the panel. */
@Composable
internal fun IntSlider(viewModel: MainViewModel, label: String, pref: IntPref, min: Int, max: Int, unit: String) {
    val stored by viewModel.pref(pref).collectAsState()
    var dragging by remember(stored) { mutableFloatStateOf(stored.toFloat()) }
    Row(Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(140.dp))
        Slider(
            value = dragging, valueRange = min.toFloat()..max.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = { viewModel.set(pref, dragging.roundToInt()) },
            modifier = Modifier.weight(1f),
        )
        Text("${dragging.roundToInt()}$unit", Modifier.width(72.dp), textAlign = TextAlign.End)
    }
}
