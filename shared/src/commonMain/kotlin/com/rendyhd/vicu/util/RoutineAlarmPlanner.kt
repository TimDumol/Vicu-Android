package com.rendyhd.vicu.util

import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.model.Routine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** One routine reminder that should ring: an open occurrence's reminder, or its follow-up. */
data class RoutineAlarmSpec(
    val occurrenceKey: String,
    val routineId: String,
    val title: String,
    /** The occurrence's scheduled date (yyyy-mm-dd). */
    val date: String,
    val slotId: String,
    val triggerAt: Instant,
    /** The local wall-clock time it rings at in the zone it was planned in. */
    val localTriggerAt: LocalDateTime,
    val followUp: Boolean,
)

/**
 * The routine reminders to set, from today through [horizonDays] days ahead: one for every
 * pending occurrence whose slot has its reminder on, at the slot's local time, and its follow-up
 * when the slot has one. Reminders already in the past are left out, and so is the follow-up of a
 * reminder in the past. Pure, so Android (45 days of alarms) and iOS (what the notification budget
 * allows) plan the same reminders.
 */
object RoutineAlarmPlanner {
    const val ANDROID_HORIZON_DAYS = 45

    fun plan(routines: List<Routine>, now: Instant, zone: TimeZone, horizonDays: Int): List<RoutineAlarmSpec> {
        val today = now.toLocalDateTime(zone).date
        val specs = mutableListOf<RoutineAlarmSpec>()
        for (routine in routines) {
            for (offset in 0..horizonDays) {
                val date = today.plus(offset, DateTimeUnit.DAY)
                RoutineScheduleEngine.occurrencesForDate(routine, date, zone, today)
                    .filter { it.status == OccurrenceStatus.PENDING && it.slot.reminderEnabled }
                    .forEach { occurrence ->
                        val hour = (occurrence.slot.reminderMinutes / 60).coerceIn(0, 23)
                        val minute = (occurrence.slot.reminderMinutes % 60).coerceIn(0, 59)
                        val local = LocalDateTime(date, LocalTime(hour, minute))
                        val trigger = local.toInstant(zone)
                        if (trigger <= now) return@forEach
                        fun spec(at: Instant, localAt: LocalDateTime, followUp: Boolean) = RoutineAlarmSpec(
                            occurrenceKey = occurrence.key,
                            routineId = occurrence.key.substringBefore(':'),
                            title = routine.definition.name,
                            date = occurrence.scheduledDate,
                            slotId = occurrence.slot.id,
                            triggerAt = at,
                            localTriggerAt = localAt,
                            followUp = followUp,
                        )
                        specs += spec(trigger, local, followUp = false)
                        if (occurrence.slot.followUpMinutes > 0) {
                            val followUp = trigger + occurrence.slot.followUpMinutes.minutes
                            specs += spec(followUp, followUp.toLocalDateTime(zone), followUp = true)
                        }
                    }
            }
        }
        return specs
    }
}
