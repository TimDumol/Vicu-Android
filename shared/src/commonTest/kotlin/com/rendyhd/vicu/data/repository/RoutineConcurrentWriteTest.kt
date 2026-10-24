package com.rendyhd.vicu.data.repository

import com.rendyhd.vicu.data.repository.RoutineRig.Companion.payload
import com.rendyhd.vicu.data.repository.RoutineRig.Companion.record
import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.model.RoutineLoggedBy
import com.rendyhd.vicu.util.NetworkResult
import com.rendyhd.vicu.util.RoutineEnvelope
import com.rendyhd.vicu.util.SharedRoutines
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Routine carrier writes are conditional (docs/cross-app-semantics-v1.md, section 6.8): a change
 * another device or person made between the read and the write is kept, never overwritten.
 */
class RoutineConcurrentWriteTest {

    private val today = "2026-10-06"

    /** Writes [record] into the server's carrier [taskId], as another device would. */
    private fun RoutineRig.otherDeviceWrites(taskId: Long, record: com.rendyhd.vicu.domain.model.RoutineOccurrenceRecord) {
        val row = server.row(taskId)
        val current = checkNotNull(RoutineEnvelope.parse(row.description, json).payload)
        row.description = RoutineEnvelope.upsert(
            "",
            current.copy(occurrences = current.occurrences + (record.key to record)),
            json,
        )
    }

