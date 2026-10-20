package com.rendyhd.vicu.util

import com.rendyhd.vicu.domain.model.RoutineKind
import com.rendyhd.vicu.domain.model.RoutinePeriod
import com.rendyhd.vicu.domain.model.RoutineSchedule
import com.rendyhd.vicu.domain.model.RoutineSlot
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutineDefinitionRulesTest {

    private val calendar = RoutineSchedule.Calendar(anchorDate = "2026-10-06")
    private val afterCompletion = RoutineSchedule.AfterCompletion(intervalDays = 14, firstDueDate = "2026-10-06")

    @Test
    fun `an after-completion routine is a chore, otherwise the stored kind or health`() {
        assertEquals(RoutineKind.CHORE, RoutineDefinitionRules.kindFor(afterCompletion, stored = null))
        assertEquals(RoutineKind.CHORE, RoutineDefinitionRules.kindFor(afterCompletion, stored = RoutineKind.HEALTH))
        assertEquals(RoutineKind.HEALTH, RoutineDefinitionRules.kindFor(calendar, stored = null))
        assertEquals(RoutineKind.CHORE, RoutineDefinitionRules.kindFor(calendar, stored = RoutineKind.CHORE))
    }

    @Test
    fun `a time is labelled HH MM in 24 hours`() {
        assertEquals("08:05", RoutineDefinitionRules.label(8 * 60 + 5))
        assertEquals("00:00", RoutineDefinitionRules.label(0))
        assertEquals("23:59", RoutineDefinitionRules.label(23 * 60 + 59))
    }

    @Test
    fun `slots get their time's period and never share one`() {
        val slots = listOf(
            slot("c", 20 * 60),
            slot("a", 7 * 60),
            slot("b", 8 * 60),
            slot("d", 21 * 60),
            slot("e", 13 * 60),
        )

        val normalized = RoutineDefinitionRules.normalizeSlots(slots)

        assertEquals(listOf("a", "b", "e", "c", "d"), normalized.map { it.id })
        assertEquals(
            listOf(
                RoutinePeriod.MORNING,
                RoutinePeriod.AFTERNOON,
                RoutinePeriod.EVENING,
                RoutinePeriod.ANYTIME,
                RoutinePeriod.HOME,
            ),
            listOf("a", "b", "e", "c", "d").map { id -> normalized.single { it.id == id }.period },
        )
        assertEquals(listOf("07:00", "08:00", "13:00", "20:00", "21:00"), normalized.map { it.label })
    }

    @Test
    fun `normalizing keeps what the slot carries besides label and period`() {
        val extra = buildJsonObject { put("color", JsonPrimitive("red")) }
        val slot = RoutineSlot(
            id = "s1",
            label = "Morning",
            period = RoutinePeriod.HOME,
            reminderMinutes = 15 * 60,
            reminderEnabled = false,
            followUpMinutes = 15,
            unknown = extra,
        )

        val normalized = RoutineDefinitionRules.normalizeSlots(listOf(slot)).single()

        assertEquals(slot.copy(label = "15:00", period = RoutinePeriod.AFTERNOON), normalized)
    }

    private fun slot(id: String, minutes: Int) =
        RoutineSlot(id = id, label = "", period = RoutinePeriod.MORNING, reminderMinutes = minutes)
}
