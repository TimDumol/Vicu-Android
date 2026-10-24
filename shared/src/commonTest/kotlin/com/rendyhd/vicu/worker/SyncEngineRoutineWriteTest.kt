package com.rendyhd.vicu.worker

import com.rendyhd.vicu.auth.authTestJson
import com.rendyhd.vicu.data.local.entity.PendingActionEntity
import com.rendyhd.vicu.data.local.entity.TaskEntity
import com.rendyhd.vicu.data.repository.FakeTaskDao
import com.rendyhd.vicu.data.repository.FakeTaskServer
import com.rendyhd.vicu.data.repository.RoutineRig
import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.util.RoutineEnvelope
import com.rendyhd.vicu.worker.SyncEngineHarness.Companion.emptyPage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A routine change made offline and replayed by the sync is written conditionally too
 * (docs/cross-app-semantics-v1.md, section 6.8): what someone else wrote meanwhile, even between
 * the replay's read and its write, is kept.
 */
class SyncEngineRoutineWriteTest {

    private fun description(vararg records: com.rendyhd.vicu.domain.model.RoutineOccurrenceRecord): String =
        RoutineEnvelope.upsert("", RoutineRig.payload(*records, definition = RoutineRig.definition().copy(shared = true)), authTestJson)

    @Test
    fun `a queued tick replays without losing the tick another person made in between`() = runTest {
        val server = FakeTaskServer()
        server.seed(100, "Vitamin D", description(), done = true)
        // This phone ticked the 6th while offline.
        val local = description(RoutineRig.record("2026-10-06", modifiedBy = "phone-b"))
        val h = SyncEngineHarness(
            taskDao = FakeTaskDao(listOf(TaskEntity(id = 100, title = "Vitamin D", description = local, done = true, projectId = 5))),
        ) { request ->
            if (request.url.encodedPath == "/tasks/100") server.handle(this, request) else emptyPage()
        }
        // Maya's phone writes between the replay's read and its write.
        var raced = false
        server.beforeRequest = { request ->
            if (request.method == "PATCH" && !raced) {
                raced = true
                server.row(100).description = description(RoutineRig.record("2026-10-05", modifiedBy = "phone-a"))
            }
        }
        h.pendingActionDao.insert(
            PendingActionEntity(
                entityType = "task", entityId = 100, actionType = "update", payload = """{"description":"x"}""",
                createdAt = "2026-10-06T08:00:00Z", updatedAt = "2026-10-06T08:00:00Z",
            ),
        )

        h.engine.performSync()

        val stored = checkNotNull(RoutineEnvelope.parse(server.row(100).description, authTestJson).payload)
        assertEquals(
            mapOf("2026-10-05" to OccurrenceStatus.COMPLETED, "2026-10-06" to OccurrenceStatus.COMPLETED),
            stored.occurrences.values.associate { it.scheduledDate to it.status },
        )
        assertEquals(2, server.requestsLike("PATCH", "/tasks/100").size, "refused once, then written")
        assertTrue(h.pendingActionDao.snapshot().isEmpty())
        h.close()
    }
}
