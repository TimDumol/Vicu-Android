package com.rendyhd.vicu.data.repository

import com.rendyhd.vicu.domain.model.SyncedPrefKeys
import com.rendyhd.vicu.domain.model.TaskOrder
import com.rendyhd.vicu.domain.model.TaskOrderSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The task order as synced settings (docs/cross-app-semantics-v1.md, sections 11.3 and 12). */
class TaskOrderPreferencesTest {

    @Test
    fun `nothing chosen is the default order with no overrides`() = runTest {
        assertEquals(TaskOrderSettings(), TaskOrderPreferences(InMemorySyncedEntries()).settings.first())
    }

    @Test
    fun `the global order and project overrides are kept and reset`() = runTest {
        val entries = InMemorySyncedEntries()
        val orders = TaskOrderPreferences(entries)

        orders.setGlobalOrder(TaskOrder.PRIORITY)
        orders.setProjectOrder(7L, TaskOrder.MANUAL)
        orders.setProjectOrder(12L, TaskOrder.TITLE)
        assertEquals(
            TaskOrderSettings(TaskOrder.PRIORITY, mapOf(7L to TaskOrder.MANUAL, 12L to TaskOrder.TITLE)),
            orders.settings.first(),
        )

        orders.setProjectOrder(7L, null)
        assertEquals(mapOf(12L to TaskOrder.TITLE), orders.settings.first().projectOverrides)
        assertEquals(null, entries.entries.first()[SyncedPrefKeys.taskOrderOf(7L)], "a reset is kept as null, so it reaches the other devices")

        orders.clearProjectOverrides()
        assertEquals(TaskOrderSettings(TaskOrder.PRIORITY), orders.settings.first())
    }

    @Test
    fun `choosing the default order writes no value`() = runTest {
        val entries = InMemorySyncedEntries()
        val orders = TaskOrderPreferences(entries)

        orders.setGlobalOrder(TaskOrder.DEFAULT)
        assertFalse(SyncedPrefKeys.TASK_ORDER in entries.entries.first(), "a default is never written")

        orders.setGlobalOrder(TaskOrder.TITLE)
        orders.setGlobalOrder(TaskOrder.DEFAULT)
        assertEquals(mapOf(SyncedPrefKeys.TASK_ORDER to null), entries.entries.first(), "but a reset is")
    }

    @Test
    fun `an order a newer app added acts as the default and is kept`() = runTest {
        val entries = InMemorySyncedEntries(mapOf(SyncedPrefKeys.TASK_ORDER to JsonPrimitive("by_colour"), SyncedPrefKeys.taskOrderOf(3) to JsonPrimitive("by_colour")))
        val settings = TaskOrderPreferences(entries).settings.first()

        assertEquals(TaskOrder.DEFAULT, settings.global)
        assertEquals(mapOf(3L to TaskOrder.DEFAULT), settings.projectOverrides)
        assertEquals(JsonPrimitive("by_colour"), entries.entries.first()[SyncedPrefKeys.TASK_ORDER])
    }
}
