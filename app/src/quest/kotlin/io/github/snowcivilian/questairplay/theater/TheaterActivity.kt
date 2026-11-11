package io.github.snowcivilian.questairplay.theater

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Surface
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.CylinderShapeOptions
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.MediaPanelSettings
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PixelDisplayOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.VideoSurfacePanelRegistration
import com.meta.spatial.toolkit.ViewPanelRegistration
import com.meta.spatial.vr.VRFeature
import io.github.jqssun.airplay.MainActivity
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.get
import io.github.jqssun.airplay.service.AirPlayService
import io.github.snowcivilian.questairplay.panel.PanelSwitcher
import io.github.snowcivilian.questairplay.video.VideoController
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Immersive theater mode (R10), the hybrid app's VR activity (Meta Spatial SDK 0.15.0).
 * The AirPlay session stays in AirPlayService; this activity only supplies a new display surface
 * for upstream's VideoPipeline, so presets, crop and the CRT shader apply here too.
 */
class TheaterActivity : AppSystemActivity() {
    private val scope = MainScope()
    private val prefs: SharedPreferences by lazy { getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE) }
    private var service: AirPlayService? = null
    private var surface: Surface? = null
    private var screen: Entity? = null
    private var spec: ScreenSpec? = null // computed once the activity has a Context
    private var sceneReady = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? AirPlayService.LocalBinder)?.service
            surface?.let { service?.setVideoSurface(it) }
        }
        override fun onServiceDisconnected(name: ComponentName?) { service = null }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Prefs.THEATER_PASSTHROUGH.key -> if (sceneReady) scene.enablePassthrough(prefs[Prefs.THEATER_PASSTHROUGH])
            Prefs.THEATER_HEIGHT.key, Prefs.THEATER_DISTANCE.key,
            Prefs.THEATER_ELEVATION.key, Prefs.THEATER_CURVE.key -> rebuildScreen()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindService(Intent(this, AirPlayService::class.java), connection, BIND_AUTO_CREATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun registerFeatures(): List<SpatialFeature> = listOf(VRFeature(this))

    override fun registerPanels(): List<PanelRegistration> = listOf(
        VideoSurfacePanelRegistration(
            R.id.theater_screen,
            surfaceConsumer = { _, s -> onScreenSurface(s) },
            settingsCreator = { _ ->
                val sp = spec ?: currentSpec().also { spec = it }
                MediaPanelSettings(
                    shape = sp.radiusM?.let { CylinderShapeOptions(radius = it, width = sp.widthM, height = sp.heightM) }
                        ?: QuadShapeOptions(width = sp.widthM, height = sp.heightM),
                    display = PixelDisplayOptions(width = sp.pixelW, height = sp.pixelH),
                )
            },
        ),
        ViewPanelRegistration(
            R.id.theater_controls,
            dynamicViewCreator = { _, ctx -> TheaterControls.build(ctx, prefs, ::backToPanel) },
            settingsCreator = { _ ->
                UIPanelSettings(shape = QuadShapeOptions(width = 0.62f, height = 0.62f), display = DpPerMeterDisplayOptions())
            },
        ),
    )

    override fun onSceneReady() {
        super.onSceneReady()
        sceneReady = true
        // LOCAL_FLOOR: the system recenter (hold the Meta button) works out of the box
        scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
        scene.enablePassthrough(prefs[Prefs.THEATER_PASSTHROUGH])
        rebuildScreen()
        Entity.create(Panel(R.id.theater_controls), Transform(Pose(Vector3(0f, 0.9f, 0.9f))))
        scope.launch {
            VideoController.displayAspect.collect { rebuildScreen() }
        }
    }

    private fun currentSpec() = TheaterGeometry.screen(
        VideoController.displayAspect.value,
        prefs[Prefs.THEATER_HEIGHT], prefs[Prefs.THEATER_DISTANCE],
        prefs[Prefs.THEATER_ELEVATION], prefs[Prefs.THEATER_CURVE],
    )

    /** Shape and pixel size are fixed when a panel is created, so changes replace the entity. */
    private fun rebuildScreen() {
        if (!sceneReady) return
        val next = currentSpec()
        if (next == spec && screen != null) return
        spec = next
        Log.i(TAG, "screen $next")
        val old = screen
        screen = Entity.create(Panel(R.id.theater_screen), Transform(Pose(Vector3(0f, next.elevationM, next.distanceM))))
        old?.destroy()
    }

    private fun onScreenSurface(s: Surface) {
        surface = s
        service?.setVideoSurface(s)
    }

    /** Back to the 2D panel through Home, per Meta's hybrid app guide. */
    private fun backToPanel() {
        val panel = Intent().setClassName(this, PanelSwitcher.bestPanelClass(this) ?: MainActivity::class.java.name)
            .setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(this, 0, panel, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("extra_launch_in_home_pending_intent", pending)
        )
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        surface?.let { service?.clearVideoSurface(it) }
        runCatching { unbindService(connection) }
        scope.cancel()
        super.onDestroy()
    }

    private companion object { const val TAG = "Theater" }
}
