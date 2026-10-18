package com.rendyhd.vicu.util

import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.repository.PlatformRepositoryHooks
import com.rendyhd.vicu.domain.repository.RoutineRepository
import com.rendyhd.vicu.domain.repository.TaskRepository

/** The platform's task reminders, as the notification actions need them. */
interface TaskReminderNotifications {
    /** Cancels the reminders and a pending snooze of [taskId] (it was completed or deleted). */
    suspend fun cancelForTask(taskId: Long)

    /** Rings the reminder of [taskId] again one snooze period from now. */
    suspend fun snooze(taskId: Long, taskTitle: String)
}

/**
 * What the buttons on a reminder or routine notification do, the same on every platform. The
 * platform only dismisses the notification and keeps itself running until the call returns.
 */
class NotificationActions(
    private val taskRepository: TaskRepository,
    private val routineRepository: RoutineRepository,
    private val reminders: TaskReminderNotifications,
    private val hooks: PlatformRepositoryHooks,
) {
    /**
     * "Mark Complete": an explicit completion, never a toggle, so a task that sync already stored
     * as done (completed on another device) stays done. It is stored and queued, not sent: the
     * platform may stop the app at any moment, and the queued change triggers a sync.
     */
    suspend fun completeTask(taskId: Long) {
        taskRepository.setDoneInBackground(taskId, true)
        reminders.cancelForTask(taskId)
        hooks.updateWidgets()
    }

    /** "Snooze": the reminder rings again one snooze period from now. */
    suspend fun snoozeTask(taskId: Long, taskTitle: String) {
        reminders.snooze(taskId, taskTitle)
    }

    /** "Complete" or "Skip" on a routine reminder. */
    suspend fun setRoutineStatus(routineId: String, date: String, slotId: String, status: OccurrenceStatus) {
        routineRepository.setOccurrenceStatus(routineId, date, slotId, status)
        hooks.routinesChanged()
    }
}
