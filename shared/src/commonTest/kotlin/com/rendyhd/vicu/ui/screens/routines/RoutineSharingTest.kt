package com.rendyhd.vicu.ui.screens.routines

import com.rendyhd.vicu.data.repository.RoutineRig
import com.rendyhd.vicu.domain.model.CollabState
import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.model.Project
import com.rendyhd.vicu.domain.model.ProjectAccess
import com.rendyhd.vicu.domain.model.Routine
import com.rendyhd.vicu.domain.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Who a routine can be shared with and what the screens say about it (cross-app semantics 6.9). */
class RoutineSharingTest {

    private val me = User(id = 1, username = "uiverify", name = "")
    private val maya = User(id = 2, username = "uimaya", name = "Maya")
    private val sam = User(id = 3, username = "uisam", name = "Sam")
    private val lee = User(id = 4, username = "lee", name = "Lee")

    private val home = Project(id = 10, title = "Home", ownerId = 1)
    private val lisbon = Project(id = 36, title = "Trip to Lisbon", ownerId = 1)
    private val family = Project(id = 37, title = "Family", ownerId = 2)
    private val readOnly = Project(id = 38, title = "Maya's plans", ownerId = 2)
    private val privateProject = Project(id = 5, title = "Inbox", ownerId = 1)
    private val archived = Project(id = 39, title = "Old", ownerId = 1, isArchived = true)

    private val state = CollabState(
        me = 1,
        projects = mapOf(
            10L to ProjectAccess(shared = true, members = listOf(me, maya), permission = 2),
            36L to ProjectAccess(shared = true, members = listOf(me, maya, sam), permission = 2),
            37L to ProjectAccess(shared = true, members = listOf(me, maya, lee), permission = 1),
            38L to ProjectAccess(shared = true, members = listOf(me, maya), permission = 0),
            5L to ProjectAccess(shared = false, members = listOf(me), permission = 2),
            39L to ProjectAccess(shared = true, members = listOf(me, sam), permission = 2),
        ),
    )
    private val projects = listOf(home, lisbon, family, readOnly, privateProject, archived)

    private fun routine(projectId: Long, owner: Long, shared: Boolean = true) = Routine(
        taskId = 100,
        payload = RoutineRig.payload(definition = RoutineRig.definition().copy(shared = shared)),
        projectId = projectId,
        ownerId = owner,
    )

    @Test
    fun `eligible projects are shared - active and writable for you`() {
        val eligible = RoutineSharing.eligibleProjects(state, projects)

        assertEquals(listOf("Home", "Trip to Lisbon", "Family"), eligible.map { it.project.title })
        assertEquals(listOf(true, true, false), eligible.map { it.yours })
    }

    @Test
    fun `people are everyone in those projects but you - with the projects they can tick it off in`() {
        val eligible = RoutineSharing.eligibleProjects(state, projects)
        // Sam can only read Lisbon; Lee's permission is not known, which counts as write.
        val permissions = mapOf(36L to mapOf(3L to 0, 2L to 1))

        val people = RoutineSharing.people(eligible, me = 1, permissions = permissions)

        assertEquals(listOf("uimaya", "uisam", "lee"), people.map { it.person.username })
        val mayas = people.first()
        assertTrue(mayas.canPick)
        assertEquals(listOf(10L, 36L, 37L), mayas.projects.map { it.project.id })
        assertFalse(people[1].canPick, "Sam can view only")
        assertTrue(people[2].canPick)
        assertEquals(listOf(37L), people[2].projects.map { it.project.id })
        assertEquals(37L, RoutineSharing.projectWithoutAsking(people[2])?.project?.id)
        assertNull(RoutineSharing.projectWithoutAsking(mayas), "Maya is in three projects: the user picks")
        assertEquals(listOf(36L, 10L, 37L), RoutineSharing.projectsFor(mayas, currentProjectId = 36).map { it.project.id })
    }

    @Test
    fun `the card says who shared it and with whom`() {
        assertEquals("Shared with Maya and Sam", RoutineSharing.cardText(routine(36, owner = 1), state))
        assertEquals("Shared with Maya", RoutineSharing.cardText(routine(10, owner = 1), state))
        assertEquals("Shared by Maya", RoutineSharing.cardText(routine(37, owner = 2), state))
        assertNull(RoutineSharing.cardText(routine(36, owner = 1, shared = false), state), "not flagged")
        assertNull(RoutineSharing.cardText(routine(5, owner = 1), state), "nobody else sees the project")
        assertEquals("Shared with Maya, Sam and Lee", RoutineSharing.sharedWithText(listOf(me, maya, sam, lee), me = 1))
    }

    @Test
    fun `a finished occurrence names who did it`() {
        val shared = routine(36, owner = 2)
        assertEquals("Done by Maya", RoutineSharing.loggedText(shared, OccurrenceStatus.COMPLETED, 2, state))
        assertEquals("Skipped by you", RoutineSharing.loggedText(shared, OccurrenceStatus.SKIPPED, 1, state))
        assertNull(RoutineSharing.loggedText(shared, OccurrenceStatus.COMPLETED, null, state))
        assertNull(RoutineSharing.loggedText(shared, OccurrenceStatus.COMPLETED, 99, state), "not a member any more")
        assertNull(RoutineSharing.loggedText(shared, OccurrenceStatus.PENDING, 2, state))
        assertNull(RoutineSharing.loggedText(routine(5, owner = 1), OccurrenceStatus.COMPLETED, 1, state), "private")
    }

    @Test
    fun `who may write follows the project`() {
        assertTrue(RoutineSharing.canWrite(routine(37, owner = 2), state))
        assertFalse(RoutineSharing.canWrite(routine(38, owner = 2), state))
        assertTrue(RoutineSharing.isOwn(routine(36, owner = 1), me = 1))
        assertFalse(RoutineSharing.isOwn(routine(36, owner = 2), me = 1))
        assertTrue(RoutineSharing.isOwn(routine(36, owner = 0), me = 1), "made offline: no creator yet")
    }

    @Test
    fun `deleting a project warns about the shared routines in it and its subprojects`() {
        assertNull(RoutineSharing.deleteProjectNote(emptyList()))
        assertTrue(RoutineSharing.asksBeforeDeleting(confirmBeforeDelete = false, listOf("Feed the cat")), "asked even with confirmations off")
        assertFalse(RoutineSharing.asksBeforeDeleting(confirmBeforeDelete = false, emptyList()))
        assertTrue(RoutineSharing.asksBeforeDeleting(confirmBeforeDelete = true, emptyList()))
        assertEquals(
            "It also holds the shared routine \"Feed the cat\". Deleting the project deletes the routine and its history for everyone.",
            RoutineSharing.deleteProjectNote(listOf("Feed the cat")),
        )
        assertEquals(
            "It also holds the shared routines \"A\", \"B\" and \"C\". Deleting the project deletes the routines and their history for everyone.",
            RoutineSharing.deleteProjectNote(listOf("A", "B", "C")),
        )
        val child = Project(id = 40, title = "Kitchen", parentProjectId = 10)
        val inChild = routine(40, owner = 1).let { it.copy(payload = it.payload.copy(definition = it.definition.copy(name = "Water plants"))) }
        val withChild = state.copy(projects = state.projects + (40L to ProjectAccess(shared = true, members = listOf(me, maya), permission = 2)))
        assertEquals(
            listOf("Vitamin D", "Water plants"),
            sharedRoutineNamesIn(10, listOf(routine(10, owner = 1), inChild, routine(36, owner = 1)), projects + child, withChild),
        )
    }
}
