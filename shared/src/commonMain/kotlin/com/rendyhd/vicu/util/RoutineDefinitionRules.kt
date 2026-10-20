package com.rendyhd.vicu.util

import com.rendyhd.vicu.domain.model.RoutineKind
import com.rendyhd.vicu.domain.model.RoutinePeriod
import com.rendyhd.vicu.domain.model.RoutineSchedule
import com.rendyhd.vicu.domain.model.RoutineSlot

/**
 * How a routine's definition is written (docs/cross-app-semantics-v1.md, section 6.7). The user
 * sees a name, times and a schedule; the kind, the label and the period of each time are still
 * written, because older app versions need them to read and edit the routine.
 */
object RoutineDefinitionRules {
    /** Older editors keep one time per period, and there are five periods. */
    const val MAX_TIMES = 5

    private val PERIOD_ORDER = listOf(
        RoutinePeriod.MORNING,
        RoutinePeriod.AFTERNOON,
        RoutinePeriod.EVENING,
        RoutinePeriod.ANYTIME,
        RoutinePeriod.HOME,
    )

    /** CHORE for an after-completion schedule; otherwise the stored kind, HEALTH for a new routine. */
    fun kindFor(schedule: RoutineSchedule, stored: RoutineKind?): RoutineKind =
        if (schedule is RoutineSchedule.AfterCompletion) RoutineKind.CHORE else stored ?: RoutineKind.HEALTH

    /** A time as the label older apps show: `HH:MM`, 24-hour. */
    fun label(minutes: Int): String =
        "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"

    /** The period a time falls in: before 12:00 morning, before 17:00 afternoon, otherwise evening. */
    fun periodFor(minutes: Int): RoutinePeriod = when {
        minutes < 12 * 60 -> RoutinePeriod.MORNING
        minutes < 17 * 60 -> RoutinePeriod.AFTERNOON
        else -> RoutinePeriod.EVENING
    }

    /**
     * The slots in time order, each labelled with its time and given a period no earlier slot has
     * (its own period when free, else the first free one of [PERIOD_ORDER]). Ids, reminders,
     * follow-ups and unknown fields are kept.
     */
    fun normalizeSlots(slots: List<RoutineSlot>): List<RoutineSlot> {
        val taken = mutableSetOf<RoutinePeriod>()
        return slots.sortedBy { it.reminderMinutes }.map { slot ->
            val own = periodFor(slot.reminderMinutes)
            val period = if (own !in taken) own else PERIOD_ORDER.firstOrNull { it !in taken } ?: own
            taken += period
            slot.copy(label = label(slot.reminderMinutes), period = period)
        }
    }
}
