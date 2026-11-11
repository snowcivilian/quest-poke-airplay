package io.github.snowcivilian.questairplay.theater

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import io.github.jqssun.airplay.IntPref
import io.github.jqssun.airplay.Pref
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.get

/** Plain-View control panel floating below the theater screen. 48dp+ targets for rays and hands. */
object TheaterControls {
    fun build(ctx: Context, prefs: SharedPreferences, onBack: () -> Unit): View {
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = GradientDrawable().apply { setColor(Color.argb(230, 24, 24, 28)); cornerRadius = dp(24).toFloat() }
        }
        fun text(s: String, size: Float = 16f) = TextView(ctx).apply {
            this.text = s; setTextColor(Color.WHITE); textSize = size
        }
        fun button(label: () -> String, onClick: () -> Unit) = Button(ctx).apply {
            text = label(); minHeight = dp(52); isAllCaps = false; textSize = 16f
            setOnClickListener { onClick(); text = label() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        }
        fun toggle(name: String, pref: Pref<Boolean>) = button({ "$name: ${if (prefs[pref]) "on" else "off"}" }) {
            pref.write(prefs, !prefs[pref])
        }

        root.addView(text("Theater", 22f))
        root.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
            addView(button({ "Back to panel" }, onBack))
            addView(toggle("Passthrough", Prefs.THEATER_PASSTHROUGH))
            addView(toggle("CRT", Prefs.CRT_ENABLED))
        })

        fun slider(label: String, pref: IntPref, min: Int, max: Int, unit: String) {
            val value = text("")
            fun show(v: Int) { value.text = "$label: $v$unit" }
            show(prefs[pref])
            root.addView(value)
            root.addView(SeekBar(ctx).apply {
                this.max = max - min
                progress = (prefs[pref] - min).coerceIn(0, max - min)
                minimumHeight = dp(48)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                        show(p + min)
                        if (fromUser && pref == Prefs.APP_VOLUME) pref.write(prefs, p + min) // volume is live
                    }
                    override fun onStartTrackingTouch(s: SeekBar) {}
                    // screen changes rebuild the panel, so apply them on release
                    override fun onStopTrackingTouch(s: SeekBar) { pref.write(prefs, s.progress + min) }
                })
            })
        }
        slider("Volume", Prefs.APP_VOLUME, 0, 100, "%")
        slider("Screen height", Prefs.THEATER_HEIGHT, 50, 600, " cm")
        slider("Distance", Prefs.THEATER_DISTANCE, 100, 1500, " cm")
        slider("Screen elevation", Prefs.THEATER_ELEVATION, 30, 250, " cm")
        slider("Curve", Prefs.THEATER_CURVE, 0, 100, "%")
        root.addView(text("Recenter: hold the Meta button on the right controller.", 14f).apply {
            setPadding(0, dp(8), 0, 0); gravity = Gravity.START; alpha = 0.7f
        })
        return root
    }
}
