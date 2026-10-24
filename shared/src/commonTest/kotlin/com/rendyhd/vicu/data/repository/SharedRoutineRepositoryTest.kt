package com.rendyhd.vicu.data.repository

import com.rendyhd.vicu.data.local.entity.ProjectEntity
import com.rendyhd.vicu.data.repository.RoutineRig.Companion.definition
import com.rendyhd.vicu.data.repository.RoutineRig.Companion.payload
import com.rendyhd.vicu.data.repository.RoutineRig.Companion.record
import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.model.RoutineArchivePart
import com.rendyhd.vicu.domain.model.RoutineLoggedBy
import com.rendyhd.vicu.domain.model.RoutineDraft
import com.rendyhd.vicu.domain.model.SyncedPrefKeys
import com.rendyhd.vicu.util.NetworkResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Shared routines in the repository (docs/cross-app-semantics-v1.md, section 6.9): who reads them,
 * who logged what, sharing and stopping, what only the owner may do, and this account's own
 * choices about them.
 */
class SharedRoutineRepositoryTest {

    private val me = 2L
    private val maya = 1L
    private val today = "2026-10-06"
    private val lisbon = 36L

    private val sharedDefinition = definition().copy(shared = true)

    @Test
    fun `a member reads the routines others shared and nobody's private ones`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn(me) }
        rig.seedCarrier(100, payload(definition = sharedDefinition), projectId = lisbon, createdBy = maya)
        rig.seedCarrier(101, payload(definition = definition().copy(id = "private", name = "Maya's own")), projectId = lisbon, createdBy = maya)
        rig.seedCarrier(102, payload(definition = definition().copy(id = "mine", name = "Mine")), createdBy = me)

        val names = rig.repository.observeActive().first().map { it.definition.name }.sorted()

        assertEquals(listOf("Mine", "Vitamin D"), names)
        val shared = rig.repository.observeRoutine(RoutineRig.ROUTINE_ID).first()!!
        assertEquals(maya, shared.ownerId)
        assertEquals(lisbon, shared.projectId)
    }

    @Test
    fun `logging an occurrence of a shared routine says who did it - a private one does not`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn(me) }
        rig.seedCarrier(100, payload(definition = sharedDefinition), projectId = lisbon, createdBy = maya)
        rig.seedCarrier(102, payload(definition = definition().copy(id = "mine", name = "Mine")), createdBy = me)
        val device = rig.prefs.getOrCreateDeviceId()

        rig.repository.setOccurrenceStatus(RoutineRig.ROUTINE_ID, today, RoutineRig.SLOT_ID, OccurrenceStatus.COMPLETED)
        rig.repository.setOccurrenceStatus("mine", today, RoutineRig.SLOT_ID, OccurrenceStatus.COMPLETED)

        val logged = rig.serverPayload(100).occurrences.values.single()
        assertEquals(RoutineLoggedBy(me, device), logged.loggedBy)
        assertEquals(me, logged.loggedByUserId)
        assertNull(rig.serverPayload(102).occurrences.values.single().loggedBy)
    }

    @Test
    fun `a tick by one member shows as done for the other`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn(me) }
        rig.seedCarrier(100, payload(definition = sharedDefinition), projectId = lisbon, createdBy = maya)
        // Maya ticks it on her phone; this phone's next refresh brings it in.
        val mayas = record(today, modifiedBy = "maya-phone").copy(loggedBy = RoutineLoggedBy(maya, "maya-phone"))
        rig.server.row(100).description = com.rendyhd.vicu.util.RoutineEnvelope.upsert(
            "",
            payload(mayas, definition = sharedDefinition),
            rig.json,
        )

        rig.refresher.refresh(forceFull = true)

        val occurrence = rig.repository.observeDay(today).first().occurrences.single()
        assertEquals(OccurrenceStatus.COMPLETED, occurrence.status)
        assertEquals(maya, occurrence.routine.payload.occurrences.values.single().loggedByUserId)
    }

    @Test
    fun `sharing moves the history first - then the carrier with the flag in one conditional write`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn(me) }
        rig.seedCarrier(100, payload(record("2026-10-05"), prunedBefore = "2025-09-01"), createdBy = me)
        rig.seedPart(200, RoutineArchivePart(routineId = RoutineRig.ROUTINE_ID, part = 1, occurrences = mapOf()), createdBy = me)

        val result = rig.repository.share(RoutineRig.ROUTINE_ID, lisbon)

        assertIs<NetworkResult.Success<*>>(result)
        assertEquals(lisbon, rig.server.row(200).projectId)
        assertEquals(lisbon, rig.server.row(100).projectId)
        assertTrue(rig.serverPayload(100).definition.shared)
        assertEquals(1, rig.serverPayload(100).occurrences.size, "nothing of the history is lost on the way")
        val patches = rig.server.requests.filter { it.method == "PATCH" }.map { it.path }
        assertEquals(listOf("/tasks/200", "/tasks/100"), patches)
        val carrierWrite = Json.parseToJsonElement(rig.server.requestsLike("PATCH", "/tasks/100").single().body!!).jsonArray
        assertEquals("test", carrierWrite.first().jsonObject["op"]!!.jsonPrimitive.content)
        assertTrue(carrierWrite.any { it.jsonObject["path"]!!.jsonPrimitive.content == "/project_id" })
        // The cache follows: the routine is now in the shared project.
        assertEquals(lisbon, rig.repository.observeRoutine(RoutineRig.ROUTINE_ID).first()!!.projectId)
        assertTrue(rig.hooks.widgetUpdates > 0, "the widgets and alarms follow")
    }

    @Test
    fun `stopping moves the carrier back to the Inbox first - then the history`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn(me) }
        rig.seedCarrier(100, payload(prunedBefore = "2025-09-01", definition = sharedDefinition), projectId = lisbon, createdBy = me)
        val mayasTick = record("2025-01-15", modifiedBy = "maya-phone")
        rig.seedPart(
            200,
            RoutineArchivePart(routineId = RoutineRig.ROUTINE_ID, part = 1, occurrences = mapOf(mayasTick.key to mayasTick)),
            projectId = lisbon,
            createdBy = maya,
        )

        val result = rig.repository.stopSharing(RoutineRig.ROUTINE_ID)

        assertIs<NetworkResult.Success<*>>(result)
        assertFalse(rig.serverPayload(100).definition.shared)
        assertEquals(rig.inboxProjectId, rig.server.row(100).projectId)
        assertEquals(rig.inboxProjectId, rig.server.row(200).projectId, "a part the other member wrote moves too")
        assertEquals(listOf("/tasks/100", "/tasks/200"), rig.server.requests.filter { it.method == "PATCH" }.map { it.path })
        // What Maya archived while it was shared is still the routine's history (section 6.9).
        assertEquals(listOf(200L), rig.store.loadParts(listOf(RoutineRig.ROUTINE_ID)).map { it.taskId })
    }

    @Test
    fun `only the owner shares - stops - archives or deletes - a member may edit`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn(me) }
        rig.seedCarrier(100, payload(definition = sharedDefinition), projectId = lisbon, createdBy = maya)

        assertIs<NetworkResult.Error>(rig.repository.stopSharing(RoutineRig.ROUTINE_ID))
        assertIs<NetworkResult.Error>(rig.repository.share(RoutineRig.ROUTINE_ID, 5))
        assertIs<NetworkResult.Error>(rig.repository.archive(RoutineRig.ROUTINE_ID, true))
        assertIs<NetworkResult.Error>(rig.repository.deletePermanently(RoutineRig.ROUTINE_ID))
        assertTrue(rig.server.requests.none { it.method == "PATCH" || it.method == "DELETE" })

        val edit = rig.repository.update(
            RoutineRig.ROUTINE_ID,
            RoutineDraft(name = "Feed the cat", schedule = sharedDefinition.schedule, slots = sharedDefinition.slots),
        )
        assertIs<NetworkResult.Success<*>>(edit)
        assertEquals("Feed the cat", rig.serverPayload(100).definition.name)
        assertTrue(rig.serverPayload(100).definition.shared, "an edit never changes the flag")
    }

    @Test
    fun `a read-only member cannot tick it off - nothing is queued or sent`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn(me) }
        rig.seedCarrier(100, payload(definition = sharedDefinition), projectId = 37, createdBy = maya)
        rig.projectDao.upsert(ProjectEntity(id = 37, title = "Family", maxPermission = 0))

        val result = rig.repository.setOccurrenceStatus(RoutineRig.ROUTINE_ID, today, RoutineRig.SLOT_ID, OccurrenceStatus.COMPLETED)

        assertIs<NetworkResult.Error>(result)
        assertTrue(rig.server.requests.none { it.method == "PATCH" })
        assertTrue(rig.pendingActionDao.getTaskIdsWithPendingActions().isEmpty())
    }

    @Test
    fun `a member hides a shared routine for themselves - an owner cannot hide their own`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn(me) }
        rig.seedCarrier(100, payload(definition = sharedDefinition), projectId = lisbon, createdBy = maya)
        rig.seedCarrier(102, payload(definition = definition().copy(id = "mine", name = "Mine", shared = true)), createdBy = me)

        rig.repository.setHidden(RoutineRig.ROUTINE_ID, true)
        rig.repository.setHidden("mine", true)

        assertEquals(JsonPrimitive(true), rig.personalEntries.entries.first()[SyncedPrefKeys.routineHiddenOf(RoutineRig.ROUTINE_ID)])
        assertEquals(listOf("Mine"), rig.repository.observeActive().first().map { it.definition.name })
        assertEquals(listOf("Vitamin D"), rig.repository.observeHidden().first().map { it.definition.name })
        assertTrue(rig.repository.observeDay(today).first().occurrences.none { it.routine.definition.id == RoutineRig.ROUTINE_ID })

        rig.repository.setHidden(RoutineRig.ROUTINE_ID, false)
        assertNull(rig.personalEntries.entries.first()[SyncedPrefKeys.routineHiddenOf(RoutineRig.ROUTINE_ID)])
        assertEquals(2, rig.repository.observeActive().first().size)
    }

    @Test
    fun `reminders leave out what this account turned off and routines it can only read`() = runTest {
        val rig = RoutineRig(backgroundScope).also { it.signIn(me) }
        rig.seedCarrier(100, payload(definition = sharedDefinition), projectId = lisbon, createdBy = maya)
        rig.seedCarrier(101, payload(definition = sharedDefinition.copy(id = "readonly", name = "Read only")), projectId = 37, createdBy = maya)
        rig.seedCarrier(102, payload(definition = definition().copy(id = "mine", name = "Mine")), createdBy = me)
        rig.projectDao.upsert(ProjectEntity(id = lisbon, title = "Trip to Lisbon", maxPermission = 1))
        rig.projectDao.upsert(ProjectEntity(id = 37, title = "Family", maxPermission = 0))

        assertEquals(listOf("Mine", "Vitamin D"), rig.repository.observeReminderRoutines().first().map { it.definition.name }.sorted())

        rig.repository.setRemindersEnabled(RoutineRig.ROUTINE_ID, false)

        assertEquals(JsonPrimitive("off"), rig.personalEntries.entries.first()[SyncedPrefKeys.routineRemindersOf(RoutineRig.ROUTINE_ID)])
        assertEquals(listOf("Mine"), rig.repository.observeReminderRoutines().first().map { it.definition.name })
        // Still listed and still to be ticked off: only the reminders are off.
        assertEquals(3, rig.repository.observeActive().first().size)
    }
}
