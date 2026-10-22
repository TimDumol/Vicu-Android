package com.rendyhd.vicu.ui.screens.taskdetail

import com.rendyhd.vicu.auth.AuthManager
import com.rendyhd.vicu.auth.FakeNetworkMonitor
import com.rendyhd.vicu.auth.InMemoryTokenStorage
import com.rendyhd.vicu.auth.RecordingAuthHooks
import com.rendyhd.vicu.data.local.BehaviorPrefsStore
import com.rendyhd.vicu.data.local.NlpPrefsStore
import com.rendyhd.vicu.data.repository.InMemoryPreferencesDataStore
import com.rendyhd.vicu.domain.model.Project
import com.rendyhd.vicu.domain.model.Task
import com.rendyhd.vicu.ui.FakeAttachmentRepository
import com.rendyhd.vicu.ui.FakeLabelRepository
import com.rendyhd.vicu.ui.FakeProjectRepository
import com.rendyhd.vicu.ui.FakeTaskRepository
import com.rendyhd.vicu.util.AppMessages
import com.rendyhd.vicu.util.DayClock
import com.rendyhd.vicu.util.DueDates
import com.rendyhd.vicu.util.FakePlatformFiles
import com.rendyhd.vicu.util.WhenQuickChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The editor's quick moves: the due date moves with its time kept, and Undo puts it back. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModelQuickMoveTest {

    @BeforeTest
    fun setMain() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun resetMain() {
        Dispatchers.resetMain()
    }

    private val tuesday = LocalDate(2026, 10, 20)
    private val thursday = LocalDate(2026, 10, 22)
    private val tomorrow = WhenQuickChoice(WhenQuickChoice.Id.TOMORROW, "Tomorrow", thursday)

    private class Rig {
        val tasks = FakeTaskRepository()
        val messages = AppMessages()
        private val scope = CoroutineScope(SupervisorJob())
        private val auth = AuthManager(
            platformAuthHooks = RecordingAuthHooks(),
            tokenStorage = InMemoryTokenStorage(),
            apiServiceProvider = { error("no network in this test") },
            appScope = scope,
            networkMonitor = FakeNetworkMonitor(),
        )
        val dayClock = DayClock(scope, ticking = false)

        fun close() = scope.cancel()

        fun viewModel() = TaskDetailViewModel(
            taskRepository = tasks,
            labelRepository = FakeLabelRepository(),
            attachmentRepository = FakeAttachmentRepository(),
            projectRepository = FakeProjectRepository(listOf(Project(id = 1, title = "Inbox"))),
            authManager = auth,
            behaviorPrefsStore = BehaviorPrefsStore(InMemoryPreferencesDataStore()),
            nlpPrefsStore = NlpPrefsStore(InMemoryPreferencesDataStore()),
            platformFiles = FakePlatformFiles(),
            appMessages = messages,
            dayClock = dayClock,
        )
    }

    @Test
    fun `a quick move keeps the time and undo puts the date back`() = runTest {
        val rig = Rig()
        val zone = rig.dayClock.day.value.zone
        val before = LocalDateTime(tuesday, LocalTime(14, 0)).toInstant(zone).toString()
        rig.tasks.put(Task(id = 42, title = "Call the plumber", projectId = 1, dueDate = before))
        val vm = rig.viewModel()
        vm.loadTask(42)
        runCurrent()

        vm.moveDue(tomorrow)
        assertEquals(movedDue(before, thursday, zone), vm.uiState.value.task?.dueDate)

        val message = rig.messages.messages.first()
        assertEquals("Due tomorrow", message.text)
        assertEquals("Undo", message.actionLabel)
        assertNotNull(message.onAction).invoke()
        assertEquals(before, vm.uiState.value.task?.dueDate)

        vm.saveIfChanged()
        advanceUntilIdle()
        rig.close()
    }

    @Test
    fun `undo after the editor moved on writes the stored task`() = runTest {
        val rig = Rig()
        val zone = rig.dayClock.day.value.zone
        val before = DueDates.dateOnlyDue(tuesday, zone).toString()
        rig.tasks.put(Task(id = 42, title = "Call the plumber", projectId = 1, dueDate = before))
        rig.tasks.put(Task(id = 7, title = "Book dentist", projectId = 1))
        val vm = rig.viewModel()
        vm.loadTask(42)
        runCurrent()

        vm.moveDue(tomorrow)
        val undo = assertNotNull(rig.messages.messages.first().onAction)
        // Opening another task saves the move first.
        vm.loadTask(7)
        advanceUntilIdle()
        assertEquals(movedDue(before, thursday, zone), rig.tasks.current(42)?.dueDate)

        undo()
        advanceUntilIdle()
        assertEquals(before, rig.tasks.current(42)?.dueDate)
        assertEquals("Book dentist", vm.uiState.value.task?.title)
        rig.close()
    }
}
