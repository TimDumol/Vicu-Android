package com.rendyhd.vicu.util

import com.rendyhd.vicu.data.repository.RecordingRepositoryHooks
import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.model.Routine
import com.rendyhd.vicu.domain.model.RoutineDay
import com.rendyhd.vicu.domain.model.RoutineDraft
import com.rendyhd.vicu.domain.model.RoutineOccurrenceRecord
import com.rendyhd.vicu.domain.model.Task
import com.rendyhd.vicu.domain.repository.RoutineParseIssue
import com.rendyhd.vicu.domain.repository.RoutineRepository
import com.rendyhd.vicu.domain.repository.TaskRepository
import com.rendyhd.vicu.ui.FakeTaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationActionsTest {

    private val doneCalls = mutableListOf<Pair<Long, Boolean>>()
    private val tasks = object : TaskRepository by FakeTaskRepository() {
        override suspend fun setDoneInBackground(taskId: Long, done: Boolean): NetworkResult<Task> {
            doneCalls += taskId to done
            return NetworkResult.Error("not stored")
        }
    }

    private val routineCalls = mutableListOf<String>()
    private val routines = object : RoutineRepository {
        override fun observeActive(): Flow<List<Routine>> = flowOf(emptyList())
        override fun observeArchived(): Flow<List<Routine>> = flowOf(emptyList())
        override fun observeRoutine(routineId: String): Flow<Routine?> = flowOf(null)
        override fun observeDay(date: String): Flow<RoutineDay> = flowOf(RoutineDay(date, emptyList()))
        override fun observeHistory(routineId: String): Flow<List<RoutineOccurrenceRecord>> = flowOf(emptyList())
        override fun observeIssues(): Flow<List<RoutineParseIssue>> = flowOf(emptyList())
        override suspend fun create(draft: RoutineDraft): NetworkResult<Routine> = NetworkResult.Error("no")
        override suspend fun update(routineId: String, draft: RoutineDraft): NetworkResult<Routine> = NetworkResult.Error("no")
        override suspend fun setOccurrenceStatus(
            routineId: String,
            date: String,
            slotId: String,
            status: OccurrenceStatus,
            note: String,
        ): NetworkResult<Routine> {
            routineCalls += "$routineId $date $slotId $status"
            return NetworkResult.Error("not stored")
        }
        override suspend fun archive(routineId: String, archived: Boolean): NetworkResult<Routine> = NetworkResult.Error("no")
        override suspend fun deletePermanently(routineId: String): NetworkResult<Unit> = NetworkResult.Success(Unit)
        override suspend fun finalizeAndPrune(): NetworkResult<Unit> = NetworkResult.Success(Unit)
        override suspend fun exportCsv(): String = ""
    }

    private val reminderCalls = mutableListOf<String>()
    private val reminders = object : TaskReminderNotifications {
        override suspend fun cancelForTask(taskId: Long) {
            reminderCalls += "cancel $taskId"
        }

        override suspend fun snooze(taskId: Long, taskTitle: String) {
            reminderCalls += "snooze $taskId $taskTitle"
        }
    }

    private val hooks = RecordingRepositoryHooks()
    private val actions = NotificationActions(tasks, routines, reminders, hooks)

    @Test
    fun completingFromANotificationMarksDoneCancelsTheRemindersAndUpdatesTheWidgets() = runTest {
        actions.completeTask(42)
        // Explicit completion (never a toggle), even when it could not be stored.
        assertEquals(listOf(42L to true), doneCalls)
        assertEquals(listOf("cancel 42"), reminderCalls)
        assertEquals(1, hooks.widgetUpdates)
    }

    @Test
    fun snoozingGoesToThePlatformReminders() = runTest {
        actions.snoozeTask(42, "Call the plumber")
        assertEquals(listOf("snooze 42 Call the plumber"), reminderCalls)
        assertEquals(emptyList(), doneCalls)
    }

    @Test
    fun aRoutineActionStoresTheStatusAndTellsThePlatform() = runTest {
        actions.setRoutineStatus("vit", "2026-10-12", "am", OccurrenceStatus.SKIPPED)
        assertEquals(listOf("vit 2026-10-12 am SKIPPED"), routineCalls)
        // routinesChanged's default updates the widgets.
        assertEquals(1, hooks.widgetUpdates)
    }
}
