package com.rendyhd.vicu.widget

import com.rendyhd.vicu.auth.TokenStorage
import com.rendyhd.vicu.data.local.BehaviorPrefsStore
import com.rendyhd.vicu.data.local.CustomListStore
import com.rendyhd.vicu.data.local.WidgetPrefsStore
import com.rendyhd.vicu.data.local.dao.ProjectDao
import com.rendyhd.vicu.data.local.dao.TaskDao
import com.rendyhd.vicu.data.local.entity.TaskEntity
import com.rendyhd.vicu.domain.model.Task
import com.rendyhd.vicu.domain.repository.TaskRepository
import com.rendyhd.vicu.domain.repository.customListTasks
import com.rendyhd.vicu.util.ClockDay
import com.rendyhd.vicu.util.CustomListContext
import com.rendyhd.vicu.util.DueDates
import com.rendyhd.vicu.util.TimeSource
import kotlinx.coroutines.flow.first

/**
 * What a task list widget shows for its configured view, from the data on the device: the same
 * queries as the list screens, at most [MAX_WIDGET_TASKS] rows with the total, tasks of archived
 * projects left out, and the project the + button adds to. Android's Glance widget and the iOS
 * widget snapshots are both built here.
 */
class TaskWidgetStates(
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao,
    private val taskRepository: TaskRepository,
    private val tokenStorage: TokenStorage,
    private val customListStore: CustomListStore,
    private val widgetPrefsStore: WidgetPrefsStore,
    private val behaviorPrefsStore: BehaviorPrefsStore,
    private val time: TimeSource,
    /** The members and signals stored on the device, for lists with collaboration conditions. */
    private val collaborationContext: suspend () -> CustomListContext,
) {
    /** Signed in with a token the widget's data can belong to. */
    suspend fun isSignedIn(): Boolean =
        tokenStorage.getVikunjaUrl()?.isNotBlank() == true &&
            (tokenStorage.getJwt()?.isNotBlank() == true || tokenStorage.getApiToken()?.isNotBlank() == true)

    /** What a widget shows while nobody is signed in, instead of stale data. */
    fun signedOut(): TaskWidgetState = TaskWidgetState(
        tasks = emptyList(),
        totalCount = 0,
        lastUpdated = time.now().toString(),
        error = SIGNED_OUT,
    )

    /** Reads what every widget needs once (projects, preferences) for the local [day]. */
    suspend fun prepare(day: ClockDay): Batch {
        val projects = projectDao.getAllSync()
        return Batch(
            day = day,
            projectNames = projects.associate { it.id to it.title },
            smartAdd = widgetPrefsStore.smartAdd.first(),
            contextNav = widgetPrefsStore.contextNav.first(),
            inboxId = tokenStorage.getInboxProjectId() ?: 0L,
        )
    }

    inner class Batch internal constructor(
        val day: ClockDay,
        /** The projects that are not archived, by id. */
        val projectNames: Map<Long, String>,
        private val smartAdd: Boolean,
        private val contextNav: Boolean,
        private val inboxId: Long,
    ) {
        private val activeProjectIds: Set<Long> get() = projectNames.keys

        suspend fun build(config: WidgetConfig): TaskWidgetState {
            val projectUnavailable = config.viewType == WidgetViewType.PROJECT &&
                config.viewId.toLongOrNull() !in activeProjectIds
            val rows = query(config).filter { it.projectId in activeProjectIds }
            val addToProjectId = if (config.viewType == WidgetViewType.CUSTOM_LIST) {
                customListStore.getById(config.viewId).first()?.filter?.addToProjectId?.takeIf { it in activeProjectIds } ?: 0L
            } else {
                0L
            }
            return TaskWidgetState(
                viewType = config.viewType,
                viewId = config.viewId,
                viewName = config.viewName,
                tasks = rows.take(MAX_WIDGET_TASKS).map { row ->
                    WidgetTaskItem(
                        id = row.id,
                        title = row.title,
                        projectName = projectNames[row.projectId] ?: "",
                        dueDate = row.dueDate,
                        priority = row.priority,
                        done = row.done,
                    )
                },
                totalCount = rows.size,
                lastUpdated = time.now().toString(),
                smartAdd = smartAdd,
                contextNav = contextNav,
                addToProjectId = addToProjectId,
                error = if (projectUnavailable) PROJECT_UNAVAILABLE else null,
            )
        }

        private suspend fun query(config: WidgetConfig): List<WidgetRow> {
            // The same boundary as the Today and Upcoming screens: the start of the local tomorrow.
            val startOfTomorrow = DueDates.startOfTomorrow(day.date, day.zone).toString()
            return when (config.viewType) {
                WidgetViewType.TODAY -> taskDao.getTodayTasksSync(startOfTomorrow, MAX_WIDGET_TASKS).map { it.toRow() }
                WidgetViewType.INBOX -> taskDao.getInboxTasksSync(
                    inboxId,
                    MAX_WIDGET_TASKS,
                    includeDated = !behaviorPrefsStore.getPrefs().first().inboxExcludeDated,
                ).map { it.toRow() }
                WidgetViewType.UPCOMING -> taskDao.getUpcomingTasksSync(startOfTomorrow, MAX_WIDGET_TASKS).map { it.toRow() }
                WidgetViewType.ANYTIME -> taskDao.getAnytimeTasksSync(inboxId, MAX_WIDGET_TASKS).map { it.toRow() }
                WidgetViewType.PROJECT -> {
                    val projectId = config.viewId.toLongOrNull() ?: 0L
                    taskDao.getByProjectIdSync(projectId, MAX_WIDGET_TASKS).map { it.toRow() }
                }
                WidgetViewType.CUSTOM_LIST -> {
                    val customList = customListStore.getById(config.viewId).first() ?: return emptyList()
                    // The same source and evaluator as the list screen: every task (done ones when
                    // the list includes them), not a capped sample, filtered and sorted the same.
                    // The collaboration conditions read the members and signals stored on the device.
                    val context = if (customList.filter.hasCollaborationCondition) collaborationContext() else CustomListContext.NONE
                    taskRepository.customListTasks(customList.filter, day.date, day.zone, activeProjectIds, context)
                        .map { it.toRow() }
                }
            }
        }
    }

    /** What the widget needs of a task, whichever query it came from. */
    private class WidgetRow(
        val id: Long,
        val title: String,
        val projectId: Long,
        val dueDate: String,
        val priority: Int,
        val done: Boolean,
    )

    private fun TaskEntity.toRow() = WidgetRow(id, title, projectId, dueDate, priority, done)

    private fun Task.toRow() = WidgetRow(id, title, projectId, dueDate, priority, done)

    companion object {
        const val MAX_WIDGET_TASKS = 20
        const val SIGNED_OUT = "Log in to see your tasks"
        const val PROJECT_UNAVAILABLE = "Project archived — reconfigure widget"
    }
}
