package com.recall.app.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recall.app.MainActivity
import com.recall.app.R
import com.recall.app.data.RecallDatabase

/**
 * Runs once a day in the background, counts what is due, and posts a notification
 * if there is anything to review.
 *
 * A Worker, not a plain thread: WorkManager persists the job to its own database,
 * so it survives the app being closed and the phone being rebooted, and it respects
 * Doze mode rather than fighting it.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = RecallDatabase.get(applicationContext).dao()
        val due = runCatching { dao.dueCountNow() }.getOrElse { return Result.retry() }

        // Nothing to nag about. Still reschedule tomorrow.
        if (due > 0) notifyDue(due)

        ReminderScheduler.scheduleNext(applicationContext)
        return Result.success()
    }

    private fun notifyDue(due: Int) {
        val context = applicationContext

        // On Android 13+ the user can refuse notifications; posting without the
        // permission throws. Check rather than assume.
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val plural = if (due == 1) "card is" else "cards are"
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to review")
            .setContentText("$due $plural due in Recall.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(Notifications.NOTIFICATION_ID, notification)
        }
    }
}
