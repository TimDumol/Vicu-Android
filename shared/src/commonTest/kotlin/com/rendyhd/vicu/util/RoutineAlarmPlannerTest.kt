package com.rendyhd.vicu.util

import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.model.Routine
import com.rendyhd.vicu.domain.model.RoutineDefinition
import com.rendyhd.vicu.domain.model.RoutineKind
import com.rendyhd.vicu.domain.model.RoutineOccurrenceRecord
import com.rendyhd.vicu.domain.model.RoutinePayload
import com.rendyhd.vicu.domain.model.RoutinePeriod
import com.rendyhd.vicu.domain.model.RoutineSchedule
import com.rendyhd.vicu.domain.model.RoutineSlot
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutineAlarmPlannerTest {
    private val zone = TimeZone.of("Europe/Amsterdam")

    private fun at(text: String) = LocalDateTime.parse(text).toInstant(zone)

    private fun routine(
        slot: RoutineSlot = RoutineSlot("morning", "Morning", RoutinePeriod.MORNING, reminderMinutes = 8 * 60, followUpMinutes = 30),
        occurrences: Map<String, RoutineOccurrenceRecord> = emptyMap(),
    ) = Routine(
        taskId = 7,
        payload = RoutinePayload(
            definition = RoutineDefinition(
                id = "vitamins",
                name = "Vitamins",
                kind = RoutineKind.HEALTH,
                schedule = RoutineSchedule.Calendar(anchorDate = "2026-10-01"),
                slots = listOf(slot),
                activeFrom = "2026-10-01",
                createdAt = "2026-10-01T08:00:00Z",
                updatedAt = "2026-10-01T08:00:00Z",
                updatedBy = "phone",
            ),
            occurrences = occurrences,
        ),
    )

    @Test
    fun aDailyRoutineRingsEachDayWithItsFollowUp() {
        val specs = RoutineAlarmPlanner.plan(listOf(routine()), at("2026-10-12T07:00"), zone, horizonDays = 1)
        assertEquals(
            listOf("2026-10-12T08:00" to false, "2026-10-12T08:30" to true, "2026-10-13T08:00" to false, "2026-10-13T08:30" to true),
            specs.map { it.localTriggerAt.toString() to it.followUp },
        )
        assertEquals(at("2026-10-12T08:00"), specs.first().triggerAt)
        assertEquals("vitamins", specs.first().routineId)
        assertEquals("Vitamins", specs.first().title)
        assertEquals("2026-10-12", specs.first().date)
    }

    @Test
    fun aReminderInThePastIsLeftOutWithItsFollowUp() {
        // 08:10: the 08:00 reminder has rung; its 08:30 follow-up is left out with it, as on Android.
        val specs = RoutineAlarmPlanner.plan(listOf(routine()), at("2026-10-12T08:10"), zone, horizonDays = 0)
        assertTrue(specs.isEmpty())
    }

    @Test
    fun aFinishedOccurrenceAndASlotWithoutReminderRingNot() {
        val done = RoutineOccurrenceRecord(
            key = "vitamins:2026-10-12:morning",
            routineId = "vitamins",
            slotId = "morning",
            scheduledDate = "2026-10-12",
            scheduledMinutes = 480,
            timeZoneId = "Europe/Amsterdam",
            status = OccurrenceStatus.COMPLETED,
            modifiedAt = "2026-10-12T06:00:00Z",
            modifiedBy = "phone",
        )
        val now = at("2026-10-12T07:00")
        assertEquals(
            listOf("2026-10-13T08:00", "2026-10-13T08:30"),
            RoutineAlarmPlanner.plan(listOf(routine(occurrences = mapOf(done.key to done))), now, zone, 1).map { it.localTriggerAt.toString() },
        )
        val quiet = RoutineSlot("morning", "Morning", RoutinePeriod.MORNING, reminderEnabled = false)
        assertTrue(RoutineAlarmPlanner.plan(listOf(routine(slot = quiet)), now, zone, 3).isEmpty())
    }

    @Test
    fun noFollowUpWhenTheSlotHasNone() {
        val slot = RoutineSlot("evening", "Evening", RoutinePeriod.EVENING, reminderMinutes = 21 * 60, followUpMinutes = 0)
        val specs = RoutineAlarmPlanner.plan(listOf(routine(slot = slot)), at("2026-10-12T07:00"), zone, 0)
        assertEquals(listOf("2026-10-12T21:00"), specs.map { it.localTriggerAt.toString() })
    }

    @Test
    fun theLocalTimeStaysAcrossADaylightSavingChange() {
        // Clocks go back on 25 October 2026 in Amsterdam: the reminder stays at 08:00 local.
        val specs = RoutineAlarmPlanner.plan(listOf(routine()), at("2026-10-24T09:00"), zone, 1)
            .filter { !it.followUp }
        assertEquals(listOf("2026-10-25T08:00"), specs.map { it.localTriggerAt.toString() })
        assertEquals(at("2026-10-25T08:00"), specs.single().triggerAt)
    }
}
