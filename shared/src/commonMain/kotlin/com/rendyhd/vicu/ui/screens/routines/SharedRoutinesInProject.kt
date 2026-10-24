package com.rendyhd.vicu.ui.screens.routines

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rendyhd.vicu.domain.model.CollabState
import com.rendyhd.vicu.domain.model.Project
import com.rendyhd.vicu.domain.model.Routine
import com.rendyhd.vicu.domain.repository.ProjectRepository
import com.rendyhd.vicu.domain.repository.RoutineRepository
import com.rendyhd.vicu.ui.components.collab.LocalCollab
import kotlinx.coroutines.flow.combine
import org.koin.compose.koinInject

/**
 * The names of the shared routines kept in [projectId] or one of its subprojects, which deleting
 * the project deletes for everyone (docs/cross-app-semantics-v1.md, section 6.9). Hidden and
 * archived ones count too: they go with the project all the same.
 */
@Composable
fun rememberSharedRoutineNames(projectId: Long): List<String> = rememberSharedRoutineLookup()(projectId)

/**
 * [rememberSharedRoutineNames] for any project: a screen with many projects asks it at the moment
 * one is deleted, so that question is asked even when delete confirmations are turned off.
 */
@Composable
fun rememberSharedRoutineLookup(): (projectId: Long) -> List<String> {
    val routines: RoutineRepository = koinInject()
    val projects: ProjectRepository = koinInject()
    val state = LocalCollab.current.state
    val flow = remember(routines, projects) {
        combine(
            routines.observeActive(),
            routines.observeArchived(),
            routines.observeHidden(),
            projects.getAllIncludingArchived(),
        ) { active, archived, hidden, all -> (active + archived + hidden).distinctBy { it.definition.id } to all }
    }
    val loaded by flow.collectAsStateWithLifecycle(emptyList<Routine>() to emptyList())
    return { projectId -> sharedRoutineNamesIn(projectId, loaded.first, loaded.second, state) }
}

/** [rememberSharedRoutineNames] without the screen: the routines of [projectId] and its subprojects. */
fun sharedRoutineNamesIn(
    projectId: Long,
    routines: List<Routine>,
    projects: List<Project>,
    state: CollabState,
): List<String> {
    val children = projects.groupBy { it.parentProjectId }
    val inTree = mutableSetOf<Long>()
    val pending = ArrayDeque(listOf(projectId))
    while (pending.isNotEmpty()) {
        val id = pending.removeFirst()
        if (inTree.add(id)) children[id].orEmpty().forEach { pending.addLast(it.id) }
    }
    return routines
        .filter { it.projectId in inTree && RoutineSharing.isShared(it, state) }
        .map { it.definition.name }
        .sorted()
}
