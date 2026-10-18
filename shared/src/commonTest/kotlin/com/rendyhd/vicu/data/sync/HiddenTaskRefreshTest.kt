package com.rendyhd.vicu.data.sync

import com.rendyhd.vicu.data.local.entity.TaskEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which hidden tasks the task cache keeps (docs/cross-app-semantics-v1.md, section 10): only the
 * signed-in user's routine carriers. Another user's carriers in a shared project, the custom-list
 * and preferences carriers, archive parts and kinds this version does not know stay out, done or
 * reopened.
 */
class HiddenTaskRefreshTest {

    private val routine = "<!-- vicu-routine:v1:eyJ4IjoxfQ -->"

    private fun RefresherHarness.serve() {
        server.put(1, title = "Milk")
        server.put(10, title = "Water plants", done = true, description = routine)
        server.put(11, title = "Their routine", done = true, description = routine, createdBy = 2)
        server.put(12, title = "Vicu preferences", done = true, description = "<!-- vicu-prefs:v1:e30 -->")
        server.put(13, title = "Vicu preferences", done = false, description = "<!-- vicu-prefs:v1:e30 -->")
        server.put(14, title = "Later", done = false, description = "<!-- vicu-someday-kind:v1:e30 -->")
        server.put(15, title = "Vicu routine archive", done = true, description = "<!-- vicu-routine:archive:v1:e30 -->")
        server.put(16, title = "Their reopened routine", done = false, description = routine, createdBy = 2)
    }

    @Test
    fun `a full reconcile caches the user's own routine carriers and no other hidden task`() = runTest {
        val h = RefresherHarness()
        h.serve()

        h.refresher.refresh(forceFull = true)

        assertEquals(listOf(1L, 10L), h.taskDao.snapshot().map { it.id }.sorted())
    }

    @Test
    fun `an incremental refresh keeps them out too`() = runTest {
        val h = RefresherHarness()
        h.refresher.refresh(forceFull = true)
        h.time.advance(kotlin.time.Duration.parse("1m"))
        h.serve()
        h.server.tasks.values.forEach { it.updated = "2026-10-06T12:00:30Z" }

        h.refresher.refresh()

        assertEquals(listOf(1L, 10L), h.taskDao.snapshot().map { it.id }.sorted())
    }

    @Test
    fun `another user's routine cached before the update is removed`() = runTest {
        val h = RefresherHarness()
        h.serve()
        h.taskDao.upsert(TaskEntity(id = 11, title = "Their routine", done = true, description = routine, projectId = 7, createdById = 2))

        h.refresher.refresh(forceFull = true)

        assertEquals(listOf(1L, 10L), h.taskDao.snapshot().map { it.id }.sorted())
    }

    @Test
    fun `while the user is not known every routine carrier is kept`() = runTest {
        val h = RefresherHarness(currentUser = com.rendyhd.vicu.auth.CurrentUser.fixed(null))
        h.serve()

        h.refresher.refresh(forceFull = true)

        assertEquals(listOf(1L, 10L, 11L, 16L), h.taskDao.snapshot().map { it.id }.sorted())
    }
}
