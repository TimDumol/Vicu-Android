package com.rendyhd.vicu.widget

import com.rendyhd.vicu.auth.InMemoryTokenStorage
import com.rendyhd.vicu.data.local.BehaviorPrefsStore
import com.rendyhd.vicu.data.local.CustomListStore
import com.rendyhd.vicu.data.local.WidgetPrefsStore
import com.rendyhd.vicu.data.local.dao.TaskDao
import com.rendyhd.vicu.data.local.entity.ProjectEntity
import com.rendyhd.vicu.data.local.entity.TaskEntity
import com.rendyhd.vicu.data.repository.FakeProjectDao
import com.rendyhd.vicu.data.repository.FakeTaskDao
import com.rendyhd.vicu.data.repository.InMemoryPreferencesDataStore
import com.rendyhd.vicu.ui.FakeTaskRepository
import com.rendyhd.vicu.util.ClockDay
import com.rendyhd.vicu.util.CustomListContext
import com.rendyhd.vicu.util.FixedTimeSource
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TaskWidgetStatesTest {
    private val zone = TimeZone.of("Europe/Amsterdam")
    private val day = ClockDay(LocalDate(2026, 10, 12), zone)
    private val storage = InMemoryTokenStorage()
    private val todayBoundaries = mutableListOf<String>()

    private val today = (1L..25L).map { TaskEntity(id = it, title = "Task $it", projectId = if (it == 25L) 9 else 1, dueDate = "2026-10-12T10:00:00Z") }

    private val taskDao = object : TaskDao by FakeTaskDao() {
        override suspend fun getTodayTasksSync(startOfTomorrow: String, limit: Int): List<TaskEntity> {
            todayBoundaries += startOfTomorrow
            return today
        }

        override suspend fun getByProjectIdSync(projectId: Long, limit: Int): List<TaskEntity> =
            today.filter { it.projectId == projectId }
    }

    private val projects = FakeProjectDao(
        listOf(
            ProjectEntity(id = 1, title = "Home"),
            ProjectEntity(id = 9, title = "Old", isArchived = true),
        ),
    )

    private val states = TaskWidgetStates(
        taskDao = taskDao,
        projectDao = projects,
        taskRepository = FakeTaskRepository(),
        tokenStorage = storage,
        customListStore = CustomListStore(InMemoryPreferencesDataStore()),
        widgetPrefsStore = WidgetPrefsStore(InMemoryPreferencesDataStore()),
        behaviorPrefsStore = BehaviorPrefsStore(InMemoryPreferencesDataStore()),
        time = FixedTimeSource(Instant.parse("2026-10-12T08:00:00Z"), zone),
        collaborationContext = { CustomListContext.NONE },
    )

    @Test
    fun aWidgetShowsTwentyRowsAndCountsAllWithoutArchivedProjects() = runTest {
        val state = states.prepare(day).build(WidgetConfig(WidgetViewType.TODAY, "", "Today"))
        // Task 25 belongs to an archived project.
        assertEquals(24, state.totalCount)
        assertEquals(TaskWidgetStates.MAX_WIDGET_TASKS, state.tasks.size)
        assertEquals("Home", state.tasks.first().projectName)
        assertNull(state.error)
        // The boundary of the day it was built for: the start of the local tomorrow.
        assertEquals(listOf("2026-10-12T22:00:00Z"), todayBoundaries)
    }

    @Test
    fun aWidgetForAnArchivedProjectSaysSo() = runTest {
        val state = states.prepare(day).build(WidgetConfig(WidgetViewType.PROJECT, "9", "Old"))
        assertEquals(TaskWidgetStates.PROJECT_UNAVAILABLE, state.error)
        assertTrue(state.tasks.isEmpty())
    }

    @Test
    fun theWidgetPreferencesAreCarried() = runTest {
        val state = states.prepare(day).build(WidgetConfig(WidgetViewType.PROJECT, "1", "Home"))
        assertTrue(state.smartAdd)
        assertTrue(state.contextNav)
        assertEquals(0L, state.addToProjectId)
        assertEquals("Home", state.viewName)
    }

    @Test
    fun signedOutNeedsAServerAndAToken() = runTest {
        assertFalse(states.isSignedIn())
        storage.storeVikunjaUrl("https://vikunja.test")
        assertFalse(states.isSignedIn())
        storage.storeApiToken("tk_1", 0L)
        assertTrue(states.isSignedIn())
        assertEquals(TaskWidgetStates.SIGNED_OUT, states.signedOut().error)
    }
}
