package com.recall.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recall.app.data.RecallDatabase

/**
 * Runs once a day in the background, counts what is due, and posts a notification
 * if there is anything to review.
 *
 * A Worker, not a plain thread: WorkManager persists the job to its own database,
 * so it survives the app being closed and the phone being rebooted, and it respects
 * Doze mode rather than fighting it. The trade is that the delivery time is
 * approximate — Android may hold the job back to batch it with other wakeups.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = RecallDatabase.get(applicationContext).dao()
        val due = runCatching { dao.dueCountNow() }.getOrElse { return Result.retry() }

        // Deliberately silent when nothing is due — a reminder that fires with
        // nothing to say trains you to ignore it.
        if (due > 0) Notifications.postDue(applicationContext, due)

        // Chain tomorrow's run before reporting success.
        ReminderScheduler.scheduleNext(applicationContext)
        return Result.success()
    }
}
