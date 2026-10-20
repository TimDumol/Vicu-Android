package com.rendyhd.vicu.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rendyhd.vicu.MainActivity
import com.rendyhd.vicu.R

/**
 * A task's reminder notification, with Mark Complete and Snooze: the same for a reminder at a time
 * (AlarmReceiver) and one at a place (AndroidLocationNotifier). Its id is the task's, so completing
 * the task from it, or a later reminder of the same task, replaces it.
 */
internal object TaskReminderNotification {
    private const val TAG = "TaskReminderNotif"

    fun post(context: Context, taskId: Long, title: String, text: String, sound: Boolean) {
        // Tap notification → open task detail
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
        }
        val tapPending = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // "Mark Complete" action
        val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_COMPLETE
            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
        }
        val completePending = PendingIntent.getBroadcast(
            context,
            taskId.toInt() * 10 + 1,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // "Snooze 15min" action
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
            putExtra(AlarmReceiver.EXTRA_TASK_TITLE, title)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            taskId.toInt() * 10 + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_TASK_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .addAction(0, "Mark Complete", completePending)
            .addAction(0, "Snooze 15min", snoozePending)
            .apply {
                if (!sound) {
                    setSilent(true)
                }
            }
            .build()

        try {
            NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing POST_NOTIFICATIONS permission", e)
        }
    }
}
