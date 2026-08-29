package com.recall.app.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.recall.app.data.ReminderPrefs
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Decides when the reminder next fires.
 *
 * This uses a one-shot job that re-schedules itself rather than WorkManager's
 * PeriodicWorkRequest. Periodic work has a floor of 15 minutes and drifts — it
 * guarantees "once per interval", not "at 8pm". Computing the delay to the next
 * 8pm each time keeps it pinned to the clock, and handles the user changing the
 * time without leaving a stale repeating job behind.
 */
object ReminderScheduler {

    private const val WORK_NAME = "recall_daily_reminder"

    /** Call after any change to the settings, and on app start. */
    fun sync(context: Context) {
        val prefs = ReminderPrefs(context)
        if (prefs.enabled) scheduleNext(context) else cancel(context)
    }

    fun scheduleNext(context: Context) {
        val prefs = ReminderPrefs(context)
        if (!prefs.enabled) return

        val delay = millisUntilNext(prefs.hour, prefs.minute)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            // REPLACE so changing the time cancels the old pending run.
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Milliseconds from now until the next occurrence of hour:minute. */
    fun millisUntilNext(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
        val target = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Already past today? Then it means tomorrow.
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now
    }
}
