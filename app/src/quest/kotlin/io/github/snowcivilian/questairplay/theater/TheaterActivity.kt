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
import androidx.compose.ui.platform.ComposeView
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.Quaternion
import com.meta.spatial.toolkit.Grabbable
import com.meta.spatial.toolkit.GrabbableType
import com.meta.spatial.isdk.IsdkGrabbable
import com.meta.spatial.isdk.IsdkPanelDimensions
import com.meta.spatial.isdk.IsdkPanelGrabHandle
import com.meta.spatial.isdk.IsdkPanelResize
import com.meta.spatial.toolkit.Visible
import io.github.snowcivilian.questairplay.pokemon.PokemonEngine
import io.github.snowcivilian.questairplay.ui.TheaterControlsPanel
import io.github.snowcivilian.questairplay.ui.TheaterPokemonCard
import io.github.snowcivilian.questairplay.ui.TheaterControlsPill
import com.meta.spatial.vr.VRFeature
import io.github.jqssun.airplay.MainActivity
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.get
import io.github.jqssun.airplay.observe
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
    private var controls: Entity? = null
    private var pill: Entity? = null
    private var spec: ScreenSpec? = null // computed once the activity has a Context
    private var sceneReady = false
    private var pokemonCard: Entity? = null // S4: battle card beside the screen

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

    override fun registerFeatures(): List<SpatialFeature> = listOf(VRFeature(this), ComposeFeature())

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
        ComposeViewPanelRegistration(
            R.id.theater_controls,
            { _, ctx -> ComposeView(ctx).apply { setContent { TheaterControlsPanel(::backToPanel) { showControls(false) } } } },
            { _ -> UIPanelSettings(shape = QuadShapeOptions(width = 0.5f, height = 0.56f), display = DpPerMeterDisplayOptions()) },
        ),
        ComposeViewPanelRegistration(
            R.id.theater_controls_pill,
            { _, ctx -> ComposeView(ctx).apply { setContent { TheaterControlsPill({ showControls(true) }, ::backToPanel) } } },
            { _ -> UIPanelSettings(shape = QuadShapeOptions(width = 0.34f, height = 0.075f), display = DpPerMeterDisplayOptions()) },
        ),
        ComposeViewPanelRegistration(
            R.id.theater_pokemon_card,
            { _, ctx -> ComposeView(ctx).apply { setContent { TheaterPokemonCard() } } },
            { _ -> UIPanelSettings(shape = QuadShapeOptions(width = 0.55f, height = 0.8f), display = DpPerMeterDisplayOptions()) },
        ),
    )

    /** Minimize swaps the full controls for a small pill at the same spot; both can be grabbed and moved. */
    private fun showControls(full: Boolean) {
        val from = if (full) pill else controls
        val to = if (full) controls else pill
        from?.tryGetComponent<Transform>()?.let { to?.setComponent(it) }
        controls?.setComponent(Visible(full))
        pill?.setComponent(Visible(!full))
    }

    override fun onSceneReady() {
        super.onSceneReady()
        sceneReady = true
        // LOCAL_FLOOR: the system recenter (hold the Meta button) works out of the box
        scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
        scene.enablePassthrough(prefs[Prefs.THEATER_PASSTHROUGH])
        spawnPokemonCard()
        rebuildScreen()
        // Low and centered below the line of sight, tilted up like a laptop (Quaternion = pitch, yaw, roll
        // in degrees; positive pitch faces up, as in Meta's SpatialVideoSample). VRFeature defaults to
        // Interaction SDK input: Isdk* components give the handles and resizing, and ISDK only enables
        // moving when a toolkit Grabbable is also present (IsdkComponentCreationSystem maps its type).
        val spot = Transform(Pose(Vector3(0f, 0.65f, 0.65f), Quaternion(35f, 0f, 0f)))
        controls = Entity.create(Panel(R.id.theater_controls), spot, Visible(true), Grabbable(true, GrabbableType.FACE),
            IsdkPanelDimensions(), IsdkGrabbable(), IsdkPanelGrabHandle(), IsdkPanelResize())
        pill = Entity.create(Panel(R.id.theater_controls_pill), spot, Visible(false), Grabbable(true, GrabbableType.FACE),
            IsdkPanelDimensions(), IsdkGrabbable(), IsdkPanelGrabHandle())
        scope.launch {
            VideoController.displayAspect.collect { rebuildScreen() }
        }
    }

    /** S4: battle card floating to the right of the screen, turned toward the viewer; shown only during a battle. */
    private fun spawnPokemonCard() {
        val sp = currentSpec()
        val x = sp.widthM / 2f + 0.4f
        val z = sp.distanceM * 0.75f
        val yaw = Math.toDegrees(kotlin.math.atan2(x.toDouble(), z.toDouble())).toFloat()
        pokemonCard = Entity.create(Panel(R.id.theater_pokemon_card),
            Transform(Pose(Vector3(x, sp.elevationM, z), Quaternion(0f, yaw, 0f))), Visible(false),
            Grabbable(true, GrabbableType.FACE), IsdkPanelDimensions(), IsdkGrabbable(), IsdkPanelGrabHandle(), IsdkPanelResize())
        scope.launch {
            kotlinx.coroutines.flow.combine(PokemonEngine.card,
                prefs.observe { this[Prefs.POKEMON_HELPER] to this[Prefs.POKEMON_CARD_SHOWN] }) { card, (on, shown) ->
                card != null && (card.demo || (on && shown))
            }
                .collect { pokemonCard?.setComponent(Visible(it)) }
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
