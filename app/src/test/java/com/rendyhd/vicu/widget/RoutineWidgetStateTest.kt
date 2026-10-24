package com.rendyhd.vicu.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.model.Routine
import com.rendyhd.vicu.domain.model.RoutineDay
import com.rendyhd.vicu.domain.model.RoutineDefinition
import com.rendyhd.vicu.domain.model.RoutineKind
import com.rendyhd.vicu.domain.model.RoutineOccurrence
import com.rendyhd.vicu.domain.model.RoutinePayload
import com.rendyhd.vicu.domain.model.RoutinePeriod
import com.rendyhd.vicu.domain.model.RoutineSchedule
import com.rendyhd.vicu.domain.model.RoutineSlot
import com.rendyhd.vicu.util.DateDisplayFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineWidgetStateTest {
    @Test
    fun statusUpdateChangesOnlyTheSelectedOccurrence() {
        val first = item("routine-1", "morning")
        val second = item("routine-2", "evening")
        val state = RoutineWidgetState(
            date = DATE,
            occurrences = listOf(first, second),
        )

        val updated = state.withStatus(
            routineId = first.routineId,
            date = first.scheduledDate,
            slotId = first.slotId,
            status = OccurrenceStatus.COMPLETED,
        )

        assertEquals(OccurrenceStatus.COMPLETED, updated.occurrences[0].status)
        assertEquals(OccurrenceStatus.PENDING, updated.occurrences[1].status)
        assertEquals(1, updated.completedCount)
    }

    @Test
    fun completedOccurrencesAreHiddenFromTheWidgetList() {
        val completed = item("routine-1", "morning", OccurrenceStatus.COMPLETED)
        val pending = item("routine-2", "evening")
        val state = RoutineWidgetState(DATE, listOf(completed, pending))

        assertEquals(listOf(pending), state.visibleOccurrences)
        assertFalse(state.isAllDone)
    }

    @Test
    fun allDoneRequiresAtLeastOneScheduledOccurrenceAndNoVisibleOccurrences() {
        assertFalse(RoutineWidgetState(date = DATE).isAllDone)

        val state = RoutineWidgetState(
            DATE,
            listOf(
                item("routine-1", "morning", OccurrenceStatus.COMPLETED),
                item("routine-2", "evening", OccurrenceStatus.COMPLETED),
            ),
        )

        assertTrue(state.isAllDone)
        assertTrue(state.visibleOccurrences.isEmpty())
    }

    @Test
    fun widgetStateSerializationRoundTrips() {
        val state = RoutineWidgetState(
            date = DATE,
            occurrences = listOf(item("routine-1", "morning").copy(color = "#e8384f")),
        )
        val prefs = mutablePreferencesOf(
            RoutineWidgetStateDefinition.KEY_STATE to
                RoutineWidgetStateDefinition.encodeState(state),
        )

        assertEquals(state, RoutineWidgetStateDefinition.parseState(prefs))
    }

    @Test
    fun widgetItemsCarryTheRoutineColour() {
        val slot = RoutineSlot(id = "morning", label = "08:00", period = RoutinePeriod.MORNING, reminderMinutes = 8 * 60)
        val definition = RoutineDefinition(
            id = "routine-1",
            name = "Vitamins",
            kind = RoutineKind.HEALTH,
            color = "#AF52DE",
            schedule = RoutineSchedule.Calendar(anchorDate = DATE),
            slots = listOf(slot),
            activeFrom = DATE,
            createdAt = "",
            updatedAt = "",
            updatedBy = "",
        )
        val routine = Routine(taskId = 1, payload = RoutinePayload(definition = definition))
        val day = RoutineDay(DATE, listOf(RoutineOccurrence(routine, "routine-1:$DATE:morning", slot, DATE, OccurrenceStatus.PENDING)))

        val state = RoutineWidgetState.from(day, DateDisplayFormat("en-GB", hour12 = false))

        assertEquals("#AF52DE", state.occurrences.single().color)
        assertEquals("08:00", state.occurrences.single().slotLabel)
    }

    private fun item(
        routineId: String,
        slotId: String,
        status: OccurrenceStatus = OccurrenceStatus.PENDING,
    ) = RoutineWidgetItem(
        key = "$routineId:$DATE:$slotId",
        routineId = routineId,
        routineName = "Routine $routineId",
        scheduledDate = DATE,
        slotId = slotId,
        slotLabel = slotId,
        status = status,
    )

    private companion object {
        const val DATE = "2026-08-11"
    }
}
