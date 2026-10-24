package com.rendyhd.vicu.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.rendyhd.vicu.data.local.RoutinePrefsStore
import com.rendyhd.vicu.domain.repository.RoutineRepository
import com.rendyhd.vicu.util.RoutineAlarmPlanner
import com.rendyhd.vicu.util.TimeSource
import kotlinx.coroutines.flow.first

class RoutineAlarmScheduler(
    private val context: Context,
    private val repository: RoutineRepository,
    private val prefsStore: RoutinePrefsStore,
    private val time: TimeSource,
) {
    companion object {
        private const val TAG = "RoutineAlarmScheduler"
        private const val HORIZON_DAYS = RoutineAlarmPlanner.ANDROID_HORIZON_DAYS
        private const val PREFS = "routine_alarm_ids"
        private const val KEY_IDS = "request_codes"
    }

    private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll() {
        cancelAll()
        // Paused from the Routines screen, or routines turned off in Settings.
        if (!prefsStore.remindersEnabled.first() || !prefsStore.enabled.first()) return

        val requestCodes = mutableSetOf<String>()
        // Not the routines this account hid or turned off, nor read-only ones (cross-app semantics 6.9).
        val routines = repository.observeReminderRoutines().first()
        RoutineAlarmPlanner.plan(routines, time.now(), time.zone(), HORIZON_DAYS).forEach { alarm ->
            schedule(alarm.occurrenceKey, alarm.title, alarm.date, alarm.slotId, alarm.triggerAt.toEpochMilliseconds(), alarm.followUp)
            requestCodes += requestCode(alarm.occurrenceKey, alarm.followUp).toString()
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putStringSet(KEY_IDS, requestCodes) }
        Log.d(TAG, "Scheduled ${requestCodes.size} routine alarms")
    }

    private fun schedule(
        occurrenceKey: String,
        title: String,
        date: String,
        slotId: String,
        triggerAtMillis: Long,
        followUp: Boolean,
    ) {
        val intent = Intent(context, RoutineAlarmReceiver::class.java).apply {
            putExtra(RoutineAlarmReceiver.EXTRA_OCCURRENCE_KEY, occurrenceKey)
            putExtra(RoutineAlarmReceiver.EXTRA_ROUTINE_ID, occurrenceKey.substringBefore(':'))
            putExtra(RoutineAlarmReceiver.EXTRA_DATE, date)
            putExtra(RoutineAlarmReceiver.EXTRA_SLOT_ID, slotId)
            putExtra(RoutineAlarmReceiver.EXTRA_TITLE, title)
            putExtra(RoutineAlarmReceiver.EXTRA_FOLLOW_UP, followUp)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(occurrenceKey, followUp),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    /** Cancels every routine alarm that is registered (sign-out, account switch). */
    fun cancelAll() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().forEach { raw ->
            val requestCode = raw.toIntOrNull() ?: return@forEach
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, RoutineAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
        prefs.edit { remove(KEY_IDS) }
    }

    private fun requestCode(occurrenceKey: String, followUp: Boolean): Int =
        "routine:$occurrenceKey:${if (followUp) 1 else 0}".hashCode()
}
