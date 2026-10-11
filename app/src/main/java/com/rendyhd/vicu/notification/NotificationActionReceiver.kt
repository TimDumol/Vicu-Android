package com.rendyhd.vicu.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.rendyhd.vicu.util.NotificationActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    companion object {
        private const val TAG = "NotifActionReceiver"
        const val ACTION_COMPLETE = "com.rendyhd.vicu.ACTION_COMPLETE"
        const val ACTION_SNOOZE = "com.rendyhd.vicu.ACTION_SNOOZE"
    }

    private val actions: NotificationActions by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(AlarmReceiver.EXTRA_TASK_ID, 0L)
        if (taskId == 0L) return

        // Dismiss the notification
        NotificationManagerCompat.from(context).cancel(taskId.toInt())

        when (intent.action) {
            ACTION_COMPLETE -> handleComplete(taskId)
            ACTION_SNOOZE -> handleSnooze(taskId, intent)
        }
    }

    private fun handleComplete(taskId: Long) {
        Log.d(TAG, "Completing task $taskId")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Stored and queued, not sent (see NotificationActions.completeTask): the queued
                // change triggers a sync, which WorkManager runs even if this process is gone.
                actions.completeTask(taskId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to complete task $taskId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleSnooze(taskId: Long, intent: Intent) {
        val taskTitle = intent.getStringExtra(AlarmReceiver.EXTRA_TASK_TITLE) ?: "Task Reminder"
        Log.d(TAG, "Snoozing task $taskId for 15 minutes")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                actions.snoozeTask(taskId, taskTitle)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
