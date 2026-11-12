package io.github.snowcivilian.questairplay.theater

import android.content.SharedPreferences

/**
 * S1: remembers where the user moved the DS bottom screen, as position (x, y, z) and rotation
 * quaternion (w, x, y, z) in LOCAL_FLOOR space. Stored outside upstream's registered prefs.
 */
object TheaterLayoutStore {
    const val KEY_BOTTOM_POSE = "theater_ds_bottom_pose"

    fun bottomPose(prefs: SharedPreferences): FloatArray? =
        prefs.getString(KEY_BOTTOM_POSE, null)?.split(',')?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 7 }?.toFloatArray()

    fun saveBottomPose(prefs: SharedPreferences, pose: FloatArray) {
        require(pose.size == 7)
        prefs.edit().putString(KEY_BOTTOM_POSE, pose.joinToString(",")).apply()
    }

    fun resetBottomPose(prefs: SharedPreferences) = prefs.edit().remove(KEY_BOTTOM_POSE).apply()
}
