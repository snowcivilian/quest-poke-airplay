package io.github.snowcivilian.questairplay.panel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.get
import io.github.jqssun.airplay.observe
import io.github.snowcivilian.questairplay.video.Geometry
import io.github.snowcivilian.questairplay.video.VideoController
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Horizon OS sizes a 2D panel from the activity's manifest `<layout>` and offers no runtime resize
 * API, so each panel aspect ratio is its own activity (quest flavor manifest, tagged with
 * [META_ASPECT]). When the shown aspect changes, relaunch into the closest one. The AirPlay session
 * lives in the service, so the stream continues.
 */
object PanelSwitcher {
    const val META_ASPECT = "io.github.snowcivilian.questairplay.PANEL_ASPECT"
    private const val TAG = "PanelSwitcher"

    /** Index of the panel aspect closest to [target] (log scale). Pure, unit tested. */
    fun nearest(aspects: List<Float>, target: Float): Int =
        aspects.indices.minByOrNull { Geometry.aspectDistance(aspects[it], target) } ?: -1

    fun parseRatio(s: String): Float? = s.split(':').takeIf { it.size == 2 }?.let { (w, h) ->
        val a = w.trim().toFloatOrNull(); val b = h.trim().toFloatOrNull()
        if (a != null && b != null && a > 0f && b > 0f) a / b else null
    }

    @Suppress("DEPRECATION")
    private fun panels(ctx: Context): List<Pair<String, Float>> = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA)
            .activities.orEmpty().mapNotNull { ai ->
                ai.metaData?.getString(META_ASPECT)?.let(::parseRatio)?.let { ai.name to it }
            }
    }.getOrDefault(emptyList())

    @OptIn(FlowPreview::class)
    fun attach(activity: ComponentActivity) {
        val panels = panels(activity)
        if (panels.size < 2) return // generic flavor: nothing to switch between
        val mine = activity.javaClass.name
        if (panels.none { it.first == mine }) return
        val prefs = activity.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val aspects = panels.map { it.second }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(VideoController.displayAspect, prefs.observe { this[Prefs.PANEL_RESIZE] }) { aspect, on ->
                    if (on) panels[nearest(aspects, aspect)].first else null
                }.distinctUntilChanged().debounce(600).collect { target ->
                    if (target == null || target == mine) return@collect
                    Log.i(TAG, "relaunch $mine -> $target")
                    activity.startActivity(
                        Intent().setClassName(activity, target)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    )
                    activity.finishAndRemoveTask()
                }
            }
        }
    }
}
