package com.recall.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.recall.app.MainActivity
import com.recall.app.R

object Notifications {

    const val CHANNEL_ID = "recall_due_reminder"
    private const val NOTIFICATION_ID = 1001

    /**
     * Channels are how Android 8+ lets the user mute one kind of notification
     * without muting the app. Creating one that already exists is a no-op, so this
     * is safe to call on every launch.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Daily review reminder",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Reminds you when cards are due for review" }
        )
    }

    /** True when Android will actually let us post. */
    fun canPost(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun postDue(context: Context, due: Int) {
        val plural = if (due == 1) "card is" else "cards are"
        post(context, "Time to review", "$due $plural due in Recall.")
    }

    /**
     * Fires immediately, whatever is or is not due.
     *
     * The real reminder deliberately stays silent when nothing is due, which makes
     * it impossible to tell "working, nothing to say" apart from "broken". This
     * exists so that question has an answer.
     */
    fun postTest(context: Context) {
        post(
            context,
            "Recall notifications work",
            "This is a test. Real reminders only arrive when cards are due."
        )
    }

    private fun post(context: Context, title: String, body: String) {
        ensureChannel(context)
        if (!canPost(context)) return

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
