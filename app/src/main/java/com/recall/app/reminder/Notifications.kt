package com.recall.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object Notifications {

    const val CHANNEL_ID = "recall_due_reminder"
    const val NOTIFICATION_ID = 1001

    /**
     * Channels are how Android 8+ lets the user mute one kind of notification
     * without muting the app. Creating one that already exists is a no-op, so this
     * is safe to call on every launch.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily review reminder",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminds you when cards are due for review"
        }
        manager.createNotificationChannel(channel)
    }
}
