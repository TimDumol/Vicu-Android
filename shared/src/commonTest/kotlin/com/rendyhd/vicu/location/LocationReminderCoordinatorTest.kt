package com.rendyhd.vicu.location

import com.rendyhd.vicu.data.local.NotificationPrefsStore
import com.rendyhd.vicu.data.local.TempIdGenerator
import com.rendyhd.vicu.data.remote.createApiJson
import com.rendyhd.vicu.data.repository.InMemoryPreferencesDataStore
import com.rendyhd.vicu.data.repository.InMemorySyncedEntries
import kotlinx.coroutines.flow.first
import com.rendyhd.vicu.domain.model.Task
import com.rendyhd.vicu.ui.FakeTaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which regions are watched for which reminders, and what a region event shows. */
class LocationReminderCoordinatorTest {

    private val home = GeoPoint(52.37022, 4.89517)
    private val synced = InMemorySyncedEntries()
    private val store = LocationReminderStore(InMemoryPreferencesDataStore(), createApiJson())
    private val positions = SavedPlacesStore(InMemoryPreferencesDataStore(), createApiJson())
    private val locations = LocationRepository(synced, positions, store)
    private val tasks = FakeTaskRepository()
    private val tempIds = TempIdGenerator(InMemoryPreferencesDataStore())
    private val prefs = NotificationPrefsStore(InMemoryPreferencesDataStore())
    private val geofencer = RecordingGeofencer()
    private val notifier = RecordingNotifier()

    private val coordinator = LocationReminderCoordinator(
        locations = locations,
        taskRepository = tasks,
        tempIds = tempIds,
        geofencer = geofencer,
        notifier = notifier,
        notificationPrefs = prefs,
        // start() is not called: each test plans by hand.
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun reminder(taskId: Long, trigger: LocationTrigger = LocationTrigger.ARRIVE) = LocationReminder(
        taskId = taskId,
        placeId = null,
        placeName = "Home",
        latitude = home.latitude,
        longitude = home.longitude,
        trigger = trigger,
    )

    @Test
    fun `open tasks are watched and a completed one is dropped`() = runTest {
        tasks.put(Task(id = 1, title = "Water the plants"))
        tasks.put(Task(id = 2, title = "Done already", done = true))
        store.put(reminder(1))
        store.put(reminder(2))

        coordinator.replan()

        assertEquals(listOf(1L), geofencer.applied.last().single().taskIds)
        assertEquals(listOf(1L), store.all().map { it.taskId })
    }

    @Test
    fun `a missing task keeps its reminder until a pruning plan`() = runTest {
        store.put(reminder(5))

        coordinator.replan()
        assertEquals(listOf(5L), store.all().map { it.taskId })
        assertTrue(geofencer.applied.last().isEmpty())

        coordinator.replan(pruneMissing = true)
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `a reminder of a task created offline follows it to the server id`() = runTest {
        store.put(reminder(-3))
        tempIds.rememberRealId(-3, 42)
        tasks.put(Task(id = 42, title = "Buy milk"))

        coordinator.replan()

        assertEquals(listOf(42L), store.all().map { it.taskId })
        assertEquals(listOf(42L), geofencer.applied.last().single().taskIds)
    }

    @Test
    fun `arriving shows the arrive reminders of that region once`() = runTest {
        tasks.put(Task(id = 1, title = "Water the plants"))
        tasks.put(Task(id = 2, title = "Take out the bins"))
        store.put(reminder(1))
        store.put(reminder(2, LocationTrigger.LEAVE))
        val arriveRegion = LocationRegionPlanner.regionId(reminder(1))

        coordinator.onRegionEvent(arriveRegion, entered = true)
        coordinator.onRegionEvent(arriveRegion, entered = true)

        assertEquals(listOf(Shown(1, "Water the plants", "Arriving at Home")), notifier.shown)
        assertNull(store.all().firstOrNull { it.taskId == 1L }, "a fired reminder is removed")
        assertEquals(listOf(2L), store.all().map { it.taskId })
    }

    @Test
    fun `leaving does not fire an arrive reminder`() = runTest {
        tasks.put(Task(id = 1, title = "Water the plants"))
        store.put(reminder(1))

        coordinator.onRegionEvent(LocationRegionPlanner.regionId(reminder(1)), entered = false)

        assertTrue(notifier.shown.isEmpty())
        assertEquals(listOf(1L), store.all().map { it.taskId })
    }

    @Test
    fun `a task completed in the meantime shows nothing`() = runTest {
        tasks.put(Task(id = 1, title = "Water the plants", done = true))
        store.put(reminder(1))

        coordinator.onRegionEvent(LocationRegionPlanner.regionId(reminder(1)), entered = true)

        assertTrue(notifier.shown.isEmpty())
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `nothing shows when task reminders are off`() = runTest {
        prefs.setTaskRemindersEnabled(false)
        tasks.put(Task(id = 1, title = "Water the plants"))
        store.put(reminder(1))

        coordinator.onRegionEvent(LocationRegionPlanner.regionId(reminder(1)), entered = true)

        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `the same regions are not handed over twice unless forced`() = runTest {
        tasks.put(Task(id = 1, title = "Water the plants"))
        store.put(reminder(1))

        coordinator.replan()
        coordinator.replan()
        assertEquals(1, geofencer.applied.size)

        coordinator.replan(force = true)
        assertEquals(2, geofencer.applied.size)
    }

    @Test
    fun `a synced reminder is watched where this phone knows the place and cleared everywhere once it fires`() = runTest {
        tasks.put(Task(id = 1, title = "Water the plants"))
        synced.set(SyncedLocation.placeKey("home"), SyncedLocation.placeValue("Home"))
        synced.set(SyncedLocation.reminderKey(1), SyncedLocation.reminderValue("home", LocationTrigger.ARRIVE, 200))

        coordinator.replan()
        assertTrue(geofencer.applied.isEmpty() || geofencer.applied.last().isEmpty(), "no position here yet")

        positions.put(Place("home", "Home", home.latitude, home.longitude))
        coordinator.replan()
        val region = geofencer.applied.last().single()
        assertEquals(listOf(1L), region.taskIds)

        coordinator.onRegionEvent(region.id, entered = true)

        assertEquals(listOf(Shown(1, "Water the plants", "Arriving at Home")), notifier.shown)
        assertTrue(SyncedLocation.reminderKey(1) in synced.entries.first())
        assertNull(synced.entries.first()[SyncedLocation.reminderKey(1)], "removed on every device")
    }

    @Test
    fun `completing a task removes its synced reminder`() = runTest {
        tasks.put(Task(id = 1, title = "Water the plants", done = true))
        synced.set(SyncedLocation.placeKey("home"), SyncedLocation.placeValue("Home"))
        synced.set(SyncedLocation.reminderKey(1), SyncedLocation.reminderValue("home", LocationTrigger.ARRIVE, 200))

        coordinator.replan()

        assertNull(synced.entries.first()[SyncedLocation.reminderKey(1)])
    }

    private data class Shown(val taskId: Long, val title: String, val body: String)

    private class RecordingGeofencer : PlatformGeofencer {
        override val capacity: Int = LocationRegionPlanner.IOS_CAPACITY
        val applied = mutableListOf<List<LocationRegion>>()
        override suspend fun apply(regions: List<LocationRegion>) {
            applied += regions
        }
    }

    private class RecordingNotifier : LocationNotifier {
        val shown = mutableListOf<Shown>()
        override fun show(taskId: Long, title: String, body: String, sound: Boolean) {
            shown += Shown(taskId, title, body)
        }
    }
}
