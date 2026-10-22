package com.rendyhd.vicu.location

import com.rendyhd.vicu.data.remote.createApiJson
import com.rendyhd.vicu.data.repository.InMemoryPreferencesDataStore
import com.rendyhd.vicu.data.repository.InMemorySyncedEntries
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Synced place names joined with this phone's positions, and where each reminder is kept. */
class LocationRepositoryTest {

    private val home = GeoPoint(52.37022, 4.89517)
    private val synced = InMemorySyncedEntries()
    private val positions = SavedPlacesStore(InMemoryPreferencesDataStore(), createApiJson())
    private val local = LocationReminderStore(InMemoryPreferencesDataStore(), createApiJson())
    private val locations = LocationRepository(synced, positions, local)

    private suspend fun entries() = synced.entries.first()

    @Test
    fun `a saved place syncs its name and keeps its position on the phone`() = runTest {
        val place = locations.addPlace(" Home ", home, "Main street 1")

        assertEquals(SyncedLocation.placeValue("Home"), entries()[SyncedLocation.placeKey(place.id)])
        assertEquals(listOf(SavedPlace(place.id, "Home", home, "Main street 1")), locations.allPlaces())
    }

    @Test
    fun `a place saved on another phone has no position until it is set here`() = runTest {
        synced.set(SyncedLocation.placeKey("work"), SyncedLocation.placeValue("Work"))
        val place = locations.allPlaces().single()
        assertNull(place.position)

        locations.setPosition(place, home)

        assertEquals(home, locations.allPlaces().single().position)
    }

    @Test
    fun `a reminder at a saved place on a server task syncs without a position`() = runTest {
        val place = locations.addPlace("Home", home)

        locations.setReminder(locations.reminderAt(7, place, LocationTrigger.LEAVE, 500))

        assertEquals(SyncedLocation.reminderValue(place.id, LocationTrigger.LEAVE, 500), entries()[SyncedLocation.reminderKey(7)])
        assertTrue(local.all().isEmpty())
        val reminder = locations.allReminders().single()
        assertEquals(PlaceReminder(7, place.id, "Home", LocationTrigger.LEAVE, 500, home, synced = true), reminder)
    }

    @Test
    fun `a reminder of a task created offline waits on the phone and syncs once the task has its id`() = runTest {
        val place = locations.addPlace("Home", home)
        locations.setReminder(locations.reminderAt(-3, place, LocationTrigger.ARRIVE))
        assertEquals(listOf(-3L), local.all().map { it.taskId })
        assertTrue(SyncedLocation.reminderKey(-3) !in entries())

        locations.followTempId(-3, 42)

        assertTrue(local.all().isEmpty())
        assertEquals(42L, locations.allReminders().single { it.synced }.taskId)
    }

    @Test
    fun `a spot that is no saved place stays on the phone and replaces a synced reminder`() = runTest {
        val place = locations.addPlace("Home", home)
        locations.setReminder(locations.reminderAt(7, place, LocationTrigger.ARRIVE))

        locations.setReminder(LocationReminder(7, null, "Station", 52.0, 4.0))

        assertNull(entries()[SyncedLocation.reminderKey(7)])
        val reminder = locations.allReminders().single()
        assertEquals("Station", reminder.placeName)
        assertEquals(GeoPoint(52.0, 4.0), reminder.position)
    }

    @Test
    fun `renaming a place shows the new name on its reminders`() = runTest {
        val place = locations.addPlace("Home", home)
        locations.setReminder(locations.reminderAt(7, place, LocationTrigger.ARRIVE))

        locations.renamePlace(place.id, "Our flat")

        assertEquals("Our flat", locations.allReminders().single().placeName)
    }

    @Test
    fun `deleting a place removes it with its reminders on every device`() = runTest {
        val place = locations.addPlace("Home", home)
        locations.setReminder(locations.reminderAt(7, place, LocationTrigger.ARRIVE))
        locations.setReminder(locations.reminderAt(-2, place, LocationTrigger.ARRIVE))

        locations.deletePlace(place.id)

        assertTrue(SyncedLocation.placeKey(place.id) in entries())
        assertNull(entries()[SyncedLocation.placeKey(place.id)])
        assertNull(entries()[SyncedLocation.reminderKey(7)])
        assertTrue(locations.allPlaces().isEmpty())
        assertTrue(locations.allReminders().isEmpty())
        assertTrue(positions.all().isEmpty())
    }

    @Test
    fun `tidy drops what another device made stale`() = runTest {
        positions.put(Place("home", "Home", home.latitude, home.longitude))
        synced.set(SyncedLocation.placeKey("home"), SyncedLocation.placeValue("Home"))
        synced.set(SyncedLocation.reminderKey(7), SyncedLocation.reminderValue("home", LocationTrigger.ARRIVE, 200))
        local.put(LocationReminder(8, null, "Station", 52.0, 4.0))
        synced.set(SyncedLocation.reminderKey(8), SyncedLocation.reminderValue("home", LocationTrigger.LEAVE, 200))
        // Another device deleted Home but its reminder's null has not arrived.
        synced.set(SyncedLocation.placeKey("home"), null)

        locations.tidy()

        assertNull(entries()[SyncedLocation.reminderKey(7)])
        assertTrue(local.all().isEmpty(), "the synced reminder of task 8 replaced the one kept here")
        assertTrue(positions.all().isEmpty())
    }

    @Test
    fun `places and reminders from before syncing move once and only when the carrier has none`() = runTest {
        val kept = positions.add("Home", home)
        val other = positions.add("Work", home)
        synced.set(SyncedLocation.placeKey(other.id), SyncedLocation.placeValue("Office"))
        local.put(LocationReminder(7, kept.id, "Home", home.latitude, home.longitude))
        local.put(LocationReminder(-1, kept.id, "Home", home.latitude, home.longitude))
        local.put(LocationReminder(9, null, "Station", 52.0, 4.0))
        // Before the move, a place only this phone knows is still listed.
        assertEquals(listOf("Home", "Office"), locations.allPlaces().map { it.name })

        locations.moveLocalData()

        assertEquals(SyncedLocation.placeValue("Home"), entries()[SyncedLocation.placeKey(kept.id)])
        assertEquals(SyncedLocation.placeValue("Office"), entries()[SyncedLocation.placeKey(other.id)], "the carrier's name is kept")
        assertEquals(SyncedLocation.reminderValue(kept.id, LocationTrigger.ARRIVE, 200), entries()[SyncedLocation.reminderKey(7)])
        assertEquals(listOf(-1L, 9L), local.all().map { it.taskId }.sorted())
        assertTrue(positions.isMoved())

        synced.set(SyncedLocation.placeKey(kept.id), null)
        locations.moveLocalData()
        assertNull(entries()[SyncedLocation.placeKey(kept.id)], "moves only once")
    }
}
