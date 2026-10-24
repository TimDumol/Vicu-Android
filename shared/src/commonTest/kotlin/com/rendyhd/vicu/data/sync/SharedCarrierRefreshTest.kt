package com.rendyhd.vicu.data.sync

import com.rendyhd.vicu.auth.authTestJson
import com.rendyhd.vicu.auth.authTestJsonHeaders
import com.rendyhd.vicu.data.repository.RoutineRig
import com.rendyhd.vicu.util.RoutineEnvelope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * A member's task cache and the routines others shared with them (docs/cross-app-semantics-v1.md,
 * section 6.9): kept while shared, dropped when the owner stops sharing or this account loses
 * access to the project, which shows up only when the carrier is asked for.
 */
class SharedCarrierRefreshTest {

    private fun carrier(id: String, shared: Boolean): String = RoutineEnvelope.upsert(
        "",
        RoutineRig.payload(definition = RoutineRig.definition().copy(id = id, shared = shared)),
        authTestJson,
    )

    private suspend fun RefresherHarness.cachedIds() = taskDao.snapshot().map { it.id }.sorted()

    @Test
    fun `another user's shared routine is cached - their private one is not`() = runTest {
        val h = RefresherHarness()
        h.server.put(10, done = true, description = carrier("mine", shared = false))
        h.server.put(11, done = true, description = carrier("theirs-shared", shared = true), createdBy = 2)
        h.server.put(12, done = true, description = carrier("theirs-private", shared = false), createdBy = 2)

        h.refresher.refresh(forceFull = true)

        assertEquals(listOf(10L, 11L), h.cachedIds())
    }

    @Test
    fun `a routine its owner stopped sharing leaves the cache with the next change`() = runTest {
        val h = RefresherHarness()
        h.server.put(11, done = true, description = carrier("theirs", shared = true), createdBy = 2)
        h.refresher.refresh(forceFull = true)
        assertEquals(listOf(11L), h.cachedIds())

        h.time.advance(1.minutes)
        h.server.tasks.getValue(11).apply {
            description = carrier("theirs", shared = false)
            updated = "2026-10-06T12:00:30Z"
        }
        h.refresher.refresh()

        assertEquals(emptyList(), h.cachedIds())
    }

    @Test
    fun `a shared routine this account can no longer read leaves the cache at the next check`() = runTest {
        val h = RefresherHarness()
        h.server.put(10, done = true, description = carrier("mine", shared = false))
        h.server.put(11, done = true, description = carrier("theirs", shared = true), createdBy = 2)
        h.refresher.refresh(forceFull = true)
        // Maya moved it out of the project, or stopped sharing the project with this account.
        h.server.override = { req ->
            if (req.method == "GET" && req.path == "/tasks/11") {
                respond("""{"title":"Forbidden","detail":"Forbidden"}""", HttpStatusCode.Forbidden, authTestJsonHeaders)
            } else {
                null
            }
        }
        h.server.remove(11)

        h.time.advance(1.minutes)
        h.refresher.refresh()
        assertEquals(listOf(10L), h.cachedIds())
    }

    @Test
    fun `the check asks the server at most every ten minutes`() = runTest {
        val h = RefresherHarness()
        h.server.put(11, done = true, description = carrier("theirs", shared = true), createdBy = 2)
        h.refresher.refresh(forceFull = true)
        fun asked() = h.server.requests.count { it.method == "GET" && it.path == "/tasks/11" }

        h.time.advance(1.minutes)
        h.refresher.refresh()
        assertEquals(1, asked())
        h.time.advance(5.minutes)
        h.refresher.refresh()
        assertEquals(1, asked())
        h.time.advance(5.minutes)
        h.refresher.refresh()
        assertEquals(2, asked())
    }

    @Test
    fun `one carrier is read into the cache before a reminder rings`() = runTest {
        val h = RefresherHarness()
        h.server.put(11, done = true, description = carrier("theirs", shared = true), createdBy = 2)
        h.refresher.refresh(forceFull = true)
        val ticked = RoutineEnvelope.upsert(
            "",
            RoutineRig.payload(RoutineRig.record("2026-10-06"), definition = RoutineRig.definition().copy(id = "theirs", shared = true)),
            authTestJson,
        )
        h.server.tasks.getValue(11).description = ticked

        assertTrue(h.refresher.refreshCarrier(11))

        assertEquals(ticked, h.taskDao.snapshot().single().description)
    }
}
