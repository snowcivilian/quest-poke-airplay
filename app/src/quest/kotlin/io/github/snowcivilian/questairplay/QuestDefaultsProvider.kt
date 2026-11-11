package io.github.snowcivilian.questairplay

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import io.github.jqssun.airplay.Prefs

/**
 * Runs before Application.onCreate (content providers are created first), so the service and UI
 * already see Quest defaults on first launch. Declared only in the quest flavor's manifest.
 */
class QuestDefaultsProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        QuestDefaults.seed(ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE))
        return true
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