    @Test
    fun `a tick written by someone else between the read and the write is kept`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn() }
        rig.seedCarrier(100, payload())
        var raced = false
        rig.server.beforeRequest = { request ->
            if (request.method == "PATCH" && !raced) {
                raced = true
                rig.otherDeviceWrites(100, record("2026-10-05", modifiedBy = "maya-phone"))
            }
        }

        val result = rig.repository.setOccurrenceStatus(RoutineRig.ROUTINE_ID, today, RoutineRig.SLOT_ID, OccurrenceStatus.COMPLETED)

        assertIs<NetworkResult.Success<*>>(result)
        val stored = rig.serverPayload(100).occurrences.values.associate { it.scheduledDate to it.status }
        assertEquals(mapOf("2026-10-05" to OccurrenceStatus.COMPLETED, today to OccurrenceStatus.COMPLETED), stored)
        // The first write was refused (422) and the second one, after reading again, went through;
        // the last read checks that the server kept it.
        assertEquals(2, rig.server.requestsLike("PATCH", "/tasks/100").size)
        assertEquals(3, rig.server.requestsLike("GET", "/tasks/100").size)
    }

    @Test
    fun `a write undone by one made at the same moment is written again`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn() }
        rig.seedCarrier(100, payload())
        val before = rig.server.row(100).description
        // Maya's write passed Vikunja's test together with this one and was stored after it, from
        // the carrier as it was before: this phone's tick is gone when it reads the carrier back.
        rig.server.beforeRequest = { request ->
            if (request.method == "GET" && request.path == "/tasks/100" && rig.server.requestsLike("PATCH", "/tasks/100").size == 1 &&
                rig.server.requestsLike("GET", "/tasks/100").size == 2
            ) {
                rig.server.row(100).description = before
                rig.otherDeviceWrites(100, record("2026-10-05", modifiedBy = "maya-phone"))
            }
        }

        val result = rig.repository.setOccurrenceStatus(RoutineRig.ROUTINE_ID, today, RoutineRig.SLOT_ID, OccurrenceStatus.COMPLETED)

        assertIs<NetworkResult.Success<*>>(result)
        val stored = rig.serverPayload(100).occurrences.values.associate { it.scheduledDate to it.status }
        assertEquals(mapOf("2026-10-05" to OccurrenceStatus.COMPLETED, today to OccurrenceStatus.COMPLETED), stored)
        assertEquals(2, rig.server.requestsLike("PATCH", "/tasks/100").size)
        assertEquals(stored, rig.cachedPayload(100).occurrences.values.associate { it.scheduledDate to it.status })
    }

    @Test
    fun `a busy server's error is tried again`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn() }
        rig.seedCarrier(100, payload())
        // An SQLite server answers 500 "database is locked" to one of two writes at the same moment.
        var failed = false
        rig.server.failure = { request ->
            if (request.method == "PATCH" && !failed) {
                failed = true
                io.ktor.http.HttpStatusCode.InternalServerError
            } else {
                null
            }
        }

        val result = rig.repository.setOccurrenceStatus(RoutineRig.ROUTINE_ID, today, RoutineRig.SLOT_ID, OccurrenceStatus.COMPLETED)

        assertIs<NetworkResult.Success<*>>(result)
        assertEquals(OccurrenceStatus.COMPLETED, rig.serverPayload(100).occurrences.values.single().status)
        assertEquals(2, rig.server.requestsLike("PATCH", "/tasks/100").size)
    }

    @Test
    fun `a carrier that keeps changing fails the write after four attempts and loses nothing`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn() }
        rig.seedCarrier(100, payload())
        var day = 1
        rig.server.beforeRequest = { request ->
            if (request.method == "PATCH") rig.otherDeviceWrites(100, record("2026-10-0${day++}", modifiedBy = "maya-phone"))
        }

        val result = rig.repository.setOccurrenceStatus(RoutineRig.ROUTINE_ID, today, RoutineRig.SLOT_ID, OccurrenceStatus.COMPLETED)

        assertIs<NetworkResult.Error>(result)
        assertEquals(RoutineCarrierWriter.MAX_WRITES, rig.server.requestsLike("PATCH", "/tasks/100").size)
        assertEquals(
            listOf("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04"),
            rig.serverPayload(100).occurrences.values.map { it.scheduledDate }.sorted(),
        )
    }

    @Test
    fun `a carrier write is one JSON Patch that tests the description and sends the hidden-task shape`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn() }
        rig.seedCarrier(100, payload())
        val before = rig.server.row(100).description

        rig.repository.setOccurrenceStatus(RoutineRig.ROUTINE_ID, today, RoutineRig.SLOT_ID, OccurrenceStatus.SKIPPED)

        val operations = Json.parseToJsonElement(checkNotNull(rig.server.requestsLike("PATCH", "/tasks/100").single().body))
            .jsonArray.map { it.jsonObject }
        val first = operations.first()
        assertEquals("test", first["op"]!!.jsonPrimitive.content)
        assertEquals("/description", first["path"]!!.jsonPrimitive.content)
        assertEquals(before, first["value"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("/title", "/description", "/done", "/due_date", "/repeat_after", "/repeat_mode", "/reminders"),
            operations.drop(1).map { it["path"]!!.jsonPrimitive.content },
        )
        assertTrue(operations.drop(1).all { it["op"]!!.jsonPrimitive.content == "replace" })
        assertEquals(
            "0001-01-01T00:00:00Z",
            operations.single { it["path"]!!.jsonPrimitive.content == "/due_date" }["value"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `completing an occurrence someone already completed keeps their record`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn() }
        rig.seedCarrier(100, payload())
        // Maya ticked it; this phone has not synced since.
        val maya = record(today, modifiedBy = "maya-phone").copy(loggedBy = RoutineLoggedBy(2, "maya-phone"))
        rig.otherDeviceWrites(100, maya)

        rig.repository.setOccurrenceStatus(RoutineRig.ROUTINE_ID, today, RoutineRig.SLOT_ID, OccurrenceStatus.COMPLETED)

        assertEquals(maya, rig.serverPayload(100).occurrences.values.single())
    }

    @Test
    fun `missed days of a calendar routine are written as placeholders`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn() }
        rig.seedCarrier(100, payload(definition = RoutineRig.definition(activeFrom = "2026-10-04")))

        rig.repository.finalizeAndPrune()

        val missed = rig.serverPayload(100).occurrences.values.sortedBy { it.scheduledDate }
        assertEquals(listOf("2026-10-04", "2026-10-05"), missed.map { it.scheduledDate })
        assertTrue(missed.all { it.status == OccurrenceStatus.NOT_LOGGED })
        assertTrue(missed.all { it.modifiedAt == SharedRoutines.PLACEHOLDER_MODIFIED_AT })
    }

    @Test
    fun `a late tick written after this device's placeholder wins`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn() }
        rig.seedCarrier(100, payload(definition = RoutineRig.definition(activeFrom = "2026-10-05")))
        // Maya ticked yesterday at 23:55; this phone fills the gap without having seen it.
        rig.server.beforeRequest = { request ->
            if (request.method == "GET" && request.path == "/tasks/100" && rig.serverPayload(100).occurrences.isEmpty()) {
                rig.otherDeviceWrites(100, record("2026-10-05", modifiedAt = "2026-10-05T21:55:00.000Z", modifiedBy = "maya-phone"))
            }
        }

        rig.repository.finalizeAndPrune()

        assertEquals(OccurrenceStatus.COMPLETED, rig.serverPayload(100).occurrences.values.single().status)
    }
}
