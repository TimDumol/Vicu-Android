package com.rendyhd.vicu.data.repository

import com.rendyhd.vicu.data.repository.RoutineRig.Companion.definition
import com.rendyhd.vicu.data.repository.RoutineRig.Companion.payload
import com.rendyhd.vicu.domain.model.HealthSubtype
import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.model.RoutineDraft
import com.rendyhd.vicu.domain.model.RoutineKind
import com.rendyhd.vicu.domain.model.RoutinePeriod
import com.rendyhd.vicu.domain.model.RoutineSchedule
import com.rendyhd.vicu.domain.model.RoutineSlot
import com.rendyhd.vicu.util.NetworkResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Routines have one type in the editor, but every write keeps the fields older app versions read
 * (docs/cross-app-semantics-v1.md, section 6.7).
 */
class RoutineDefinitionWriteTest {

    private fun newSlot(minutes: Int) =
        RoutineSlot(id = "", label = "", period = RoutinePeriod.MORNING, reminderMinutes = minutes, followUpMinutes = 0)

    @Test
    fun `a new routine on a schedule is written as a health routine with labelled times`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn() }

        val result = rig.repository.create(
            RoutineDraft(
                name = "  Stretch ",
                schedule = RoutineSchedule.Calendar(anchorDate = "2026-10-06"),
                slots = listOf(newSlot(20 * 60), newSlot(8 * 60)),
            ),
        )

        assertIs<NetworkResult.Success<*>>(result)
        val written = rig.serverPayload(rig.server.rows.values.single().id).definition
        assertEquals("Stretch", written.name)
        assertEquals(RoutineKind.HEALTH, written.kind)
        assertNull(written.healthSubtype)
        assertEquals("", written.amount)
        assertEquals("", written.unit)
        assertEquals(listOf("08:00", "20:00"), written.slots.map { it.label })
        assertEquals(listOf(RoutinePeriod.MORNING, RoutinePeriod.EVENING), written.slots.map { it.period })
        assertTrue(written.slots.all { it.id.isNotBlank() })
    }

    @Test
    fun `a new after-completion routine is written as a chore`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn() }

        rig.repository.create(
            RoutineDraft(
                name = "Water the plants",
                schedule = RoutineSchedule.AfterCompletion(intervalDays = 3, firstDueDate = "2026-10-06"),
                slots = listOf(newSlot(18 * 60)),
            ),
        )

        assertEquals(RoutineKind.CHORE, rig.serverPayload(rig.server.rows.values.single().id).definition.kind)
    }

    @Test
    fun `an edit keeps slot ids, follow-ups and unknown fields, and clears amount and unit`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn() }
        val extra = buildJsonObject { put("sound", JsonPrimitive("chime")) }
        val stored = definition().let {
            it.copy(
                amount = "1",
                unit = "pill",
                slots = listOf(it.slots.single().copy(followUpMinutes = 15, unknown = extra)),
            )
        }
        rig.seedCarrier(100, payload(definition = stored))

        rig.repository.update(
            RoutineRig.ROUTINE_ID,
            RoutineDraft(
                name = "Vitamin D",
                schedule = stored.schedule,
                slots = listOf(stored.slots.single().copy(reminderMinutes = 9 * 60 + 30)),
            ),
        )

        val written = rig.serverPayload(100).definition
        val slot = written.slots.single()
        assertEquals(RoutineRig.SLOT_ID, slot.id)
        assertEquals(15, slot.followUpMinutes)
        assertEquals(extra, slot.unknown)
        assertEquals("09:30", slot.label)
        assertEquals(RoutineKind.HEALTH, written.kind)
        assertNull(written.healthSubtype)
        assertEquals("", written.amount)
        assertEquals("", written.unit)
    }

    @Test
    fun `an edit keeps a stored chore on a schedule a chore, and switching to after completion makes a chore`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn() }
        val stored = definition(kind = RoutineKind.HEALTH)
        rig.seedCarrier(100, payload(definition = stored))

        rig.repository.update(
            RoutineRig.ROUTINE_ID,
            RoutineDraft(
                name = stored.name,
                schedule = RoutineSchedule.AfterCompletion(intervalDays = 7, firstDueDate = "2026-10-06"),
                slots = stored.slots,
            ),
        )
        assertEquals(RoutineKind.CHORE, rig.serverPayload(100).definition.kind)

        rig.repository.update(
            RoutineRig.ROUTINE_ID,
            RoutineDraft(name = stored.name, schedule = stored.schedule, slots = stored.slots),
        )
        assertEquals(RoutineKind.CHORE, rig.serverPayload(100).definition.kind, "the stored kind is kept")
    }

    @Test
    fun `more than five times is refused`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn() }

        val result = rig.repository.create(
            RoutineDraft(
                name = "Water",
                schedule = RoutineSchedule.Calendar(anchorDate = "2026-10-06"),
                slots = (8..13).map { newSlot(it * 60) },
            ),
        )

        assertIs<NetworkResult.Error>(result)
        assertTrue(rig.server.rows.isEmpty())
    }

    @Test
    fun `undoing a carried-over after-completion occurrence leaves it due, not unlogged`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn() }
        // Due on 2026-10-01, shown on today (2026-10-06) until it is done.
        val stored = definition(
            kind = RoutineKind.HEALTH,
            schedule = RoutineSchedule.AfterCompletion(intervalDays = 14, firstDueDate = "2026-10-01"),
        ).copy(healthSubtype = HealthSubtype.SUPPLEMENT)
        rig.seedCarrier(100, payload(definition = stored))

        rig.repository.setOccurrenceStatus(RoutineRig.ROUTINE_ID, "2026-10-01", RoutineRig.SLOT_ID, OccurrenceStatus.PENDING)

        val record = rig.serverPayload(100).occurrences.getValue("${RoutineRig.ROUTINE_ID}:2026-10-01:${RoutineRig.SLOT_ID}")
        assertEquals(OccurrenceStatus.PENDING, record.status)
    }

    @Test
    fun `finalizing never marks an after-completion routine not logged`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn() }
        val stored = definition(
            kind = RoutineKind.HEALTH,
            schedule = RoutineSchedule.AfterCompletion(intervalDays = 14, firstDueDate = "2026-10-01"),
            activeFrom = "2026-09-20",
        )
        rig.seedCarrier(100, payload(definition = stored))

        rig.repository.finalizeAndPrune()

        assertTrue(rig.serverPayload(100).occurrences.values.none { it.status == OccurrenceStatus.NOT_LOGGED })
    }
}
