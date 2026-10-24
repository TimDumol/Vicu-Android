package com.rendyhd.vicu.data.sync

import com.rendyhd.vicu.auth.authTestJson
import com.rendyhd.vicu.auth.authTestJsonHeaders
import com.rendyhd.vicu.data.repository.RoutineRig
import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.model.RoutineOccurrenceRecord
import com.rendyhd.vicu.domain.model.RoutinePayload
import com.rendyhd.vicu.util.RoutineEnvelope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A refresh that brings back a routine carrier without this device's recent changes writes them
 * again (docs/cross-app-semantics-v1.md, section 6.8): Vikunja can let two writes made at the same
 * moment both through, and the later one then removes what the earlier one wrote.
 */
class LostRoutineChangesRefreshTest {

    private val phone = "this-phone"

    private fun carrier(vararg records: RoutineOccurrenceRecord): String =
        RoutineEnvelope.upsert("", RoutineRig.payload(*records), authTestJson)

    private fun RefresherHarness.serverPayload(id: Long): RoutinePayload =
        checkNotNull(RoutineEnvelope.parse(server.tasks.getValue(id).description, authTestJson).payload)

    private suspend fun RefresherHarness.cachedPayload(id: Long): RoutinePayload =
        checkNotNull(RoutineEnvelope.parse(checkNotNull(taskDao.getByIdSync(id)).description, authTestJson).payload)

    private fun RoutinePayload.dates() = occurrences.values.map { it.scheduledDate }.sorted()

    private val mine = RoutineRig.record("2026-10-06", modifiedAt = "2026-10-06T08:00:00.000Z", modifiedBy = phone)
    private val mayas = RoutineRig.record("2026-10-05", modifiedAt = "2026-10-06T08:00:00.100Z", modifiedBy = "maya-phone")

    /** This phone wrote [cached]; the server now holds [server] instead. */
    private suspend fun harness(cached: String, server: String): RefresherHarness {
        val h = RefresherHarness(routineDeviceId = phone)
        h.server.put(11, done = true, description = cached)
        h.refresher.refresh(forceFull = true)
        h.server.tasks.getValue(11).apply {
            description = server
            updated = "2026-10-06T12:00:30Z"
        }
        return h
    }

    @Test
    fun `this device's tick the server lost is written again`() = runTest {
        // Maya's write went through together with this phone's and was stored after it.
        val h = harness(cached = carrier(mine), server = carrier(mayas))

        h.refresher.refresh(forceFull = true)

        assertEquals(listOf("2026-10-05", "2026-10-06"), h.serverPayload(11).dates())
        assertEquals(listOf("2026-10-05", "2026-10-06"), h.cachedPayload(11).dates())
        assertTrue(h.pendingActionDao.snapshot().isEmpty())
    }

    @Test
    fun `nothing is written when the server holds everything`() = runTest {
        val h = harness(cached = carrier(mine), server = carrier(mine, mayas))

        h.refresher.refresh(forceFull = true)

        assertTrue(h.server.requests.none { it.method == "PATCH" })
        assertEquals(listOf("2026-10-05", "2026-10-06"), h.cachedPayload(11).dates())
    }

    @Test
    fun `an undo by someone else is not written over`() = runTest {
        val undone = mine.copy(status = OccurrenceStatus.PENDING, modifiedAt = "2026-10-06T09:00:00.000Z", modifiedBy = "maya-phone")
        val h = harness(cached = carrier(mine), server = carrier(undone))

        h.refresher.refresh(forceFull = true)

        assertTrue(h.server.requests.none { it.method == "PATCH" })
        assertEquals(OccurrenceStatus.PENDING, h.cachedPayload(11).occurrences.values.single().status)
    }

    @Test
    fun `records of other devices and old ones are left to the server`() = runTest {
        // Another device's record that left the carrier, and this phone's tick from weeks ago,
        // which another device has since moved to the archive.
        val old = RoutineRig.record("2026-09-01", modifiedAt = "2026-09-01T08:00:00.000Z", modifiedBy = phone)
        val h = harness(cached = carrier(old, mayas), server = carrier())

        h.refresher.refresh(forceFull = true)

        assertTrue(h.server.requests.none { it.method == "PATCH" })
        assertEquals(emptyList(), h.cachedPayload(11).dates())
    }

    @Test
    fun `when the write fails the change is kept and queued for the sync`() = runTest {
        val h = harness(cached = carrier(mine), server = carrier(mayas))
        h.server.override = { req ->
            if (req.method == "PATCH") respond("""{"title":"Unavailable"}""", HttpStatusCode.ServiceUnavailable, authTestJsonHeaders) else null
        }

        h.refresher.refresh(forceFull = true)

        assertEquals(listOf("2026-10-05", "2026-10-06"), h.cachedPayload(11).dates())
        val queued = h.pendingActionDao.snapshot().single()
        assertEquals(11L to "update", queued.entityId to queued.actionType)
        assertEquals(1, h.hooks.syncTriggers)
    }
}
