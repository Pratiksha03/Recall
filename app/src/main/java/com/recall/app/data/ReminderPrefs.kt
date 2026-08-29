package com.recall.app.data

import android.content.Context

/**
 * Where the reminder settings live.
 *
 * SharedPreferences rather than Room: this is three values, read once at startup,
 * with no queries and no relationships. Putting it in the database would mean a
 * table, a DAO, and a migration for something a key-value file does better.
 */
class ReminderPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("recall_reminder", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var hour: Int
        get() = prefs.getInt(KEY_HOUR, 20)      // 8pm by default
        set(value) = prefs.edit().putInt(KEY_HOUR, value).apply()

    var minute: Int
        get() = prefs.getInt(KEY_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_MINUTE, value).apply()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
    }
}
