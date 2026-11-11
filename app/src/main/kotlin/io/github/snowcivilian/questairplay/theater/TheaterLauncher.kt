package io.github.snowcivilian.questairplay.theater

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Opens theater mode (R10). The immersive activity only exists in the quest flavor. */
object TheaterLauncher {
    const val THEATER_CLASS = "io.github.snowcivilian.questairplay.theater.TheaterActivity"

    fun available(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getActivityInfo(ComponentName(ctx, THEATER_CLASS), 0); true
    }.getOrDefault(false)

    /** Hybrid-app switch per Meta's docs: start the immersive activity, close the panel task. */
    fun enter(activity: Activity) {
        activity.startActivity(
            Intent().setClassName(activity, THEATER_CLASS)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        activity.finishAndRemoveTask()
    }
}
