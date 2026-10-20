package com.rendyhd.vicu.location

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LocationRegionPlannerTest {

    private val home = GeoPoint(52.37022, 4.89517)
    private val work = GeoPoint(52.34, 4.87)
    private val far = GeoPoint(48.8566, 2.3522)

    private fun reminder(taskId: Long, at: GeoPoint, trigger: LocationTrigger = LocationTrigger.ARRIVE, radius: Int = 200) =
        LocationReminder(taskId, placeName = "x", latitude = at.latitude, longitude = at.longitude, radiusMeters = radius, trigger = trigger)

    @Test
    fun `reminders at one place with the same radius and trigger share a region`() {
        val regions = LocationRegionPlanner.plan(
            listOf(reminder(1, home), reminder(2, home), reminder(3, home, LocationTrigger.LEAVE), reminder(4, home, radius = 500)),
            capacity = 20,
        )

        assertEquals(3, regions.size)
        assertEquals(listOf(1L, 2L), regions.single { it.trigger == LocationTrigger.ARRIVE && it.radiusMeters == 200 }.taskIds)
    }

    @Test
    fun `the region id is stable and changes with the place - radius or trigger`() {
        val id = LocationRegionPlanner.regionId(reminder(1, home))

        assertEquals(id, LocationRegionPlanner.regionId(reminder(9, home)))
        assertNotEquals(id, LocationRegionPlanner.regionId(reminder(1, work)))
        assertNotEquals(id, LocationRegionPlanner.regionId(reminder(1, home, radius = 100)))
        assertNotEquals(id, LocationRegionPlanner.regionId(reminder(1, home, LocationTrigger.LEAVE)))
        assertTrue(id.length <= 100, "Play services takes request ids up to 100 characters")
    }

    @Test
    fun `over capacity the nearest regions are kept`() {
        val regions = LocationRegionPlanner.plan(
            listOf(reminder(1, far), reminder(2, work), reminder(3, home)),
            capacity = 2,
            near = home,
        )

        assertEquals(listOf(3L, 2L), regions.map { it.taskIds.single() })
    }

    @Test
    fun `over capacity without a position the regions with the most tasks are kept`() {
        val regions = LocationRegionPlanner.plan(
            listOf(reminder(1, far), reminder(2, work), reminder(3, work)),
            capacity = 1,
        )

        assertEquals(listOf(2L, 3L), regions.single().taskIds)
    }

    @Test
    fun `distance is the great-circle distance`() {
        // Amsterdam to Paris, about 430 km.
        assertTrue(abs(LocationRegionPlanner.distanceMeters(home, far) - 430_000) < 5_000)
        assertEquals(0.0, LocationRegionPlanner.distanceMeters(home, home))
    }
}
