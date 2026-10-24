package com.rendyhd.vicu.ui.screens.routines

import com.rendyhd.vicu.domain.model.CollabState
import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.model.Project
import com.rendyhd.vicu.domain.model.Routine
import com.rendyhd.vicu.domain.model.User
import com.rendyhd.vicu.domain.model.toCollabUser
import com.rendyhd.vicu.util.Collaboration
import com.rendyhd.vicu.util.Permission

/** A project a routine can be shared through, with everyone who can see it. */
data class ShareProjectOption(
    val project: Project,
    /** Everyone who can see it, you first. */
    val members: List<User>,
    /** The user owns it, so nobody else can delete it or take their access away. */
    val yours: Boolean,
)

/** A person a routine can be shared with, and the projects that would hold it. */
data class SharePersonOption(
    val person: User,
    /** The eligible projects this person can tick it off in; all of theirs when [canPick] is false. */
    val projects: List<ShareProjectOption>,
    /** False when the person can only read every eligible project ("Can view only"). */
    val canPick: Boolean,
)

/**
 * Who a routine can be shared with and the texts about it (docs/cross-app-semantics-v1.md,
 * section 6.9). Pure, so the screen and the tests use the same rules.
 */
object RoutineSharing {

    const val EXPLANATION =
        "A shared routine is kept in a project you share with that person, so everyone in that project can see it " +
            "and tick it off. Only people you already share a project with are listed; to share with someone else, " +
            "share a project with them first, in Vicu or in Vikunja. Deleting that project deletes the routine and its " +
            "history for everyone, and if you lose access to the project, you lose the routine."

    const val ARCHIVE_PROJECT_NOTE = "Shared routines in it can't be ticked off until it is restored."

    /** The projects a routine can be shared through: shared with someone, active, and writable for you. */
    fun eligibleProjects(state: CollabState, projects: List<Project>): List<ShareProjectOption> =
        projects.filter { !it.isArchived }.mapNotNull { project ->
            val access = state.access(project.id)
            if (!access.shared || access.permission < Permission.WRITE) return@mapNotNull null
            ShareProjectOption(project, access.members, yours = state.me != null && project.ownerId == state.me)
        }

    /**
     * Everyone in [eligible] except you, in the order they first appear. [permissions] gives each
     * member's permission per project id (contract 9.1); a member it does not know counts as able
     * to write.
     */
    fun people(
        eligible: List<ShareProjectOption>,
        me: Long?,
        permissions: Map<Long, Map<Long, Int?>> = emptyMap(),
    ): List<SharePersonOption> {
        val people = LinkedHashMap<Long, User>()
        eligible.forEach { option -> option.members.forEach { if (it.id != me) people.getOrPut(it.id) { it } } }
        return people.values.map { person ->
            val theirs = eligible.filter { option -> option.members.any { it.id == person.id } }
            val writable = theirs.filter { permissions[it.project.id]?.get(person.id) != Permission.READ }
            SharePersonOption(person, writable.ifEmpty { theirs }, canPick = writable.isNotEmpty())
        }
    }

    /** The projects to offer for [person], the one the routine is already in first. */
    fun projectsFor(person: SharePersonOption, currentProjectId: Long): List<ShareProjectOption> =
        person.projects.sortedBy { it.project.id != currentProjectId }

    /** The project to use without asking: the only one, or none when the user has to choose. */
    fun projectWithoutAsking(person: SharePersonOption): ShareProjectOption? = person.projects.singleOrNull()

    /** Shared, and someone else can see it: a flagged routine in a project nobody else sees is private. */
    fun isShared(routine: Routine, state: CollabState): Boolean =
        routine.definition.shared && state.access(routine.projectId).shared

    /** The user created it (or it is not known yet, as with a routine made offline). */
    fun isOwn(routine: Routine, me: Long?): Boolean = me == null || routine.ownerId <= 0L || routine.ownerId == me

    /** The user may tick it off and edit it: they can write to its project. */
    fun canWrite(routine: Routine, state: CollabState): Boolean = state.access(routine.projectId).canWrite

    /** "Shared with Maya", "Shared with Maya and Sam", "Shared with Maya, Sam and Lee"; the others only. */
    fun sharedWithText(members: List<User>, me: Long?): String {
        val names = members.filter { it.id != me }.map { Collaboration.displayName(it.toCollabUser()) }
        return when (names.size) {
            0 -> "Shared"
            1 -> "Shared with ${names[0]}"
            else -> "Shared with ${names.dropLast(1).joinToString(", ")} and ${names.last()}"
        }
    }

    /** "Shared by Maya": the owner's name, from the people of the routine's project. */
    fun sharedByText(routine: Routine, state: CollabState): String {
        val owner = state.access(routine.projectId).members.firstOrNull { it.id == routine.ownerId }
        return owner?.let { "Shared by ${Collaboration.displayName(it.toCollabUser())}" } ?: "Shared with you"
    }

    /** The card's line: "Shared with Maya" for the owner, "Shared by Maya" for a member; null when private. */
    fun cardText(routine: Routine, state: CollabState): String? = when {
        !isShared(routine, state) -> null
        isOwn(routine, state.me) -> sharedWithText(state.access(routine.projectId).members, state.me)
        else -> sharedByText(routine, state)
    }

    /** "Done by Maya", "Skipped by you"; null when nobody is to be named or the routine is not shared. */
    fun loggedText(
        routine: Routine,
        status: OccurrenceStatus,
        loggedByUserId: Long?,
        state: CollabState,
    ): String? {
        if (loggedByUserId == null || !isShared(routine, state)) return null
        val verb = when (status) {
            OccurrenceStatus.COMPLETED -> "Done"
            OccurrenceStatus.SKIPPED -> "Skipped"
            else -> return null
        }
        val name = if (loggedByUserId == state.me) {
            "you"
        } else {
            state.access(routine.projectId).members.firstOrNull { it.id == loggedByUserId }
                ?.let { Collaboration.displayName(it.toCollabUser()) }
                ?: return null
        }
        return "$verb by $name"
    }

    /**
     * True when deleting a project asks first: when "Confirm before deleting" is on, and always
     * when it holds shared routines ([sharedRoutineNames]), which go for everyone.
     */
    fun asksBeforeDeleting(confirmBeforeDelete: Boolean, sharedRoutineNames: List<String>): Boolean =
        confirmBeforeDelete || sharedRoutineNames.isNotEmpty()

    /**
     * What the delete confirmation of a project adds when it holds shared routines:
     * `It also holds the shared routine "Feed the cat". ...`; null when it holds none.
     */
    fun deleteProjectNote(routineNames: List<String>): String? {
        if (routineNames.isEmpty()) return null
        val quoted = routineNames.map { "\"$it\"" }
        return if (quoted.size == 1) {
            "It also holds the shared routine ${quoted[0]}. Deleting the project deletes the routine and its history for everyone."
        } else {
            "It also holds the shared routines ${quoted.dropLast(1).joinToString(", ")} and ${quoted.last()}. " +
                "Deleting the project deletes the routines and their history for everyone."
        }
    }
}
